package ir.xilo.app.data.repository

import ir.xilo.app.data.local.dao.PostDao
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.local.prefs.AnalyticsSessionStore
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.CreatePostRequest
import ir.xilo.app.data.remote.dto.PostResponse
import ir.xilo.app.data.remote.dto.RecordViewRequest
import ir.xilo.app.data.remote.dto.ToggleReactionRequest
import ir.xilo.app.data.remote.dto.UpdatePostRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val apiService: XiloApiService,
    private val postDao: PostDao,
    private val analyticsSessionStore: AnalyticsSessionStore,
    private val json: Json
) {
    private val likeMutexes = ConcurrentHashMap<String, Mutex>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseDateToEpoch(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L
        return try {
            // Remove timezone suffix if present for simple parsing
            val cleanStr = dateStr.substringBefore("Z").substringBefore("+")
            dateFormat.parse(cleanStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    suspend fun getPostSlugById(id: String): String? = postDao.getPostById(id)?.slug

    fun getFeed(): Flow<List<PostEntity>> = postDao.getFeedFlow()

    suspend fun refreshFeed(): Result<Unit> {
        return try {
            val responseMap = apiService.listPosts(limit = 40)
            val dataElement = responseMap["data"] ?: throw Exception("Invalid response structure")
            val postsList = json.decodeFromJsonElement<List<PostResponse>>(dataElement)

            // Assign feedRank from API order so like/repost cannot reshuffle the list.
            val entities = postsList.mapIndexed { index, dto ->
                dto.toEntity(feedRank = index)
            }

            postDao.clearAllPosts()
            postDao.insertPosts(entities.take(50))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPostBySlug(slug: String): Result<PostEntity> {
        return try {
            // First check database
            val local = postDao.getPostBySlug(slug)
            if (local != null) {
                // Background refresh — preserve feedRank so the home list stays stable.
                // Skip while a like toggle is in flight so a stale GET cannot restore the heart.
                val likeLocked = likeMutexes[local.id]?.isLocked == true
                if (!likeLocked) {
                    try {
                        val remote = apiService.getPostBySlug(slug)
                        val updated = remote.toEntity(feedRank = local.feedRank)
                        postDao.insertPost(updated)
                    } catch (_: Exception) {
                    }
                }
                Result.success(postDao.getPostById(local.id) ?: local)
            } else {
                val remote = apiService.getPostBySlug(slug)
                val entity = remote.toEntity(feedRank = Int.MAX_VALUE)
                postDao.insertPost(entity)
                Result.success(entity)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createPost(
        title: String,
        content: String,
        audioUrl: String? = null,
    ): Result<PostEntity> {
        return try {
            val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            // Backend expects Tiptap JSON; wrap plain text with structured encoding (safe escaping).
            val tiptapJson = buildTiptapDoc(content)
            val tags = ir.xilo.app.core.util.HashtagParser.extract(content)

            val request = CreatePostRequest(
                title = title,
                slug = slug + "-" + System.currentTimeMillis().toString().takeLast(4),
                content = tiptapJson,
                contentMd = content,
                excerpt = content.take(100),
                audioUrl = audioUrl?.takeIf { it.isNotBlank() },
                tags = tags.takeIf { it.isNotEmpty() },
                status = "published",
            )
            val remote = apiService.createPost(request)
            val entity = remote.toEntity(feedRank = 0)
            postDao.insertPost(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleLike(postId: String, currentLikeState: Boolean): Result<Boolean> {
        val mutex = likeMutexes.getOrPut(postId) { Mutex() }
        // Drop rapid double-taps that would toggle the server twice and bring the like back.
        if (!mutex.tryLock()) {
            val latest = postDao.getPostById(postId)?.isLiked ?: currentLikeState
            return Result.success(latest)
        }
        return try {
            val snapshot = postDao.getPostById(postId)
                ?: return Result.failure(IllegalStateException("Post not found: $postId"))
            // Prefer Room as source of truth — UI `currentLikeState` can be stale on fast taps.
            val previousLiked = snapshot.isLiked
            val previousCount = snapshot.likeCount
            val wantLiked = !previousLiked
            val optimisticCount = (previousCount + if (wantLiked) 1 else -1).coerceAtLeast(0)
            postDao.updateLikeState(postId, wantLiked, optimisticCount)

            try {
                apiService.toggleReaction(
                    type = "post",
                    id = postId,
                    request = ToggleReactionRequest(reaction = "like"),
                )

                // Production may still have legacy "heart" rows. When unliking, clear any
                // remaining like/heart viewer reactions so the heart cannot stick.
                if (!wantLiked) {
                    clearRemainingLikeReactions(postId = postId, slug = snapshot.slug)
                }

                val confirmed = runCatching {
                    apiService.getPostBySlug(snapshot.slug.ifBlank { postId })
                }.getOrNull()
                val liked = confirmed?.resolvedIsLiked() ?: wantLiked
                val count = confirmed?.resolvedLikeCount() ?: optimisticCount
                postDao.updateLikeState(postId, liked, count.coerceAtLeast(0))
                Result.success(liked)
            } catch (e: Exception) {
                postDao.updateLikeState(postId, previousLiked, previousCount)
                Result.failure(e)
            }
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun clearRemainingLikeReactions(postId: String, slug: String) {
        val remote = runCatching {
            apiService.getPostBySlug(slug.ifBlank { postId })
        }.getOrNull() ?: return
        val leftovers = remote.viewerReactions.filter {
            it.equals("like", ignoreCase = true) || it.equals("heart", ignoreCase = true)
        }
        for (reaction in leftovers.distinctBy { it.lowercase() }) {
            runCatching {
                apiService.toggleReaction(
                    type = "post",
                    id = postId,
                    request = ToggleReactionRequest(reaction = reaction),
                )
            }
        }
    }

    private fun PostResponse.toEntity(feedRank: Int): PostEntity = PostEntity(
        id = id,
        authorId = authorId,
        authorName = author?.displayName ?: "",
        authorUsername = author?.username ?: "",
        authorAvatar = author?.avatarUrl ?: "",
        title = title,
        slug = slug,
        content = content,
        excerpt = excerpt,
        coverImageUrl = coverImageUrl,
        audioUrl = audioUrl,
        likeCount = resolvedLikeCount(),
        commentCount = commentCount,
        repostCount = repostCount,
        viewCount = viewCount,
        isLiked = resolvedIsLiked(),
        isBookmarked = isBookmarked,
        isReposted = isReposted,
        // Prefer publish time to match backend feed ordering.
        createdAt = parseDateToEpoch(publishedAt?.takeIf { it.isNotBlank() } ?: createdAt),
        feedRank = feedRank
    )

    suspend fun recordView(postId: String): Result<Long> {
        return try {
            val response = apiService.recordPostView(
                id = postId,
                request = RecordViewRequest(sessionId = analyticsSessionStore.getSessionId()),
            )
            val local = postDao.getPostById(postId)
            if (local != null) {
                postDao.updatePost(local.copy(viewCount = response.viewCount))
            }
            Result.success(response.viewCount)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBookmarkedPosts(): Result<List<PostEntity>> {
        return try {
            val page = apiService.getBookmarks()
            Result.success(page.data.map { it.toEntity(feedRank = Int.MAX_VALUE) })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(postId: String, currentBookmarkState: Boolean): Result<Boolean> {
        val snapshot = postDao.getPostById(postId)
        val newState = !currentBookmarkState
        // Optimistic UPDATE (not REPLACE) so feed order stays stable.
        if (snapshot != null) {
            postDao.updatePost(snapshot.copy(isBookmarked = newState))
        }

        return try {
            if (currentBookmarkState) {
                apiService.unbookmarkPost(postId)
            } else {
                apiService.bookmarkPost(postId)
            }
            Result.success(newState)
        } catch (e: Exception) {
            if (snapshot != null) {
                postDao.updatePost(snapshot)
            }
            Result.failure(e)
        }
    }

    suspend fun toggleRepost(postId: String, currentRepostState: Boolean): Result<Boolean> {
        val snapshot = postDao.getPostById(postId)
        val newState = !currentRepostState
        return try {
            if (snapshot != null) {
                val newCount = snapshot.repostCount + (if (newState) 1 else -1)
                postDao.updatePost(
                    snapshot.copy(
                        isReposted = newState,
                        repostCount = newCount.coerceAtLeast(0)
                    )
                )
            }

            if (currentRepostState) {
                apiService.unrepostPost(postId)
            } else {
                apiService.repostPost(postId)
            }
            Result.success(newState)
        } catch (e: Exception) {
            if (snapshot != null) {
                postDao.updatePost(snapshot)
            }
            Result.failure(e)
        }
    }

    suspend fun getPostById(id: String): PostEntity? = postDao.getPostById(id)

    suspend fun updatePost(
        postId: String,
        title: String,
        content: String,
        audioUrl: String? = null,
    ): Result<PostEntity> {
        return try {
            val tiptapJson = buildTiptapDoc(content)
            val tags = ir.xilo.app.core.util.HashtagParser.extract(content)

            val remote = apiService.updatePost(
                id = postId,
                request = UpdatePostRequest(
                    title = title,
                    content = tiptapJson,
                    contentMd = content,
                    excerpt = content.take(100),
                    audioUrl = audioUrl ?: "",
                    tags = tags,
                ),
            )
            val local = postDao.getPostById(postId)
            val entity = remote.toEntity(feedRank = local?.feedRank ?: Int.MAX_VALUE)
            postDao.insertPost(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildTiptapDoc(content: String): String =
        buildJsonObject {
            put("type", "doc")
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "paragraph")
                            put(
                                "content",
                                buildJsonArray {
                                    add(
                                        buildJsonObject {
                                            put("type", "text")
                                            put("text", content)
                                        },
                                    )
                                },
                            )
                        },
                    )
                },
            )
        }.toString()

    suspend fun archivePost(postId: String): Result<Unit> {
        return try {
            apiService.updatePost(
                id = postId,
                request = UpdatePostRequest(status = "archived"),
            )
            postDao.deletePostById(postId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deletePost(postId: String): Result<Unit> {
        return try {
            apiService.deletePost(postId)
            postDao.deletePostById(postId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

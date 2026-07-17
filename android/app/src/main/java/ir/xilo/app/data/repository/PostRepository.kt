package ir.xilo.app.data.repository

import ir.xilo.app.data.local.dao.PostDao
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.CreatePostRequest
import ir.xilo.app.data.remote.dto.PostResponse
import ir.xilo.app.data.remote.dto.ToggleReactionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PostRepository @Inject constructor(
    private val apiService: XiloApiService,
    private val postDao: PostDao,
    private val json: Json
) {
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
                try {
                    val remote = apiService.getPostBySlug(slug)
                    val updated = remote.toEntity(feedRank = local.feedRank)
                    postDao.insertPost(updated)
                } catch (_: Exception) {
                }
                Result.success(local)
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

    suspend fun createPost(title: String, content: String): Result<PostEntity> {
        return try {
            val slug = title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
            // Backend expects Tiptap JSON; wrap plain text with structured encoding (safe escaping).
            val tiptapJson = buildJsonObject {
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

            val request = CreatePostRequest(
                title = title,
                slug = slug + "-" + System.currentTimeMillis().toString().takeLast(4),
                content = tiptapJson,
                excerpt = content.take(100)
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
        val snapshot = postDao.getPostById(postId)
        val newState = !currentLikeState
        if (snapshot != null) {
            val newCount = snapshot.likeCount + (if (newState) 1 else -1)
            // Use UPDATE (not REPLACE) so row identity / feed order stay stable.
            postDao.updatePost(
                snapshot.copy(isLiked = newState, likeCount = newCount.coerceAtLeast(0))
            )
        }

        return try {
            apiService.toggleReaction(
                type = "post",
                id = postId,
                request = ToggleReactionRequest(reaction = "like")
            )
            Result.success(newState)
        } catch (e: Exception) {
            // Keep optimistic UI (filled red heart); a later refresh reconciles with the server.
            Result.failure(e)
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
        likeCount = resolvedLikeCount(),
        commentCount = commentCount,
        repostCount = repostCount,
        isLiked = resolvedIsLiked(),
        isBookmarked = isBookmarked,
        isReposted = isReposted,
        // Prefer publish time to match backend feed ordering.
        createdAt = parseDateToEpoch(publishedAt?.takeIf { it.isNotBlank() } ?: createdAt),
        feedRank = feedRank
    )

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
}

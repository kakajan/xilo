package ir.xilo.app.data.repository

import ir.xilo.app.core.util.EmojiReactions
import ir.xilo.app.data.local.dao.PostDao
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.local.prefs.AnalyticsSessionStore
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.decodeListOrEmpty
import ir.xilo.app.data.remote.dto.CreatePostRequest
import ir.xilo.app.data.remote.dto.PostSearchHit
import ir.xilo.app.data.remote.dto.PostResponse
import ir.xilo.app.data.remote.dto.toPostEntity
import ir.xilo.app.data.remote.dto.RecordViewRequest
import ir.xilo.app.data.remote.dto.ToggleReactionRequest
import ir.xilo.app.data.remote.dto.UpdatePostRequest
import androidx.paging.ExperimentalPagingApi
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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
    @Volatile private var feedNextCursor: String? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun nextCursorOf(map: Map<String, kotlinx.serialization.json.JsonElement>): String? {
        val el = map["next_cursor"] ?: return null
        if (el is JsonNull) return null
        return el.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }
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

    suspend fun listDrafts(limit: Int = 20): Result<List<PostEntity>> {
        return try {
            val responseMap = apiService.listPosts(limit = limit, status = "draft")
            val data = responseMap["data"]
            val list = json.decodeListOrEmpty<PostResponse>(data).map { it.toPostEntity() }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPostSlugById(id: String): String? = postDao.getPostById(id)?.slug

    fun getFeed(): Flow<List<PostEntity>> = postDao.getFeedFlow()

    @OptIn(ExperimentalPagingApi::class)
    fun feedPager(): Flow<PagingData<PostEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 10,
                prefetchDistance = 4,
                enablePlaceholders = false,
            ),
            remoteMediator = FeedRemoteMediator(this),
            pagingSourceFactory = { postDao.feedPagingSource() },
        ).flow
    }

    suspend fun refreshFeed(): Result<Unit> {
        return try {
            val responseMap = apiService.listPosts(limit = 20)
            val postsList = json.decodeListOrEmpty<PostResponse>(responseMap["data"])
            feedNextCursor = nextCursorOf(responseMap)

            val entities = postsList.mapIndexed { index, dto ->
                dto.toPostEntity(feedRank = index)
            }

            postDao.clearAllPosts()
            postDao.insertPosts(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadMoreFeed(): Result<Unit> {
        val cursor = feedNextCursor ?: return Result.success(Unit)
        return try {
            val responseMap = apiService.listPosts(cursor = cursor, limit = 20)
            val postsList = json.decodeListOrEmpty<PostResponse>(responseMap["data"])
            feedNextCursor = nextCursorOf(responseMap)
            val rankBase = postDao.maxFeedRank() + 1
            val entities = postsList.mapIndexed { index, dto ->
                dto.toPostEntity(feedRank = rankBase + index)
            }
            if (entities.isNotEmpty()) {
                postDao.insertPosts(entities)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun hasMoreFeed(): Boolean = !feedNextCursor.isNullOrBlank()

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
                        val updated = remote.toPostEntity(feedRank = local.feedRank)
                        postDao.insertPost(updated)
                    } catch (_: Exception) {
                    }
                }
                Result.success(postDao.getPostById(local.id) ?: local)
            } else {
                val remote = apiService.getPostBySlug(slug)
                val entity = remote.toPostEntity(feedRank = Int.MAX_VALUE)
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
        coverImageUrl: String? = null,
        scheduledAt: String? = null,
        quotedPostId: String? = null,
        quotedCommentId: String? = null,
        postType: String? = null,
        linkUrl: String? = null,
        mediaIds: List<String>? = null,
        status: String? = null,
    ): Result<PostEntity> {
        return try {
            val slugBase = title.ifBlank { content }.lowercase()
                .replace(Regex("[^a-z0-9\\u0600-\\u06FF]+"), "-")
                .trim('-')
                .ifBlank { "quote" }
            val tiptapJson = buildTiptapDoc(content)
            val tags = ir.xilo.app.core.util.HashtagParser.extract(content)
            val quoteComment = quotedCommentId?.takeIf { it.isNotBlank() }
            val quotePost = quotedPostId?.takeIf { it.isNotBlank() }.takeIf { quoteComment == null }
            val scheduled = scheduledAt?.takeIf { it.isNotBlank() }
            val resolvedType = postType?.takeIf { it.isNotBlank() }

            val request = CreatePostRequest(
                title = title.ifBlank { content.take(80).ifBlank { "نقل‌قول" } },
                slug = slugBase.take(40) + "-" + System.currentTimeMillis().toString().takeLast(4),
                content = tiptapJson,
                contentMd = content,
                excerpt = content.take(100),
                audioUrl = audioUrl?.takeIf { it.isNotBlank() },
                coverImageUrl = coverImageUrl?.takeIf { it.isNotBlank() },
                tags = tags.takeIf { it.isNotEmpty() },
                status = status?.takeIf { it.isNotBlank() }
                    ?: if (scheduled != null) "scheduled" else "published",
                quotedPostId = quotePost,
                quotedCommentId = quoteComment,
                scheduledAt = scheduled,
                postType = resolvedType,
                linkUrl = linkUrl?.takeIf { it.isNotBlank() },
                mediaIds = mediaIds?.takeIf { it.isNotEmpty() },
            )
            val remote = apiService.createPost(request)
            val entity = remote.toPostEntity(feedRank = 0)
            postDao.insertPost(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Ensure [seed] is in Room so like toggles from tag/discover/profile work,
     * then toggle. Prefer Room as source of truth when a row already exists.
     */
    suspend fun toggleLike(seed: PostEntity): Result<Boolean> {
        val existing = postDao.getPostById(seed.id)
        if (existing == null) {
            postDao.insertPost(seed.copy(feedRank = Int.MAX_VALUE))
        }
        return toggleLike(seed.id, existing?.isLiked ?: seed.isLiked)
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
            // Prefer Room as source of truth — UI `currentLikeState` can be stale on fast taps.
            val previousLiked = snapshot?.isLiked ?: currentLikeState
            val previousCount = snapshot?.likeCount ?: 0
            val wantLiked = !previousLiked
            val optimisticCount = (previousCount + if (wantLiked) 1 else -1).coerceAtLeast(0)
            val optimisticJson = snapshot?.let {
                likeReactionsJson(it.reactionsJson, wantLiked, optimisticCount)
            }
            if (snapshot != null && optimisticJson != null) {
                postDao.updateLikeState(postId, wantLiked, optimisticCount, optimisticJson)
            }

            try {
                apiService.toggleReaction(
                    type = "post",
                    id = postId,
                    request = ToggleReactionRequest(reaction = "like"),
                )

                // Backend like/heart is one family. If unlike left a legacy heart, clear once
                // with "like" only — never toggle leftover keys in a loop (that can re-like).
                val slug = snapshot?.slug.orEmpty()
                if (!wantLiked && slug.isNotBlank()) {
                    clearRemainingLikeReactionOnce(postId = postId, slug = slug)
                }

                val confirmed = if (slug.isNotBlank()) {
                    runCatching { apiService.getPostBySlug(slug) }.getOrNull()
                } else {
                    null
                }
                val liked = confirmed?.resolvedIsLiked() ?: wantLiked
                val count = confirmed?.resolvedLikeCount() ?: optimisticCount
                if (snapshot != null || confirmed != null) {
                    if (confirmed != null) {
                        val rank = snapshot?.feedRank ?: Int.MAX_VALUE
                        postDao.insertPost(confirmed.toPostEntity(feedRank = rank))
                    } else if (snapshot != null) {
                        postDao.updateLikeState(
                            postId,
                            liked,
                            count.coerceAtLeast(0),
                            likeReactionsJson(snapshot.reactionsJson, liked, count.coerceAtLeast(0)),
                        )
                    }
                }
                Result.success(liked)
            } catch (e: Exception) {
                if (snapshot != null) {
                    postDao.updateLikeState(
                        postId,
                        previousLiked,
                        previousCount,
                        snapshot.reactionsJson,
                    )
                }
                Result.failure(e)
            }
        } finally {
            mutex.unlock()
        }
    }

    suspend fun toggleEmojiReaction(postId: String, emoji: String): Result<Unit> {
        if (EmojiReactions.isLikeFamily(emoji)) {
            val current = postDao.getPostById(postId)?.isLiked ?: false
            return toggleLike(postId, current).map { }
        }
        val snapshot = postDao.getPostById(postId)
            ?: return Result.failure(IllegalStateException("post not cached"))
        val optimistic = applyEmoji(snapshot, emoji)
        postDao.updatePost(optimistic)
        return try {
            apiService.toggleReaction(
                type = "post",
                id = postId,
                request = ToggleReactionRequest(reaction = EmojiReactions.apiKey(emoji)),
            )
            val confirmed = snapshot.slug.takeIf { it.isNotBlank() }?.let { slug ->
                runCatching { apiService.getPostBySlug(slug) }.getOrNull()
            }
            if (confirmed != null) {
                postDao.insertPost(confirmed.toPostEntity(feedRank = snapshot.feedRank))
            }
            Result.success(Unit)
        } catch (e: Exception) {
            postDao.updatePost(snapshot)
            Result.failure(e)
        }
    }

    suspend fun toggleEmojiReaction(seed: PostEntity, emoji: String): Result<Unit> {
        val existing = postDao.getPostById(seed.id)
        if (existing == null) {
            postDao.insertPost(seed.copy(feedRank = Int.MAX_VALUE))
        }
        return toggleEmojiReaction(seed.id, emoji)
    }

    suspend fun restoreArchivedPost(post: PostEntity): Result<Unit> {
        return try {
            apiService.updatePost(
                id = post.id,
                request = UpdatePostRequest(status = "published"),
            )
            postDao.insertPost(post)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun likeReactionsJson(raw: String?, liked: Boolean, count: Int): String {
        val current = EmojiReactions.decode(raw).toMutableList()
        val index = current.indexOfFirst { EmojiReactions.isLikeFamily(it.reaction) }
        when {
            liked && count > 0 -> {
                val next = ir.xilo.app.core.util.ReactionCount("❤️", count.toLong(), true)
                if (index >= 0) current[index] = next else current.add(0, next)
            }
            index >= 0 -> current.removeAt(index)
        }
        return EmojiReactions.encode(current)
    }

    private fun applyEmoji(post: PostEntity, emoji: String): PostEntity {
        val json = EmojiReactions.toggleSelf(post.reactionsJson, emoji)
        if (!EmojiReactions.isLikeFamily(emoji)) {
            return post.copy(reactionsJson = json)
        }
        val heart = EmojiReactions.decode(json).firstOrNull { EmojiReactions.isLikeFamily(it.reaction) }
        return post.copy(
            reactionsJson = json,
            isLiked = heart?.reacted == true,
            likeCount = heart?.count?.toInt() ?: 0,
        )
    }

    private suspend fun clearRemainingLikeReactionOnce(postId: String, slug: String) {
        val remote = runCatching {
            apiService.getPostBySlug(slug.ifBlank { postId })
        }.getOrNull() ?: return
        if (!remote.resolvedIsLiked()) return
        runCatching {
            apiService.toggleReaction(
                type = "post",
                id = postId,
                request = ToggleReactionRequest(reaction = "like"),
            )
        }
    }

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
            Result.success(page.data.map { it.toPostEntity(feedRank = Int.MAX_VALUE) })
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

    suspend fun loadPostForEdit(postId: String): PostEntity? {
        val local = postDao.getPostById(postId)
        val slug = local?.slug?.takeIf { it.isNotBlank() }
        if (!slug.isNullOrBlank()) {
            return getPostBySlug(slug).getOrNull() ?: local
        }
        return getPostBySlug(postId).getOrNull() ?: local
    }

    suspend fun searchPosts(query: String, limit: Int = 8): Result<List<PostSearchHit>> {
        return try {
            val res = apiService.searchPosts(query = query, limit = limit)
            Result.success(res.data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updatePost(
        postId: String,
        title: String,
        content: String,
        audioUrl: String? = null,
        coverImageUrl: String? = null,
        postType: String? = null,
        linkUrl: String? = null,
        mediaIds: List<String>? = null,
        quotedPostId: String? = null,
        quotedCommentId: String? = null,
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
                    audioUrl = audioUrl,
                    tags = tags,
                    coverImageUrl = coverImageUrl,
                    postType = postType,
                    linkUrl = linkUrl,
                    mediaIds = mediaIds,
                    quotedPostId = quotedPostId,
                    quotedCommentId = quotedCommentId,
                ),
            )
            val local = postDao.getPostById(postId)
            val entity = remote.toPostEntity(feedRank = local?.feedRank ?: Int.MAX_VALUE)
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
                    val paragraphs = content.split("\n")
                    if (paragraphs.isEmpty()) {
                        add(tiptapParagraph(""))
                    } else {
                        paragraphs.forEach { line ->
                            add(tiptapParagraph(line))
                        }
                    }
                },
            )
        }.toString()

    private fun tiptapParagraph(text: String) = buildJsonObject {
        put("type", "paragraph")
        if (text.isNotEmpty()) {
            put(
                "content",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", text)
                        },
                    )
                },
            )
        }
    }

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

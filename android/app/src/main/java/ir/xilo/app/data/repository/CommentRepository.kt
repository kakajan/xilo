package ir.xilo.app.data.repository

import ir.xilo.app.data.local.dao.CommentDao
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.BookmarkedCommentResponse
import ir.xilo.app.data.remote.dto.CommentResponse
import ir.xilo.app.data.remote.dto.CreateCommentRequest
import ir.xilo.app.data.remote.dto.ToggleReactionRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommentRepository @Inject constructor(
    private val apiService: XiloApiService,
    private val commentDao: CommentDao,
    private val json: Json
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseDateToEpoch(dateStr: String): Long {
        return try {
            val cleanStr = dateStr.substringBefore("Z").substringBefore("+")
            dateFormat.parse(cleanStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun getComments(postId: String): Flow<List<CommentEntity>> = commentDao.getCommentsForPostFlow(postId)

    fun getRecentComments(limit: Int = 50): Flow<List<CommentEntity>> =
        commentDao.getRecentCommentsFlow(limit)

    suspend fun refreshComments(postId: String): Result<Unit> {
        return try {
            val responseMap = apiService.listComments(postId)
            val dataElement = responseMap["data"] ?: throw Exception("Invalid response structure")
            val commentsList = json.decodeFromJsonElement<List<CommentResponse>>(dataElement)

            val entities = flattenComments(commentsList).map { it.toEntity() }

            commentDao.clearCommentsForPost(postId)
            commentDao.insertComments(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createComment(postId: String, content: String, parentId: String? = null, rootId: String? = null): Result<CommentEntity> {
        return try {
            val response = apiService.createComment(postId, CreateCommentRequest(content, parentId, rootId))
            val entity = response.toEntity()
            commentDao.insertComments(listOf(entity))
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Toggles a comment reaction with client-side mutual exclusivity between like and dislike.
     */
    suspend fun toggleCommentReaction(
        commentId: String,
        reaction: String,
        currentlyActive: Boolean,
        oppositeActive: Boolean = false
    ): Result<Boolean> {
        return try {
            val opposite = when (reaction) {
                "like" -> "dislike"
                "dislike" -> "like"
                else -> null
            }
            if (oppositeActive && opposite != null && !currentlyActive) {
                apiService.toggleReaction(
                    type = "comment",
                    id = commentId,
                    request = ToggleReactionRequest(reaction = opposite)
                )
            }
            apiService.toggleReaction(
                type = "comment",
                id = commentId,
                request = ToggleReactionRequest(reaction = reaction)
            )
            applyOptimisticReaction(commentId, reaction, currentlyActive, oppositeActive)
            Result.success(!currentlyActive)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getBookmarkedComments(): Result<List<BookmarkedCommentResponse>> {
        return try {
            Result.success(apiService.getCommentBookmarks().data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(commentId: String, currentBookmarkState: Boolean): Result<Boolean> {
        val snapshot = commentDao.getCommentById(commentId)
        val newState = !currentBookmarkState
        if (snapshot != null) {
            commentDao.updateComment(snapshot.copy(isBookmarked = newState))
        }
        return try {
            if (currentBookmarkState) {
                apiService.unbookmarkComment(commentId)
            } else {
                apiService.bookmarkComment(commentId)
            }
            Result.success(newState)
        } catch (e: Exception) {
            if (snapshot != null) {
                commentDao.updateComment(snapshot)
            }
            Result.failure(e)
        }
    }

    private suspend fun applyOptimisticReaction(
        commentId: String,
        reaction: String,
        currentlyActive: Boolean,
        oppositeActive: Boolean
    ) {
        val existing = commentDao.getCommentById(commentId) ?: return
        val becomingActive = !currentlyActive
        val updated = when (reaction) {
            "like" -> existing.copy(
                isLiked = becomingActive,
                likeCount = (existing.likeCount + if (becomingActive) 1 else -1).coerceAtLeast(0),
                isDisliked = if (becomingActive) false else existing.isDisliked,
                dislikeCount = if (becomingActive && oppositeActive) {
                    (existing.dislikeCount - 1).coerceAtLeast(0)
                } else {
                    existing.dislikeCount
                }
            )
            "dislike" -> existing.copy(
                isDisliked = becomingActive,
                dislikeCount = (existing.dislikeCount + if (becomingActive) 1 else -1).coerceAtLeast(0),
                isLiked = if (becomingActive) false else existing.isLiked,
                likeCount = if (becomingActive && oppositeActive) {
                    (existing.likeCount - 1).coerceAtLeast(0)
                } else {
                    existing.likeCount
                }
            )
            else -> existing
        }
        commentDao.updateComment(updated)
    }

    private fun CommentResponse.toEntity(): CommentEntity = CommentEntity(
        id = id,
        postId = postId,
        authorId = authorId,
        authorName = author?.displayName ?: "",
        authorUsername = author?.username ?: "",
        authorAvatar = author?.avatarUrl ?: "",
        parentId = parentId,
        rootId = rootId,
        depth = depth,
        content = content,
        likeCount = resolvedLikeCount(),
        dislikeCount = resolvedDislikeCount(),
        replyCount = replyCount,
        isLiked = resolvedIsLiked(),
        isDisliked = resolvedIsDisliked(),
        isBookmarked = isBookmarked,
        isPinned = isPinned,
        createdAt = parseDateToEpoch(createdAt)
    )

    companion object {
        /** Depth-first flatten of nested `replies` trees from the list-comments API. */
        fun flattenComments(comments: List<CommentResponse>): List<CommentResponse> {
            val out = ArrayList<CommentResponse>(comments.size)
            fun walk(list: List<CommentResponse>) {
                for (c in list) {
                    out += c.copy(replies = emptyList())
                    if (c.replies.isNotEmpty()) walk(c.replies)
                }
            }
            walk(comments)
            return out
        }
    }
}

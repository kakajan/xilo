package com.example.xilo.data.repository

import com.example.xilo.data.local.dao.CommentDao
import com.example.xilo.data.local.entity.CommentEntity
import com.example.xilo.data.remote.api.XiloApiService
import com.example.xilo.data.remote.dto.CommentResponse
import com.example.xilo.data.remote.dto.CreateCommentRequest
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

            val entities = commentsList.map { dto ->
                CommentEntity(
                    id = dto.id,
                    postId = dto.postId,
                    authorId = dto.authorId,
                    authorName = dto.author?.displayName ?: "",
                    authorUsername = dto.author?.username ?: "",
                    authorAvatar = dto.author?.avatarUrl ?: "",
                    parentId = dto.parentId,
                    rootId = dto.rootId,
                    depth = dto.depth,
                    content = dto.content,
                    likeCount = dto.likeCount,
                    replyCount = dto.replyCount,
                    isLiked = dto.isLiked,
                    isPinned = dto.isPinned,
                    createdAt = parseDateToEpoch(dto.createdAt)
                )
            }

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
            val entity = CommentEntity(
                id = response.id,
                postId = response.postId,
                authorId = response.authorId,
                authorName = response.author?.displayName ?: "",
                authorUsername = response.author?.username ?: "",
                authorAvatar = response.author?.avatarUrl ?: "",
                parentId = response.parentId,
                rootId = response.rootId,
                depth = response.depth,
                content = response.content,
                likeCount = response.likeCount,
                replyCount = response.replyCount,
                isLiked = response.isLiked,
                isPinned = response.isPinned,
                createdAt = parseDateToEpoch(response.createdAt)
            )
            commentDao.insertComments(listOf(entity))
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleCommentReaction(commentId: String, currentLikeState: Boolean): Result<Boolean> {
        return try {
            apiService.toggleReaction(type = "comments", id = commentId, reaction = "like")
            Result.success(!currentLikeState)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

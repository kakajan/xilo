package com.example.xilo.data.repository

import com.example.xilo.data.local.dao.PostDao
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.data.remote.api.XiloApiService
import com.example.xilo.data.remote.dto.CreatePostRequest
import com.example.xilo.data.remote.dto.PostResponse
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
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

    private fun parseDateToEpoch(dateStr: String): Long {
        return try {
            // Remove timezone suffix if present for simple parsing
            val cleanStr = dateStr.substringBefore("Z").substringBefore("+")
            dateFormat.parse(cleanStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    suspend fun getPostSlugById(id: String): String? = postDao.getPostById(id)?.slug

    fun getFeed(): Flow<List<PostEntity>> = postDao.getFeedFlow()

    suspend fun refreshFeed(): Result<Unit> {
        return try {
            val responseMap = apiService.listPosts(limit = 40)
            val dataElement = responseMap["data"] ?: throw Exception("Invalid response structure")
            val postsList = json.decodeFromJsonElement<List<PostResponse>>(dataElement)

            val entities = postsList.map { dto ->
                PostEntity(
                    id = dto.id,
                    authorId = dto.authorId,
                    authorName = dto.author?.displayName ?: "",
                    authorUsername = dto.author?.username ?: "",
                    authorAvatar = dto.author?.avatarUrl ?: "",
                    title = dto.title,
                    slug = dto.slug,
                    content = dto.content,
                    excerpt = dto.excerpt,
                    coverImageUrl = dto.coverImageUrl,
                    likeCount = dto.likeCount,
                    commentCount = dto.commentCount,
                    isLiked = dto.isLiked,
                    isBookmarked = dto.isBookmarked,
                    createdAt = parseDateToEpoch(dto.createdAt)
                )
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
                // Background refresh
                try {
                    val remote = apiService.getPostBySlug(slug)
                    val updated = local.copy(
                        title = remote.title,
                        content = remote.content,
                        coverImageUrl = remote.coverImageUrl,
                        likeCount = remote.likeCount,
                        commentCount = remote.commentCount,
                        isLiked = remote.isLiked,
                        isBookmarked = remote.isBookmarked
                    )
                    postDao.insertPost(updated)
                } catch (e: Exception) {}
                Result.success(local)
            } else {
                val remote = apiService.getPostBySlug(slug)
                val entity = PostEntity(
                    id = remote.id,
                    authorId = remote.authorId,
                    authorName = remote.author?.displayName ?: "",
                    authorUsername = remote.author?.username ?: "",
                    authorAvatar = remote.author?.avatarUrl ?: "",
                    title = remote.title,
                    slug = remote.slug,
                    content = remote.content,
                    excerpt = remote.excerpt,
                    coverImageUrl = remote.coverImageUrl,
                    likeCount = remote.likeCount,
                    commentCount = remote.commentCount,
                    isLiked = remote.isLiked,
                    isBookmarked = remote.isBookmarked,
                    createdAt = parseDateToEpoch(remote.createdAt)
                )
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
            // Backend expects Tiptap JSON, we wrap plain text in a basic Tiptap structure
            val tiptapJson = """{"type":"doc","content":[{"type":"paragraph","content":[{"type":"text","text":"${content.replace("\"", "\\\"")}""}]}]}"""
            
            val request = CreatePostRequest(
                title = title,
                slug = slug + "-" + System.currentTimeMillis().toString().takeLast(4),
                content = tiptapJson,
                excerpt = content.take(100)
            )
            val remote = apiService.createPost(request)
            val entity = PostEntity(
                id = remote.id,
                authorId = remote.authorId,
                authorName = remote.author?.displayName ?: "",
                authorUsername = remote.author?.username ?: "",
                authorAvatar = remote.author?.avatarUrl ?: "",
                title = remote.title,
                slug = remote.slug,
                content = remote.content,
                excerpt = remote.excerpt,
                coverImageUrl = remote.coverImageUrl,
                likeCount = remote.likeCount,
                commentCount = remote.commentCount,
                isLiked = remote.isLiked,
                isBookmarked = remote.isBookmarked,
                createdAt = parseDateToEpoch(remote.createdAt)
            )
            postDao.insertPost(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleLike(postId: String, currentLikeState: Boolean): Result<Boolean> {
        return try {
            // Toggle local instantly for optimistic update
            val post = postDao.getPostById(postId)
            if (post != null) {
                val newState = !currentLikeState
                val newCount = post.likeCount + (if (newState) 1 else -1)
                postDao.insertPost(post.copy(isLiked = newState, likeCount = newCount.coerceAtLeast(0)))
            }

            apiService.toggleReaction(type = "posts", id = postId, reaction = "like")
            Result.success(!currentLikeState)
        } catch (e: Exception) {
            // Revert state if failed
            val post = postDao.getPostById(postId)
            if (post != null) {
                postDao.insertPost(post.copy(isLiked = currentLikeState, likeCount = post.likeCount))
            }
            Result.failure(e)
        }
    }

    suspend fun toggleBookmark(postId: String, currentBookmarkState: Boolean): Result<Boolean> {
        return try {
            val post = postDao.getPostById(postId)
            if (post != null) {
                postDao.insertPost(post.copy(isBookmarked = !currentBookmarkState))
            }

            if (currentBookmarkState) {
                apiService.unbookmarkPost(postId)
            } else {
                apiService.bookmarkPost(postId)
            }
            Result.success(!currentBookmarkState)
        } catch (e: Exception) {
            val post = postDao.getPostById(postId)
            if (post != null) {
                postDao.insertPost(post.copy(isBookmarked = currentBookmarkState))
            }
            Result.failure(e)
        }
    }
}

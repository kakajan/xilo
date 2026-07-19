package ir.xilo.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.HashtagParser
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.PostResponse
import ir.xilo.app.data.repository.PostRepository
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

@HiltViewModel
class TagFeedViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val postRepository: PostRepository,
    private val errorMessageResolver: ErrorMessageResolver,
    private val json: Json,
) : ViewModel() {

    private val _posts = MutableStateFlow<List<PostEntity>>(emptyList())
    val posts: StateFlow<List<PostEntity>> = _posts.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(rawTag: String) {
        val tag = HashtagParser.normalize(rawTag).ifBlank { rawTag.trim().removePrefix("#") }
        if (tag.isBlank()) {
            _error.value = errorMessageResolver.string(R.string.tag_feed_empty)
            return
        }
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                val map = apiService.listPosts(limit = 30, tag = tag)
                val data = map["data"]
                val list = if (data != null) {
                    json.decodeFromJsonElement<List<PostResponse>>(data).map { remote ->
                        PostEntity(
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
                            likeCount = remote.resolvedLikeCount(),
                            commentCount = remote.commentCount,
                            repostCount = remote.repostCount,
                            viewCount = remote.viewCount,
                            isLiked = remote.resolvedIsLiked(),
                            isBookmarked = remote.isBookmarked,
                            isReposted = remote.isReposted,
                            createdAt = System.currentTimeMillis(),
                        )
                    }
                } else {
                    emptyList()
                }
                _posts.value = list
            } catch (e: Exception) {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_load_feed)
            }
            _loading.value = false
        }
    }

    fun toggleLike(post: PostEntity) {
        viewModelScope.launch {
            val previous = post.isLiked
            postRepository.toggleLike(post.id, post.isLiked)
                .onSuccess { liked ->
                    _posts.value = _posts.value.map {
                        if (it.id != post.id) it else {
                            val delta = when {
                                liked && !previous -> 1
                                !liked && previous -> -1
                                else -> 0
                            }
                            it.copy(isLiked = liked, likeCount = (it.likeCount + delta).coerceAtLeast(0))
                        }
                    }
                }
        }
    }

    fun toggleBookmark(post: PostEntity) {
        viewModelScope.launch {
            postRepository.toggleBookmark(post.id, post.isBookmarked)
                .onSuccess { bookmarked ->
                    _posts.value = _posts.value.map {
                        if (it.id == post.id) it.copy(isBookmarked = bookmarked) else it
                    }
                }
        }
    }
}

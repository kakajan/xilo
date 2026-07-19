package ir.xilo.app.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.xilo.app.R
import ir.xilo.app.data.NetworkMonitor
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.CommentRepository
import ir.xilo.app.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import ir.xilo.app.util.ErrorMessageResolver
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val json: Json,
    private val errorMessageResolver: ErrorMessageResolver,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val recentComments: StateFlow<List<CommentEntity>> = commentRepository.getRecentComments(50)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchResults = MutableStateFlow<List<PostEntity>>(emptyList())
    val searchResults: StateFlow<List<PostEntity>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _infoMessage = MutableStateFlow<Int?>(null)
    val infoMessage: StateFlow<Int?> = _infoMessage.asStateFlow()

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _currentUserId = MutableStateFlow(authRepository.getUserId())
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _currentUsername = MutableStateFlow(authRepository.getUsername())
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    init {
        refreshDiscoverComments()
    }

    fun refreshDiscoverComments() {
        viewModelScope.launch {
            try {
                val postsMap = apiService.listPosts(limit = 10)
                val dataElement = postsMap["data"] ?: return@launch
                val posts = json.decodeFromJsonElement<List<ir.xilo.app.data.remote.dto.PostResponse>>(dataElement)
                posts.forEach { post ->
                    commentRepository.refreshComments(post.id)
                }
            } catch (_: Exception) {
                // Offline — cached comments still shown via recentComments flow
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearInfoMessage() {
        _infoMessage.value = null
    }

    fun toggleCommentLike(comment: CommentEntity) {
        viewModelScope.launch {
            commentRepository.toggleCommentReaction(
                commentId = comment.id,
                reaction = "like",
                currentlyActive = comment.isLiked,
                oppositeActive = comment.isDisliked
            ).onFailure {
                _errorMessage.value =
                    errorMessageResolver.fromThrowable(it, R.string.error_unknown)
            }
        }
    }

    fun toggleCommentDislike(comment: CommentEntity) {
        viewModelScope.launch {
            commentRepository.toggleCommentReaction(
                commentId = comment.id,
                reaction = "dislike",
                currentlyActive = comment.isDisliked,
                oppositeActive = comment.isLiked
            ).onFailure {
                _errorMessage.value =
                    errorMessageResolver.fromThrowable(it, R.string.error_unknown)
            }
        }
    }

    fun toggleCommentBookmark(comment: CommentEntity) {
        viewModelScope.launch {
            commentRepository.toggleBookmark(comment.id, comment.isBookmarked)
                .onFailure {
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_unknown)
                }
        }
    }

    fun reportComment() {
        _infoMessage.value = R.string.discover_report_submitted
    }

    fun toggleLike(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            _searchResults.update { posts ->
                posts.map { post ->
                    if (post.id != postId) post
                    else {
                        val liked = !currentState
                        post.copy(
                            isLiked = liked,
                            likeCount = (post.likeCount + if (liked) 1 else -1).coerceAtLeast(0)
                        )
                    }
                }
            }
            postRepository.toggleLike(postId, currentState)
        }
    }

    fun toggleBookmark(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            _searchResults.update { posts ->
                posts.map { post ->
                    if (post.id != postId) post
                    else post.copy(isBookmarked = !currentState)
                }
            }
            postRepository.toggleBookmark(postId, currentState)
        }
    }

    fun toggleRepost(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            _searchResults.update { posts ->
                posts.map { post ->
                    if (post.id != postId) post
                    else {
                        val reposted = !currentState
                        post.copy(
                            isReposted = reposted,
                            repostCount = (post.repostCount + if (reposted) 1 else -1).coerceAtLeast(0)
                        )
                    }
                }
            }
            postRepository.toggleRepost(postId, currentState)
        }
    }

    fun archivePost(postId: String) {
        viewModelScope.launch {
            postRepository.archivePost(postId)
                .onSuccess {
                    _searchResults.update { posts -> posts.filterNot { it.id == postId } }
                }
                .onFailure {
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_archive_post)
                }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            postRepository.deletePost(postId)
                .onSuccess {
                    _searchResults.update { posts -> posts.filterNot { it.id == postId } }
                }
                .onFailure {
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_delete_post)
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        _errorMessage.value = null
        if (query.isNotBlank()) {
            performSearch(query)
        } else {
            _searchResults.value = emptyList()
        }
    }

    fun openCommentPost(postId: String, onNavigate: (String) -> Unit) {
        viewModelScope.launch {
            val slug = postRepository.getPostSlugById(postId)
            if (slug != null) onNavigate(slug)
        }
    }

    fun openCommentReply(
        postId: String,
        commentId: String,
        authorUsername: String,
        onNavigate: (slug: String, commentId: String, authorUsername: String) -> Unit,
    ) {
        viewModelScope.launch {
            val slug = postRepository.getPostSlugById(postId) ?: return@launch
            onNavigate(slug, commentId, authorUsername)
        }
    }

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val postsMap = apiService.listPosts(limit = 20, tag = query)
                val dataElement = postsMap["data"]
                if (dataElement != null) {
                    val postsDto = json.decodeFromJsonElement<List<ir.xilo.app.data.remote.dto.PostResponse>>(dataElement)
                    _searchResults.value = postsDto.map { remote ->
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
                            createdAt = System.currentTimeMillis()
                        )
                    }
                } else {
                    _searchResults.value = emptyList()
                }
            } catch (e: Exception) {
                _searchResults.value = emptyList()
                _errorMessage.value = errorMessageResolver.fromThrowable(e, R.string.error_search)
            }
            _isSearching.value = false
        }
    }
}

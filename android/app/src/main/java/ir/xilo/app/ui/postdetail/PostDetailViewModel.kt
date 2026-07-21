package ir.xilo.app.ui.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.R
import ir.xilo.app.core.util.canRepost
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.CommentRepository
import ir.xilo.app.data.repository.PostRepository
import ir.xilo.app.util.ErrorMessageResolver
import ir.xilo.app.data.remote.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val authRepository: AuthRepository,
    private val webSocketManager: WebSocketManager,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _post = MutableStateFlow<PostEntity?>(null)
    val post: StateFlow<PostEntity?> = _post.asStateFlow()

    private val _comments = MutableStateFlow<List<CommentEntity>>(emptyList())
    val comments: StateFlow<List<CommentEntity>> = _comments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var loadedSlug: String? = null

    private val _infoMessage = MutableStateFlow<Int?>(null)
    val infoMessage: StateFlow<Int?> = _infoMessage.asStateFlow()

    val currentUserAvatarUrl: StateFlow<String?> = authRepository.observeLocalProfile()
        .map { profile -> profile?.avatarUrl?.takeIf { it.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentUserId = MutableStateFlow(authRepository.getUserId())
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _currentUsername = MutableStateFlow(authRepository.getUsername())
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    private val _canRepost = MutableStateFlow(canRepost(authRepository.getRole()))
    val canRepost: StateFlow<Boolean> = _canRepost.asStateFlow()

    private val _postRemoved = MutableStateFlow(false)
    val postRemoved: StateFlow<Boolean> = _postRemoved.asStateFlow()

    private var currentPostId: String? = null
    private var commentsJob: Job? = null

    fun loadPost(slug: String) {
        loadedSlug = slug
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            bindPost(slug, recordViewOnSuccess = true)
            _isLoading.value = false
        }
    }

    /** Soft reload for pull-to-refresh — keeps current UI while fetching. */
    fun refresh() {
        val slug = loadedSlug ?: return
        if (_isRefreshing.value || _isLoading.value) return
        viewModelScope.launch {
            _isRefreshing.value = true
            _errorMessage.value = null
            bindPost(slug, recordViewOnSuccess = false)
            _isRefreshing.value = false
        }
    }

    private suspend fun bindPost(slug: String, recordViewOnSuccess: Boolean) {
        postRepository.getPostBySlug(slug)
            .onSuccess { postEntity ->
                _post.value = postEntity
                val postChanged = currentPostId != postEntity.id
                currentPostId = postEntity.id

                webSocketManager.subscribeToPost(postEntity.id)
                // Ensure Room Flow is collecting before replace so UI updates atomically.
                if (postChanged || commentsJob == null) {
                    commentsJob?.cancel()
                    commentsJob = viewModelScope.launch {
                        commentRepository.getComments(postEntity.id).collect { commentList ->
                            _comments.value = commentList
                        }
                    }
                }
                commentRepository.refreshComments(postEntity.id)
                    .onFailure { e ->
                        _errorMessage.value =
                            errorMessageResolver.fromThrowable(e, R.string.error_load_post)
                    }
                if (recordViewOnSuccess) {
                    recordView(postEntity.id)
                }
            }
            .onFailure { e ->
                _errorMessage.value = errorMessageResolver.fromThrowable(e, R.string.error_load_post)
            }
    }

    private fun recordView(postId: String) {
        viewModelScope.launch {
            postRepository.recordView(postId).onSuccess { count ->
                _post.value = _post.value?.copy(viewCount = count)
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun clearInfoMessage() {
        _infoMessage.value = null
    }

    fun addComment(content: String, parentId: String? = null) {
        val postId = currentPostId ?: return
        viewModelScope.launch {
            commentRepository.createComment(postId, content, parentId)
        }
    }

    fun toggleCommentLike(comment: CommentEntity) {
        viewModelScope.launch {
            commentRepository.toggleCommentReaction(
                commentId = comment.id,
                reaction = "like",
                currentlyActive = comment.isLiked,
                oppositeActive = comment.isDisliked,
            ).onFailure {
                _errorMessage.value =
                    errorMessageResolver.fromThrowable(it, R.string.error_unknown)
            }
        }
    }

    fun toggleCommentBookmark(comment: CommentEntity) {
        viewModelScope.launch {
            commentRepository.toggleBookmark(comment.id, comment.isBookmarked)
        }
    }

    fun toggleCommentDislike(comment: CommentEntity) {
        viewModelScope.launch {
            commentRepository.toggleCommentReaction(
                commentId = comment.id,
                reaction = "dislike",
                currentlyActive = comment.isDisliked,
                oppositeActive = comment.isLiked,
            ).onFailure {
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
            val snapshot = _post.value?.takeIf { it.id == postId }
            if (snapshot != null) {
                val liked = !currentState
                _post.value = snapshot.copy(
                    isLiked = liked,
                    likeCount = (snapshot.likeCount + if (liked) 1 else -1).coerceAtLeast(0),
                )
            }
            postRepository.toggleLike(postId, currentState)
                .onSuccess {
                    postRepository.getPostById(postId)?.let { fresh ->
                        _post.value = _post.value?.copy(
                            isLiked = fresh.isLiked,
                            likeCount = fresh.likeCount,
                        )
                    }
                }
                .onFailure {
                    if (snapshot != null) {
                        _post.value = snapshot
                    }
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_unknown)
                }
        }
    }

    fun toggleBookmark(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            val snapshot = _post.value?.takeIf { it.id == postId }
            if (snapshot != null) {
                _post.value = snapshot.copy(isBookmarked = !currentState)
            }
            postRepository.toggleBookmark(postId, currentState)
                .onSuccess { bookmarked ->
                    _post.value = _post.value?.copy(isBookmarked = bookmarked)
                        ?: postRepository.getPostById(postId)
                }
                .onFailure {
                    if (snapshot != null) {
                        _post.value = snapshot
                    }
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_unknown)
                }
        }
    }

    fun toggleRepost(postId: String, currentState: Boolean) {
        if (!_canRepost.value) return
        viewModelScope.launch {
            val snapshot = _post.value
            if (snapshot != null && snapshot.id == postId) {
                val reposted = !currentState
                _post.value = snapshot.copy(
                    isReposted = reposted,
                    repostCount = (snapshot.repostCount + if (reposted) 1 else -1).coerceAtLeast(0)
                )
            }
            postRepository.toggleRepost(postId, currentState)
                .onFailure {
                    if (snapshot != null) {
                        _post.value = snapshot
                    }
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_unknown)
                }
        }
    }

    fun archivePost(postId: String) {
        viewModelScope.launch {
            postRepository.archivePost(postId)
                .onSuccess {
                    _post.value = null
                    _postRemoved.value = true
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
                    _post.value = null
                    _postRemoved.value = true
                }
                .onFailure {
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(it, R.string.error_delete_post)
                }
        }
    }

    override fun onCleared() {
        super.onCleared()
        commentsJob?.cancel()
        currentPostId?.let { webSocketManager.unsubscribeFromPost(it) }
    }
}

package ir.xilo.app.ui.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.R
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

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _infoMessage = MutableStateFlow<Int?>(null)
    val infoMessage: StateFlow<Int?> = _infoMessage.asStateFlow()

    private val _currentUserAvatarUrl = MutableStateFlow<String?>(null)
    val currentUserAvatarUrl: StateFlow<String?> = _currentUserAvatarUrl.asStateFlow()

    private val _currentUserId = MutableStateFlow(authRepository.getUserId())
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _currentUsername = MutableStateFlow(authRepository.getUsername())
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    private val _postRemoved = MutableStateFlow(false)
    val postRemoved: StateFlow<Boolean> = _postRemoved.asStateFlow()

    private var currentPostId: String? = null
    private var commentsJob: Job? = null

    init {
        viewModelScope.launch {
            _currentUserAvatarUrl.value = authRepository.getLocalProfile()?.avatarUrl
        }
    }

    fun loadPost(slug: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            postRepository.getPostBySlug(slug)
                .onSuccess { postEntity ->
                    _post.value = postEntity
                    currentPostId = postEntity.id

                    webSocketManager.subscribeToPost(postEntity.id)
                    commentRepository.refreshComments(postEntity.id)
                        .onFailure { e ->
                            _errorMessage.value =
                                errorMessageResolver.fromThrowable(e, R.string.error_load_post)
                        }
                    recordView(postEntity.id)

                    commentsJob?.cancel()
                    commentsJob = viewModelScope.launch {
                        commentRepository.getComments(postEntity.id).collect { commentList ->
                            _comments.value = commentList
                        }
                    }
                }
                .onFailure { e ->
                    _errorMessage.value = errorMessageResolver.fromThrowable(e, R.string.error_load_post)
                }
            _isLoading.value = false
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

    fun toggleRepost(postId: String, currentState: Boolean) {
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

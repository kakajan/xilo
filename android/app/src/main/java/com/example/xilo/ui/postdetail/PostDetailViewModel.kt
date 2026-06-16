package com.example.xilo.ui.postdetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xilo.data.local.entity.CommentEntity
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.data.repository.CommentRepository
import com.example.xilo.data.repository.PostRepository
import com.example.xilo.data.remote.websocket.WebSocketManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PostDetailViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    private val webSocketManager: WebSocketManager
) : ViewModel() {

    private val _post = MutableStateFlow<PostEntity?>(null)
    val post: StateFlow<PostEntity?> = _post.asStateFlow()

    private val _comments = MutableStateFlow<List<CommentEntity>>(emptyList())
    val comments: StateFlow<List<CommentEntity>> = _comments.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var currentPostId: String? = null
    private var commentsJob: Job? = null

    fun loadPost(slug: String) {
        viewModelScope.launch {
            _isLoading.value = true
            postRepository.getPostBySlug(slug)
                .onSuccess { postEntity ->
                    _post.value = postEntity
                    currentPostId = postEntity.id

                    webSocketManager.subscribeToPost(postEntity.id)
                    commentRepository.refreshComments(postEntity.id)

                    commentsJob?.cancel()
                    commentsJob = viewModelScope.launch {
                        commentRepository.getComments(postEntity.id).collect { commentList ->
                            _comments.value = commentList
                        }
                    }
                }
            _isLoading.value = false
        }
    }

    fun addComment(content: String, parentId: String? = null) {
        val postId = currentPostId ?: return
        viewModelScope.launch {
            commentRepository.createComment(postId, content, parentId)
        }
    }

    fun toggleCommentLike(commentId: String, currentLikeState: Boolean) {
        viewModelScope.launch {
            commentRepository.toggleCommentReaction(commentId, currentLikeState)
            val postId = currentPostId ?: return@launch
            commentRepository.refreshComments(postId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        commentsJob?.cancel()
        currentPostId?.let { webSocketManager.unsubscribeFromPost(it) }
    }
}

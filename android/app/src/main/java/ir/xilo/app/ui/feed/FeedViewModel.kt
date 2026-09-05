package ir.xilo.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import androidx.paging.PagingData
import androidx.paging.cachedIn
import ir.xilo.app.R
import ir.xilo.app.core.util.canRepost
import ir.xilo.app.data.NetworkMonitor
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.PostRepository
import ir.xilo.app.util.ErrorMessageResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val posts: Flow<PagingData<PostEntity>> = postRepository.feedPager()
        .cachedIn(viewModelScope)

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val currentUserAvatarUrl: StateFlow<String?> = authRepository.observeLocalProfile()
        .map { profile -> profile?.avatarUrl?.takeIf { it.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentUserId = MutableStateFlow(authRepository.getUserId())
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _currentUsername = MutableStateFlow(authRepository.getUsername())
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    private val _canRepost = MutableStateFlow(canRepost(authRepository.getRole()))
    val canRepost: StateFlow<Boolean> = _canRepost.asStateFlow()

    /** In-content skeleton while switching category filters (not pull-to-refresh). */
    private val _isContentLoading = MutableStateFlow(false)
    val isContentLoading: StateFlow<Boolean> = _isContentLoading.asStateFlow()

    private val _selectedCategoryIndex = MutableStateFlow(0)
    val selectedCategoryIndex: StateFlow<Int> = _selectedCategoryIndex.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    private val _undoMessage = MutableStateFlow<Int?>(null)
    val undoMessage: StateFlow<Int?> = _undoMessage.asStateFlow()
    private var pendingUndo: FeedUndo? = null

    private val _pagingRefreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val pagingRefreshRequests = _pagingRefreshRequests.asSharedFlow()

    val categoryResIds = listOf(
        R.string.feed_category_for_you,
        R.string.feed_category_following,
        R.string.feed_category_pinned,
        R.string.feed_category_tech,
        R.string.feed_category_ai,
        R.string.feed_category_design,
    )

    fun selectCategory(index: Int) {
        if (_selectedCategoryIndex.value == index) return
        _selectedCategoryIndex.value = index
        requestPagingRefresh(showContentLoading = true)
    }

    fun requestPagingRefresh(showContentLoading: Boolean = false) {
        viewModelScope.launch {
            if (showContentLoading) {
                _isContentLoading.value = true
            }
            _pagingRefreshRequests.emit(Unit)
        }
    }

    fun clearContentLoading() {
        _isContentLoading.value = false
    }

    fun reportPagingRefreshError(throwable: Throwable) {
        Log.e("FeedViewModel", "feed paging refresh failed: ${throwable.message}", throwable)
        _errorMessage.value = errorMessageResolver.fromThrowable(throwable, R.string.error_load_feed)
        clearContentLoading()
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun toggleLike(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            postRepository.toggleLike(postId, currentState)
                .onFailure { e ->
                    Log.e("FeedViewModel", "toggleLike failed: ${e.message}", e)
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                }
        }
    }

    fun toggleRepost(postId: String, currentState: Boolean) {
        if (!_canRepost.value) return
        viewModelScope.launch {
            postRepository.toggleRepost(postId, currentState)
                .onFailure { e ->
                    Log.e("FeedViewModel", "toggleRepost failed: ${e.message}", e)
                    _errorMessage.value = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                }
        }
    }

    fun react(postId: String, emoji: String) {
        viewModelScope.launch {
            postRepository.toggleEmojiReaction(postId, emoji)
                .onFailure { e ->
                    Log.e("FeedViewModel", "react failed: ${e.message}", e)
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                }
        }
    }

    fun toggleBookmark(postId: String, currentState: Boolean, emitUndo: Boolean = true) {
        viewModelScope.launch {
            postRepository.toggleBookmark(postId, currentState)
                .onSuccess { bookmarked ->
                    if (emitUndo) {
                        pendingUndo = FeedUndo.Bookmark(postId, bookmarked)
                        _undoMessage.value = if (bookmarked) {
                            R.string.feed_undo_bookmarked
                        } else {
                            R.string.feed_undo_unbookmarked
                        }
                    }
                }
        }
    }

    fun archivePost(postId: String) {
        viewModelScope.launch {
            val snapshot = postRepository.getPostById(postId)
            postRepository.archivePost(postId)
                .onSuccess {
                    pendingUndo = snapshot?.let { FeedUndo.Archive(it) }
                    _undoMessage.value = R.string.feed_undo_archived
                }
                .onFailure { e ->
                    Log.e("FeedViewModel", "archivePost failed: ${e.message}", e)
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(e, R.string.error_archive_post)
                }
        }
    }

    fun deletePost(postId: String) {
        viewModelScope.launch {
            postRepository.deletePost(postId)
                .onSuccess {
                    pendingUndo = null
                    _undoMessage.value = R.string.feed_undo_deleted
                }
                .onFailure { e ->
                    Log.e("FeedViewModel", "deletePost failed: ${e.message}", e)
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(e, R.string.error_delete_post)
                }
        }
    }

    fun undoLast() {
        val action = pendingUndo ?: return
        pendingUndo = null
        _undoMessage.value = null
        viewModelScope.launch {
            when (action) {
                is FeedUndo.Bookmark -> {
                    toggleBookmark(action.postId, action.bookmarked, emitUndo = false)
                }
                is FeedUndo.Archive -> {
                    postRepository.restoreArchivedPost(action.post)
                        .onFailure { e ->
                            _errorMessage.value =
                                errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                        }
                }
            }
        }
    }

    fun consumeUndoMessage() {
        _undoMessage.value = null
    }
}

private sealed interface FeedUndo {
    data class Bookmark(val postId: String, val bookmarked: Boolean) : FeedUndo
    data class Archive(val post: PostEntity) : FeedUndo
}

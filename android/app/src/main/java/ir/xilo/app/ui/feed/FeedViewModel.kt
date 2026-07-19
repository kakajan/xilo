package ir.xilo.app.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import ir.xilo.app.R
import ir.xilo.app.data.NetworkMonitor
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.PostRepository
import ir.xilo.app.util.ErrorMessageResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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

    val posts: StateFlow<List<PostEntity>> = postRepository.getFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val currentUserAvatarUrl: StateFlow<String?> = authRepository.observeLocalProfile()
        .map { profile -> profile?.avatarUrl?.takeIf { it.isNotBlank() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _currentUserId = MutableStateFlow(authRepository.getUserId())
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _currentUsername = MutableStateFlow(authRepository.getUsername())
    val currentUsername: StateFlow<String?> = _currentUsername.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _selectedCategoryIndex = MutableStateFlow(0)
    val selectedCategoryIndex: StateFlow<Int> = _selectedCategoryIndex.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val categoryResIds = listOf(
        R.string.feed_category_for_you,
        R.string.feed_category_following,
        R.string.feed_category_pinned,
        R.string.feed_category_tech,
        R.string.feed_category_ai,
        R.string.feed_category_design,
    )

    init {
        refreshFeed()
    }

    fun selectCategory(index: Int) {
        _selectedCategoryIndex.value = index
        refreshFeed()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _isRefreshing.value = true
            postRepository.refreshFeed()
                .onFailure { e ->
                    Log.e("FeedViewModel", "refreshFeed failed: ${e.message}", e)
                    _errorMessage.value = errorMessageResolver.fromThrowable(e, R.string.error_load_feed)
                }
            _isRefreshing.value = false
            _isInitialLoading.value = false
        }
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

    fun toggleBookmark(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            postRepository.toggleBookmark(postId, currentState)
        }
    }

    fun toggleRepost(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            postRepository.toggleRepost(postId, currentState)
                .onFailure { e ->
                    Log.e("FeedViewModel", "toggleRepost failed: ${e.message}", e)
                    _errorMessage.value = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                }
        }
    }

    fun archivePost(postId: String) {
        viewModelScope.launch {
            postRepository.archivePost(postId)
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
                .onFailure { e ->
                    Log.e("FeedViewModel", "deletePost failed: ${e.message}", e)
                    _errorMessage.value =
                        errorMessageResolver.fromThrowable(e, R.string.error_delete_post)
                }
        }
    }
}

package com.example.xilo.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.xilo.R
import com.example.xilo.data.NetworkMonitor
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.data.repository.PostRepository
import com.example.xilo.util.ErrorMessageResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FeedViewModel @Inject constructor(
    private val postRepository: PostRepository,
    private val errorMessageResolver: ErrorMessageResolver,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val posts: StateFlow<List<PostEntity>> = postRepository.getFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isInitialLoading = MutableStateFlow(true)
    val isInitialLoading: StateFlow<Boolean> = _isInitialLoading.asStateFlow()

    private val _selectedCategoryIndex = MutableStateFlow(0)
    val selectedCategoryIndex: StateFlow<Int> = _selectedCategoryIndex.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    val categories = listOf("برای شما", "دنبال‌شده‌ها", "سنجاق‌شده", "فناوری", "هوش مصنوعی", "طراحی")

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
        }
    }

    fun toggleBookmark(postId: String, currentState: Boolean) {
        viewModelScope.launch {
            postRepository.toggleBookmark(postId, currentState)
        }
    }
}

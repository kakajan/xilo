package com.example.xilo.ui.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.example.xilo.data.NetworkMonitor
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.data.repository.PostRepository
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
    networkMonitor: NetworkMonitor
) : ViewModel() {

    val posts: StateFlow<List<PostEntity>> = postRepository.getFeed()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    init {
        refreshFeed()
    }

    fun refreshFeed() {
        viewModelScope.launch {
            _isRefreshing.value = true
            postRepository.refreshFeed()
                .onFailure { e ->
                    Log.e("FeedViewModel", "refreshFeed failed: ${e.message}", e)
                }
            _isRefreshing.value = false
        }
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

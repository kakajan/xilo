package com.example.xilo.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xilo.R
import com.example.xilo.data.NetworkMonitor
import com.example.xilo.data.local.entity.CommentEntity
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.data.remote.api.XiloApiService
import com.example.xilo.data.repository.CommentRepository
import com.example.xilo.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import com.example.xilo.util.ErrorMessageResolver
import javax.inject.Inject

@HiltViewModel
class DiscoverViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val commentRepository: CommentRepository,
    private val postRepository: PostRepository,
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

    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    init {
        refreshDiscoverComments()
    }

    fun refreshDiscoverComments() {
        viewModelScope.launch {
            try {
                val postsMap = apiService.listPosts(limit = 10)
                val dataElement = postsMap["data"] ?: return@launch
                val posts = json.decodeFromJsonElement<List<com.example.xilo.data.remote.dto.PostResponse>>(dataElement)
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

    private fun performSearch(query: String) {
        viewModelScope.launch {
            _isSearching.value = true
            try {
                val postsMap = apiService.listPosts(limit = 20, tag = query)
                val dataElement = postsMap["data"]
                if (dataElement != null) {
                    val postsDto = json.decodeFromJsonElement<List<com.example.xilo.data.remote.dto.PostResponse>>(dataElement)
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
                            likeCount = remote.likeCount,
                            commentCount = remote.commentCount,
                            isLiked = remote.isLiked,
                            isBookmarked = remote.isBookmarked,
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

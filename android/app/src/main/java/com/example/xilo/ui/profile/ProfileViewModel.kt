package com.example.xilo.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.data.remote.api.XiloApiService
import com.example.xilo.data.remote.dto.UserResponse
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val apiService: XiloApiService
) : ViewModel() {

    private val _userProfile = MutableStateFlow<UserResponse?>(null)
    val userProfile: StateFlow<UserResponse?> = _userProfile.asStateFlow()

    private val _userPosts = MutableStateFlow<List<PostEntity>>(emptyList())
    val userPosts: StateFlow<List<PostEntity>> = _userPosts.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadProfile(username: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Fetch user profile
                val profile = apiService.getPublicProfile(username)
                _userProfile.value = profile

                // Fetch user posts
                val postsMap = apiService.listPosts(limit = 20, author = username)
                val dataElement = postsMap["data"]
                if (dataElement != null) {
                    val postsDto = kotlinx.serialization.json.Json.decodeFromJsonElement<List<com.example.xilo.data.remote.dto.PostResponse>>(dataElement)
                    val entities = postsDto.map { remote ->
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
                            createdAt = 0L // Assuming we don't strictly need accurate sorting epoch in Profile tab for this basic setup
                        )
                    }
                    _userPosts.value = entities
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load profile"
            }
            _isLoading.value = false
        }
    }
}

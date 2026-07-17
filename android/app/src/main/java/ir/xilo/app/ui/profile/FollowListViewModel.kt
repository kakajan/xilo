package ir.xilo.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.FollowListUserResponse
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FollowListMode {
    Followers,
    Following,
}

@HiltViewModel
class FollowListViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private var username: String = ""
    private var mode: FollowListMode = FollowListMode.Followers

    private val _users = MutableStateFlow<List<FollowListUserResponse>>(emptyList())
    val users: StateFlow<List<FollowListUserResponse>> = _users.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private var nextCursor: String? = null
    private var hasMore: Boolean = false
    private var loadedKey: String? = null

    fun clearError() {
        _error.value = null
    }

    fun load(username: String, mode: FollowListMode) {
        val key = "$username:${mode.name}"
        if (key == loadedKey && _users.value.isNotEmpty()) return
        this.username = username
        this.mode = mode
        loadedKey = key
        refresh()
    }

    fun refresh() {
        if (username.isBlank()) return
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            nextCursor = null
            hasMore = false
            try {
                val page = fetchPage(cursor = null)
                _users.value = page.data
                nextCursor = page.nextCursor
                hasMore = page.hasMore
            } catch (e: Exception) {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_load_follow_list)
            }
            _isLoading.value = false
        }
    }

    fun loadMore() {
        if (_isLoading.value || !hasMore) return
        val cursor = nextCursor ?: return
        viewModelScope.launch {
            try {
                val page = fetchPage(cursor = cursor)
                _users.update { it + page.data }
                nextCursor = page.nextCursor
                hasMore = page.hasMore
            } catch (e: Exception) {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_load_follow_list)
            }
        }
    }

    fun toggleFollow(user: FollowListUserResponse) {
        viewModelScope.launch {
            val becomingFollow = !user.isFollowing
            _users.update { list ->
                list.map {
                    if (it.id == user.id) it.copy(isFollowing = becomingFollow) else it
                }
            }
            try {
                val response = if (becomingFollow) {
                    apiService.followUser(user.username)
                } else {
                    apiService.unfollowUser(user.username)
                }
                _users.update { list ->
                    list.map {
                        if (it.id == user.id) it.copy(isFollowing = response.following) else it
                    }
                }
            } catch (e: Exception) {
                _users.update { list ->
                    list.map {
                        if (it.id == user.id) it.copy(isFollowing = !becomingFollow) else it
                    }
                }
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_follow_failed)
            }
        }
    }

    private suspend fun fetchPage(cursor: String?) =
        when (mode) {
            FollowListMode.Followers -> apiService.listUserFollowers(
                username = username,
                cursor = cursor,
                limit = 30,
            )
            FollowListMode.Following -> apiService.listUserFollowing(
                username = username,
                cursor = cursor,
                limit = 30,
            )
        }
}

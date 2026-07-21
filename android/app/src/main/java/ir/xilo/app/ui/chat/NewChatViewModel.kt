package ir.xilo.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.canCreateGroup
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.FollowListUserResponse
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.ChatRepository
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException
import javax.inject.Inject

data class NewChatContact(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val isVerified: Boolean = false,
)

@HiltViewModel
class NewChatViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _suggestions = MutableStateFlow<List<NewChatContact>>(emptyList())
    val suggestions: StateFlow<List<NewChatContact>> = _suggestions.asStateFlow()

    private val _searchResult = MutableStateFlow<NewChatContact?>(null)
    val searchResult: StateFlow<NewChatContact?> = _searchResult.asStateFlow()

    private val _isLoadingSuggestions = MutableStateFlow(true)
    val isLoadingSuggestions: StateFlow<Boolean> = _isLoadingSuggestions.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _isStartingChat = MutableStateFlow(false)
    val isStartingChat: StateFlow<Boolean> = _isStartingChat.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _openChatId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openChatId: SharedFlow<String> = _openChatId.asSharedFlow()

    private val _canCreateGroup = MutableStateFlow(canCreateGroup(authRepository.getRole()))
    val canCreateGroupChat: StateFlow<Boolean> = _canCreateGroup.asStateFlow()

    private var searchJob: Job? = null

    init {
        loadFollowing()
    }

    fun clearError() {
        _error.value = null
    }

    fun updateQuery(value: String) {
        val trimmed = value.trimStart()
        _query.value = trimmed
        _error.value = null
        _searchResult.value = null
        searchJob?.cancel()
        val needle = trimmed.trim().removePrefix("@")
        if (needle.length < 2) {
            _isSearching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            lookupUsername(needle)
        }
    }

    fun startChat(contact: NewChatContact) {
        if (_isStartingChat.value) return
        if (contact.id == authRepository.getUserId()) {
            _error.value = errorMessageResolver.fromThrowable(
                IllegalArgumentException("self"),
                R.string.error_start_chat,
            )
            return
        }
        viewModelScope.launch {
            _isStartingChat.value = true
            _error.value = null
            chatRepository.createDirectChat(contact.id)
                .onSuccess { chat -> _openChatId.emit(chat.id) }
                .onFailure { e ->
                    _error.value = errorMessageResolver.fromThrowable(e, R.string.error_start_chat)
                }
            _isStartingChat.value = false
        }
    }

    private fun loadFollowing() {
        val username = authRepository.getUsername()
        if (username.isNullOrBlank()) {
            _isLoadingSuggestions.value = false
            return
        }
        viewModelScope.launch {
            _isLoadingSuggestions.value = true
            try {
                val page = apiService.listUserFollowing(username = username, limit = 50)
                _suggestions.value = page.data
                    .filter { it.id != authRepository.getUserId() }
                    .map { it.toContact() }
            } catch (e: Exception) {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_load_follow_list)
            }
            _isLoadingSuggestions.value = false
        }
    }

    private suspend fun lookupUsername(username: String) {
        _isSearching.value = true
        try {
            val profile = apiService.getPublicProfile(username)
            if (profile.id == authRepository.getUserId()) {
                _searchResult.value = null
            } else {
                _searchResult.value = NewChatContact(
                    id = profile.id,
                    username = profile.username,
                    displayName = profile.displayName,
                    avatarUrl = profile.avatarUrl,
                    isVerified = profile.isVerified,
                )
            }
        } catch (e: HttpException) {
            if (e.code() == 404) {
                _searchResult.value = null
            } else {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_start_chat)
            }
        } catch (e: Exception) {
            _error.value = errorMessageResolver.fromThrowable(e, R.string.error_start_chat)
        }
        _isSearching.value = false
    }

    private fun FollowListUserResponse.toContact() = NewChatContact(
        id = id,
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl,
        isVerified = isVerified,
    )
}

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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NewGroupStep { Members, Name }

@HiltViewModel
class NewGroupViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _step = MutableStateFlow(NewGroupStep.Members)
    val step: StateFlow<NewGroupStep> = _step.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _allContacts = MutableStateFlow<List<NewChatContact>>(emptyList())
    private val _contacts = MutableStateFlow<List<NewChatContact>>(emptyList())
    val contacts: StateFlow<List<NewChatContact>> = _contacts.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _groupName = MutableStateFlow("")
    val groupName: StateFlow<String> = _groupName.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _openChatId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openChatId: SharedFlow<String> = _openChatId.asSharedFlow()

    init {
        if (!canCreateGroup(authRepository.getRole())) {
            _error.value = errorMessageResolver.string(R.string.chat_new_group_forbidden)
        }
        loadFollowing()
    }

    fun clearError() {
        _error.value = null
    }

    fun updateQuery(value: String) {
        _query.value = value
        filterContacts()
    }

    fun toggleMember(userId: String) {
        val next = _selectedIds.value.toMutableSet()
        if (!next.add(userId)) next.remove(userId)
        _selectedIds.value = next
    }

    fun goToNameStep() {
        if (_selectedIds.value.isEmpty()) {
            _error.value = errorMessageResolver.string(R.string.chat_new_group_need_members)
            return
        }
        _step.value = NewGroupStep.Name
    }

    fun backToMembers() {
        _step.value = NewGroupStep.Members
    }

    fun updateGroupName(value: String) {
        _groupName.value = value
    }

    fun createGroup() {
        if (_isCreating.value) return
        if (!canCreateGroup(authRepository.getRole())) {
            _error.value = errorMessageResolver.string(R.string.chat_new_group_forbidden)
            return
        }
        val name = _groupName.value.trim()
        if (name.isEmpty()) {
            _error.value = errorMessageResolver.string(R.string.chat_new_group_need_name)
            return
        }
        val members = _selectedIds.value.toList()
        if (members.isEmpty()) {
            _error.value = errorMessageResolver.string(R.string.chat_new_group_need_members)
            return
        }
        viewModelScope.launch {
            _isCreating.value = true
            val result = chatRepository.createGroupChat(name, members)
            _isCreating.value = false
            result.fold(
                onSuccess = { _openChatId.tryEmit(it.id) },
                onFailure = {
                    _error.value = errorMessageResolver.fromThrowable(
                        it,
                        R.string.chat_new_group_create_failed,
                    )
                },
            )
        }
    }

    private fun loadFollowing() {
        val username = authRepository.getUsername()
        if (username.isNullOrBlank()) {
            _isLoading.value = false
            return
        }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val page = apiService.listUserFollowing(username = username, limit = 100)
                _allContacts.value = page.data
                    .filter { it.id != authRepository.getUserId() }
                    .map { it.toContact() }
                filterContacts()
            } catch (e: Exception) {
                _error.value = errorMessageResolver.fromThrowable(e, R.string.error_load_follow_list)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun filterContacts() {
        val needle = _query.value.trim().removePrefix("@").lowercase()
        _contacts.value = if (needle.isBlank()) {
            _allContacts.value
        } else {
            _allContacts.value.filter {
                it.username.lowercase().contains(needle) ||
                    (it.displayName?.lowercase()?.contains(needle) == true)
            }
        }
    }

    private fun FollowListUserResponse.toContact() = NewChatContact(
        id = id,
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl,
        isVerified = isVerified,
    )
}

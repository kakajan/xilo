package ir.xilo.app.ui.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.contacts.ContactsReader
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.ContactMatchRequest
import ir.xilo.app.data.remote.dto.ContactUserDto
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.ChatRepository
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ContactsUiState(
    val contacts: List<ContactUserDto> = emptyList(),
    val query: String = "",
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSyncing: Boolean = false,
    val isStartingChat: Boolean = false,
    val needsContactsPermission: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ContactsViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val contactsReader: ContactsReader,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactsUiState())
    val uiState: StateFlow<ContactsUiState> = _uiState.asStateFlow()

    private val _openChatId = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val openChatId: SharedFlow<String> = _openChatId.asSharedFlow()

    init {
        refresh()
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearNeedsPermission() {
        _uiState.update { it.copy(needsContactsPermission = false) }
    }

    fun updateQuery(value: String) {
        _uiState.update { it.copy(query = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            val refreshing = _uiState.value.contacts.isNotEmpty()
            _uiState.update {
                it.copy(
                    isLoading = !refreshing,
                    isRefreshing = refreshing,
                    error = null,
                )
            }
            try {
                val response = apiService.listContacts()
                _uiState.update {
                    it.copy(
                        contacts = response.data,
                        isLoading = false,
                        isRefreshing = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = errorMessageResolver.fromThrowable(
                            e,
                            R.string.error_load_contacts,
                        ),
                    )
                }
            }
        }
    }

    fun requestSyncContacts() {
        _uiState.update { it.copy(needsContactsPermission = true, error = null) }
    }

    fun onContactsPermissionResult(granted: Boolean) {
        _uiState.update { it.copy(needsContactsPermission = false) }
        if (granted) {
            syncContacts()
        } else {
            _uiState.update {
                it.copy(
                    error = errorMessageResolver.fromThrowable(
                        SecurityException("contacts denied"),
                        R.string.contacts_permission_denied,
                    ),
                )
            }
        }
    }

    fun syncContacts() {
        if (_uiState.value.isSyncing) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, error = null) }
            try {
                val payload = withContext(Dispatchers.IO) { contactsReader.collectHashes() }
                apiService.matchContacts(
                    ContactMatchRequest(
                        phoneHashes = payload.phoneHashes,
                        emailHashes = payload.emailHashes,
                    ),
                )
                val response = apiService.listContacts()
                _uiState.update {
                    it.copy(
                        contacts = response.data,
                        isSyncing = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSyncing = false,
                        error = errorMessageResolver.fromThrowable(
                            e,
                            R.string.contacts_sync_failed,
                        ),
                    )
                }
            }
        }
    }

    fun startChat(contact: ContactUserDto) {
        if (_uiState.value.isStartingChat) return
        if (contact.id == authRepository.getUserId()) {
            _uiState.update {
                it.copy(
                    error = errorMessageResolver.fromThrowable(
                        IllegalArgumentException("self"),
                        R.string.error_start_chat,
                    ),
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isStartingChat = true, error = null) }
            chatRepository.createDirectChat(contact.id)
                .onSuccess { chat -> _openChatId.emit(chat.id) }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            error = errorMessageResolver.fromThrowable(
                                e,
                                R.string.error_start_chat,
                            ),
                        )
                    }
                }
            _uiState.update { it.copy(isStartingChat = false) }
        }
    }
}

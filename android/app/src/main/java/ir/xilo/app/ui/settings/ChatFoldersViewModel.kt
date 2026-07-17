package ir.xilo.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.repository.ChatFolderRepository
import ir.xilo.app.data.repository.ChatFolderWithChats
import ir.xilo.app.data.repository.ChatRepository
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatFoldersUiState(
    val folders: List<ChatFolderWithChats> = emptyList(),
    val chats: List<ChatEntity> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ChatFoldersViewModel @Inject constructor(
    private val chatFolderRepository: ChatFolderRepository,
    private val chatRepository: ChatRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatFoldersUiState())
    val uiState: StateFlow<ChatFoldersUiState> = _uiState.asStateFlow()

    init {
        refresh()
        viewModelScope.launch {
            chatRepository.getChats().collect { chats ->
                _uiState.update { it.copy(chats = chats.filter { chat -> !chat.isArchived }) }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            chatRepository.refreshChats()
            chatFolderRepository.refreshFolders()
                .onSuccess { folders ->
                    _uiState.update { it.copy(isLoading = false, folders = folders) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                        )
                    }
                }
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            chatFolderRepository.createFolder(name)
                .onSuccess { refresh() }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                        )
                    }
                }
        }
    }

    fun renameFolder(folderId: String, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            chatFolderRepository.renameFolder(folderId, name)
                .onSuccess { refresh() }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                        )
                    }
                }
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            chatFolderRepository.deleteFolder(folderId)
                .onSuccess { refresh() }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                        )
                    }
                }
        }
    }

    fun setFolderChats(folderId: String, chatIds: List<String>) {
        viewModelScope.launch {
            chatFolderRepository.setFolderChats(folderId, chatIds)
                .onSuccess { refresh() }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            errorMessage = errorMessageResolver.fromThrowable(e, R.string.error_unknown)
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

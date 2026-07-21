package ir.xilo.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.ChatMemberResponse
import ir.xilo.app.data.remote.dto.ChatPinResponse
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.ChatRepository
import ir.xilo.app.data.repository.toChatEntity
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

data class GroupInfoUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val chat: ChatEntity? = null,
    val members: List<ChatMemberResponse> = emptyList(),
    val pins: List<ChatPinResponse> = emptyList(),
    val editName: String = "",
    val inviteToken: String? = null,
    val selfId: String? = null,
    val isAdmin: Boolean = false,
)

sealed interface GroupInfoEvent {
    data class Message(val text: String) : GroupInfoEvent
    data object Left : GroupInfoEvent
}

@HiltViewModel
class GroupInfoViewModel @Inject constructor(
    private val apiService: XiloApiService,
    private val chatRepository: ChatRepository,
    private val authRepository: AuthRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(GroupInfoUiState(selfId = authRepository.getUserId()))
    val uiState: StateFlow<GroupInfoUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GroupInfoEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<GroupInfoEvent> = _events.asSharedFlow()

    private var chatId: String = ""

    fun load(id: String) {
        chatId = id
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true) }
            try {
                val dto = apiService.getChat(id)
                val entity = dto.toChatEntity(authRepository.getUserId(), ::parseDateToEpoch)
                chatRepository.fetchAndCacheChat(id)
                val pins = chatRepository.listPins(id).getOrDefault(emptyList())
                _uiState.update {
                    it.copy(
                        loading = false,
                        chat = entity,
                        members = dto.members,
                        pins = pins,
                        editName = dto.name.orEmpty(),
                        isAdmin = dto.currentRole == "admin",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(loading = false) }
                emitError(e)
            }
        }
    }

    fun updateEditName(value: String) {
        _uiState.update { it.copy(editName = value) }
    }

    fun saveName() {
        val name = _uiState.value.editName.trim()
        if (name.isEmpty()) return
        runBusy {
            chatRepository.updateGroupMeta(chatId, name, null)
                .onSuccess { load(chatId) }
                .onFailure(::emitError)
        }
    }

    fun setMuted(muted: Boolean) {
        runBusy {
            chatRepository.setMuted(chatId, muted)
                .onSuccess { load(chatId) }
                .onFailure(::emitError)
        }
    }

    fun setRole(userId: String, role: String) {
        runBusy {
            chatRepository.updateMemberRole(chatId, userId, role)
                .onSuccess { load(chatId) }
                .onFailure(::emitError)
        }
    }

    fun removeMember(userId: String) {
        runBusy {
            chatRepository.removeMember(chatId, userId)
                .onSuccess { load(chatId) }
                .onFailure(::emitError)
        }
    }

    fun createInvite() {
        runBusy {
            chatRepository.createInviteLink(chatId)
                .onSuccess { link ->
                    _uiState.update { it.copy(inviteToken = link.token) }
                    _events.tryEmit(
                        GroupInfoEvent.Message(
                            errorMessageResolver.string(R.string.chat_group_invite_created),
                        ),
                    )
                }
                .onFailure(::emitError)
        }
    }

    fun revokeInvite() {
        val token = _uiState.value.inviteToken ?: return
        runBusy {
            chatRepository.revokeInviteLink(chatId, token)
                .onSuccess { _uiState.update { it.copy(inviteToken = null) } }
                .onFailure(::emitError)
        }
    }

    fun unpin(messageId: String) {
        runBusy {
            chatRepository.unpinMessage(chatId, messageId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(pins = state.pins.filterNot { it.messageId == messageId })
                    }
                }
                .onFailure(::emitError)
        }
    }

    fun leaveGroup() {
        runBusy {
            chatRepository.deleteChat(chatId)
                .onSuccess { _events.tryEmit(GroupInfoEvent.Left) }
                .onFailure(::emitError)
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            try {
                block()
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    private fun emitError(e: Throwable) {
        _events.tryEmit(
            GroupInfoEvent.Message(
                errorMessageResolver.fromThrowable(e, R.string.error_unknown),
            ),
        )
    }

    private fun parseDateToEpoch(value: String): Long {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
        )
        for (pattern in formats) {
            runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(value)?.time ?: 0L
            }
        }
        return 0L
    }
}

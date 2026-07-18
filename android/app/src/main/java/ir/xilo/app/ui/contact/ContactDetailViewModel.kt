package ir.xilo.app.ui.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ContactDetail(
    val id: String,
    val name: String,
    val username: String,
    val phone: String,
    val lastSeen: String,
    val avatarUrl: String?,
    val mediaItems: List<String>
)

data class ContactDetailUiState(
    val isLoading: Boolean = true,
    val contact: ContactDetail = ContactDetail(
        id = "",
        name = "",
        username = "",
        phone = "",
        lastSeen = "",
        avatarUrl = null,
        mediaItems = emptyList()
    )
)

@HiltViewModel
class ContactDetailViewModel @Inject constructor(
    private val chatRepository: ChatRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ContactDetailUiState())
    val uiState: StateFlow<ContactDetailUiState> = _uiState.asStateFlow()

    fun loadContact(chatId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            var chat = chatRepository.getChatById(chatId)
            if (chat == null) {
                chat = chatRepository.fetchAndCacheChat(chatId).getOrNull()
            }
            val contact = chat?.toContactDetail() ?: missingContact(chatId)
            _uiState.update { it.copy(isLoading = false, contact = contact) }
        }
    }

    private fun ChatEntity.toContactDetail(): ContactDetail {
        val displayName = peerDisplayName?.takeIf { it.isNotBlank() }
            ?: name?.takeIf { it.isNotBlank() }
            ?: "مخاطب"
        val username = peerUsername?.takeIf { it.isNotBlank() }?.let { "@$it" }.orEmpty()
        return ContactDetail(
            id = id,
            name = displayName,
            username = username,
            phone = "",
            lastSeen = "—",
            avatarUrl = peerAvatarUrl ?: avatarUrl,
            mediaItems = emptyList()
        )
    }

    private fun missingContact(chatId: String) = ContactDetail(
        id = chatId,
        name = "مخاطب",
        username = "",
        phone = "",
        lastSeen = "—",
        avatarUrl = null,
        mediaItems = emptyList()
    )
}

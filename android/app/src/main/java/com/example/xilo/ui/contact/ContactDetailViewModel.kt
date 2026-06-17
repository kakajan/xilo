package com.example.xilo.ui.contact

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xilo.data.repository.ChatRepository
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
            val chat = chatRepository.getChatById(chatId)
            val contact = if (chat != null) {
                ContactDetail(
                    id = chat.id,
                    name = chat.name ?: "مخاطب",
                    username = "@${chat.name?.replace(" ", "")?.lowercase() ?: "user"}",
                    phone = "+98 912 345 6789",
                    lastSeen = "آخرین بازدید اخیراً",
                    avatarUrl = chat.avatarUrl,
                    mediaItems = listOf(
                        "https://picsum.photos/seed/${chatId}a/300/300",
                        "https://picsum.photos/seed/${chatId}b/300/300",
                        "https://picsum.photos/seed/${chatId}c/300/300",
                        "https://picsum.photos/seed/${chatId}d/300/300",
                        "https://picsum.photos/seed/${chatId}e/300/300",
                        "https://picsum.photos/seed/${chatId}f/300/300"
                    )
                )
            } else {
                ContactDetail(
                    id = chatId,
                    name = "شارلوت هریس",
                    username = "@charlotte",
                    phone = "+98 912 123 4567",
                    lastSeen = "آخرین بازدید اخیراً",
                    avatarUrl = "https://picsum.photos/seed/$chatId/400/600",
                    mediaItems = listOf(
                        "https://picsum.photos/seed/${chatId}1/300/300",
                        "https://picsum.photos/seed/${chatId}2/300/300",
                        "https://picsum.photos/seed/${chatId}3/300/300"
                    )
                )
            }
            _uiState.update { it.copy(isLoading = false, contact = contact) }
        }
    }
}

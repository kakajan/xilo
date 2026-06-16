package com.example.xilo.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.xilo.data.local.entity.ChatEntity
import com.example.xilo.data.local.entity.MessageEntity
import com.example.xilo.data.repository.AuthRepository
import com.example.xilo.data.repository.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val chats: StateFlow<List<ChatEntity>> = chatRepository.getChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _currentChat = MutableStateFlow<ChatEntity?>(null)
    val currentChat: StateFlow<ChatEntity?> = _currentChat.asStateFlow()

    val currentUserId: String = authRepository.getUserId() ?: ""

    private var messagesJob: Job? = null

    init {
        refreshChats()
    }

    fun refreshChats() {
        viewModelScope.launch {
            chatRepository.refreshChats()
        }
    }

    fun selectChat(chatId: String) {
        _currentChat.value = chats.value.find { it.id == chatId }

        viewModelScope.launch {
            chatRepository.refreshMessages(chatId)
        }

        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessages(chatId).collect { messageList ->
                _messages.value = messageList
            }
        }
    }

    fun sendMessage(content: String) {
        val chat = _currentChat.value ?: return
        viewModelScope.launch {
            chatRepository.sendMessage(chat.id, content)
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
    }
}

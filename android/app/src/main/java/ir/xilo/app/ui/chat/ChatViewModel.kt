package ir.xilo.app.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.remote.dto.BookmarkedCommentResponse
import ir.xilo.app.data.remote.dto.MessageType
import ir.xilo.app.data.remote.dto.SendMessageRequest
import ir.xilo.app.data.remote.websocket.RealtimeEvent
import ir.xilo.app.data.local.entity.ChatFolderEntity
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.ChatFolderRepository
import ir.xilo.app.data.repository.ChatRepository
import ir.xilo.app.data.repository.CommentRepository
import ir.xilo.app.data.repository.PostRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChatSendEvent {
    data class Accepted(val operationKey: String) : ChatSendEvent
    data class Failed(val draft: String?) : ChatSendEvent
}

enum class ChatListMode {
    Chats,
    Saved,
}

enum class SavedHubSegment {
    Messages,
    Posts,
    Comments,
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val chatFolderRepository: ChatFolderRepository,
    private val postRepository: PostRepository,
    private val commentRepository: CommentRepository,
    authRepository: AuthRepository
) : ViewModel() {

    val chats: StateFlow<List<ChatEntity>> = chatRepository.getChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<ChatFolderEntity>> = chatFolderRepository.observeFolders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedFolderId = MutableStateFlow<String?>(null)
    val selectedFolderId: StateFlow<String?> = _selectedFolderId.asStateFlow()

    private val _listMode = MutableStateFlow(ChatListMode.Chats)
    val listMode: StateFlow<ChatListMode> = _listMode.asStateFlow()

    private val _savedSegment = MutableStateFlow(SavedHubSegment.Messages)
    val savedSegment: StateFlow<SavedHubSegment> = _savedSegment.asStateFlow()

    private val _savedMessages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val savedMessages: StateFlow<List<MessageEntity>> = _savedMessages.asStateFlow()

    private val _bookmarkedPosts = MutableStateFlow<List<PostEntity>>(emptyList())
    val bookmarkedPosts: StateFlow<List<PostEntity>> = _bookmarkedPosts.asStateFlow()

    private val _bookmarkedComments = MutableStateFlow<List<BookmarkedCommentResponse>>(emptyList())
    val bookmarkedComments: StateFlow<List<BookmarkedCommentResponse>> = _bookmarkedComments.asStateFlow()

    private val _savedHubLoading = MutableStateFlow(false)
    val savedHubLoading: StateFlow<Boolean> = _savedHubLoading.asStateFlow()

    private val folderChatIds = MutableStateFlow<Set<String>>(emptySet())

    val filteredChats: StateFlow<List<ChatEntity>> = combine(
        chats,
        _selectedFolderId,
        folderChatIds
    ) { allChats, folderId, chatIds ->
        val withoutSaved = allChats.filter { it.type != "saved" && it.id != "saved" }
        if (folderId == null) {
            withoutSaved
        } else {
            withoutSaved.filter { it.id in chatIds }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    val messages: StateFlow<List<MessageEntity>> = _messages.asStateFlow()

    private val _currentChat = MutableStateFlow<ChatEntity?>(null)
    val currentChat: StateFlow<ChatEntity?> = _currentChat.asStateFlow()

    private val _peerTyping = MutableStateFlow(false)
    val peerTyping: StateFlow<Boolean> = _peerTyping.asStateFlow()

    private val _peerOnline = MutableStateFlow<Boolean?>(null)
    val peerOnline: StateFlow<Boolean?> = _peerOnline.asStateFlow()

    private val _sendEvents = MutableSharedFlow<ChatSendEvent>(extraBufferCapacity = 1)
    val sendEvents = _sendEvents.asSharedFlow()

    val currentUserId: String = authRepository.getUserId() ?: ""

    private var messagesJob: Job? = null
    private var realtimeJob: Job? = null
    private var peerTypingClearJob: Job? = null
    private var localTypingPulseJob: Job? = null
    private var localTypingIdleJob: Job? = null
    private var joinedChatId: String? = null
    private var localTypingActive = false
    private val activeDraftKeys = mutableMapOf<String, String>()
    private val activeActionKeys = mutableSetOf<String>()

    init {
        refreshChats()
        viewModelScope.launch {
            chatFolderRepository.refreshFolders()
        }
    }

    fun refreshChats() {
        viewModelScope.launch {
            chatRepository.refreshChats()
            chatFolderRepository.refreshFolders()
        }
    }

    fun selectFolder(folderId: String?) {
        _listMode.value = ChatListMode.Chats
        _selectedFolderId.value = folderId
        viewModelScope.launch {
            folderChatIds.value = if (folderId == null) {
                emptySet()
            } else {
                chatFolderRepository.getFolderChatIds(folderId).toSet()
            }
        }
    }

    /** Messages-tab filter: only saved chat messages, no post/comment segments. */
    fun openSavedMessagesFilter() {
        _listMode.value = ChatListMode.Saved
        _selectedFolderId.value = null
        folderChatIds.value = emptySet()
        _savedSegment.value = SavedHubSegment.Messages
        refreshSavedHub()
    }

    /** Full Saved hub (Settings page) with Messages / Posts / Comments. */
    fun openSavedHub(segment: SavedHubSegment = SavedHubSegment.Messages) {
        _listMode.value = ChatListMode.Saved
        _selectedFolderId.value = null
        folderChatIds.value = emptySet()
        _savedSegment.value = segment
        refreshSavedHub()
    }

    fun selectSavedSegment(segment: SavedHubSegment) {
        _savedSegment.value = segment
        refreshSavedHub()
    }

    fun refreshSavedHub() {
        viewModelScope.launch {
            _savedHubLoading.value = true
            when (_savedSegment.value) {
                SavedHubSegment.Messages -> loadSavedMessages()
                SavedHubSegment.Posts -> {
                    postRepository.getBookmarkedPosts()
                        .onSuccess { _bookmarkedPosts.value = it }
                        .onFailure { _bookmarkedPosts.value = emptyList() }
                }
                SavedHubSegment.Comments -> {
                    commentRepository.getBookmarkedComments()
                        .onSuccess { _bookmarkedComments.value = it }
                        .onFailure { _bookmarkedComments.value = emptyList() }
                }
            }
            _savedHubLoading.value = false
        }
    }

    private suspend fun loadSavedMessages() {
        chatRepository.getOrCreateSavedMessages()
            .onSuccess { chat ->
                chatRepository.refreshMessages(chat.id)
                _savedMessages.value = chatRepository.getMessages(chat.id).first()
            }
            .onFailure {
                _savedMessages.value = emptyList()
            }
    }

    fun selectChat(chatId: String) {
        val previousChatId = joinedChatId
        if (previousChatId != null && previousChatId != chatId) {
            stopLocalTyping(previousChatId)
            chatRepository.leaveChat(previousChatId)
        }
        joinedChatId = chatId
        chatRepository.joinChat(chatId)

        _currentChat.value = chats.value.find { it.id == chatId }
        _peerTyping.value = false
        _peerOnline.value = null

        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            chatRepository.getMessages(chatId).collect { messageList ->
                _messages.value = messageList
            }
        }

        realtimeJob?.cancel()
        realtimeJob = viewModelScope.launch {
            chatRepository.realtimeEvents.collect { event ->
                handleRealtimeUiEvent(chatId, event)
            }
        }

        viewModelScope.launch {
            _currentChat.value = chatRepository.getChatById(chatId) ?: _currentChat.value
            chatRepository.refreshMessages(chatId)
        }
    }

    /**
     * Debounced outbound typing per REQ-CHAT-005: pulse every 3s while the
     * composer has text; stop after idle or when the field clears.
     */
    fun onComposerTextChanged(text: String) {
        val chatId = joinedChatId ?: return
        if (text.isBlank()) {
            stopLocalTyping(chatId)
            return
        }
        ensureLocalTyping(chatId)
    }

    fun sendMessage(content: String) {
        val chat = _currentChat.value ?: return
        val draft = content.trim()
        if (draft.isEmpty()) return
        stopLocalTyping(chat.id)
        val draftIdentity = "${chat.id}\u0000$draft"
        val operationKey = synchronized(activeDraftKeys) {
            if (activeDraftKeys.containsKey(draftIdentity)) {
                return
            }
            chatRepository.createMessageOperationKey().also {
                activeDraftKeys[draftIdentity] = it
            }
        }
        viewModelScope.launch {
            var durablyAccepted = false
            try {
                chatRepository.sendMessage(
                    chatId = chat.id,
                    request = SendMessageRequest(
                        type = MessageType.TEXT,
                        content = draft
                    ),
                    operationKey = operationKey,
                    onDurablyAccepted = {
                        durablyAccepted = true
                        releaseDraftGuard(draftIdentity, operationKey)
                        _sendEvents.tryEmit(ChatSendEvent.Accepted(operationKey))
                    }
                ).fold(
                    onSuccess = {},
                    onFailure = {
                        if (!durablyAccepted) {
                            _sendEvents.emit(ChatSendEvent.Failed(draft))
                        }
                    }
                )
            } finally {
                if (!durablyAccepted) {
                    releaseDraftGuard(draftIdentity, operationKey)
                }
            }
        }
    }

    private fun releaseDraftGuard(draftIdentity: String, operationKey: String) {
        synchronized(activeDraftKeys) {
            if (activeDraftKeys[draftIdentity] == operationKey) {
                activeDraftKeys.remove(draftIdentity)
            }
        }
    }

    fun retryMessage(operationKey: String) {
        runMessageAction(operationKey) {
            chatRepository.retryPermanentOutboxOperation(operationKey)
        }
    }

    fun deleteFailedMessage(operationKey: String) {
        runMessageAction(operationKey) {
            chatRepository.deletePermanentOutboxOperation(operationKey)
        }
    }

    private fun runMessageAction(
        operationKey: String,
        action: suspend () -> Result<Unit>
    ) {
        val accepted = synchronized(activeActionKeys) {
            activeActionKeys.add(operationKey)
        }
        if (!accepted) return
        viewModelScope.launch {
            try {
                action().onFailure {
                    _sendEvents.emit(ChatSendEvent.Failed(draft = null))
                }
            } finally {
                synchronized(activeActionKeys) {
                    activeActionKeys.remove(operationKey)
                }
            }
        }
    }

    val archivedChats: StateFlow<List<ChatEntity>> = chatRepository.getArchivedChats()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun archiveChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.archiveChat(chatId)
        }
    }

    fun unarchiveChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.unarchiveChat(chatId)
        }
    }

    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            chatRepository.deleteChat(chatId)
        }
    }

    private fun handleRealtimeUiEvent(chatId: String, event: RealtimeEvent) {
        when (event) {
            is RealtimeEvent.Typing -> {
                val payload = event.payload
                if (payload.chatId != chatId) return
                if (payload.userId == currentUserId) return
                _peerTyping.value = payload.typing
                peerTypingClearJob?.cancel()
                if (payload.typing) {
                    peerTypingClearJob = viewModelScope.launch {
                        delay(PEER_TYPING_TIMEOUT_MS)
                        _peerTyping.value = false
                    }
                }
            }
            is RealtimeEvent.Presence -> {
                val payload = event.payload
                if (payload.chatId != chatId) return
                if (payload.userId == currentUserId) return
                _peerOnline.value = event.online
            }
            else -> Unit
        }
    }

    private fun ensureLocalTyping(chatId: String) {
        if (!localTypingActive) {
            localTypingActive = true
            chatRepository.sendTyping(chatId, typing = true)
        }
        localTypingIdleJob?.cancel()
        localTypingIdleJob = viewModelScope.launch {
            delay(LOCAL_TYPING_IDLE_MS)
            stopLocalTyping(chatId)
        }
        if (localTypingPulseJob?.isActive != true) {
            localTypingPulseJob = viewModelScope.launch {
                while (isActive && localTypingActive && joinedChatId == chatId) {
                    delay(LOCAL_TYPING_PULSE_MS)
                    if (localTypingActive && joinedChatId == chatId) {
                        chatRepository.sendTyping(chatId, typing = true)
                    }
                }
            }
        }
    }

    private fun stopLocalTyping(chatId: String) {
        localTypingIdleJob?.cancel()
        localTypingIdleJob = null
        localTypingPulseJob?.cancel()
        localTypingPulseJob = null
        if (localTypingActive) {
            localTypingActive = false
            chatRepository.sendTyping(chatId, typing = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        messagesJob?.cancel()
        realtimeJob?.cancel()
        peerTypingClearJob?.cancel()
        joinedChatId?.let { stopLocalTyping(it) }
        joinedChatId?.let { chatRepository.leaveChat(it) }
        joinedChatId = null
    }

    companion object {
        private const val LOCAL_TYPING_PULSE_MS = 3_000L
        private const val LOCAL_TYPING_IDLE_MS = 3_000L
        private const val PEER_TYPING_TIMEOUT_MS = 5_000L
    }
}

package ir.xilo.app.ui.chat

import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.local.entity.MessageDeliveryState
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.remote.dto.CursorPage
import ir.xilo.app.data.remote.dto.MessageResponse
import ir.xilo.app.data.remote.websocket.RealtimeEnvelope
import ir.xilo.app.data.remote.websocket.RealtimeEvent
import ir.xilo.app.data.remote.websocket.RealtimeEvents
import ir.xilo.app.data.remote.websocket.RealtimePresencePayload
import ir.xilo.app.data.remote.websocket.RealtimeTypingPayload
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.ChatFolderRepository
import ir.xilo.app.data.repository.ChatRepository
import ir.xilo.app.data.repository.CommentRepository
import ir.xilo.app.data.repository.PostRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val repository = mockk<ChatRepository>()
    private val folderRepository = mockk<ChatFolderRepository>(relaxed = true)
    private val postRepository = mockk<PostRepository>(relaxed = true)
    private val commentRepository = mockk<CommentRepository>(relaxed = true)
    private val authRepository = mockk<AuthRepository>()
    private val messages = MutableStateFlow<List<MessageEntity>>(emptyList())
    private val realtimeEvents = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 8)

    private fun createViewModel() = ChatViewModel(
        repository,
        folderRepository,
        postRepository,
        commentRepository,
        authRepository,
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        every { repository.getChats() } returns flowOf(listOf(chat()))
        every { repository.getArchivedChats() } returns flowOf(emptyList())
        every { repository.getMessages(CHAT_ID) } returns messages
        every { repository.realtimeEvents } returns realtimeEvents
        every { repository.createMessageOperationKey() } returns OPERATION_KEY
        every { repository.joinChat(any()) } returns Unit
        every { repository.leaveChat(any()) } returns Unit
        every { repository.sendTyping(any(), any()) } returns Unit
        every { folderRepository.observeFolders() } returns flowOf(emptyList())
        coEvery { folderRepository.refreshFolders() } returns Result.success(emptyList())
        every { authRepository.getUserId() } returns USER_ID
        coEvery { repository.getChatById(CHAT_ID) } returns chat()
        coEvery { repository.refreshChats(any(), any()) } returns
            Result.success(CursorPage(data = emptyList()))
        coEvery { repository.refreshMessages(CHAT_ID, any(), any()) } returns
            Result.success(CursorPage<MessageResponse>(data = emptyList()))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun persistedQueuedMessage_isSurfacedAfterConversationSelection() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.selectChat(CHAT_ID)
        messages.value = listOf(optimistic())

        advanceUntilIdle()

        assertEquals(OPERATION_KEY, viewModel.messages.value.single().clientOperationKey)
        assertEquals(
            MessageDeliveryState.PENDING,
            viewModel.messages.value.single().deliveryState
        )
    }

    @Test
    fun duplicateCallbackBeforeDurableAcceptance_isSuppressed() =
        runTest(dispatcher) {
            val persistenceGate = CompletableDeferred<Unit>()
            coEvery {
                repository.sendMessage(CHAT_ID, any(), OPERATION_KEY, any())
            } coAnswers {
                persistenceGate.await()
                arg<(MessageEntity) -> Unit>(3)(optimistic())
                Result.success(optimistic())
            }
            val viewModel = createViewModel()
            viewModel.selectChat(CHAT_ID)
            advanceUntilIdle()

            viewModel.sendMessage("hello")
            viewModel.sendMessage("hello")
            runCurrent()

            coVerify(exactly = 1) {
                repository.sendMessage(CHAT_ID, any(), OPERATION_KEY, any())
            }

            persistenceGate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun identicalMessageAfterDurableAcceptance_getsDistinctOperationKey() =
        runTest(dispatcher) {
            val secondKey = "223e4567-e89b-42d3-a456-426614174000"
            val firstNetworkGate = CompletableDeferred<Unit>()
            every {
                repository.createMessageOperationKey()
            } returnsMany listOf(OPERATION_KEY, secondKey)
            coEvery {
                repository.sendMessage(CHAT_ID, any(), any(), any())
            } coAnswers {
                val key = arg<String>(2)
                val accepted = optimistic(key)
                arg<(MessageEntity) -> Unit>(3)(accepted)
                if (key == OPERATION_KEY) {
                    firstNetworkGate.await()
                }
                Result.success(accepted)
            }
            val viewModel = createViewModel()
            viewModel.selectChat(CHAT_ID)
            advanceUntilIdle()

            viewModel.sendMessage("hello")
            runCurrent()
            viewModel.sendMessage("hello")
            runCurrent()

            coVerify(exactly = 1) {
                repository.sendMessage(CHAT_ID, any(), OPERATION_KEY, any())
            }
            coVerify(exactly = 1) {
                repository.sendMessage(CHAT_ID, any(), secondKey, any())
            }

            firstNetworkGate.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun permanentFailureActions_delegateRetryAndDeleteByOperationKey() =
        runTest(dispatcher) {
            coEvery { repository.retryPermanentOutboxOperation(OPERATION_KEY) } returns
                Result.success(Unit)
            coEvery { repository.deletePermanentOutboxOperation(OPERATION_KEY) } returns
                Result.success(Unit)
            val viewModel = createViewModel()

            viewModel.retryMessage(OPERATION_KEY)
            advanceUntilIdle()
            viewModel.deleteFailedMessage(OPERATION_KEY)
            advanceUntilIdle()

            coVerify(exactly = 1) {
                repository.retryPermanentOutboxOperation(OPERATION_KEY)
            }
            coVerify(exactly = 1) {
                repository.deletePermanentOutboxOperation(OPERATION_KEY)
            }
        }

    @Test
    fun peerTypingAndPresence_updateConversationStatus() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.selectChat(CHAT_ID)
        advanceUntilIdle()

        realtimeEvents.emit(
            RealtimeEvent.Typing(
                envelope = RealtimeEnvelope(
                    event = RealtimeEvents.USER_TYPING,
                    eventId = "evt-typing",
                ),
                payload = RealtimeTypingPayload(
                    chatId = CHAT_ID,
                    userId = "peer-1",
                    typing = true,
                ),
            )
        )
        // Do not advanceUntilIdle: peer typing auto-clears after a delay.
        runCurrent()
        assertTrue(viewModel.peerTyping.value)

        realtimeEvents.emit(
            RealtimeEvent.Presence(
                envelope = RealtimeEnvelope(
                    event = RealtimeEvents.USER_ONLINE,
                    eventId = "evt-online",
                ),
                payload = RealtimePresencePayload(
                    chatId = CHAT_ID,
                    userId = "peer-1",
                    online = true,
                ),
                online = true,
            )
        )
        runCurrent()
        assertEquals(true, viewModel.peerOnline.value)

        realtimeEvents.emit(
            RealtimeEvent.Typing(
                envelope = RealtimeEnvelope(
                    event = RealtimeEvents.USER_TYPING,
                    eventId = "evt-typing-stop",
                ),
                payload = RealtimeTypingPayload(
                    chatId = CHAT_ID,
                    userId = "peer-1",
                    typing = false,
                ),
            )
        )
        runCurrent()
        assertFalse(viewModel.peerTyping.value)
    }

    @Test
    fun composerTextChanged_pulsesOutboundTyping() = runTest(dispatcher) {
        val viewModel = createViewModel()
        viewModel.selectChat(CHAT_ID)
        advanceUntilIdle()

        viewModel.onComposerTextChanged("سلام")
        runCurrent()

        verify(exactly = 1) { repository.sendTyping(CHAT_ID, true) }

        viewModel.onComposerTextChanged("")
        runCurrent()

        verify(exactly = 1) { repository.sendTyping(CHAT_ID, false) }
    }

    private fun chat() = ChatEntity(
        id = CHAT_ID,
        type = "direct",
        name = "Chat",
        avatarUrl = null,
        lastMessageContent = null,
        lastMessageTime = null
    )

    private fun optimistic(operationKey: String = OPERATION_KEY) = MessageEntity(
        id = "local-$operationKey",
        chatId = CHAT_ID,
        senderId = USER_ID,
        senderName = null,
        senderAvatar = null,
        content = "hello",
        mediaUrl = null,
        replyToId = null,
        clientOperationKey = operationKey,
        clientPayloadHash = "payload-hash",
        deliveryState = MessageDeliveryState.PENDING,
        createdAt = 100
    )

    private companion object {
        const val CHAT_ID = "chat-1"
        const val USER_ID = "user-1"
        const val OPERATION_KEY = "123e4567-e89b-42d3-a456-426614174000"
    }
}

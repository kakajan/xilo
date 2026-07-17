package ir.xilo.app.data.remote.websocket

import ir.xilo.app.data.local.dao.ChatDao
import ir.xilo.app.data.local.dao.MessageDao
import ir.xilo.app.data.local.dao.OutboxDao
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.local.entity.MessageDeliveryState
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.remote.dto.MessageResponse
import ir.xilo.app.data.remote.dto.MessageType
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.sync.OutboxTransactionRunner
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRealtimeReconcilerTest {

    private val inboundEvents = MutableSharedFlow<RealtimeEvent>(extraBufferCapacity = 8)
    private val webSocketManager = mockk<WebSocketManager>()
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val outboxDao = mockk<OutboxDao>(relaxed = true)
    private val authRepository = mockk<AuthRepository> {
        every { getUserId() } returns "me"
    }
    private val transactionRunner = mockk<OutboxTransactionRunner> {
        coEvery { run(any<suspend () -> Unit>()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
    }

    private fun reconciler(): ChatRealtimeReconciler {
        every { webSocketManager.events } returns inboundEvents
        return ChatRealtimeReconciler(
            webSocketManager = webSocketManager,
            chatDao = chatDao,
            messageDao = messageDao,
            outboxDao = outboxDao,
            authRepository = authRepository,
            transactionRunner = transactionRunner,
        )
    }

    @Test
    fun messageReceive_upsertsAuthoritativeMessageAndBumpsUnread() = runTest {
        val chat = ChatEntity(
            id = "chat-1",
            type = "direct",
            name = null,
            avatarUrl = null,
            lastMessageContent = null,
            lastMessageTime = null,
            unreadCount = 2,
        )
        coEvery { messageDao.upsertAuthoritativeMessage(any()) } returns true
        coEvery { chatDao.getChatById("chat-1") } returns chat
        coEvery { messageDao.getLastMessageForChat("chat-1") } returns MessageEntity(
            id = "msg-1",
            chatId = "chat-1",
            senderId = "other",
            senderName = null,
            senderAvatar = null,
            content = "hi",
            mediaUrl = null,
            replyToId = null,
            createdAt = 100L,
        )
        val inserted = slot<ChatEntity>()
        coEvery { chatDao.insertChat(capture(inserted)) } returns Unit

        val sut = reconciler()
        sut.reconcile(
            RealtimeEvent.MessageReceive(
                envelope = RealtimeEnvelope(
                    event = RealtimeEvents.MESSAGE_RECEIVE,
                    eventId = "evt-1",
                ),
                message = MessageResponse(
                    id = "msg-1",
                    chatId = "chat-1",
                    senderId = "other",
                    type = MessageType.TEXT,
                    content = "hi",
                    createdAt = "2026-07-16T12:00:00Z",
                    updatedAt = "2026-07-16T12:00:00Z",
                ),
            )
        )

        coVerify { messageDao.upsertAuthoritativeMessage(any()) }
        assertEquals(3, inserted.captured.unreadCount)
        assertEquals("hi", inserted.captured.lastMessageContent)
    }

    @Test
    fun duplicateEventId_isIgnored() = runTest {
        coEvery { messageDao.upsertAuthoritativeMessage(any()) } returns true
        coEvery { chatDao.getChatById(any()) } returns null
        val sut = reconciler()
        val event = RealtimeEvent.MessageReceive(
            envelope = RealtimeEnvelope(
                event = RealtimeEvents.MESSAGE_RECEIVE,
                eventId = "evt-dup",
            ),
            message = MessageResponse(
                id = "msg-1",
                chatId = "chat-1",
                senderId = "other",
                type = MessageType.TEXT,
                content = "hi",
                createdAt = "2026-07-16T12:00:00Z",
                updatedAt = "2026-07-16T12:00:00Z",
            ),
        )

        sut.reconcile(event)
        sut.reconcile(event)

        coVerify(exactly = 1) { messageDao.upsertAuthoritativeMessage(any()) }
    }

    @Test
    fun messageSendAck_promotesOptimisticMessageLikeOutboxSuccess() = runTest {
        val optimistic = MessageEntity(
            id = "local-abc",
            chatId = "chat-1",
            senderId = "me",
            senderName = null,
            senderAvatar = null,
            content = "pending",
            mediaUrl = null,
            replyToId = null,
            clientOperationKey = "op-1",
            clientPayloadHash = "hash",
            deliveryState = MessageDeliveryState.PENDING,
            createdAt = 50L,
        )
        coEvery { messageDao.getMessageByOperationKey("op-1") } returns optimistic
        coEvery { messageDao.deleteByOperationKey("op-1") } returns 1
        coEvery { messageDao.upsertAuthoritativeMessage(any()) } returns true
        coEvery { outboxDao.delete("op-1") } returns 1
        coEvery { chatDao.getChatById("chat-1") } returns null
        val delivered = slot<MessageEntity>()
        coEvery { messageDao.upsertAuthoritativeMessage(capture(delivered)) } returns true

        val sut = reconciler()
        sut.reconcile(
            RealtimeEvent.Ack(
                envelope = RealtimeEnvelope(
                    event = RealtimeEvents.ACK,
                    eventId = "evt-ack",
                    operationKey = "op-1",
                ),
                payload = RealtimeAckPayload(
                    forEvent = RealtimeEvents.MESSAGE_SEND,
                    operationKey = "op-1",
                    resourceType = "message",
                    resourceId = "msg-server",
                    accepted = true,
                ),
            )
        )

        coVerify { messageDao.deleteByOperationKey("op-1") }
        coVerify { outboxDao.delete("op-1") }
        assertEquals("msg-server", delivered.captured.id)
        assertEquals(MessageDeliveryState.DELIVERED, delivered.captured.deliveryState)
        assertEquals("op-1", delivered.captured.clientOperationKey)
        assertTrue(delivered.captured.deliveryErrorCode == null)
    }

    @Test
    fun messageEdit_updatesLocalContent() = runTest {
        coEvery { messageDao.updateEditedContent("msg-1", "edited") } returns 1
        coEvery { chatDao.getChatById("chat-1") } returns null
        val sut = reconciler()

        sut.reconcile(
            RealtimeEvent.MessageEdit(
                envelope = RealtimeEnvelope(
                    event = RealtimeEvents.MESSAGE_EDIT,
                    eventId = "evt-edit",
                ),
                payload = RealtimeMessageEditPayload(
                    messageId = "msg-1",
                    chatId = "chat-1",
                    content = "edited",
                    updatedAt = "2026-07-16T12:01:00Z",
                ),
            )
        )

        coVerify { messageDao.updateEditedContent("msg-1", "edited") }
    }

    @Test
    fun messageDelete_softDeletesInsteadOfHardDelete() = runTest {
        coEvery { messageDao.softDeleteById("msg-1") } returns 1
        coEvery { chatDao.getChatById("chat-1") } returns null
        val sut = reconciler()

        sut.reconcile(
            RealtimeEvent.MessageDelete(
                envelope = RealtimeEnvelope(
                    event = RealtimeEvents.MESSAGE_DELETE,
                    eventId = "evt-del",
                ),
                payload = RealtimeMessageDeletePayload(
                    messageId = "msg-1",
                    chatId = "chat-1",
                ),
            )
        )

        coVerify { messageDao.softDeleteById("msg-1") }
        coVerify(exactly = 0) { messageDao.deleteById(any()) }
    }
}

package ir.xilo.app.data.sync

import android.database.sqlite.SQLiteException
import ir.xilo.app.data.local.dao.ChatDao
import ir.xilo.app.data.local.dao.MessageDao
import ir.xilo.app.data.local.dao.OutboxDao
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.local.entity.MessageDeliveryState
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.local.entity.OutboxOperationEntity
import ir.xilo.app.data.local.entity.OutboxOperationType
import ir.xilo.app.data.local.entity.OutboxState
import ir.xilo.app.data.local.prefs.TokenManager
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.MessageResponse
import ir.xilo.app.data.remote.dto.MessageType
import ir.xilo.app.data.remote.dto.SendMessageRequest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class ChatOutboxProcessorTest {
    private val outboxDao = mockk<OutboxDao>()
    private val chatDao = mockk<ChatDao>()
    private val messageDao = mockk<MessageDao>()
    private val api = mockk<XiloApiService>()
    private val transactionRunner = mockk<OutboxTransactionRunner>()
    private val tokenManager = mockk<TokenManager> {
        every { getUserId() } returns "user-1"
    }
    private val clock = mockk<OutboxClock> {
        every { nowMillis() } returns NOW
    }
    private val codec = OutboxPayloadCodec(
        Json {
            encodeDefaults = true
            namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
        }
    )

    @Test
    fun replayResponse_isTransactionallyReconciledAndOperationRemoved() = runTest {
        val request = SendMessageRequest(type = MessageType.TEXT, content = "replayed")
        val pending = operation(request)
        val claimed = pending.copy(
            state = OutboxState.IN_FLIGHT,
            attemptCount = 1,
            inFlightAt = NOW
        )
        coEvery { outboxDao.get(KEY) } returnsMany listOf(pending, claimed)
        coEvery { outboxDao.getNextReady(NOW, any(), any(), any()) } returns pending
        coEvery { outboxDao.claim(KEY, NOW, any(), any()) } returns 1
        coEvery { api.sendMessage("chat-1", KEY, request) } returns messageResponse()
        coEvery { transactionRunner.run(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery { messageDao.getMessageByOperationKey(KEY) } returns optimisticMessage()
        coEvery { messageDao.deleteByOperationKey(KEY) } returns 1
        coEvery { messageDao.upsertAuthoritativeMessage(any()) } returns true
        coEvery { messageDao.getLastMessageForChat("chat-1") } returns authoritativeMessage()
        coEvery { chatDao.getChatById("chat-1") } returns cachedChat()
        coEvery { chatDao.insertChat(any()) } returns Unit
        coEvery { outboxDao.delete(KEY) } returns 1
        val processor = processor()

        val result = processor.process(KEY)

        assertTrue(result is OutboxProcessResult.Delivered)
        val delivered = result as OutboxProcessResult.Delivered
        assertEquals("server-message-id", delivered.message?.id)
        coVerify(exactly = 1) { api.sendMessage("chat-1", KEY, request) }
        coVerifyOrder {
            messageDao.deleteByOperationKey(KEY)
            messageDao.upsertAuthoritativeMessage(
                match {
                    it.id == "server-message-id" &&
                        it.clientOperationKey == KEY &&
                        it.deliveryState == MessageDeliveryState.DELIVERED
                }
            )
            chatDao.insertChat(
                match {
                    it.lastMessageContent == "replayed" &&
                        it.lastMessageTime == 1_721_260_800_000L
                }
            )
            outboxDao.delete(KEY)
        }
    }

    @Test
    fun localReconciliationFailure_retriesSameKeyForBackendReplay() = runTest {
        val request = SendMessageRequest(type = MessageType.TEXT, content = "replayed")
        val pending = operation(request)
        val claimed = pending.copy(
            state = OutboxState.IN_FLIGHT,
            attemptCount = 1,
            inFlightAt = NOW
        )
        coEvery { outboxDao.get(KEY) } returnsMany listOf(pending, claimed)
        coEvery { outboxDao.getNextReady(NOW, any(), any(), any()) } returns pending
        coEvery { outboxDao.claim(KEY, NOW, any(), any()) } returns 1
        coEvery { api.sendMessage("chat-1", KEY, request) } returns messageResponse()
        coEvery { messageDao.getMessageByOperationKey(KEY) } returns optimisticMessage()
        var transactionCalls = 0
        coEvery { transactionRunner.run(any()) } coAnswers {
            transactionCalls += 1
            if (transactionCalls == 1) {
                throw SQLiteException("disk unavailable")
            }
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery {
            outboxDao.markPending(
                operationKey = KEY,
                now = NOW,
                nextAttemptAt = NOW + OutboxRetryPolicy.BASE_DELAY_MS,
                errorCode = "local_reconciliation",
                httpStatus = null,
                errorMessage = any(),
                pendingState = any(),
                inFlightState = any()
            )
        } returns 1
        coEvery {
            messageDao.updateDeliveryState(
                operationKey = KEY,
                deliveryState = MessageDeliveryState.PENDING,
                errorCode = "local_reconciliation",
                errorMessage = any()
            )
        } returns 1

        val result = processor().process(KEY)

        assertTrue(result is OutboxProcessResult.RetryScheduled)
        assertEquals(
            "local_reconciliation",
            (result as OutboxProcessResult.RetryScheduled).error.metadata.code
        )
        coVerify(exactly = 1) { api.sendMessage("chat-1", KEY, request) }
        coVerify(exactly = 1) {
            outboxDao.markPending(
                operationKey = KEY,
                now = NOW,
                nextAttemptAt = NOW + OutboxRetryPolicy.BASE_DELAY_MS,
                errorCode = "local_reconciliation",
                httpStatus = null,
                errorMessage = any(),
                pendingState = any(),
                inFlightState = any()
            )
        }
        coVerify(exactly = 1) {
            messageDao.updateDeliveryState(
                operationKey = KEY,
                deliveryState = MessageDeliveryState.PENDING,
                errorCode = "local_reconciliation",
                errorMessage = any()
            )
        }
    }

    @Test
    fun permanentFailure_updatesOutboxAndOptimisticMessageTogether() = runTest {
        val request = SendMessageRequest(type = MessageType.TEXT, content = "rejected")
        val pending = operation(request)
        val claimed = pending.copy(
            state = OutboxState.IN_FLIGHT,
            attemptCount = 1,
            inFlightAt = NOW
        )
        coEvery { outboxDao.get(KEY) } returnsMany listOf(pending, claimed)
        coEvery { outboxDao.getNextReady(NOW, any(), any(), any()) } returns pending
        coEvery { outboxDao.claim(KEY, NOW, any(), any()) } returns 1
        coEvery { api.sendMessage("chat-1", KEY, request) } throws
            IllegalArgumentException("sensitive backend detail")
        coEvery { transactionRunner.run(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery {
            outboxDao.markPermanentFailure(
                operationKey = KEY,
                now = NOW,
                errorCode = "invalid_operation",
                httpStatus = null,
                errorMessage = "The saved operation could not be delivered",
                permanentState = any(),
                inFlightState = any()
            )
        } returns 1
        coEvery {
            messageDao.updateDeliveryState(
                operationKey = KEY,
                deliveryState = MessageDeliveryState.PERMANENT_FAILURE,
                errorCode = "invalid_operation",
                errorMessage = "The saved operation could not be delivered"
            )
        } returns 1

        val result = processor().process(KEY)

        assertTrue(result is OutboxProcessResult.PermanentFailure)
        coVerifyOrder {
            outboxDao.markPermanentFailure(
                operationKey = KEY,
                now = NOW,
                errorCode = "invalid_operation",
                httpStatus = null,
                errorMessage = "The saved operation could not be delivered",
                permanentState = any(),
                inFlightState = any()
            )
            messageDao.updateDeliveryState(
                operationKey = KEY,
                deliveryState = MessageDeliveryState.PERMANENT_FAILURE,
                errorCode = "invalid_operation",
                errorMessage = "The saved operation could not be delivered"
            )
        }
    }

    @Test
    fun staleRecovery_resetsOnlyThroughDurableDaoTransition() = runTest {
        coEvery { transactionRunner.run(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery {
            messageDao.markStaleInFlightPending(
                staleBefore = NOW - ChatOutboxProcessor.STALE_IN_FLIGHT_MS,
                errorCode = any(),
                errorMessage = any(),
                pendingState = any(),
                inFlightState = any()
            )
        } returns 2
        coEvery {
            outboxDao.resetStaleInFlight(
                staleBefore = NOW - ChatOutboxProcessor.STALE_IN_FLIGHT_MS,
                now = NOW,
                errorCode = any(),
                errorMessage = any(),
                pendingState = any(),
                inFlightState = any()
            )
        } returns 2

        assertEquals(2, processor().resetStaleInFlight())
        coVerify(exactly = 1) {
            outboxDao.resetStaleInFlight(
                staleBefore = NOW - ChatOutboxProcessor.STALE_IN_FLIGHT_MS,
                now = NOW,
                errorCode = any(),
                errorMessage = any(),
                pendingState = any(),
                inFlightState = any()
            )
        }
    }

    @Test
    fun coldStartRecovery_resetsEveryInFlightOperationImmediately() = runTest {
        coEvery { transactionRunner.run(any()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
        coEvery {
            messageDao.markAllInFlightPending(
                errorCode = any(),
                errorMessage = any(),
                pendingState = any(),
                inFlightState = any()
            )
        } returns 3
        coEvery {
            outboxDao.resetAllInFlight(
                now = NOW,
                errorCode = any(),
                errorMessage = any(),
                pendingState = any(),
                inFlightState = any()
            )
        } returns 3

        assertEquals(3, processor().resetAllInFlight())
    }

    @Test
    fun nextRecoveryAt_usesEarliestPendingOrStaleInFlightTime() = runTest {
        coEvery {
            outboxDao.getEarliestPendingNextAttemptAt(any(), any(), any())
        } returns NOW + 60_000L
        coEvery { outboxDao.getEarliestInFlightAt(any()) } returns NOW - 30_000L

        assertEquals(
            NOW + 60_000L,
            processor().nextRecoveryAt()
        )
    }

    private fun processor() = ChatOutboxProcessor(
        transactionRunner = transactionRunner,
        outboxDao = outboxDao,
        chatDao = chatDao,
        messageDao = messageDao,
        apiService = api,
        payloadCodec = codec,
        retryPolicy = OutboxRetryPolicy(),
        clock = clock,
        tokenManager = tokenManager
    )

    private fun operation(request: SendMessageRequest) = OutboxOperationEntity(
        operationKey = KEY,
        operationType = OutboxOperationType.MESSAGE_SEND,
        aggregateId = "chat-1",
        payload = codec.encode(request),
        createdAt = NOW,
        updatedAt = NOW,
        nextAttemptAt = NOW
    )

    private fun cachedChat() = ChatEntity(
        id = "chat-1",
        type = "direct",
        name = null,
        avatarUrl = null,
        lastMessageContent = null,
        lastMessageTime = null
    )

    private fun optimisticMessage() = MessageEntity(
        id = "local-message-id",
        chatId = "chat-1",
        senderId = "sender-1",
        senderName = null,
        senderAvatar = null,
        content = "replayed",
        mediaUrl = null,
        replyToId = null,
        clientOperationKey = KEY,
        clientPayloadHash = "payload-hash",
        deliveryState = MessageDeliveryState.PENDING,
        createdAt = NOW
    )

    private fun authoritativeMessage() = optimisticMessage().copy(
        id = "server-message-id",
        deliveryState = MessageDeliveryState.DELIVERED,
        createdAt = 1_721_260_800_000L
    )

    private fun messageResponse() = MessageResponse(
        id = "server-message-id",
        chatId = "chat-1",
        senderId = "sender-1",
        type = MessageType.TEXT,
        content = "replayed",
        createdAt = "2024-07-18T00:00:00Z",
        updatedAt = "2024-07-18T00:00:00Z"
    )

    private companion object {
        const val KEY = "123e4567-e89b-42d3-a456-426614174000"
        const val NOW = 1_721_299_200_000L
    }
}

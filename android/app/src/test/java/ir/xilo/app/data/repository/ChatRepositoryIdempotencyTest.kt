package ir.xilo.app.data.repository

import ir.xilo.app.data.local.dao.ChatDao
import ir.xilo.app.data.local.dao.MessageDao
import ir.xilo.app.data.local.dao.OutboxDao
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.local.entity.OutboxOperationEntity
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.MessageType
import ir.xilo.app.data.remote.dto.SendMessageRequest
import ir.xilo.app.data.remote.idempotency.OperationKeyGenerator
import ir.xilo.app.data.remote.websocket.ChatRealtimeReconciler
import ir.xilo.app.data.remote.websocket.WebSocketManager
import ir.xilo.app.data.sync.ChatOutboxProcessor
import ir.xilo.app.data.sync.OutboxClock
import ir.xilo.app.data.sync.OutboxDeliveryException
import ir.xilo.app.data.sync.OutboxFailureMetadata
import ir.xilo.app.data.sync.OutboxPayloadCodec
import ir.xilo.app.data.sync.OutboxProcessResult
import ir.xilo.app.data.sync.OutboxTransactionRunner
import ir.xilo.app.data.sync.OutboxWorkScheduler
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class ChatRepositoryIdempotencyTest {
    private val apiService = mockk<XiloApiService>()
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val messageDao = mockk<MessageDao>(relaxed = true)
    private val outboxDao = mockk<OutboxDao>()
    private val processor = mockk<ChatOutboxProcessor>()
    private val scheduler = mockk<OutboxWorkScheduler>(relaxed = true)
    private val authRepository = mockk<AuthRepository> {
        every { getUserId() } returns "sender-1"
    }
    private val transactionRunner = mockk<OutboxTransactionRunner> {
        coEvery { run(any<suspend () -> Unit>()) } coAnswers {
            firstArg<suspend () -> Unit>().invoke()
        }
    }
    private val clock = mockk<OutboxClock> {
        every { nowMillis() } returns NOW
    }
    private val webSocketManager = mockk<WebSocketManager>(relaxed = true)
    private val chatRealtimeReconciler = mockk<ChatRealtimeReconciler>(relaxed = true)
    private val json = Json {
        encodeDefaults = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }
    private val payloadCodec = OutboxPayloadCodec(json)

    @Test
    fun sendMessage_persistsStableKeyBeforeAnyDelivery() = runTest {
        val generator = CountingOperationKeyGenerator(VALID_KEY)
        val message = messageEntity()
        coEvery { outboxDao.get(VALID_KEY) } returns null
        coEvery { messageDao.getMessageByOperationKey(VALID_KEY) } returns null
        coEvery { messageDao.insertMessageOnce(any()) } returns 1L
        coEvery { chatDao.getChatById("chat-1") } returns null
        coEvery { outboxDao.insert(any()) } returns 1L
        coEvery { processor.process(VALID_KEY) } returns
            OutboxProcessResult.Delivered(message = message)
        val repository = repository(generator)

        val result = repository.sendMessage("chat-1", "hello")

        assertTrue(result.isSuccess)
        assertEquals(1, generator.generateCount)
        coVerifyOrder {
            outboxDao.insert(
                match {
                    it.operationKey == VALID_KEY &&
                        it.aggregateId == "chat-1" &&
                        it.payload.contains("\"content\":\"hello\"")
                }
            )
            scheduler.enqueueNow()
            processor.process(VALID_KEY)
        }
    }

    @Test
    fun cancellationAfterPersist_stillHasRecoveryScheduledFirst() = runTest {
        coEvery { outboxDao.get(VALID_KEY) } returns null
        coEvery { messageDao.getMessageByOperationKey(VALID_KEY) } returns null
        coEvery { messageDao.insertMessageOnce(any()) } returns 1L
        coEvery { chatDao.getChatById("chat-1") } returns null
        coEvery { outboxDao.insert(any()) } returns 1L
        coEvery { processor.process(VALID_KEY) } throws CancellationException("cancelled")
        val repository = repository(CountingOperationKeyGenerator(VALID_KEY))

        var cancelled = false
        try {
            repository.sendMessage("chat-1", "hello")
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)

        coVerifyOrder {
            outboxDao.insert(any())
            scheduler.enqueueNow()
            processor.process(VALID_KEY)
        }
    }

    @Test
    fun persistedKey_survivesRetryAndRepositoryRecreation() = runTest {
        val request = SendMessageRequest(type = MessageType.TEXT, content = "hello")
        val persisted = OutboxOperationEntity(
            operationKey = VALID_KEY,
            operationType = "message.send",
            aggregateId = "chat-1",
            payload = payloadCodec.encode(request),
            createdAt = NOW,
            updatedAt = NOW,
            nextAttemptAt = NOW
        )
        val optimistic = messageEntity().copy(
            id = "local-id",
            clientOperationKey = VALID_KEY,
            clientPayloadHash = sha256ForTest(persisted.payload),
            deliveryState = ir.xilo.app.data.local.entity.MessageDeliveryState.PENDING
        )
        coEvery { outboxDao.insert(any()) } returns 1L
        coEvery { outboxDao.get(VALID_KEY) } returns persisted
        coEvery { messageDao.getMessageByOperationKey(VALID_KEY) } returns optimistic
        coEvery { processor.process(VALID_KEY) } returnsMany listOf(
            OutboxProcessResult.RetryScheduled(
                OutboxDeliveryException(
                    OutboxFailureMetadata("network_io", null, "Network delivery failed"),
                    isPermanent = false
                )
            ),
            OutboxProcessResult.Delivered(message = messageEntity())
        )

        val firstProcess = repository(CountingOperationKeyGenerator("unused"))
        val recreatedProcess = repository(CountingOperationKeyGenerator("unused"))

        assertTrue(firstProcess.sendMessage("chat-1", request, VALID_KEY).isSuccess)
        assertTrue(recreatedProcess.sendMessage("chat-1", request, VALID_KEY).isSuccess)
        coVerify(exactly = 2) { processor.process(VALID_KEY) }
        verify(exactly = 2) { scheduler.enqueueNow() }
    }

    @Test
    fun permanentFailure_canBeRetriedOrDeletedExplicitly() = runTest {
        coEvery { outboxDao.retryPermanentFailure(VALID_KEY, NOW, any(), any()) } returns 1
        coEvery { outboxDao.deletePermanentFailure(VALID_KEY, any()) } returns 1
        val repository = repository(CountingOperationKeyGenerator("unused"))

        assertTrue(repository.retryPermanentOutboxOperation(VALID_KEY).isSuccess)
        assertTrue(repository.deletePermanentOutboxOperation(VALID_KEY).isSuccess)

        verify(exactly = 1) { scheduler.enqueueNow() }
    }

    @Test
    fun invalidCallerSuppliedKey_failsBeforeOutboxOrNetwork() = runTest {
        val repository = repository(CountingOperationKeyGenerator("unused"))

        val result = repository.sendMessage(
            "chat-1",
            SendMessageRequest(type = MessageType.TEXT, content = "hello"),
            "not-a-uuid"
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { outboxDao.insert(any()) }
        coVerify(exactly = 0) { processor.process(any()) }
    }

    @Test
    fun reusedKeyWithDifferentPayload_isRejectedWithoutMutatingOptimisticMessage() = runTest {
        val original = SendMessageRequest(type = MessageType.TEXT, content = "original")
        coEvery { outboxDao.get(VALID_KEY) } returns OutboxOperationEntity(
            operationKey = VALID_KEY,
            operationType = "message.send",
            aggregateId = "chat-1",
            payload = payloadCodec.encode(original),
            createdAt = NOW,
            updatedAt = NOW,
            nextAttemptAt = NOW
        )

        val result = repository(CountingOperationKeyGenerator("unused")).sendMessage(
            "chat-1",
            SendMessageRequest(type = MessageType.TEXT, content = "mutated"),
            VALID_KEY
        )

        assertTrue(result.isFailure)
        coVerify(exactly = 0) { messageDao.insertMessageOnce(any()) }
        coVerify(exactly = 0) { processor.process(any()) }
    }

    @Test
    fun identicalSequentialMessages_afterDurableAcceptance_createDistinctOptimisticRows() =
        runTest {
            val insertedMessages = mutableListOf<MessageEntity>()
            val generator = SequenceOperationKeyGenerator(
                listOf(VALID_KEY, SECOND_VALID_KEY)
            )
            coEvery { outboxDao.get(any()) } returns null
            coEvery { messageDao.getMessageByOperationKey(any()) } returns null
            coEvery { outboxDao.insert(any()) } returns 1L
            coEvery { messageDao.insertMessageOnce(any()) } coAnswers {
                insertedMessages += firstArg<MessageEntity>()
                1L
            }
            coEvery { chatDao.getChatById("chat-1") } returns null
            coEvery { processor.process(any()) } returns
                OutboxProcessResult.RetryScheduled(
                    OutboxDeliveryException(
                        OutboxFailureMetadata("network_io", null, "Network delivery failed"),
                        isPermanent = false
                    )
                )
            coEvery { messageDao.getMessageByOperationKey(any()) } coAnswers {
                insertedMessages.lastOrNull {
                    it.clientOperationKey == firstArg<String>()
                }
            }
            val repository = repository(generator)

            assertTrue(repository.sendMessage("chat-1", "same").isSuccess)
            assertTrue(repository.sendMessage("chat-1", "same").isSuccess)

            assertEquals(
                listOf(VALID_KEY, SECOND_VALID_KEY),
                insertedMessages.map { it.clientOperationKey }
            )
            assertEquals(2, insertedMessages.map { it.id }.distinct().size)
            coVerify(exactly = 2) { outboxDao.insert(any()) }
        }

    @Test
    fun optimisticId_isStableAndBoundToPayloadSenderAndLocalTime() {
        val request = SendMessageRequest(
            type = MessageType.TEXT,
            content = "hello",
            mediaUrl = null,
            replyToId = "reply-1"
        )
        val first = createOptimisticMessageId(VALID_KEY, "sender-1", request, NOW)

        assertEquals(first, createOptimisticMessageId(VALID_KEY, "sender-1", request, NOW))
        assertNotEquals(
            first,
            createOptimisticMessageId(
                VALID_KEY,
                "sender-1",
                request.copy(content = "different"),
                NOW
            )
        )
        assertNotEquals(
            first,
            createOptimisticMessageId(VALID_KEY, "sender-2", request, NOW)
        )
        assertNotEquals(
            first,
            createOptimisticMessageId(VALID_KEY, "sender-1", request, NOW + 1)
        )
    }

    private fun repository(generator: OperationKeyGenerator) = ChatRepository(
        apiService = apiService,
        chatDao = chatDao,
        messageDao = messageDao,
        webSocketManager = webSocketManager,
        chatRealtimeReconciler = chatRealtimeReconciler,
        operationKeyGenerator = generator,
        outboxDao = outboxDao,
        outboxProcessor = processor,
        outboxPayloadCodec = payloadCodec,
        outboxWorkScheduler = scheduler,
        outboxClock = clock,
        authRepository = authRepository,
        transactionRunner = transactionRunner
    )

    private fun messageEntity() = MessageEntity(
        id = "message-1",
        chatId = "chat-1",
        senderId = "sender-1",
        senderName = null,
        senderAvatar = null,
        content = "hello",
        mediaUrl = null,
        replyToId = null,
        createdAt = NOW
    )

    private class CountingOperationKeyGenerator(
        private val key: String
    ) : OperationKeyGenerator() {
        var generateCount = 0
            private set

        override fun generate(): String {
            generateCount += 1
            return key
        }
    }

    private class SequenceOperationKeyGenerator(
        keys: List<String>
    ) : OperationKeyGenerator() {
        private val iterator = keys.iterator()

        override fun generate(): String = iterator.next()
    }

    private fun sha256ForTest(value: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        const val VALID_KEY = "123e4567-e89b-42d3-a456-426614174000"
        const val SECOND_VALID_KEY = "223e4567-e89b-42d3-a456-426614174000"
        const val NOW = 1_721_299_200_000L
    }
}

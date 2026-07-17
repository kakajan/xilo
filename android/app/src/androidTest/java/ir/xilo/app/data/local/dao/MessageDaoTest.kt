package ir.xilo.app.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.xilo.app.data.local.db.XiloDatabase
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.local.entity.MessageDeliveryState
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.local.entity.OutboxOperationEntity
import ir.xilo.app.data.local.entity.OutboxOperationType
import ir.xilo.app.data.sync.OutboxTransactionRunner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageDaoTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: XiloDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(DATABASE_NAME)
        database = openDatabase()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun optimisticMessageAndOutbox_surviveDatabaseRecreation() = runTest {
        database.withTransaction {
            database.chatDao.insertChat(chat())
            database.outboxDao.insert(operation())
            database.messageDao.insertMessageOnce(optimistic())
        }

        database.close()
        database = openDatabase()

        val restored = database.messageDao
            .getMessagesForChatFlow(CHAT_ID)
            .first()
            .single()
        assertEquals(OPERATION_KEY, restored.clientOperationKey)
        assertEquals(MessageDeliveryState.PENDING, restored.deliveryState)
        assertNotNull(database.outboxDao.get(OPERATION_KEY))
    }

    @Test
    fun operationKeyUniqueness_preventsDuplicateOptimisticRows() = runTest {
        assertEquals(1L, database.messageDao.insertMessageOnce(optimistic()))
        assertEquals(
            -1L,
            database.messageDao.insertMessageOnce(
                optimistic().copy(id = "different-local-id", content = "mutated")
            )
        )
        assertEquals(
            listOf("hello"),
            database.messageDao.getMessagesForChatFlow(CHAT_ID).first().map { it.content }
        )
    }

    @Test
    fun deliveryTransitionsAndAuthoritativeDuplicate_preserveCorrelation() = runTest {
        database.messageDao.insertMessageOnce(optimistic())
        assertEquals(
            1,
            database.messageDao.updateDeliveryState(
                OPERATION_KEY,
                MessageDeliveryState.PERMANENT_FAILURE,
                "http_400",
                "Server permanently rejected delivery"
            )
        )
        assertEquals(
            MessageDeliveryState.PERMANENT_FAILURE,
            database.messageDao.getMessageByOperationKey(OPERATION_KEY)?.deliveryState
        )

        database.withTransaction {
            database.messageDao.deleteByOperationKey(OPERATION_KEY)
            database.messageDao.upsertAuthoritativeMessage(authoritative())
        }
        assertEquals(false, database.messageDao.upsertAuthoritativeMessage(authoritative()))

        val messages = database.messageDao.getMessagesForChatFlow(CHAT_ID).first()
        assertEquals(1, messages.size)
        assertEquals("server-id", messages.single().id)
        assertEquals(MessageDeliveryState.DELIVERED, messages.single().deliveryState)
        assertEquals(OPERATION_KEY, messages.single().clientOperationKey)
        assertNull(messages.single().deliveryErrorCode)
    }

    @Test
    fun authoritativeReconciliation_commitsTogetherAndRollsBackOnFailure() = runTest {
        val runner = OutboxTransactionRunner(database)
        database.withTransaction {
            database.outboxDao.insert(operation())
            database.messageDao.insertMessageOnce(optimistic())
        }

        runner.run {
            database.messageDao.deleteByOperationKey(OPERATION_KEY)
            database.messageDao.upsertAuthoritativeMessage(
                authoritative(OPERATION_KEY, "server-committed")
            )
            check(database.outboxDao.delete(OPERATION_KEY) == 1)
        }

        assertNull(database.messageDao.getMessageById("local-id"))
        assertNotNull(database.messageDao.getMessageById("server-committed"))
        assertNull(database.outboxDao.get(OPERATION_KEY))

        database.withTransaction {
            database.outboxDao.insert(operation(ROLLBACK_KEY))
            database.messageDao.insertMessageOnce(
                optimistic(ROLLBACK_KEY, "local-rollback")
            )
        }
        try {
            runner.run {
                database.messageDao.deleteByOperationKey(ROLLBACK_KEY)
                database.messageDao.upsertAuthoritativeMessage(
                    authoritative(ROLLBACK_KEY, "server-rollback")
                )
                check(database.outboxDao.delete(ROLLBACK_KEY) == 1)
                throw DeliberateReconciliationFailure()
            }
            fail("The deliberate reconciliation failure must escape")
        } catch (_: DeliberateReconciliationFailure) {
            // Expected: Room must roll back all message and outbox writes.
        }

        assertNotNull(database.messageDao.getMessageById("local-rollback"))
        assertNull(database.messageDao.getMessageById("server-rollback"))
        assertNotNull(database.outboxDao.get(ROLLBACK_KEY))
    }

    private fun openDatabase(): XiloDatabase =
        Room.databaseBuilder(context, XiloDatabase::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .build()

    private fun chat() = ChatEntity(
        id = CHAT_ID,
        type = "direct",
        name = null,
        avatarUrl = null,
        lastMessageContent = null,
        lastMessageTime = null
    )

    private fun operation(operationKey: String = OPERATION_KEY) = OutboxOperationEntity(
        operationKey = operationKey,
        operationType = OutboxOperationType.MESSAGE_SEND,
        aggregateId = CHAT_ID,
        payload = """{"content":"hello","type":"text"}""",
        createdAt = 100,
        updatedAt = 100,
        nextAttemptAt = 100
    )

    private fun optimistic(
        operationKey: String = OPERATION_KEY,
        id: String = "local-id"
    ) = MessageEntity(
        id = id,
        chatId = CHAT_ID,
        senderId = "user-1",
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

    private fun authoritative(
        operationKey: String = OPERATION_KEY,
        id: String = "server-id"
    ) = optimistic(operationKey).copy(
        id = id,
        deliveryState = MessageDeliveryState.DELIVERED,
        createdAt = 110
    )

    private companion object {
        const val DATABASE_NAME = "message-dao-recreation-test"
        const val CHAT_ID = "chat-1"
        const val OPERATION_KEY = "123e4567-e89b-42d3-a456-426614174000"
        const val ROLLBACK_KEY = "223e4567-e89b-42d3-a456-426614174000"
    }

    private class DeliberateReconciliationFailure : RuntimeException()
}

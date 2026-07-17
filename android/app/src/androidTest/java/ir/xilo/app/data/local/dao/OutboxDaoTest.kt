package ir.xilo.app.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.xilo.app.data.local.db.XiloDatabase
import ir.xilo.app.data.local.entity.OutboxOperationEntity
import ir.xilo.app.data.local.entity.OutboxOperationType
import ir.xilo.app.data.local.entity.OutboxState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxDaoTest {
    private lateinit var database: XiloDatabase
    private lateinit var dao: OutboxDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            XiloDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.outboxDao
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun readySelection_isOldestFirstAndPreservesPerChatMessageOrder() = runTest {
        dao.insert(operation("00000000-0000-4000-8000-000000000001", "chat-a", 1, 1_000))
        dao.insert(operation("00000000-0000-4000-8000-000000000002", "chat-a", 2, 0))
        dao.insert(operation("00000000-0000-4000-8000-000000000003", "chat-b", 3, 0))

        assertEquals(
            "00000000-0000-4000-8000-000000000003",
            dao.getNextReady(now = 100)?.operationKey
        )
        assertEquals(0L, dao.getEarliestPendingNextAttemptAt())
        assertNull(dao.getNextReady(now = -1))
    }

    @Test
    fun stateTransitions_incrementAttemptsAndRecoverStaleInFlight() = runTest {
        val key = "00000000-0000-4000-8000-000000000010"
        dao.insert(operation(key, "chat-a", 1, 0))

        assertEquals(1, dao.claim(key, now = 100))
        assertEquals(OutboxState.IN_FLIGHT, dao.get(key)?.state)
        assertEquals(1, dao.get(key)?.attemptCount)

        assertEquals(
            1,
            dao.resetStaleInFlight(staleBefore = 100, now = 200)
        )
        val recovered = dao.get(key)
        assertEquals(OutboxState.PENDING, recovered?.state)
        assertEquals(200L, recovered?.nextAttemptAt)
        assertNull(recovered?.inFlightAt)

        val staleWithHttpError = operation(
            "00000000-0000-4000-8000-000000000011",
            "chat-b",
            2,
            0
        ).copy(
            state = OutboxState.IN_FLIGHT,
            inFlightAt = 100,
            errorHttpStatus = 503
        )
        dao.insert(staleWithHttpError)
        assertEquals(1, dao.resetStaleInFlight(staleBefore = 100, now = 200))
        assertNull(dao.get(staleWithHttpError.operationKey)?.errorHttpStatus)

        assertEquals(1, dao.claim(key, now = 200))
        assertEquals(
            1,
            dao.markPermanentFailure(
                operationKey = key,
                now = 201,
                errorCode = "http_400",
                httpStatus = 400,
                errorMessage = "Server permanently rejected delivery"
            )
        )
        assertEquals(OutboxState.PERMANENT_FAILURE, dao.get(key)?.state)
        assertEquals(2, dao.get(key)?.attemptCount)
    }

    @Test
    fun permanentOlderMessage_isVisibleAndDoesNotBlockLaterMessage() = runTest {
        val older = "00000000-0000-4000-8000-000000000020"
        val later = "00000000-0000-4000-8000-000000000021"
        dao.insert(operation(older, "chat-a", 1, 0))
        dao.insert(operation(later, "chat-a", 2, 0))
        assertEquals(1, dao.claim(older, now = 100))
        assertEquals(
            1,
            dao.markPermanentFailure(
                operationKey = older,
                now = 101,
                errorCode = "http_400",
                httpStatus = 400,
                errorMessage = "Server permanently rejected delivery"
            )
        )

        assertEquals(later, dao.getNextReady(now = 101)?.operationKey)
        assertEquals(older, dao.observePermanentFailures().first().single().operationKey)
    }

    @Test
    fun coldResetRetryDeleteAndRetention_areBoundedAndExplicit() = runTest {
        val inFlight = operation(
            "00000000-0000-4000-8000-000000000030",
            "chat-a",
            1,
            0
        ).copy(
            state = OutboxState.IN_FLIGHT,
            inFlightAt = 500,
            errorHttpStatus = 503
        )
        dao.insert(inFlight)
        assertEquals(500L, dao.getEarliestInFlightAt())
        assertEquals(1, dao.resetAllInFlight(now = 600))
        assertEquals(OutboxState.PENDING, dao.get(inFlight.operationKey)?.state)
        assertNull(dao.get(inFlight.operationKey)?.errorHttpStatus)

        val retryKey = "00000000-0000-4000-8000-000000000031"
        val expiredKey = "00000000-0000-4000-8000-000000000032"
        dao.insert(
            operation(retryKey, "chat-a", 2, 0).copy(
                state = OutboxState.PERMANENT_FAILURE,
                attemptCount = 6,
                updatedAt = 1_000,
                errorCode = "http_400",
                errorHttpStatus = 400,
                errorMessage = "Permanent"
            )
        )
        dao.insert(
            operation(expiredKey, "chat-b", 3, 0).copy(
                state = OutboxState.PERMANENT_FAILURE,
                updatedAt = 10,
                errorCode = "http_400"
            )
        )

        assertEquals(1, dao.purgePermanentFailures(olderThan = 100))
        assertNull(dao.get(expiredKey))
        assertEquals(1, dao.retryPermanentFailure(retryKey, now = 1_100))
        assertEquals(OutboxState.PENDING, dao.get(retryKey)?.state)
        assertEquals(0, dao.get(retryKey)?.attemptCount)

        assertEquals(1, dao.claim(retryKey, now = 1_100))
        assertEquals(
            1,
            dao.markPermanentFailure(
                retryKey,
                now = 1_101,
                errorCode = "http_400",
                httpStatus = 400,
                errorMessage = "Permanent"
            )
        )
        assertEquals(1, dao.deletePermanentFailure(retryKey))
        assertNull(dao.get(retryKey))
    }

    private fun operation(
        key: String,
        chatId: String,
        createdAt: Long,
        nextAttemptAt: Long
    ) = OutboxOperationEntity(
        operationKey = key,
        operationType = OutboxOperationType.MESSAGE_SEND,
        aggregateId = chatId,
        payload = """{"content":"message","type":"text"}""",
        createdAt = createdAt,
        updatedAt = createdAt,
        nextAttemptAt = nextAttemptAt
    )
}

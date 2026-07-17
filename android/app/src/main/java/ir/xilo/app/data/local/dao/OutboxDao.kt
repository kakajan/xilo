package ir.xilo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ir.xilo.app.data.local.entity.OutboxOperationEntity
import ir.xilo.app.data.local.entity.OutboxOperationType
import ir.xilo.app.data.local.entity.OutboxState
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(operation: OutboxOperationEntity): Long

    @Query("SELECT * FROM outbox_operations WHERE operationKey = :operationKey")
    suspend fun get(operationKey: String): OutboxOperationEntity?

    @Query(
        """
        SELECT * FROM outbox_operations AS candidate
        WHERE candidate.state = :pendingState
          AND candidate.nextAttemptAt <= :now
          AND (
            candidate.operationType != :messageSendType
            OR NOT EXISTS (
                SELECT 1 FROM outbox_operations AS earlier
                WHERE earlier.operationType = :messageSendType
                  AND earlier.aggregateId = candidate.aggregateId
                  AND earlier.state IN (:pendingState, :inFlightState)
                  AND (
                    earlier.createdAt < candidate.createdAt
                    OR (
                        earlier.createdAt = candidate.createdAt
                        AND earlier.operationKey < candidate.operationKey
                    )
                  )
            )
          )
        ORDER BY candidate.createdAt ASC, candidate.operationKey ASC
        LIMIT 1
        """
    )
    suspend fun getNextReady(
        now: Long,
        pendingState: String = OutboxState.PENDING,
        inFlightState: String = OutboxState.IN_FLIGHT,
        messageSendType: String = OutboxOperationType.MESSAGE_SEND
    ): OutboxOperationEntity?

    @Query(
        """
        SELECT MIN(candidate.nextAttemptAt) FROM outbox_operations AS candidate
        WHERE candidate.state = :pendingState
          AND (
            candidate.operationType != :messageSendType
            OR NOT EXISTS (
                SELECT 1 FROM outbox_operations AS earlier
                WHERE earlier.operationType = :messageSendType
                  AND earlier.aggregateId = candidate.aggregateId
                  AND earlier.state IN (:pendingState, :inFlightState)
                  AND (
                    earlier.createdAt < candidate.createdAt
                    OR (
                        earlier.createdAt = candidate.createdAt
                        AND earlier.operationKey < candidate.operationKey
                    )
                  )
            )
          )
        """
    )
    suspend fun getEarliestPendingNextAttemptAt(
        pendingState: String = OutboxState.PENDING,
        inFlightState: String = OutboxState.IN_FLIGHT,
        messageSendType: String = OutboxOperationType.MESSAGE_SEND
    ): Long?

    @Query(
        """
        SELECT MIN(inFlightAt) FROM outbox_operations
        WHERE state = :inFlightState AND inFlightAt IS NOT NULL
        """
    )
    suspend fun getEarliestInFlightAt(
        inFlightState: String = OutboxState.IN_FLIGHT
    ): Long?

    @Query(
        """
        UPDATE outbox_operations
        SET state = :inFlightState,
            attemptCount = attemptCount + 1,
            updatedAt = :now,
            inFlightAt = :now,
            errorCode = NULL,
            errorHttpStatus = NULL,
            errorMessage = NULL
        WHERE operationKey = :operationKey
          AND state = :pendingState
          AND nextAttemptAt <= :now
        """
    )
    suspend fun claim(
        operationKey: String,
        now: Long,
        pendingState: String = OutboxState.PENDING,
        inFlightState: String = OutboxState.IN_FLIGHT
    ): Int

    @Query(
        """
        UPDATE outbox_operations
        SET state = :pendingState,
            updatedAt = :now,
            nextAttemptAt = :nextAttemptAt,
            inFlightAt = NULL,
            errorCode = :errorCode,
            errorHttpStatus = :httpStatus,
            errorMessage = :errorMessage
        WHERE operationKey = :operationKey
          AND state = :inFlightState
        """
    )
    suspend fun markPending(
        operationKey: String,
        now: Long,
        nextAttemptAt: Long,
        errorCode: String,
        httpStatus: Int?,
        errorMessage: String,
        pendingState: String = OutboxState.PENDING,
        inFlightState: String = OutboxState.IN_FLIGHT
    ): Int

    @Query(
        """
        UPDATE outbox_operations
        SET state = :permanentState,
            updatedAt = :now,
            nextAttemptAt = :now,
            inFlightAt = NULL,
            errorCode = :errorCode,
            errorHttpStatus = :httpStatus,
            errorMessage = :errorMessage
        WHERE operationKey = :operationKey
          AND state = :inFlightState
        """
    )
    suspend fun markPermanentFailure(
        operationKey: String,
        now: Long,
        errorCode: String,
        httpStatus: Int?,
        errorMessage: String,
        permanentState: String = OutboxState.PERMANENT_FAILURE,
        inFlightState: String = OutboxState.IN_FLIGHT
    ): Int

    @Query("DELETE FROM outbox_operations WHERE operationKey = :operationKey")
    suspend fun delete(operationKey: String): Int

    @Query(
        """
        UPDATE outbox_operations
        SET state = :pendingState,
            updatedAt = :now,
            nextAttemptAt = :now,
            inFlightAt = NULL,
            errorCode = :errorCode,
            errorHttpStatus = NULL,
            errorMessage = :errorMessage
        WHERE state = :inFlightState
          AND inFlightAt IS NOT NULL
          AND inFlightAt <= :staleBefore
        """
    )
    suspend fun resetStaleInFlight(
        staleBefore: Long,
        now: Long,
        errorCode: String = "stale_in_flight",
        errorMessage: String = "Delivery interrupted; queued for retry",
        pendingState: String = OutboxState.PENDING,
        inFlightState: String = OutboxState.IN_FLIGHT
    ): Int

    @Query(
        """
        UPDATE outbox_operations
        SET state = :pendingState,
            updatedAt = :now,
            nextAttemptAt = :now,
            inFlightAt = NULL,
            errorCode = :errorCode,
            errorHttpStatus = NULL,
            errorMessage = :errorMessage
        WHERE state = :inFlightState
        """
    )
    suspend fun resetAllInFlight(
        now: Long,
        errorCode: String = "cold_start_recovery",
        errorMessage: String = "Delivery recovered after app restart",
        pendingState: String = OutboxState.PENDING,
        inFlightState: String = OutboxState.IN_FLIGHT
    ): Int

    @Query(
        """
        SELECT COUNT(*) FROM outbox_operations
        WHERE state IN (:pendingState, :inFlightState)
        """
    )
    suspend fun unfinishedCount(
        pendingState: String = OutboxState.PENDING,
        inFlightState: String = OutboxState.IN_FLIGHT
    ): Int

    @Query(
        """
        SELECT * FROM outbox_operations
        WHERE state = :permanentState
        ORDER BY updatedAt DESC, operationKey ASC
        """
    )
    fun observePermanentFailures(
        permanentState: String = OutboxState.PERMANENT_FAILURE
    ): Flow<List<OutboxOperationEntity>>

    @Query(
        """
        DELETE FROM outbox_operations
        WHERE operationKey = :operationKey AND state = :permanentState
        """
    )
    suspend fun deletePermanentFailure(
        operationKey: String,
        permanentState: String = OutboxState.PERMANENT_FAILURE
    ): Int

    @Query(
        """
        UPDATE outbox_operations
        SET state = :pendingState,
            attemptCount = 0,
            updatedAt = :now,
            nextAttemptAt = :now,
            inFlightAt = NULL,
            errorCode = NULL,
            errorHttpStatus = NULL,
            errorMessage = NULL
        WHERE operationKey = :operationKey AND state = :permanentState
        """
    )
    suspend fun retryPermanentFailure(
        operationKey: String,
        now: Long,
        pendingState: String = OutboxState.PENDING,
        permanentState: String = OutboxState.PERMANENT_FAILURE
    ): Int

    @Query(
        """
        DELETE FROM outbox_operations
        WHERE state = :permanentState AND updatedAt < :olderThan
        """
    )
    suspend fun purgePermanentFailures(
        olderThan: Long,
        permanentState: String = OutboxState.PERMANENT_FAILURE
    ): Int

    @Query(
        """
        SELECT * FROM outbox_operations
        WHERE state = :permanentState AND updatedAt < :olderThan
        ORDER BY updatedAt ASC, operationKey ASC
        """
    )
    suspend fun getExpiredPermanentFailures(
        olderThan: Long,
        permanentState: String = OutboxState.PERMANENT_FAILURE
    ): List<OutboxOperationEntity>

    @Query("SELECT * FROM outbox_operations ORDER BY createdAt ASC, operationKey ASC")
    suspend fun getAll(): List<OutboxOperationEntity>
}

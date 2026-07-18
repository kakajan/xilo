package ir.xilo.app.data.sync

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
import ir.xilo.app.data.repository.toChatEntity
import ir.xilo.app.data.repository.toMessageEntity
import kotlinx.coroutines.CancellationException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

sealed interface OutboxProcessResult {
    data class Delivered(
        val chat: ChatEntity? = null,
        val message: MessageEntity? = null
    ) : OutboxProcessResult

    data class RetryScheduled(
        val error: OutboxDeliveryException
    ) : OutboxProcessResult

    data class PermanentFailure(
        val error: OutboxDeliveryException
    ) : OutboxProcessResult

    data object NotReady : OutboxProcessResult
    data object AlreadyInFlight : OutboxProcessResult
    data object Missing : OutboxProcessResult
}

@Singleton
class ChatOutboxProcessor @Inject constructor(
    private val transactionRunner: OutboxTransactionRunner,
    private val outboxDao: OutboxDao,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val apiService: XiloApiService,
    private val payloadCodec: OutboxPayloadCodec,
    private val retryPolicy: OutboxRetryPolicy,
    private val clock: OutboxClock,
    private val tokenManager: TokenManager
) {
    suspend fun nextReadyOperationKey(): String? =
        outboxDao.getNextReady(clock.nowMillis())?.operationKey

    suspend fun hasUnfinishedOperations(): Boolean = outboxDao.unfinishedCount() > 0

    suspend fun nextRecoveryAt(): Long? {
        val pendingAt = outboxDao.getEarliestPendingNextAttemptAt()
        val staleInFlightAt = outboxDao.getEarliestInFlightAt()?.let {
            it + STALE_IN_FLIGHT_MS
        }
        return listOfNotNull(pendingAt, staleInFlightAt).minOrNull()
    }

    fun nowMillis(): Long = clock.nowMillis()

    suspend fun process(operationKey: String): OutboxProcessResult {
        val now = clock.nowMillis()
        val existing = outboxDao.get(operationKey) ?: return OutboxProcessResult.Missing
        if (existing.state == OutboxState.PERMANENT_FAILURE) {
            return OutboxProcessResult.PermanentFailure(existing.asStoredFailure())
        }
        if (existing.state == OutboxState.IN_FLIGHT) {
            return OutboxProcessResult.AlreadyInFlight
        }
        if (existing.nextAttemptAt > now) {
            return OutboxProcessResult.NotReady
        }
        if (outboxDao.getNextReady(now)?.operationKey != operationKey) {
            return OutboxProcessResult.NotReady
        }
        if (outboxDao.claim(operationKey, now) != 1) {
            return OutboxProcessResult.AlreadyInFlight
        }
        val claimed = outboxDao.get(operationKey) ?: return OutboxProcessResult.Missing

        return try {
            deliver(claimed)
        } catch (cancellation: CancellationException) {
            // Keep the row in-flight. A later worker resets it after the stale timeout.
            throw cancellation
        } catch (error: Exception) {
            recordFailure(claimed, error)
        }
    }

    suspend fun resetStaleInFlight(): Int {
        val now = clock.nowMillis()
        val staleBefore = now - STALE_IN_FLIGHT_MS
        var resetCount = 0
        transactionRunner.run {
            messageDao.markStaleInFlightPending(
                staleBefore = staleBefore,
                errorCode = STALE_ERROR_CODE,
                errorMessage = STALE_ERROR_MESSAGE
            )
            resetCount = outboxDao.resetStaleInFlight(
                staleBefore = staleBefore,
                now = now,
                errorCode = STALE_ERROR_CODE,
                errorMessage = STALE_ERROR_MESSAGE
            )
        }
        return resetCount
    }

    suspend fun resetAllInFlight(): Int {
        var resetCount = 0
        transactionRunner.run {
            messageDao.markAllInFlightPending(
                errorCode = COLD_START_ERROR_CODE,
                errorMessage = COLD_START_ERROR_MESSAGE
            )
            resetCount = outboxDao.resetAllInFlight(
                now = clock.nowMillis(),
                errorCode = COLD_START_ERROR_CODE,
                errorMessage = COLD_START_ERROR_MESSAGE
            )
        }
        return resetCount
    }

    suspend fun purgeExpiredPermanentFailures(): Int {
        val olderThan = clock.nowMillis() - PERMANENT_FAILURE_RETENTION_MS
        var purgedCount = 0
        transactionRunner.run {
            val expired = outboxDao.getExpiredPermanentFailures(olderThan)
            val affectedChatIds = expired.mapNotNull { operation ->
                messageDao.getMessageByOperationKey(operation.operationKey)?.chatId
            }.toSet()
            expired.forEach { operation ->
                messageDao.deleteByOperationKey(operation.operationKey)
            }
            purgedCount = outboxDao.purgePermanentFailures(olderThan)
            check(purgedCount == expired.size) {
                "Expired outbox/message cleanup was not atomic"
            }
            affectedChatIds.forEach { updateChatPreview(it) }
        }
        return purgedCount
    }

    private suspend fun deliver(operation: OutboxOperationEntity): OutboxProcessResult =
        when (operation.operationType) {
            OutboxOperationType.CHAT_CREATE -> deliverCreateChat(operation)
            OutboxOperationType.MESSAGE_SEND -> deliverMessage(operation)
            else -> throw IllegalArgumentException("Unsupported outbox operation type")
        }

    private suspend fun deliverCreateChat(
        operation: OutboxOperationEntity
    ): OutboxProcessResult {
        val request = payloadCodec.decodeCreateChat(operation.payload)
        val response = apiService.createChat(operation.operationKey, request)
        val entity = response.toChatEntity(tokenManager.getUserId(), ::parseDateToEpoch)
        reconcile {
            chatDao.insertChat(entity)
            check(outboxDao.delete(operation.operationKey) == 1) {
                "Delivered outbox row was not removed"
            }
        }
        return OutboxProcessResult.Delivered(chat = entity)
    }

    private suspend fun deliverMessage(
        operation: OutboxOperationEntity
    ): OutboxProcessResult {
        val chatId = requireNotNull(operation.aggregateId) {
            "message.send requires an aggregate chat id"
        }
        val request = payloadCodec.decodeSendMessage(operation.payload)
        val response = apiService.sendMessage(chatId, operation.operationKey, request)
        val optimistic = messageDao.getMessageByOperationKey(operation.operationKey)
        val entity = response.toMessageEntity(::parseDateToEpoch).copy(
            clientOperationKey = operation.operationKey,
            clientPayloadHash = optimistic?.clientPayloadHash,
            deliveryState = MessageDeliveryState.DELIVERED,
            deliveryErrorCode = null,
            deliveryErrorMessage = null
        )
        reconcile {
            messageDao.deleteByOperationKey(operation.operationKey)
            messageDao.upsertAuthoritativeMessage(entity)
            chatDao.getChatById(chatId)?.let { chat ->
                val newest = messageDao.getLastMessageForChat(chatId)
                chatDao.insertChat(
                    chat.copy(
                        lastMessageContent = newest?.previewContent(),
                        lastMessageTime = newest?.createdAt
                    )
                )
            }
            check(outboxDao.delete(operation.operationKey) == 1) {
                "Delivered outbox row was not removed"
            }
        }
        return OutboxProcessResult.Delivered(message = entity)
    }

    private suspend fun reconcile(block: suspend () -> Unit) {
        try {
            transactionRunner.run(block)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            throw OutboxReconciliationException(error)
        }
    }

    private suspend fun recordFailure(
        operation: OutboxOperationEntity,
        error: Throwable
    ): OutboxProcessResult {
        val now = clock.nowMillis()
        return when (
            val decision = retryPolicy.classify(
                error = error,
                attemptCount = operation.attemptCount,
                now = now
            )
        ) {
            is DeliveryDecision.Retry -> {
                val updated = updateFailureState(
                    operation = operation,
                    deliveryState = MessageDeliveryState.PENDING,
                    errorCode = decision.metadata.code,
                    errorMessage = decision.metadata.message
                ) {
                    outboxDao.markPending(
                        operationKey = operation.operationKey,
                        now = now,
                        nextAttemptAt = decision.nextAttemptAt,
                        errorCode = decision.metadata.code,
                        httpStatus = decision.metadata.httpStatus,
                        errorMessage = decision.metadata.message
                    )
                }
                if (updated == 1) {
                    OutboxProcessResult.RetryScheduled(
                        OutboxDeliveryException(decision.metadata, isPermanent = false)
                    )
                } else {
                    OutboxProcessResult.Missing
                }
            }
            is DeliveryDecision.Permanent -> {
                val updated = updateFailureState(
                    operation = operation,
                    deliveryState = MessageDeliveryState.PERMANENT_FAILURE,
                    errorCode = decision.metadata.code,
                    errorMessage = decision.metadata.message
                ) {
                    outboxDao.markPermanentFailure(
                        operationKey = operation.operationKey,
                        now = now,
                        errorCode = decision.metadata.code,
                        httpStatus = decision.metadata.httpStatus,
                        errorMessage = decision.metadata.message
                    )
                }
                if (updated == 1) {
                    OutboxProcessResult.PermanentFailure(
                        OutboxDeliveryException(decision.metadata, isPermanent = true)
                    )
                } else {
                    OutboxProcessResult.Missing
                }
            }
        }
    }

    private suspend fun updateFailureState(
        operation: OutboxOperationEntity,
        deliveryState: String,
        errorCode: String,
        errorMessage: String,
        updateOutbox: suspend () -> Int
    ): Int {
        var updated = 0
        transactionRunner.run {
            updated = updateOutbox()
            if (updated == 1 && operation.operationType == OutboxOperationType.MESSAGE_SEND) {
                messageDao.updateDeliveryState(
                    operationKey = operation.operationKey,
                    deliveryState = deliveryState,
                    errorCode = errorCode,
                    errorMessage = errorMessage
                )
            }
        }
        return updated
    }

    private fun OutboxOperationEntity.asStoredFailure(): OutboxDeliveryException =
        OutboxDeliveryException(
            metadata = OutboxFailureMetadata(
                code = errorCode ?: "permanent_failure",
                httpStatus = errorHttpStatus,
                message = errorMessage ?: "Operation permanently failed"
            ),
            isPermanent = true
        )

    private fun parseDateToEpoch(date: String): Long = runCatching {
        val clean = date.substringBefore("Z").substringBefore("+")
        DATE_FORMAT.get()!!.parse(clean)?.time ?: clock.nowMillis()
    }.getOrElse { clock.nowMillis() }

    private fun MessageEntity.previewContent(): String? =
        content ?: mediaUrl?.let { "[Media]" }

    private suspend fun updateChatPreview(chatId: String) {
        val chat = chatDao.getChatById(chatId) ?: return
        val newest = messageDao.getLastMessageForChat(chatId)
        chatDao.insertChat(
            chat.copy(
                lastMessageContent = newest?.previewContent(),
                lastMessageTime = newest?.createdAt
            )
        )
    }

    companion object {
        const val STALE_IN_FLIGHT_MS = 15 * 60 * 1_000L
        const val PERMANENT_FAILURE_RETENTION_MS = 30L * 24 * 60 * 60 * 1_000L
        private const val STALE_ERROR_CODE = "stale_in_flight"
        private const val STALE_ERROR_MESSAGE = "Delivery interrupted; queued for retry"
        private const val COLD_START_ERROR_CODE = "cold_start_recovery"
        private const val COLD_START_ERROR_MESSAGE = "Delivery recovered after app restart"

        private val DATE_FORMAT = object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat =
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = false
                }
        }
    }
}

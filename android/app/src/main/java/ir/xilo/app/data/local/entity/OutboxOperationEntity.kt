package ir.xilo.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "outbox_operations",
    indices = [
        Index(value = ["state", "nextAttemptAt", "createdAt"]),
        Index(value = ["aggregateId", "createdAt"])
    ]
)
data class OutboxOperationEntity(
    @PrimaryKey val operationKey: String,
    val operationType: String,
    val aggregateId: String?,
    val payload: String,
    val state: String = OutboxState.PENDING,
    val attemptCount: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val nextAttemptAt: Long,
    val inFlightAt: Long? = null,
    val errorCode: String? = null,
    val errorHttpStatus: Int? = null,
    val errorMessage: String? = null
)

object OutboxOperationType {
    const val CHAT_CREATE = "chat.create"
    const val MESSAGE_SEND = "message.send"
}

object OutboxState {
    const val PENDING = "pending"
    const val IN_FLIGHT = "in_flight"
    const val PERMANENT_FAILURE = "permanent_failure"
}

package ir.xilo.app.data.remote.websocket

import ir.xilo.app.data.remote.dto.MessageResponse
import ir.xilo.app.data.remote.dto.NotificationResponse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Protocol version required by the backend realtime hub. */
const val REALTIME_PROTOCOL_VERSION = "1"

object RealtimeEvents {
    const val ACK = "ack"
    const val ERROR = "error"
    const val CHAT_JOIN = "chat.join"
    const val CHAT_LEAVE = "chat.leave"
    const val MESSAGE_SEND = "message.send"
    const val MESSAGE_RECEIVE = "message.receive"
    const val MESSAGE_EDIT = "message.edit"
    const val MESSAGE_DELETE = "message.delete"
    const val MESSAGE_READ = "message.read"
    const val MESSAGE_REACTION = "message.reaction"
    const val USER_TYPING = "user.typing"
    const val USER_ONLINE = "user.online"
    const val USER_OFFLINE = "user.offline"
    const val NOTIFICATION_NEW = "notification.new"
    const val NOTIFICATION_COUNT = "notification.count"
}

/**
 * Versioned wire envelope matching `backend/pkg/realtime.Envelope`.
 * Property names rely on the app Json SnakeCase naming strategy.
 */
@Serializable
data class RealtimeEnvelope(
    val version: String = REALTIME_PROTOCOL_VERSION,
    val event: String,
    val eventId: String? = null,
    val requestId: String? = null,
    val operationKey: String? = null,
    val occurredAt: String? = null,
    val sequence: Long? = null,
    val data: JsonElement? = null,
    val error: RealtimeErrorPayload? = null,
)

@Serializable
data class RealtimeErrorPayload(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
)

@Serializable
data class RealtimeAckPayload(
    val forEvent: String,
    val operationKey: String? = null,
    val resourceType: String? = null,
    val resourceId: String? = null,
    val replayed: Boolean = false,
    val accepted: Boolean = false,
)

@Serializable
data class RealtimeChatPayload(
    val chatId: String? = null,
) {
    fun resolvedChatId(): String? = chatId?.takeIf { it.isNotBlank() }
}

@Serializable
data class RealtimeMessageSendPayload(
    val chatId: String? = null,
    val type: String? = null,
    val content: String? = null,
    val mediaUrl: String? = null,
    val replyToId: String? = null,
    val operationKey: String? = null,
) {
    fun resolvedChatId(): String? = chatId?.takeIf { it.isNotBlank() }

    fun resolvedOperationKey(): String? = operationKey?.takeIf { it.isNotBlank() }
}

@Serializable
data class RealtimeMessageEditPayload(
    val messageId: String,
    val chatId: String,
    val content: String,
    val editedAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class RealtimeMessageDeletePayload(
    val messageId: String,
    val chatId: String,
)

@Serializable
data class RealtimeMessageReadPayload(
    val messageId: String,
    val chatId: String,
    val userId: String,
    val readAt: String,
)

@Serializable
data class RealtimeMessageReactionPayload(
    val messageId: String,
    val chatId: String,
    val userId: String,
    val reaction: String,
    val active: Boolean = false,
    val count: Long = 0,
)

@Serializable
data class RealtimeTypingPayload(
    val chatId: String,
    val userId: String,
    val typing: Boolean = false,
    val expiresAt: String? = null,
)

@Serializable
data class RealtimePresencePayload(
    val chatId: String,
    val userId: String,
    val online: Boolean = false,
    val changedAt: String? = null,
)

@Serializable
data class RealtimeNotificationCountPayload(
    val unread: Int = 0,
)

/** Typed inbound events decoded from versioned envelopes. */
sealed class RealtimeEvent {
    abstract val envelope: RealtimeEnvelope

    val eventId: String? get() = envelope.eventId

    data class MessageReceive(
        override val envelope: RealtimeEnvelope,
        val message: MessageResponse,
    ) : RealtimeEvent()

    data class MessageEdit(
        override val envelope: RealtimeEnvelope,
        val payload: RealtimeMessageEditPayload,
    ) : RealtimeEvent()

    data class MessageDelete(
        override val envelope: RealtimeEnvelope,
        val payload: RealtimeMessageDeletePayload,
    ) : RealtimeEvent()

    data class MessageRead(
        override val envelope: RealtimeEnvelope,
        val payload: RealtimeMessageReadPayload,
    ) : RealtimeEvent()

    data class MessageReaction(
        override val envelope: RealtimeEnvelope,
        val payload: RealtimeMessageReactionPayload,
    ) : RealtimeEvent()

    data class Typing(
        override val envelope: RealtimeEnvelope,
        val payload: RealtimeTypingPayload,
    ) : RealtimeEvent()

    data class Presence(
        override val envelope: RealtimeEnvelope,
        val payload: RealtimePresencePayload,
        val online: Boolean,
    ) : RealtimeEvent()

    data class Ack(
        override val envelope: RealtimeEnvelope,
        val payload: RealtimeAckPayload,
    ) : RealtimeEvent()

    data class Error(
        override val envelope: RealtimeEnvelope,
        val error: RealtimeErrorPayload,
    ) : RealtimeEvent()

    data class NotificationNew(
        override val envelope: RealtimeEnvelope,
        val notification: NotificationResponse,
    ) : RealtimeEvent()

    data class NotificationCount(
        override val envelope: RealtimeEnvelope,
        val unread: Int,
    ) : RealtimeEvent()

    data class Unknown(
        override val envelope: RealtimeEnvelope,
    ) : RealtimeEvent()
}

package ir.xilo.app.data.remote.websocket

import ir.xilo.app.data.local.dao.ChatDao
import ir.xilo.app.data.local.dao.MessageDao
import ir.xilo.app.data.local.dao.OutboxDao
import ir.xilo.app.data.local.entity.MessageDeliveryState
import ir.xilo.app.data.remote.dto.MessageResponse
import ir.xilo.app.data.repository.AuthRepository
import ir.xilo.app.data.repository.previewContent
import ir.xilo.app.data.repository.toMessageEntity
import ir.xilo.app.data.sync.OutboxTransactionRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reconciles typed realtime chat events into Room, mirroring outbox success
 * semantics for `ack` / `message.send` with an `operation_key`.
 */
@Singleton
class ChatRealtimeReconciler @Inject constructor(
    private val webSocketManager: WebSocketManager,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val outboxDao: OutboxDao,
    private val authRepository: AuthRepository,
    private val transactionRunner: OutboxTransactionRunner,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val seenEventIds = LinkedHashSet<String>()
    private val seenEventOrder = ArrayDeque<String>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        scope.launch {
            webSocketManager.events.collect { event ->
                reconcile(event)
            }
        }
    }

    internal suspend fun reconcile(event: RealtimeEvent) {
        val eventId = event.eventId
        if (!eventId.isNullOrBlank() && !rememberEventId(eventId)) {
            return
        }
        try {
            when (event) {
                is RealtimeEvent.MessageReceive -> onMessageReceive(event.message)
                is RealtimeEvent.MessageEdit -> onMessageEdit(event.payload)
                is RealtimeEvent.MessageDelete -> onMessageDelete(event.payload)
                is RealtimeEvent.MessageRead -> onMessageRead(event.payload)
                is RealtimeEvent.MessageReaction -> {
                    // Reactions are not persisted in the local Room schema yet.
                }
                is RealtimeEvent.Ack -> onAck(event)
                is RealtimeEvent.Typing,
                is RealtimeEvent.Presence,
                is RealtimeEvent.NotificationNew,
                is RealtimeEvent.NotificationCount,
                is RealtimeEvent.Error,
                is RealtimeEvent.Unknown -> Unit
            }
        } catch (_: Exception) {
            // A single bad event must not stop the collector.
        }
    }

    private suspend fun onMessageReceive(dto: MessageResponse) {
        val messageEntity = dto.toMessageEntity(::parseDateToEpoch)
        val viewingChat = webSocketManager.activeJoinedChatIds().contains(dto.chatId)
        val fromPeer = dto.senderId != authRepository.getUserId()
        transactionRunner.run {
            val inserted = messageDao.upsertAuthoritativeMessage(messageEntity)
            val chat = chatDao.getChatById(dto.chatId) ?: return@run
            val newest = messageDao.getLastMessageForChat(dto.chatId)
            chatDao.insertChat(
                chat.copy(
                    lastMessageContent = newest?.previewContent(),
                    lastMessageTime = newest?.createdAt,
                    unreadCount = when {
                        viewingChat -> 0
                        inserted && fromPeer -> chat.unreadCount + 1
                        else -> chat.unreadCount
                    }
                )
            )
        }
        if (viewingChat && fromPeer && !dto.id.startsWith("local-")) {
            webSocketManager.sendMessageRead(dto.id)
        }
    }

    private suspend fun onMessageEdit(payload: RealtimeMessageEditPayload) {
        transactionRunner.run {
            messageDao.updateEditedContent(payload.messageId, payload.content)
            updateChatPreview(payload.chatId)
        }
    }

    private suspend fun onMessageDelete(payload: RealtimeMessageDeletePayload) {
        transactionRunner.run {
            messageDao.softDeleteById(payload.messageId)
            updateChatPreview(payload.chatId)
        }
    }

    private suspend fun onMessageRead(payload: RealtimeMessageReadPayload) {
        messageDao.markRead(payload.messageId)
    }

    private suspend fun onAck(event: RealtimeEvent.Ack) {
        val payload = event.payload
        if (!payload.accepted) return
        if (payload.forEvent != RealtimeEvents.MESSAGE_SEND) return
        val operationKey = payload.operationKey
            ?.takeIf { it.isNotBlank() }
            ?: event.envelope.operationKey?.takeIf { it.isNotBlank() }
            ?: return
        val resourceId = payload.resourceId?.takeIf { it.isNotBlank() }

        transactionRunner.run {
            val optimistic = messageDao.getMessageByOperationKey(operationKey) ?: return@run
            val delivered = if (resourceId != null) {
                optimistic.copy(
                    id = resourceId,
                    deliveryState = MessageDeliveryState.DELIVERED,
                    deliveryErrorCode = null,
                    deliveryErrorMessage = null,
                )
            } else {
                optimistic.copy(
                    deliveryState = MessageDeliveryState.DELIVERED,
                    deliveryErrorCode = null,
                    deliveryErrorMessage = null,
                )
            }
            messageDao.deleteByOperationKey(operationKey)
            messageDao.upsertAuthoritativeMessage(delivered)
            outboxDao.delete(operationKey)
            updateChatPreview(optimistic.chatId)
        }
    }

    private suspend fun updateChatPreview(chatId: String) {
        val chat = chatDao.getChatById(chatId) ?: return
        val newest = messageDao.getLastMessageForChat(chatId)
        chatDao.insertChat(
            chat.copy(
                lastMessageContent = newest?.previewContent(),
                lastMessageTime = newest?.createdAt,
            )
        )
    }

    private fun parseDateToEpoch(dateStr: String): Long {
        return try {
            val cleanStr = dateStr.substringBefore("Z").substringBefore("+")
                .substringBefore(".")
            dateFormat.parse(cleanStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun rememberEventId(eventId: String): Boolean {
        synchronized(seenEventIds) {
            if (!seenEventIds.add(eventId)) return false
            seenEventOrder.addLast(eventId)
            while (seenEventOrder.size > MAX_SEEN_EVENT_IDS) {
                val oldest = seenEventOrder.removeFirst()
                seenEventIds.remove(oldest)
            }
            return true
        }
    }

    companion object {
        const val MAX_SEEN_EVENT_IDS = 512
    }
}

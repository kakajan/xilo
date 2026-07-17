package ir.xilo.app.data.repository

import ir.xilo.app.data.local.dao.ChatDao
import ir.xilo.app.data.local.dao.MessageDao
import ir.xilo.app.data.local.dao.OutboxDao
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.local.entity.MessageDeliveryState
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.local.entity.OutboxOperationEntity
import ir.xilo.app.data.local.entity.OutboxOperationType
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.ChatType
import ir.xilo.app.data.remote.dto.ChatResponse
import ir.xilo.app.data.remote.dto.CreateChatRequest
import ir.xilo.app.data.remote.dto.CursorPage
import ir.xilo.app.data.remote.dto.MessageResponse
import ir.xilo.app.data.remote.dto.MessageType
import ir.xilo.app.data.remote.dto.SendMessageRequest
import ir.xilo.app.data.remote.idempotency.OperationKeyGenerator
import ir.xilo.app.data.remote.websocket.ChatRealtimeReconciler
import ir.xilo.app.data.remote.websocket.WebSocketManager
import ir.xilo.app.data.sync.ChatOutboxProcessor
import ir.xilo.app.data.sync.OutboxClock
import ir.xilo.app.data.sync.OutboxPayloadCodec
import ir.xilo.app.data.sync.OutboxProcessResult
import ir.xilo.app.data.sync.OutboxTransactionRunner
import ir.xilo.app.data.sync.OutboxWorkScheduler
import kotlinx.coroutines.flow.Flow
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val apiService: XiloApiService,
    private val chatDao: ChatDao,
    private val messageDao: MessageDao,
    private val webSocketManager: WebSocketManager,
    @Suppress("unused")
    private val chatRealtimeReconciler: ChatRealtimeReconciler,
    private val operationKeyGenerator: OperationKeyGenerator,
    private val outboxDao: OutboxDao,
    private val outboxProcessor: ChatOutboxProcessor,
    private val outboxPayloadCodec: OutboxPayloadCodec,
    private val outboxWorkScheduler: OutboxWorkScheduler,
    private val outboxClock: OutboxClock,
    private val authRepository: AuthRepository,
    private val transactionRunner: OutboxTransactionRunner
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private fun parseDateToEpoch(dateStr: String): Long {
        return try {
            val cleanStr = dateStr.substringBefore("Z").substringBefore("+")
            dateFormat.parse(cleanStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    fun getChats(): Flow<List<ChatEntity>> = chatDao.getChatsFlow()

    fun getMessages(chatId: String): Flow<List<MessageEntity>> = messageDao.getMessagesForChatFlow(chatId)

    suspend fun createDirectChat(memberId: String): Result<ChatEntity> {
        val operationKey = operationKeyGenerator.generate()
        return createDirectChat(memberId, operationKey)
    }

    internal suspend fun createDirectChat(
        memberId: String,
        operationKey: String
    ): Result<ChatEntity> = createChat(
        request = CreateChatRequest(
            type = ChatType.DIRECT,
            memberIds = listOf(memberId)
        ),
        operationKey = operationKey
    )

    suspend fun createGroupChat(
        name: String,
        memberIds: List<String>,
        avatarUrl: String? = null
    ): Result<ChatEntity> {
        val operationKey = operationKeyGenerator.generate()
        return createGroupChat(name, memberIds, avatarUrl, operationKey)
    }

    internal suspend fun createGroupChat(
        name: String,
        memberIds: List<String>,
        avatarUrl: String?,
        operationKey: String
    ): Result<ChatEntity> = createChat(
        request = CreateChatRequest(
            type = ChatType.GROUP,
            name = name,
            avatarUrl = avatarUrl,
            memberIds = memberIds
        ),
        operationKey = operationKey
    )

    internal suspend fun createChat(
        request: CreateChatRequest,
        operationKey: String
    ): Result<ChatEntity> {
        val validatedKey = runCatching {
            operationKeyGenerator.requireValid(operationKey)
        }.getOrElse { return Result.failure(it) }
        val operation = newOutboxOperation(
            operationKey = validatedKey,
            operationType = OutboxOperationType.CHAT_CREATE,
            aggregateId = null,
            payload = runCatching { outboxPayloadCodec.encode(request) }
                .getOrElse { return Result.failure(it) }
        )
        persistOnce(operation).exceptionOrNull()?.let {
            return Result.failure(it)
        }
        // This must happen before any suspendable delivery work. KEEP is bounded,
        // and a scheduled worker recovers cancellation/process death from here on.
        outboxWorkScheduler.enqueueNow()

        return when (val processed = outboxProcessor.process(validatedKey)) {
            is OutboxProcessResult.Delivered -> processed.chat
                ?.let(Result.Companion::success)
                ?: Result.failure(IllegalStateException("Chat delivery returned no chat"))
            is OutboxProcessResult.RetryScheduled ->
                Result.failure(processed.error)
            is OutboxProcessResult.PermanentFailure ->
                Result.failure(processed.error)
            OutboxProcessResult.NotReady ->
                Result.failure(IllegalStateException("Chat delivery is queued"))
            OutboxProcessResult.AlreadyInFlight ->
                Result.failure(IllegalStateException("Chat delivery is already running"))
            OutboxProcessResult.Missing ->
                Result.failure(IllegalStateException("Chat outbox operation is missing"))
        }
    }

    suspend fun refreshChats(
        cursor: String? = null,
        limit: Int = 20
    ): Result<CursorPage<ChatResponse>> {
        return try {
            val page = apiService.listChats(cursor = cursor, limit = limit)
            val entities = page.data.map { dto ->
                dto.toChatEntity(::parseDateToEpoch)
            }
            chatDao.insertChats(entities)
            Result.success(page)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshMessages(
        chatId: String,
        cursor: String? = null,
        limit: Int = 50
    ): Result<CursorPage<MessageResponse>> {
        return try {
            val page = apiService.listMessages(
                id = chatId,
                cursor = cursor,
                limit = limit
            )
            val entities = page.data.map { dto ->
                dto.toMessageEntity(::parseDateToEpoch)
            }
            messageDao.upsertAuthoritativeMessages(entities)
            messageDao.trimMessages(chatId, 100)
            Result.success(page)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(chatId: String, content: String): Result<MessageEntity> {
        val operationKey = createMessageOperationKey()
        return sendMessage(
            chatId = chatId,
            request = SendMessageRequest(
                type = MessageType.TEXT,
                content = content
            ),
            operationKey = operationKey
        )
    }

    /**
     * Typed send path for text, image, video, and file requests.
     * Media callers must provide a mediaUrl and the matching non-text type.
     */
    suspend fun sendMessage(
        chatId: String,
        request: SendMessageRequest
    ): Result<MessageEntity> {
        val operationKey = createMessageOperationKey()
        return sendMessage(chatId, request, operationKey)
    }

    internal fun createMessageOperationKey(): String = operationKeyGenerator.generate()

    internal suspend fun sendMessage(
        chatId: String,
        request: SendMessageRequest,
        operationKey: String,
        onDurablyAccepted: (MessageEntity) -> Unit = {}
    ): Result<MessageEntity> {
        val validatedKey = runCatching {
            operationKeyGenerator.requireValid(operationKey)
        }.getOrElse { return Result.failure(it) }
        val operation = newOutboxOperation(
            operationKey = validatedKey,
            operationType = OutboxOperationType.MESSAGE_SEND,
            aggregateId = chatId,
            payload = runCatching { outboxPayloadCodec.encode(request) }
                .getOrElse { return Result.failure(it) }
        )
        val senderId = authRepository.getUserId()?.takeIf(String::isNotBlank)
            ?: return Result.failure(IllegalStateException("Authenticated user is required"))
        val accepted = persistMessageOnce(
            operation = operation,
            request = request,
            senderId = senderId
        ).getOrElse {
            return Result.failure(it)
        }
        onDurablyAccepted(accepted)
        if (accepted.deliveryState == MessageDeliveryState.DELIVERED) {
            return Result.success(accepted)
        }
        outboxWorkScheduler.enqueueNow()

        return when (val processed = outboxProcessor.process(validatedKey)) {
            is OutboxProcessResult.Delivered -> processed.message
                ?.let(Result.Companion::success)
                ?: Result.failure(IllegalStateException("Message delivery returned no message"))
            is OutboxProcessResult.RetryScheduled,
            is OutboxProcessResult.PermanentFailure,
            OutboxProcessResult.NotReady,
            OutboxProcessResult.AlreadyInFlight ->
                messageDao.getMessageByOperationKey(validatedKey)
                    ?.let(Result.Companion::success)
                    ?: Result.success(accepted)
            OutboxProcessResult.Missing ->
                messageDao.getMessageByOperationKey(validatedKey)
                    ?.let(Result.Companion::success)
                    ?: Result.failure(IllegalStateException("Message outbox operation is missing"))
        }
    }

    fun getPermanentOutboxFailures(): Flow<List<OutboxOperationEntity>> =
        outboxDao.observePermanentFailures()

    suspend fun retryPermanentOutboxOperation(operationKey: String): Result<Unit> {
        val validatedKey = runCatching {
            operationKeyGenerator.requireValid(operationKey)
        }.getOrElse { return Result.failure(it) }
        val retried = runCatching {
            var updated = 0
            transactionRunner.run {
                updated = outboxDao.retryPermanentFailure(
                    operationKey = validatedKey,
                    now = outboxClock.nowMillis()
                )
                check(updated == 1) {
                    "Permanent outbox failure was not found"
                }
                messageDao.updateDeliveryState(
                    operationKey = validatedKey,
                    deliveryState = MessageDeliveryState.PENDING,
                    errorCode = null,
                    errorMessage = null
                )
            }
            updated
        }.getOrElse { return Result.failure(it) }
        return if (retried == 1) {
            outboxWorkScheduler.enqueueNow()
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Permanent outbox failure was not found"))
        }
    }

    suspend fun deletePermanentOutboxOperation(operationKey: String): Result<Unit> {
        val validatedKey = runCatching {
            operationKeyGenerator.requireValid(operationKey)
        }.getOrElse { return Result.failure(it) }
        val deleted = runCatching {
            var updated = 0
            transactionRunner.run {
                val optimistic = messageDao.getMessageByOperationKey(validatedKey)
                updated = outboxDao.deletePermanentFailure(validatedKey)
                check(updated == 1) {
                    "Permanent outbox failure was not found"
                }
                messageDao.deleteByOperationKey(validatedKey)
                optimistic?.chatId?.let { updateChatPreview(it) }
            }
            updated
        }.getOrElse { return Result.failure(it) }
        return if (deleted == 1) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Permanent outbox failure was not found"))
        }
    }

    private fun newOutboxOperation(
        operationKey: String,
        operationType: String,
        aggregateId: String?,
        payload: String
    ): OutboxOperationEntity {
        val now = outboxClock.nowMillis()
        return OutboxOperationEntity(
            operationKey = operationKey,
            operationType = operationType,
            aggregateId = aggregateId,
            payload = payload,
            createdAt = now,
            updatedAt = now,
            nextAttemptAt = now
        )
    }

    private suspend fun persistMessageOnce(
        operation: OutboxOperationEntity,
        request: SendMessageRequest,
        senderId: String
    ): Result<MessageEntity> = runCatching {
        var accepted: MessageEntity? = null
        transactionRunner.run {
            val existingOperation = outboxDao.get(operation.operationKey)
            val existingMessage = messageDao.getMessageByOperationKey(operation.operationKey)
            val expectedPayloadHash = sha256Hex(operation.payload)

            if (existingOperation != null) {
                require(
                    existingOperation.operationType == operation.operationType &&
                        existingOperation.aggregateId == operation.aggregateId &&
                        existingOperation.payload == operation.payload
                ) {
                    "An idempotency key cannot be reused for a different payload"
                }
            }
            if (existingMessage != null) {
                require(
                    existingMessage.chatId == operation.aggregateId &&
                        existingMessage.senderId == senderId &&
                        existingMessage.clientPayloadHash == expectedPayloadHash
                ) {
                    "An idempotency key cannot be reused for a different payload"
                }
                check(
                    existingOperation != null ||
                        existingMessage.deliveryState == MessageDeliveryState.DELIVERED
                ) {
                    "Optimistic message exists without its outbox operation"
                }
                accepted = existingMessage
                return@run
            }

            val persistedOperation = existingOperation ?: operation
            if (existingOperation == null) {
                check(outboxDao.insert(operation) != -1L) {
                    "Outbox operation could not be persisted"
                }
            }

            val optimistic = MessageEntity(
                id = createOptimisticMessageId(
                    operationKey = persistedOperation.operationKey,
                    senderId = senderId,
                    request = request,
                    createdAt = persistedOperation.createdAt
                ),
                chatId = requireNotNull(persistedOperation.aggregateId),
                senderId = senderId,
                senderName = null,
                senderAvatar = null,
                content = request.content,
                mediaUrl = request.mediaUrl,
                replyToId = request.replyToId,
                isEdited = false,
                isRead = false,
                clientOperationKey = persistedOperation.operationKey,
                clientPayloadHash = expectedPayloadHash,
                deliveryState = MessageDeliveryState.PENDING,
                deliveryErrorCode = existingOperation?.errorCode,
                deliveryErrorMessage = existingOperation?.errorMessage,
                createdAt = persistedOperation.createdAt
            )
            check(messageDao.insertMessageOnce(optimistic) != -1L) {
                "Optimistic message could not be persisted"
            }
            updateChatPreview(optimistic.chatId)
            accepted = optimistic
        }
        checkNotNull(accepted)
    }

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

    private suspend fun persistOnce(
        operation: OutboxOperationEntity
    ): Result<Unit> {
        if (outboxDao.insert(operation) != -1L) {
            return Result.success(Unit)
        }
        val existing = outboxDao.get(operation.operationKey)
            ?: return Result.failure(IllegalStateException("Outbox operation disappeared"))
        return if (
            existing.operationType == operation.operationType &&
            existing.aggregateId == operation.aggregateId &&
            existing.payload == operation.payload
        ) {
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalArgumentException(
                    "An idempotency key cannot be reused for a different payload"
                )
            )
        }
    }

    fun startWebSocketConnection() {
        webSocketManager.connect()
    }

    fun stopWebSocketConnection() {
        webSocketManager.disconnect()
    }

    fun joinChat(chatId: String) {
        webSocketManager.joinChat(chatId)
    }

    fun leaveChat(chatId: String) {
        webSocketManager.leaveChat(chatId)
    }

    fun sendTyping(chatId: String, typing: Boolean = true) {
        webSocketManager.sendTyping(chatId, typing)
    }

    /** Typed inbound realtime events for chat UI (typing, presence, etc.). */
    val realtimeEvents = webSocketManager.events

    suspend fun getChatById(chatId: String): ChatEntity? = chatDao.getChatById(chatId)

    /** Get or create the per-user Saved Messages chat via `GET /api/chats/saved`. */
    suspend fun getOrCreateSavedMessages(): Result<ChatEntity> {
        return try {
            val dto = apiService.getSavedMessagesChat()
            val entity = dto.toChatEntity(::parseDateToEpoch)
            chatDao.insertChat(entity)
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getArchivedChats(): Flow<List<ChatEntity>> = chatDao.getArchivedChatsFlow()

    suspend fun archiveChat(chatId: String) {
        chatDao.updateArchivedStatus(chatId, true)
    }

    suspend fun unarchiveChat(chatId: String) {
        chatDao.updateArchivedStatus(chatId, false)
    }

    suspend fun deleteChat(chatId: String) {
        chatDao.deleteChatById(chatId)
    }
}

internal fun ChatResponse.toChatEntity(
    parseTimestamp: (String) -> Long
): ChatEntity {
    val lastMessageTimestamp = lastMessageAt ?: lastMessage?.createdAt
    return ChatEntity(
        id = id,
        type = type,
        name = name,
        avatarUrl = avatarUrl,
        lastMessageContent = lastMessage?.previewContent(),
        lastMessageTime = lastMessageTimestamp?.let(parseTimestamp),
        unreadCount = unreadCount.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
        isMuted = isMuted,
        isArchived = isArchived
    )
}

internal fun MessageResponse.toMessageEntity(
    parseTimestamp: (String) -> Long
): MessageEntity = MessageEntity(
    id = id,
    chatId = chatId,
    senderId = senderId,
    senderName = senderName,
    senderAvatar = senderAvatar,
    content = if (isDeleted) null else content,
    mediaUrl = if (isDeleted) null else mediaUrl,
    replyToId = replyToId,
    isEdited = isEdited,
    isRead = readBy.isNotEmpty(),
    isDeleted = isDeleted,
    createdAt = parseTimestamp(createdAt)
)

internal fun MessageResponse.previewContent(): String? =
    content ?: if (type == MessageType.TEXT) null else "[Media]"

internal fun MessageEntity.previewContent(): String? =
    when {
        isDeleted -> null
        content != null -> content
        mediaUrl != null -> "[Media]"
        else -> null
    }

internal fun createOptimisticMessageId(
    operationKey: String,
    senderId: String,
    request: SendMessageRequest,
    createdAt: Long
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    listOf(
        operationKey,
        senderId,
        request.type.name,
        request.content,
        request.mediaUrl,
        request.replyToId,
        createdAt.toString()
    ).forEach { component ->
        val bytes = component?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
        digest.update(bytes)
    }
    return "local-" + digest.digest().joinToString(separator = "") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}

private fun sha256Hex(value: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

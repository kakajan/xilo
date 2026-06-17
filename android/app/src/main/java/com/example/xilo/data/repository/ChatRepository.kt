package com.example.xilo.data.repository

import com.example.xilo.data.local.dao.ChatDao
import com.example.xilo.data.local.dao.MessageDao
import com.example.xilo.data.local.entity.ChatEntity
import com.example.xilo.data.local.entity.MessageEntity
import com.example.xilo.data.remote.api.XiloApiService
import com.example.xilo.data.remote.dto.MessageResponse
import com.example.xilo.data.remote.dto.SendMessageRequest
import com.example.xilo.data.remote.websocket.WebSocketManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private val json: Json
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    init {
        // Start listening to incoming WebSocket messages in the background
        scope.launch {
            webSocketManager.messages.collectLatest { text ->
                handleIncomingWebSocketMessage(text)
            }
        }
    }

    private suspend fun handleIncomingWebSocketMessage(text: String) {
        try {
            val jsonObject = json.parseToJsonElement(text).jsonObject
            val event = jsonObject["event"]?.jsonPrimitive?.content ?: return
            
            if (event == "message.receive") {
                val dataElement = jsonObject["data"] ?: return
                val dto = json.decodeFromJsonElement<MessageResponse>(dataElement)
                
                val messageEntity = MessageEntity(
                    id = dto.id,
                    chatId = dto.chatId,
                    senderId = dto.senderId,
                    senderName = dto.senderName,
                    senderAvatar = dto.senderAvatar,
                    content = dto.content,
                    mediaUrl = dto.mediaUrl,
                    replyToId = dto.replyToId,
                    isEdited = dto.isEdited,
                    isRead = dto.isRead,
                    createdAt = parseDateToEpoch(dto.createdAt)
                )
                
                // Insert message locally
                messageDao.insertMessage(messageEntity)
                
                // Update corresponding chat's last message info
                val chat = chatDao.getChatById(dto.chatId)
                if (chat != null) {
                    val updatedChat = chat.copy(
                        lastMessageContent = dto.content ?: "[Media]",
                        lastMessageTime = messageEntity.createdAt,
                        unreadCount = chat.unreadCount + 1
                    )
                    chatDao.insertChat(updatedChat)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    suspend fun refreshChats(): Result<Unit> {
        return try {
            val chatsList = apiService.listChats()
            val entities = chatsList.map { dto ->
                ChatEntity(
                    id = dto.id,
                    type = dto.type,
                    name = dto.name,
                    avatarUrl = dto.avatarUrl,
                    lastMessageContent = dto.lastMessageContent,
                    lastMessageTime = dto.lastMessageTime?.let { parseDateToEpoch(it) },
                    unreadCount = dto.unreadCount,
                    isMuted = dto.isMuted
                )
            }
            chatDao.insertChats(entities)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshMessages(chatId: String): Result<Unit> {
        return try {
            val messagesList = apiService.listMessages(chatId)
            val entities = messagesList.map { dto ->
                MessageEntity(
                    id = dto.id,
                    chatId = dto.chatId,
                    senderId = dto.senderId,
                    senderName = dto.senderName,
                    senderAvatar = dto.senderAvatar,
                    content = dto.content,
                    mediaUrl = dto.mediaUrl,
                    replyToId = dto.replyToId,
                    isEdited = dto.isEdited,
                    isRead = dto.isRead,
                    createdAt = parseDateToEpoch(dto.createdAt)
                )
            }
            messageDao.insertMessages(entities)
            messageDao.trimMessages(chatId, 100)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun sendMessage(chatId: String, content: String): Result<MessageEntity> {
        return try {
            // Optimistically send message via API
            val response = apiService.sendMessage(chatId, SendMessageRequest(content))
            val entity = MessageEntity(
                id = response.id,
                chatId = response.chatId,
                senderId = response.senderId,
                senderName = response.senderName,
                senderAvatar = response.senderAvatar,
                content = response.content,
                mediaUrl = response.mediaUrl,
                replyToId = response.replyToId,
                isEdited = response.isEdited,
                isRead = response.isRead,
                createdAt = parseDateToEpoch(response.createdAt)
            )
            messageDao.insertMessage(entity)
            
            // Also update chat locally
            val chat = chatDao.getChatById(chatId)
            if (chat != null) {
                chatDao.insertChat(
                    chat.copy(
                        lastMessageContent = content,
                        lastMessageTime = entity.createdAt
                    )
                )
            }
            Result.success(entity)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun startWebSocketConnection() {
        webSocketManager.connect()
    }

    fun stopWebSocketConnection() {
        webSocketManager.disconnect()
    }

    suspend fun getChatById(chatId: String): ChatEntity? = chatDao.getChatById(chatId)
}

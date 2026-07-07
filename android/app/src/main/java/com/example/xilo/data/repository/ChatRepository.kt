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
        // Prepopulate local SQLite DB with mock chats and messages for offline capability
        scope.launch {
            prepopulateMockData()
        }
    }

    private suspend fun prepopulateMockData() {
        if (chatDao.getChatById("saved") == null) {
            val now = System.currentTimeMillis()
            val mockChats = listOf(
                ChatEntity(
                    id = "saved",
                    type = "direct",
                    name = "پیام‌های ذخیره‌شده",
                    avatarUrl = null,
                    lastMessageContent = "یادآوری: بررسی طراحی دکمه‌های صفحه فید فردا صبح",
                    lastMessageTime = now - 3600000,
                    unreadCount = 0,
                    isArchived = false
                ),
                ChatEntity(
                    id = "1",
                    type = "direct",
                    name = "امیر محمدی",
                    avatarUrl = null,
                    lastMessageContent = "ساعت چند جلسه داریم؟",
                    lastMessageTime = now - 1800000,
                    unreadCount = 3,
                    isArchived = false
                ),
                ChatEntity(
                    id = "2",
                    type = "direct",
                    name = "سارا کریمی",
                    avatarUrl = null,
                    lastMessageContent = "پیش‌نویس نهایی شد 👍",
                    lastMessageTime = now - 14400000,
                    unreadCount = 0,
                    isArchived = false
                )
            )
            chatDao.insertChats(mockChats)

            val mockMessages = listOf(
                MessageEntity("m1", "saved", "system", "سیستم", null, "به پیام‌های ذخیره‌شده خود خوش آمدید. می‌توانید عکس‌ها، نوشته‌ها و فایل‌های خود را در اینجا نگهداری کنید.", null, null, false, true, now - 86400000),
                MessageEntity("m2", "saved", "me", "من", null, "یادآوری: بررسی طراحی دکمه‌های صفحه فید فردا صبح", null, null, false, true, now - 3600000),

                MessageEntity("m3", "1", "other", "امیر محمدی", null, "سلام! وضعیت توسعه پروژه چطور پیش میره؟", null, null, false, true, now - 7200000),
                MessageEntity("m4", "1", "me", "من", null, "سلام امیر جان، بخش‌های فرانت‌اند و بک‌اند با موفقیت متصل شدند و نسخه آزمایشی اندروید هم آماده است.", null, null, false, true, now - 3600000),
                MessageEntity("m5", "1", "other", "امیر محمدی", null, "ساعت چند جلسه داریم؟", null, null, false, true, now - 1800000),

                MessageEntity("m6", "2", "other", "سارا کریمی", null, "سلام، خسته نباشید. طرح‌های نهایی رو برای بررسی فرستادم.", null, null, false, true, now - 28800000),
                MessageEntity("m7", "2", "me", "من", null, "سلام سارا خانم، ممنون. بررسی می‌کنم و بازخورد میدم.", null, null, false, true, now - 21600000),
                MessageEntity("m8", "2", "other", "سارا کریمی", null, "پیش‌نویس نهایی شد 👍", null, null, false, true, now - 14400000)
            )
            messageDao.insertMessages(mockMessages)
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

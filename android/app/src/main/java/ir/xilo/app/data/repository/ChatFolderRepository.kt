package ir.xilo.app.data.repository

import ir.xilo.app.data.local.dao.ChatFolderDao
import ir.xilo.app.data.local.entity.ChatFolderEntity
import ir.xilo.app.data.local.entity.ChatFolderItemEntity
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.ChatFolderResponse
import ir.xilo.app.data.remote.dto.CreateChatFolderRequest
import ir.xilo.app.data.remote.dto.SetChatFolderChatsRequest
import ir.xilo.app.data.remote.dto.UpdateChatFolderRequest
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class ChatFolderWithChats(
    val id: String,
    val name: String,
    val sortOrder: Int,
    val chatIds: List<String>
)

@Singleton
class ChatFolderRepository @Inject constructor(
    private val apiService: XiloApiService,
    private val chatFolderDao: ChatFolderDao,
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun observeFolders(): Flow<List<ChatFolderEntity>> = chatFolderDao.getFoldersFlow()

    suspend fun getFolderChatIds(folderId: String): List<String> =
        chatFolderDao.getChatIdsForFolder(folderId)

    suspend fun refreshFolders(): Result<List<ChatFolderWithChats>> {
        return try {
            val remote = apiService.listChatFolders()
            persistAll(remote)
            Result.success(remote.map { it.toDomain() })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createFolder(name: String): Result<ChatFolderWithChats> {
        return try {
            val created = apiService.createChatFolder(CreateChatFolderRequest(name = name.trim()))
            chatFolderDao.insertFolder(created.toEntity(::parseDate))
            Result.success(created.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun renameFolder(folderId: String, name: String): Result<ChatFolderWithChats> {
        return try {
            val updated = apiService.updateChatFolder(
                folderId,
                UpdateChatFolderRequest(name = name.trim())
            )
            chatFolderDao.insertFolder(updated.toEntity(::parseDate))
            Result.success(updated.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteFolder(folderId: String): Result<Unit> {
        return try {
            apiService.deleteChatFolder(folderId)
            chatFolderDao.deleteItemsForFolder(folderId)
            chatFolderDao.deleteFolder(folderId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setFolderChats(folderId: String, chatIds: List<String>): Result<ChatFolderWithChats> {
        return try {
            val updated = apiService.setChatFolderChats(
                folderId,
                SetChatFolderChatsRequest(chatIds = chatIds)
            )
            chatFolderDao.deleteItemsForFolder(folderId)
            chatFolderDao.insertItems(
                updated.chatIds.mapIndexed { index, chatId ->
                    ChatFolderItemEntity(folderId = folderId, chatId = chatId, sortOrder = index)
                }
            )
            chatFolderDao.insertFolder(updated.toEntity(::parseDate))
            Result.success(updated.toDomain())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun persistAll(folders: List<ChatFolderResponse>) {
        val entities = folders.map { it.toEntity(::parseDate) }
        val items = folders.flatMap { folder ->
            folder.chatIds.mapIndexed { index, chatId ->
                ChatFolderItemEntity(
                    folderId = folder.id,
                    chatId = chatId,
                    sortOrder = index
                )
            }
        }
        chatFolderDao.replaceAll(entities, items)
    }

    private fun parseDate(value: String?): Long {
        if (value.isNullOrBlank()) return 0L
        return try {
            val clean = value.substringBefore("Z").substringBefore("+")
            dateFormat.parse(clean)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}

private fun ChatFolderResponse.toDomain() = ChatFolderWithChats(
    id = id,
    name = name,
    sortOrder = sortOrder,
    chatIds = chatIds
)

private fun ChatFolderResponse.toEntity(parseDate: (String?) -> Long) = ChatFolderEntity(
    id = id,
    name = name,
    sortOrder = sortOrder,
    createdAt = parseDate(createdAt)
)

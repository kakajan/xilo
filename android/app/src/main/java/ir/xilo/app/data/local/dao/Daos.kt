package ir.xilo.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.local.entity.ChatFolderEntity
import ir.xilo.app.data.local.entity.ChatFolderItemEntity
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.local.entity.MessageDeliveryState
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id")
    fun observeUserById(id: String): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): UserEntity?

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteUserById(id: String)
}

@Dao
interface ChatFolderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolders(folders: List<ChatFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: ChatFolderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ChatFolderItemEntity>)

    @Query("SELECT * FROM chat_folders ORDER BY sortOrder ASC, name ASC")
    fun getFoldersFlow(): Flow<List<ChatFolderEntity>>

    @Query("SELECT * FROM chat_folders ORDER BY sortOrder ASC, name ASC")
    suspend fun getFolders(): List<ChatFolderEntity>

    @Query("SELECT chatId FROM chat_folder_items WHERE folderId = :folderId ORDER BY sortOrder ASC")
    suspend fun getChatIdsForFolder(folderId: String): List<String>

    @Query("SELECT * FROM chat_folder_items WHERE folderId = :folderId")
    suspend fun getItemsForFolder(folderId: String): List<ChatFolderItemEntity>

    @Query("DELETE FROM chat_folder_items WHERE folderId = :folderId")
    suspend fun deleteItemsForFolder(folderId: String)

    @Query("DELETE FROM chat_folders WHERE id = :folderId")
    suspend fun deleteFolder(folderId: String)

    @Query("DELETE FROM chat_folders")
    suspend fun clearFolders()

    @Query("DELETE FROM chat_folder_items")
    suspend fun clearItems()

    @Transaction
    suspend fun replaceAll(folders: List<ChatFolderEntity>, items: List<ChatFolderItemEntity>) {
        clearItems()
        clearFolders()
        if (folders.isNotEmpty()) insertFolders(folders)
        if (items.isNotEmpty()) insertItems(items)
    }
}

@Dao
interface PostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity)

    @Update
    suspend fun updatePost(post: PostEntity)

    @Query("UPDATE posts SET isLiked = :isLiked, likeCount = :likeCount WHERE id = :postId")
    suspend fun updateLikeState(postId: String, isLiked: Boolean, likeCount: Int): Int

    @Query("SELECT * FROM posts ORDER BY feedRank ASC, createdAt DESC, id DESC LIMIT 50")
    fun getFeedFlow(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun getPostById(id: String): PostEntity?

    @Query("SELECT * FROM posts WHERE slug = :slug")
    suspend fun getPostBySlug(slug: String): PostEntity?

    @Query("SELECT * FROM posts WHERE authorUsername = :username ORDER BY createdAt DESC, id DESC")
    fun getUserPostsFlow(username: String): Flow<List<PostEntity>>

    @Query("DELETE FROM posts WHERE id = :id")
    suspend fun deletePostById(id: String)

    @Query("DELETE FROM posts")
    suspend fun clearAllPosts()
}

@Dao
interface CommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)

    @Update
    suspend fun updateComment(comment: CommentEntity)

    @Query("SELECT * FROM comments WHERE id = :id")
    suspend fun getCommentById(id: String): CommentEntity?

    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY createdAt ASC")
    fun getCommentsForPostFlow(postId: String): Flow<List<CommentEntity>>

    @Query("DELETE FROM comments WHERE postId = :postId")
    suspend fun clearCommentsForPost(postId: String)

    /** Clear + insert in one transaction so Flow observers never see an empty intermediate list. */
    @Transaction
    suspend fun replaceCommentsForPost(postId: String, comments: List<CommentEntity>) {
        clearCommentsForPost(postId)
        if (comments.isNotEmpty()) {
            insertComments(comments)
        }
    }

    @Query("SELECT * FROM comments ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentCommentsFlow(limit: Int): Flow<List<CommentEntity>>
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("SELECT * FROM chats WHERE isArchived = 0 ORDER BY lastMessageTime DESC")
    fun getChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE isArchived = 1 ORDER BY lastMessageTime DESC")
    fun getArchivedChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChatById(id: String): ChatEntity?

    @Query("UPDATE chats SET isArchived = :isArchived WHERE id = :chatId")
    suspend fun updateArchivedStatus(chatId: String, isArchived: Boolean)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChatById(chatId: String)
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessageOnce(message: MessageEntity): Long

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: String): MessageEntity?

    @Query("SELECT * FROM messages WHERE clientOperationKey = :operationKey")
    suspend fun getMessageByOperationKey(operationKey: String): MessageEntity?

    @Query(
        """
        SELECT * FROM messages
        WHERE chatId = :chatId
          AND (
            deliveryState != :deliveredState
            OR id IN (
                SELECT id FROM messages
                WHERE chatId = :chatId AND deliveryState = :deliveredState
                ORDER BY createdAt DESC
                LIMIT :deliveredLimit
            )
          )
        ORDER BY createdAt ASC, id ASC
        """
    )
    fun getMessagesForChatFlow(
        chatId: String,
        deliveredState: String = MessageDeliveryState.DELIVERED,
        deliveredLimit: Int = 100
    ): Flow<List<MessageEntity>>

    /**
     * Realtime/history duplicates update authoritative fields while preserving
     * REST correlation already attached to the same server message.
     *
     * @return true only when this server id was not previously cached.
     */
    @Transaction
    suspend fun upsertAuthoritativeMessage(message: MessageEntity): Boolean {
        val existing = getMessageById(message.id)
        insertMessage(
            message.copy(
                clientOperationKey =
                    message.clientOperationKey ?: existing?.clientOperationKey,
                clientPayloadHash =
                    message.clientPayloadHash ?: existing?.clientPayloadHash,
                // Authoritative receive restores a previously soft-deleted row.
                isDeleted = message.isDeleted,
                deliveryState = MessageDeliveryState.DELIVERED,
                deliveryErrorCode = null,
                deliveryErrorMessage = null
            )
        )
        return existing == null
    }

    @Transaction
    suspend fun upsertAuthoritativeMessages(messages: List<MessageEntity>) {
        messages.forEach { upsertAuthoritativeMessage(it) }
    }

    @Query(
        """
        UPDATE messages
        SET deliveryState = :deliveryState,
            deliveryErrorCode = :errorCode,
            deliveryErrorMessage = :errorMessage
        WHERE clientOperationKey = :operationKey
        """
    )
    suspend fun updateDeliveryState(
        operationKey: String,
        deliveryState: String,
        errorCode: String?,
        errorMessage: String?
    ): Int

    @Query("DELETE FROM messages WHERE clientOperationKey = :operationKey")
    suspend fun deleteByOperationKey(operationKey: String): Int

    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String): Int

    @Query(
        """
        UPDATE messages
        SET isDeleted = 1,
            content = NULL,
            mediaUrl = NULL
        WHERE id = :messageId
        """
    )
    suspend fun softDeleteById(messageId: String): Int

    @Query(
        """
        UPDATE messages
        SET content = :content,
            isEdited = 1
        WHERE id = :messageId
          AND isDeleted = 0
        """
    )
    suspend fun updateEditedContent(messageId: String, content: String): Int

    @Query(
        """
        UPDATE messages
        SET isRead = 1
        WHERE id = :messageId
        """
    )
    suspend fun markRead(messageId: String): Int

    @Query(
        """
        UPDATE messages
        SET deliveryState = :pendingState,
            deliveryErrorCode = :errorCode,
            deliveryErrorMessage = :errorMessage
        WHERE clientOperationKey IN (
            SELECT operationKey FROM outbox_operations
            WHERE state = :inFlightState
              AND inFlightAt IS NOT NULL
              AND inFlightAt <= :staleBefore
        )
        """
    )
    suspend fun markStaleInFlightPending(
        staleBefore: Long,
        errorCode: String,
        errorMessage: String,
        pendingState: String = MessageDeliveryState.PENDING,
        inFlightState: String = "in_flight"
    ): Int

    @Query(
        """
        UPDATE messages
        SET deliveryState = :pendingState,
            deliveryErrorCode = :errorCode,
            deliveryErrorMessage = :errorMessage
        WHERE clientOperationKey IN (
            SELECT operationKey FROM outbox_operations
            WHERE state = :inFlightState
        )
        """
    )
    suspend fun markAllInFlightPending(
        errorCode: String,
        errorMessage: String,
        pendingState: String = MessageDeliveryState.PENDING,
        inFlightState: String = "in_flight"
    ): Int

    @Query(
        """
        DELETE FROM messages
        WHERE chatId = :chatId
          AND deliveryState = :deliveredState
          AND id NOT IN (
            SELECT id FROM messages
            WHERE chatId = :chatId AND deliveryState = :deliveredState
            ORDER BY createdAt DESC
            LIMIT :limit
        )
        """
    )
    suspend fun trimMessages(
        chatId: String,
        limit: Int,
        deliveredState: String = MessageDeliveryState.DELIVERED
    )

    @Query(
        """
        SELECT * FROM messages
        WHERE chatId = :chatId
          AND isDeleted = 0
        ORDER BY createdAt DESC, id DESC
        LIMIT 1
        """
    )
    suspend fun getLastMessageForChat(chatId: String): MessageEntity?
}

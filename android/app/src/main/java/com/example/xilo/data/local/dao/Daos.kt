package com.example.xilo.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.xilo.data.local.entity.ChatEntity
import com.example.xilo.data.local.entity.CommentEntity
import com.example.xilo.data.local.entity.MessageEntity
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getUserById(id: String): UserEntity?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): UserEntity?
}

@Dao
interface PostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosts(posts: List<PostEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: PostEntity)

    @Query("SELECT * FROM posts ORDER BY createdAt DESC LIMIT 50")
    fun getFeedFlow(): Flow<List<PostEntity>>

    @Query("SELECT * FROM posts WHERE id = :id")
    suspend fun getPostById(id: String): PostEntity?

    @Query("SELECT * FROM posts WHERE slug = :slug")
    suspend fun getPostBySlug(slug: String): PostEntity?

    @Query("SELECT * FROM posts WHERE authorUsername = :username ORDER BY createdAt DESC")
    fun getUserPostsFlow(username: String): Flow<List<PostEntity>>

    @Query("DELETE FROM posts")
    suspend fun clearAllPosts()
}

@Dao
interface CommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComments(comments: List<CommentEntity>)

    @Query("SELECT * FROM comments WHERE postId = :postId ORDER BY createdAt ASC")
    fun getCommentsForPostFlow(postId: String): Flow<List<CommentEntity>>

    @Query("DELETE FROM comments WHERE postId = :postId")
    suspend fun clearCommentsForPost(postId: String)

    @Query("SELECT * FROM comments ORDER BY createdAt DESC LIMIT :limit")
    fun getRecentCommentsFlow(limit: Int): Flow<List<CommentEntity>>
}

@Dao
interface ChatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<ChatEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity)

    @Query("SELECT * FROM chats ORDER BY lastMessageTime DESC")
    fun getChatsFlow(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :id")
    suspend fun getChatById(id: String): ChatEntity?
}

@Dao
interface MessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt ASC LIMIT 100")
    fun getMessagesForChatFlow(chatId: String): Flow<List<MessageEntity>>

    @Query(
        """
        DELETE FROM messages WHERE chatId = :chatId AND id NOT IN (
            SELECT id FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT :limit
        )
        """
    )
    suspend fun trimMessages(chatId: String, limit: Int)

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLastMessageForChat(chatId: String): MessageEntity?
}

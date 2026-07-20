package ir.xilo.app.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import ir.xilo.app.data.local.dao.ChatDao
import ir.xilo.app.data.local.dao.ChatFolderDao
import ir.xilo.app.data.local.dao.CommentDao
import ir.xilo.app.data.local.dao.MessageDao
import ir.xilo.app.data.local.dao.OutboxDao
import ir.xilo.app.data.local.dao.PostDao
import ir.xilo.app.data.local.dao.UserDao
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.data.local.entity.ChatFolderEntity
import ir.xilo.app.data.local.entity.ChatFolderItemEntity
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.local.entity.OutboxOperationEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        PostEntity::class,
        CommentEntity::class,
        ChatEntity::class,
        MessageEntity::class,
        OutboxOperationEntity::class,
        ChatFolderEntity::class,
        ChatFolderItemEntity::class
    ],
    version = 14,
    exportSchema = true
)
abstract class XiloDatabase : RoomDatabase() {
    abstract val userDao: UserDao
    abstract val postDao: PostDao
    abstract val commentDao: CommentDao
    abstract val chatDao: ChatDao
    abstract val messageDao: MessageDao
    abstract val outboxDao: OutboxDao
    abstract val chatFolderDao: ChatFolderDao
}

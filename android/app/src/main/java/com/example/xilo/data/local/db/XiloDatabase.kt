package com.example.xilo.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.xilo.data.local.dao.ChatDao
import com.example.xilo.data.local.dao.CommentDao
import com.example.xilo.data.local.dao.MessageDao
import com.example.xilo.data.local.dao.PostDao
import com.example.xilo.data.local.dao.UserDao
import com.example.xilo.data.local.entity.ChatEntity
import com.example.xilo.data.local.entity.CommentEntity
import com.example.xilo.data.local.entity.MessageEntity
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        PostEntity::class,
        CommentEntity::class,
        ChatEntity::class,
        MessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class XiloDatabase : RoomDatabase() {
    abstract val userDao: UserDao
    abstract val postDao: PostDao
    abstract val commentDao: CommentDao
    abstract val chatDao: ChatDao
    abstract val messageDao: MessageDao
}

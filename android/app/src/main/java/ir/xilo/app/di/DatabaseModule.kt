package ir.xilo.app.di

import android.content.Context
import androidx.room.Room
import ir.xilo.app.data.local.dao.*
import ir.xilo.app.data.local.db.XiloDatabase
import ir.xilo.app.data.local.db.XiloMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): XiloDatabase {
        return Room.databaseBuilder(
            context,
            XiloDatabase::class.java,
            "xilo_db"
        ).addMigrations(*XiloMigrations.ALL).build()
    }

    @Provides
    fun provideUserDao(db: XiloDatabase): UserDao = db.userDao

    @Provides
    fun providePostDao(db: XiloDatabase): PostDao = db.postDao

    @Provides
    fun provideCommentDao(db: XiloDatabase): CommentDao = db.commentDao

    @Provides
    fun provideChatDao(db: XiloDatabase): ChatDao = db.chatDao

    @Provides
    fun provideMessageDao(db: XiloDatabase): MessageDao = db.messageDao

    @Provides
    fun provideOutboxDao(db: XiloDatabase): OutboxDao = db.outboxDao

    @Provides
    fun provideChatFolderDao(db: XiloDatabase): ChatFolderDao = db.chatFolderDao
}

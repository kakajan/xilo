package com.example.xilo.di

import android.content.Context
import androidx.room.Room
import com.example.xilo.data.local.dao.*
import com.example.xilo.data.local.db.XiloDatabase
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
        ).fallbackToDestructiveMigration().build()
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
}

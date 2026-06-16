package com.example.xilo.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val email: String,
    val displayName: String?,
    val avatarUrl: String?,
    val bio: String?,
    val isFollowed: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0
)

@Entity(tableName = "posts")
data class PostEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val authorName: String?,
    val authorUsername: String,
    val authorAvatar: String?,
    val title: String,
    val slug: String,
    val content: String, // Stored as raw text/markdown
    val excerpt: String?,
    val coverImageUrl: String?,
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val createdAt: Long // Timestamp in ms
)

@Entity(tableName = "comments")
data class CommentEntity(
    @PrimaryKey val id: String,
    val postId: String,
    val authorId: String,
    val authorName: String?,
    val authorUsername: String,
    val authorAvatar: String?,
    val parentId: String?,
    val rootId: String?,
    val depth: Int = 0,
    val content: String,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val isLiked: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: Long
)

@Entity(tableName = "chats")
data class ChatEntity(
    @PrimaryKey val id: String,
    val type: String, // "direct" or "group"
    val name: String?,
    val avatarUrl: String?,
    val lastMessageContent: String?,
    val lastMessageTime: Long?,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String?,
    val senderAvatar: String?,
    val content: String?,
    val mediaUrl: String?,
    val replyToId: String?,
    val isEdited: Boolean = false,
    val isRead: Boolean = false,
    val createdAt: Long
)

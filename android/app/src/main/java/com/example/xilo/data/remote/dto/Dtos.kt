package com.example.xilo.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ────────────────────── Auth DTOs ──────────────────────

@Serializable
data class RegisterRequest(
    val email: String,
    val username: String,
    val password: String,
    val displayName: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

/**
 * Matches Go backend AuthResponse:
 * { "access_token": "...", "refresh_token": "...", "expires_in": 3600, "user": {...} }
 * JsonNamingStrategy.SnakeCase maps accessToken -> access_token automatically
 */
@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int = 3600,
    val user: UserResponse? = null
)

// Keep TokenResponse as alias for legacy references
typealias TokenResponse = AuthResponse

@Serializable
data class UserResponse(
    val id: String,
    val username: String,
    val email: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val role: String = "reader",
    val emailVerified: Boolean = false,
    val isVerified: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0,
    val createdAt: String? = null
)

// ────────────────────── Post DTOs ──────────────────────

@Serializable
data class CreatePostRequest(
    val title: String,
    val slug: String,
    val excerpt: String? = null,
    val content: String, // Expected to be Tiptap JSON or text
    val coverImageUrl: String? = null,
    val categoryId: String? = null,
    val isPublished: Boolean = true
)

@Serializable
data class PostResponse(
    val id: String,
    val authorId: String,
    val author: UserResponse? = null,
    val title: String,
    val slug: String,
    val content: String = "",
    val contentMd: String = "",
    val excerpt: String? = null,
    val coverImageUrl: String? = null,
    val category: String? = null,
    val tags: List<String> = emptyList(),
    val likeCount: Int = 0,
    val commentCount: Int = 0,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val readingTime: Int = 1,
    val isPremium: Boolean = false,
    val createdAt: String = "",
    val publishedAt: String? = null
)

// ────────────────────── Comment DTOs ──────────────────────

@Serializable
data class CommentResponse(
    val id: String,
    val postId: String,
    val authorId: String,
    val author: UserResponse? = null,
    val parentId: String? = null,
    val rootId: String? = null,
    val depth: Int = 0,
    val content: String,
    val likeCount: Int = 0,
    val replyCount: Int = 0,
    val isLiked: Boolean = false,
    val isPinned: Boolean = false,
    val createdAt: String
)

@Serializable
data class CreateCommentRequest(
    val content: String,
    val parentId: String? = null,
    val rootId: String? = null,
    val mediaUrl: String? = null
)

// ────────────────────── Chat DTOs ──────────────────────

@Serializable
data class ChatResponse(
    val id: String,
    val type: String,
    val name: String? = null,
    val avatarUrl: String? = null,
    val lastMessageContent: String? = null,
    val lastMessageTime: String? = null,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false
)

@Serializable
data class MessageResponse(
    val id: String,
    val chatId: String,
    val senderId: String,
    val senderName: String? = null,
    val senderAvatar: String? = null,
    val content: String? = null,
    val mediaUrl: String? = null,
    val replyToId: String? = null,
    val isEdited: Boolean = false,
    val isRead: Boolean = false,
    val createdAt: String
)

@Serializable
data class SendMessageRequest(
    val content: String?,
    val mediaUrl: String? = null,
    val replyToId: String? = null
)

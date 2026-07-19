package ir.xilo.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CursorPage<T>(
    val data: List<T> = emptyList(),
    @SerialName("next_cursor")
    val nextCursor: String? = null,
    @SerialName("has_more")
    val hasMore: Boolean = false
)

// ────────────────────── Auth DTOs ──────────────────────

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    /** Optional; server assigns a temporary tmp_* handle when blank. */
    val username: String = "",
    val displayName: String? = null
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RequestOTPRequest(
    val phone: String,
    val purpose: String = "auth"
)

@Serializable
data class VerifyOTPLoginRequest(
    val phone: String,
    val code: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class LogoutRequest(
    val refreshToken: String = ""
)

@Serializable
data class UpdateProfileRequest(
    val displayName: String = "",
    val bio: String = "",
    val avatarUrl: String = "",
    val preferredLanguage: String = "",
    val preferredCalendar: String = "",
    val username: String = "",
)

@Serializable
data class LanguagesResponse(
    val languages: List<LanguageInfo> = emptyList(),
    val default: String = "fa",
    val calendarDefaults: Map<String, String> = emptyMap()
)

@Serializable
data class PlatformSettingsResponse(
    val calendarDefaults: Map<String, String> = emptyMap(),
    val theme: ThemeSettingsDto? = null,
    val brand: BrandSettingsDto? = null,
)

@Serializable
data class BrandSettingsDto(
    val nameFa: String = "آیله",
    val nameEn: String = "aile",
    val display: String = "آیله | aile",
)

@Serializable
data class ThemeSettingsDto(
    val light: ThemePaletteDto = ThemePaletteDto(),
    val dark: ThemePaletteDto = ThemePaletteDto()
)

@Serializable
data class ThemePaletteDto(
    val primary: String = "",
    val primaryHover: String = "",
    val primaryPressed: String = "",
    val primarySurface: String = "",
    val background: String = "",
    val backgroundSecondary: String = "",
    val backgroundTertiary: String = "",
    val textPrimary: String = "",
    val textSecondary: String = "",
    val textTertiary: String = "",
    val border: String = "",
    val borderStrong: String = "",
    val error: String = "",
    val success: String = "",
    val warning: String = "",
    val bubbleOwn: String = "",
    val bubbleOthers: String = ""
)

@Serializable
data class LanguageInfo(
    val code: String = "",
    val nameNative: String = "",
    val nameEnglish: String = "",
    val direction: String = "ltr"
)

@Serializable
data class UploadResponse(
    val id: String,
    val url: String,
    val variants: Map<String, String> = emptyMap(),
    val width: Int = 0,
    val height: Int = 0,
    val size: Long = 0
)

@Serializable
data class SessionResponse(
    val id: String,
    val family: String = "",
    val deviceName: String? = null,
    val platform: String? = null,
    val userAgent: String? = null,
    val ip: String? = null,
    val lastSeenAt: String? = null,
    val createdAt: String? = null,
    val isCurrent: Boolean = false
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
    /** Absent on public profile payloads and some nested author objects. */
    val email: String = "",
    val phone: String? = null,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val role: String = "reader",
    val emailVerified: Boolean = false,
    val isVerified: Boolean = false,
    val preferredLanguage: String? = null,
    val preferredCalendar: String? = null,
    @SerialName("username_pending")
    val usernamePending: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0,
    val createdAt: String? = null
)

/** Matches `GET /api/users/:username` (`publicProfileResponse` on the gateway). */
@Serializable
data class PublicProfileResponse(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val bio: String? = null,
    val isVerified: Boolean = false,
    val createdAt: String? = null,
    val stats: PublicProfileStats = PublicProfileStats(),
    val isFollowing: Boolean = false,
) {
    fun toUserResponse(): UserResponse = UserResponse(
        id = id,
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl,
        bio = bio,
        isVerified = isVerified,
        followerCount = stats.followers,
        followingCount = stats.following,
        postCount = stats.posts,
        createdAt = createdAt,
    )
}

@Serializable
data class PublicProfileStats(
    val posts: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
)

/** Row from `GET /api/users/:username/followers|following`. */
@Serializable
data class FollowListUserResponse(
    val id: String,
    val username: String,
    val displayName: String? = null,
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
    val isFollowing: Boolean = false,
)

@Serializable
data class FollowToggleResponse(
    val following: Boolean = false,
)

// ────────────────────── Post DTOs ──────────────────────

@Serializable
data class CreatePostRequest(
    val title: String,
    val slug: String,
    val excerpt: String? = null,
    val content: String, // Expected to be Tiptap JSON or text
    val contentMd: String? = null,
    val coverImageUrl: String? = null,
    val category: String? = null,
    val tags: List<String>? = null,
    val status: String = "published",
    val isPremium: Boolean = false,
)

@Serializable
data class UpdatePostRequest(
    val title: String? = null,
    val content: String? = null,
    val contentMd: String? = null,
    val excerpt: String? = null,
    val tags: List<String>? = null,
    val status: String? = null,
)

@Serializable
data class TagSuggestion(
    val tag: String,
    val count: Long = 0,
)

@Serializable
data class TagListResponse(
    val data: List<TagSuggestion> = emptyList(),
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
    val repostCount: Int = 0,
    val viewCount: Long = 0,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val isReposted: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    val viewerReactions: List<String> = emptyList(),
    val readingTime: Int = 1,
    val isPremium: Boolean = false,
    val createdAt: String = "",
    val publishedAt: String? = null
) {
    fun resolvedLikeCount(): Int =
        reactions["like"]
            ?: reactions["heart"]
            ?: likeCount

    fun resolvedIsLiked(): Boolean =
        viewerReactions.any { it == "like" || it == "heart" } || isLiked
}

@Serializable
data class ToggleReactionRequest(
    val reaction: String
)

@Serializable
data class RecordViewRequest(
    val sessionId: String,
)

@Serializable
data class RecordViewResponse(
    val counted: Boolean = false,
    val viewCount: Long = 0,
)

// ────────────────────── Comment DTOs ──────────────────────

@Serializable
data class PostRefResponse(
    val id: String,
    val title: String = "",
    val slug: String = "",
)

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
    val isBookmarked: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    @SerialName("viewer_reactions")
    val viewerReactions: List<String> = emptyList(),
    val createdAt: String,
    /** Present on profile replies (`ListUserReplies`). */
    val post: PostRefResponse? = null,
) {
    fun resolvedLikeCount(): Int =
        reactions["like"] ?: reactions["heart"] ?: likeCount

    fun resolvedDislikeCount(): Int =
        reactions["dislike"] ?: 0

    fun resolvedIsLiked(): Boolean =
        viewerReactions.any { it == "like" || it == "heart" } || isLiked

    fun resolvedIsDisliked(): Boolean =
        viewerReactions.any { it == "dislike" }
}

@Serializable
data class BookmarkedCommentResponse(
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
    val isBookmarked: Boolean = true,
    val reactions: Map<String, Int> = emptyMap(),
    @SerialName("viewer_reactions")
    val viewerReactions: List<String> = emptyList(),
    val createdAt: String,
    val post: PostRefResponse? = null,
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
enum class ChatType {
    @SerialName("direct")
    DIRECT,

    @SerialName("group")
    GROUP,

    @SerialName("saved")
    SAVED
}

@Serializable
data class ChatFolderResponse(
    val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val chatIds: List<String> = emptyList(),
    val createdAt: String? = null
)

@Serializable
data class CreateChatFolderRequest(
    val name: String
)

@Serializable
data class UpdateChatFolderRequest(
    val name: String? = null,
    val sortOrder: Int? = null
)

@Serializable
data class SetChatFolderChatsRequest(
    val chatIds: List<String>
)

@Serializable
data class CreateChatRequest(
    val type: ChatType,
    val name: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("member_ids")
    val memberIds: List<String>
)

@Serializable
data class ChatResponse(
    val id: String,
    val type: String,
    val name: String? = null,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("last_message_at")
    val lastMessageAt: String? = null,
    val members: List<ChatMemberResponse> = emptyList(),
    @SerialName("last_message")
    val lastMessage: MessageResponse? = null,
    @SerialName("unread_count")
    val unreadCount: Long = 0,
    @SerialName("is_muted")
    val isMuted: Boolean = false,
    @SerialName("is_archived")
    val isArchived: Boolean = false,
    @SerialName("current_role")
    val currentRole: String = "member"
)

@Serializable
data class ChatMemberResponse(
    @SerialName("chat_id")
    val chatId: String,
    @SerialName("user_id")
    val userId: String,
    val role: String,
    val username: String,
    @SerialName("display_name")
    val displayName: String,
    @SerialName("avatar_url")
    val avatarUrl: String? = null,
    @SerialName("joined_at")
    val joinedAt: String,
    @SerialName("last_read_at")
    val lastReadAt: String? = null,
    @SerialName("is_muted")
    val isMuted: Boolean = false,
    @SerialName("is_archived")
    val isArchived: Boolean = false
)

@Serializable
enum class MessageType {
    @SerialName("text")
    TEXT,

    @SerialName("image")
    IMAGE,

    @SerialName("video")
    VIDEO,

    @SerialName("file")
    FILE
}

@Serializable
data class MessageResponse(
    val id: String,
    @SerialName("chat_id")
    val chatId: String,
    @SerialName("sender_id")
    val senderId: String,
    val type: MessageType,
    // The backend currently does not enrich messages with sender display data.
    // Defaults keep the UI mapping safe if/when those fields are added.
    @SerialName("sender_name")
    val senderName: String? = null,
    @SerialName("sender_avatar")
    val senderAvatar: String? = null,
    val content: String? = null,
    @SerialName("media_id")
    val mediaId: String? = null,
    @SerialName("media_url")
    val mediaUrl: String? = null,
    @SerialName("reply_to_id")
    val replyToId: String? = null,
    @SerialName("is_edited")
    val isEdited: Boolean = false,
    @SerialName("is_deleted")
    val isDeleted: Boolean = false,
    @SerialName("created_at")
    val createdAt: String,
    @SerialName("updated_at")
    val updatedAt: String,
    @SerialName("edited_at")
    val editedAt: String? = null,
    @SerialName("deleted_at")
    val deletedAt: String? = null,
    val reactions: List<MessageReactionResponse> = emptyList(),
    @SerialName("read_by")
    val readBy: List<MessageReadResponse> = emptyList()
)

@Serializable
data class MessageReactionResponse(
    val reaction: String,
    val count: Long,
    val reacted: Boolean
)

@Serializable
data class MessageReadResponse(
    @SerialName("user_id")
    val userId: String,
    @SerialName("read_at")
    val readAt: String
)

@Serializable
data class SendMessageRequest(
    val type: MessageType,
    val content: String? = null,
    @SerialName("media_url")
    val mediaUrl: String? = null,
    @SerialName("reply_to_id")
    val replyToId: String? = null
)

// ────────────────────── Interests / Contacts / Discover ──────────────────────

@Serializable
data class InterestDto(
    val id: String,
    val slug: String,
    val labels: Map<String, String> = emptyMap(),
    val icon: String? = null,
    val sortOrder: Int = 0,
) {
    fun labelFor(languageCode: String): String {
        val code = languageCode.lowercase()
        return labels[code]
            ?: labels["en"]
            ?: labels["fa"]
            ?: labels.values.firstOrNull()
            ?: slug
    }
}

@Serializable
data class InterestsResponse(
    val interests: List<InterestDto> = emptyList(),
)

@Serializable
data class UserInterestsResponse(
    val interestIds: List<String> = emptyList(),
    val interests: List<InterestDto> = emptyList(),
)

@Serializable
data class UpdateInterestsRequest(
    val interestIds: List<String>,
)

@Serializable
data class ContactMatchRequest(
    val phoneHashes: List<String> = emptyList(),
    val emailHashes: List<String> = emptyList(),
)

@Serializable
data class ContactMatchUserDto(
    val id: String,
    val username: String,
    val displayName: String = "",
    val avatarUrl: String? = null,
    val alreadyFollowing: Boolean = false,
)

@Serializable
data class ContactMatchResponse(
    val matches: List<ContactMatchUserDto> = emptyList(),
)

/** Row from `GET /api/contacts` — followings with address-book sync badge. */
@Serializable
data class ContactUserDto(
    val id: String,
    val username: String,
    val displayName: String = "",
    val avatarUrl: String? = null,
    val isVerified: Boolean = false,
    val isFollowing: Boolean = true,
    val fromContacts: Boolean = false,
)

@Serializable
data class ContactsListResponse(
    val data: List<ContactUserDto> = emptyList(),
)

@Serializable
data class DiscoverCommentDto(
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
    val isBookmarked: Boolean = false,
    val reactions: Map<String, Int> = emptyMap(),
    @SerialName("viewer_reactions")
    val viewerReactions: List<String> = emptyList(),
    val createdAt: String,
    val post: PostRefResponse? = null,
) {
    fun resolvedLikeCount(): Int =
        reactions["like"] ?: reactions["heart"] ?: likeCount

    fun resolvedDislikeCount(): Int =
        reactions["dislike"] ?: 0

    fun resolvedIsLiked(): Boolean =
        viewerReactions.any { it == "like" || it == "heart" } || isLiked

    fun resolvedIsDisliked(): Boolean =
        viewerReactions.any { it == "dislike" }
}

@Serializable
data class DiscoverCommentsResponse(
    val data: List<DiscoverCommentDto> = emptyList(),
)

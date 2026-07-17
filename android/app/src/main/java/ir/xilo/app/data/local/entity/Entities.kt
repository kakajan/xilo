package ir.xilo.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: String,
    val username: String,
    val email: String,
    val phone: String? = null,
    val displayName: String?,
    val avatarUrl: String?,
    val bio: String?,
    val isFollowed: Boolean = false,
    val followerCount: Int = 0,
    val followingCount: Int = 0,
    val postCount: Int = 0
)

@Entity(
    tableName = "chat_folders",
    indices = [Index(value = ["sortOrder"])]
)
data class ChatFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L
)

@Entity(
    tableName = "chat_folder_items",
    primaryKeys = ["folderId", "chatId"],
    indices = [Index(value = ["chatId"])]
)
data class ChatFolderItemEntity(
    val folderId: String,
    val chatId: String,
    val sortOrder: Int = 0
)

@Entity(
    tableName = "posts",
    indices = [Index(value = ["feedRank"])]
)
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
    val repostCount: Int = 0,
    val isLiked: Boolean = false,
    val isBookmarked: Boolean = false,
    val isReposted: Boolean = false,
    val createdAt: Long, // Timestamp in ms
    /** Stable feed position from last refresh; engagement must not change this. */
    val feedRank: Int = Int.MAX_VALUE
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
    val dislikeCount: Int = 0,
    val replyCount: Int = 0,
    val isLiked: Boolean = false,
    val isDisliked: Boolean = false,
    val isBookmarked: Boolean = false,
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
    val isMuted: Boolean = false,
    val isArchived: Boolean = false
)

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["clientOperationKey"], unique = true),
        Index(value = ["chatId", "createdAt"])
    ]
)
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
    @ColumnInfo(defaultValue = "0")
    val isDeleted: Boolean = false,
    val clientOperationKey: String? = null,
    val clientPayloadHash: String? = null,
    @ColumnInfo(defaultValue = "'delivered'")
    val deliveryState: String = MessageDeliveryState.DELIVERED,
    val deliveryErrorCode: String? = null,
    val deliveryErrorMessage: String? = null,
    val createdAt: Long
)

object MessageDeliveryState {
    const val PENDING = "pending"
    const val DELIVERED = "delivered"
    const val PERMANENT_FAILURE = "permanent_failure"
}

package ir.xilo.app

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Main : NavKey
@Serializable
data class PostDetailKey(
    val slug: String,
    val replyToCommentId: String? = null,
    val replyToAuthor: String? = null,
    val replyToPost: Boolean = false,
) : NavKey
@Serializable
data class ProfileKey(val username: String) : NavKey
@Serializable
data class FollowListKey(
    val username: String,
    /** "Followers" or "Following" */
    val mode: String,
) : NavKey
@Serializable
data class ChatConversationKey(
    val chatId: String,
    val isSavedMessages: Boolean = false,
) : NavKey
@Serializable data object CreatePostKey : NavKey
@Serializable data object SettingsKey : NavKey
@Serializable data object SavedHubKey : NavKey
@Serializable data class ContactDetailKey(val chatId: String) : NavKey
@Serializable data object DevicesKey : NavKey
@Serializable data object ChatFoldersKey : NavKey

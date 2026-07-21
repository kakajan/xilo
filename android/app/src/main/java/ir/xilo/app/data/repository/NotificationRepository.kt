package ir.xilo.app.data.repository

import ir.xilo.app.data.local.dao.UserDao
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.NotificationPreferencesResponse
import ir.xilo.app.data.remote.dto.NotificationResponse
import ir.xilo.app.data.remote.dto.toStringMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

data class NotificationItem(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val isRead: Boolean,
    val createdAtMs: Long,
)

sealed interface NotificationDeepLink {
    data class PostDetail(
        val slug: String,
        val commentId: String? = null,
    ) : NotificationDeepLink

    data class ChatConversation(val chatId: String) : NotificationDeepLink
    data class Profile(val username: String) : NotificationDeepLink
    data object Inbox : NotificationDeepLink
    data object None : NotificationDeepLink
}

@Singleton
class NotificationRepository @Inject constructor(
    private val apiService: XiloApiService,
    private val userDao: UserDao,
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    private val _notifications = MutableStateFlow<List<NotificationItem>>(emptyList())
    val notifications: StateFlow<List<NotificationItem>> = _notifications.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _inboxInvalidated = MutableStateFlow(false)
    val inboxInvalidated: StateFlow<Boolean> = _inboxInvalidated.asStateFlow()

    suspend fun refreshInbox(limit: Int = 50): Result<List<NotificationItem>> {
        return runCatching {
            val response = apiService.listNotifications(limit = limit)
            val items = response.data.map { it.toItem(::parseDateToEpoch) }
            _notifications.value = items
            _inboxInvalidated.value = false
            items
        }
    }

    suspend fun refreshUnreadCount(): Result<Int> {
        return runCatching {
            val count = apiService.getUnreadNotificationCount().unread
            _unreadCount.value = count.coerceAtLeast(0)
            count
        }
    }

    suspend fun reconcileOnReconnect() {
        refreshUnreadCount()
        _inboxInvalidated.value = true
    }

    fun consumeInboxInvalidation() {
        _inboxInvalidated.value = false
    }

    suspend fun markRead(id: String): Result<Unit> {
        return runCatching {
            apiService.markNotificationRead(id)
            _notifications.update { list ->
                list.map { item ->
                    if (item.id == id && !item.isRead) {
                        item.copy(isRead = true)
                    } else {
                        item
                    }
                }
            }
            _unreadCount.update { current -> (current - 1).coerceAtLeast(0) }
        }
    }

    suspend fun markAllRead(): Result<Unit> {
        return runCatching {
            apiService.markAllNotificationsRead()
            _notifications.update { list -> list.map { it.copy(isRead = true) } }
            _unreadCount.value = 0
        }
    }

    suspend fun getPreferences(): Result<NotificationPreferencesResponse> {
        return runCatching { apiService.getNotificationPreferences() }
    }

    suspend fun updatePreference(key: String, enabled: Boolean): Result<Unit> {
        return runCatching {
            apiService.patchNotificationPreferences(mapOf(key to enabled))
        }
    }

    internal fun applyRealtimeNew(payload: NotificationResponse) {
        val item = payload.toItem(::parseDateToEpoch)
        _notifications.update { current ->
            if (current.any { it.id == item.id }) current else listOf(item) + current
        }
        if (!item.isRead) {
            _unreadCount.update { it + 1 }
        }
        _inboxInvalidated.value = true
    }

    internal fun applyRealtimeCount(unread: Int) {
        _unreadCount.value = unread.coerceAtLeast(0)
    }

    suspend fun resolveDeepLink(item: NotificationItem): NotificationDeepLink {
        return resolveDeepLinkFromData(item.data)
    }

    suspend fun resolveDeepLinkFromData(data: Map<String, String>): NotificationDeepLink {
        val sanitized = sanitizePushData(data)
        if (sanitized.isEmpty()) {
            return NotificationDeepLink.None
        }

        sanitized["chat_id"]?.takeIf { isSafeId(it) }?.let {
            return NotificationDeepLink.ChatConversation(it)
        }

        val postSlug = sanitized["slug"]?.takeIf { isSafeSlug(it) }
            ?: sanitized["post_slug"]?.takeIf { isSafeSlug(it) }
            ?: sanitized["post_id"]?.takeIf { isSafeId(it) }
        if (postSlug != null) {
            val commentId = sanitized["comment_id"]?.takeIf { isSafeId(it) }
            return NotificationDeepLink.PostDetail(slug = postSlug, commentId = commentId)
        }

        val followerUsername = sanitized["follower_username"]?.takeIf { isSafeUsername(it) }
            ?: sanitized["username"]?.takeIf { isSafeUsername(it) }
        if (followerUsername != null) {
            return NotificationDeepLink.Profile(followerUsername)
        }

        sanitized["follower_id"]?.takeIf { isSafeId(it) }?.let { followerId ->
            userDao.getUserById(followerId)?.username?.takeIf { isSafeUsername(it) }?.let { username ->
                return NotificationDeepLink.Profile(username)
            }
        }

        sanitized["author_id"]?.takeIf { isSafeId(it) }?.let { authorId ->
            userDao.getUserById(authorId)?.username?.takeIf { isSafeUsername(it) }?.let { username ->
                return NotificationDeepLink.Profile(username)
            }
        }

        sanitized["sender_id"]?.takeIf { isSafeId(it) }?.let { senderId ->
            userDao.getUserById(senderId)?.username?.takeIf { isSafeUsername(it) }?.let { username ->
                return NotificationDeepLink.Profile(username)
            }
        }

        if (sanitized["type"]?.isNotBlank() == true) {
            return NotificationDeepLink.Inbox
        }

        return NotificationDeepLink.None
    }

    fun sanitizePushData(raw: Map<String, String>): Map<String, String> {
        return raw.mapNotNull { (key, value) ->
            val normalizedKey = key.trim().lowercase()
            if (normalizedKey !in KNOWN_PUSH_DATA_KEYS) return@mapNotNull null
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return@mapNotNull null
            normalizedKey to trimmed
        }.toMap()
    }

    private fun NotificationResponse.toItem(parseDate: (String) -> Long): NotificationItem {
        return NotificationItem(
            id = id,
            type = type,
            title = title,
            body = body,
            data = data.toStringMap(),
            isRead = isRead,
            createdAtMs = parseDate(createdAt),
        )
    }

    companion object {
        val KNOWN_PUSH_DATA_KEYS = setOf(
            "type",
            "post_id",
            "slug",
            "post_slug",
            "chat_id",
            "message_id",
            "sender_id",
            "follower_id",
            "author_id",
            "comment_id",
            "parent_id",
            "post_author_username",
            "follower_username",
            "follower_display_name",
            "username",
            "title",
            "body",
        )
    }

    private fun isSafeId(value: String): Boolean {
        if (value.length > 128) return false
        return value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    private fun isSafeSlug(value: String): Boolean {
        if (value.isBlank() || value.length > 200) return false
        if (value.contains("://") || value.contains('/') || value.contains('\\')) return false
        return true
    }

    private fun isSafeUsername(value: String): Boolean {
        if (value.isBlank() || value.length > 64) return false
        if (value.contains("://") || value.contains('/') || value.contains('\\')) return false
        return value.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
    }

    private fun parseDateToEpoch(dateStr: String): Long {
        return try {
            val cleanStr = dateStr.substringBefore("Z").substringBefore("+")
                .substringBefore(".")
            dateFormat.parse(cleanStr)?.time ?: System.currentTimeMillis()
        } catch (_: Exception) {
            System.currentTimeMillis()
        }
    }
}

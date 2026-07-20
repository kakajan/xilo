package ir.xilo.app.push

import androidx.navigation3.runtime.NavKey
import ir.xilo.app.ChatConversationKey
import ir.xilo.app.NotificationsKey
import ir.xilo.app.PostDetailKey
import ir.xilo.app.ProfileKey
import ir.xilo.app.data.repository.NotificationDeepLink
import ir.xilo.app.data.repository.NotificationRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNavigationCoordinator @Inject constructor(
    private val notificationRepository: NotificationRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _pendingNavKey = MutableStateFlow<NavKey?>(null)
    val pendingNavKey: StateFlow<NavKey?> = _pendingNavKey.asStateFlow()

    fun handlePushData(data: Map<String, String>) {
        if (data.isEmpty()) return
        scope.launch {
            val deepLink = notificationRepository.resolveDeepLinkFromData(data)
            _pendingNavKey.value = deepLink.toNavKey()
        }
    }

    fun consumePendingNavKey() {
        _pendingNavKey.value = null
    }
}

internal fun NotificationDeepLink.toNavKey(): NavKey? = when (this) {
    is NotificationDeepLink.PostDetail -> PostDetailKey(
        slug = slug,
        replyToCommentId = commentId,
    )
    is NotificationDeepLink.ChatConversation -> ChatConversationKey(chatId = chatId)
    is NotificationDeepLink.Profile -> ProfileKey(username = username)
    NotificationDeepLink.Inbox -> NotificationsKey
    NotificationDeepLink.None -> null
}

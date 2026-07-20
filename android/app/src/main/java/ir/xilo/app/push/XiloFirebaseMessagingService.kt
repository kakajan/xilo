package ir.xilo.app.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import ir.xilo.app.data.repository.NotificationRepository
import ir.xilo.app.data.repository.PushTokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class XiloFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var pushTokenRepository: PushTokenRepository
    @Inject lateinit var pushNotificationHelper: PushNotificationHelper
    @Inject lateinit var notificationRepository: NotificationRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        pushNotificationHelper.ensureChannelCreated()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        serviceScope.launch {
            pushTokenRepository.onTokenRefreshed(token.trim())
                .onFailure { error -> Log.w(TAG, "FCM token refresh register failed", error) }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = notificationRepository.sanitizePushData(message.data)
        if (data.isEmpty()) {
            Log.d(TAG, "Ignoring push with no recognized data keys")
            return
        }

        val title = message.notification?.title?.takeIf { it.isNotBlank() }
            ?: data["title"].orEmpty()
        val body = message.notification?.body?.takeIf { it.isNotBlank() }
            ?: data["body"].orEmpty()

        pushNotificationHelper.showNotification(
            title = title,
            body = body,
            data = data,
        )

        serviceScope.launch {
            notificationRepository.refreshUnreadCount()
        }
    }

    companion object {
        private const val TAG = "XiloFirebaseMessaging"
    }
}

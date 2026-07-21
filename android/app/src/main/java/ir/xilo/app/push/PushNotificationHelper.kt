package ir.xilo.app.push

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.xilo.app.MainActivity
import ir.xilo.app.R
import ir.xilo.app.core.util.AppLocale
import ir.xilo.app.core.util.NotificationCopy
import ir.xilo.app.data.repository.NotificationRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationRepository: NotificationRepository,
) {
    fun ensureChannelCreated() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            AppLocale.string(context, R.string.push_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = AppLocale.string(context, R.string.push_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun showNotification(
        title: String,
        body: String,
        data: Map<String, String>,
    ) {
        ensureChannelCreated()
        val type = data["type"].orEmpty()
        val localizedTitle = NotificationCopy.title(context, type, title)
        val localizedBody = NotificationCopy.body(context, type, body, data)
        val displayTitle = localizedTitle.ifBlank { AppLocale.string(context, R.string.app_name) }
        val displayBody = localizedBody.ifBlank {
            AppLocale.string(context, R.string.notifications_inbox_title)
        }

        val intent = buildDeepLinkIntent(data)
        val pendingIntent = PendingIntent.getActivity(
            context,
            data.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(displayTitle)
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                data["message_id"]?.hashCode() ?: System.currentTimeMillis().toInt(),
                notification,
            )
        }
    }

    fun buildDeepLinkIntent(data: Map<String, String>): Intent {
        return Intent(context, MainActivity::class.java).apply {
            action = ACTION_PUSH_OPEN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_FROM_PUSH, true)
            NotificationRepository.KNOWN_PUSH_DATA_KEYS.forEach { key ->
                data[key]?.let { putExtra(key, it) }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "xilo_push_default"
        const val ACTION_PUSH_OPEN = "ir.xilo.app.action.PUSH_OPEN"
        const val EXTRA_FROM_PUSH = "xilo_from_push"
    }
}

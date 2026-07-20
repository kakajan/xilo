package ir.xilo.app.push

import android.content.Intent
import ir.xilo.app.data.repository.NotificationRepository

fun Intent.extractPushNotificationData(): Map<String, String>? {
    val fromPush = getBooleanExtra(PushNotificationHelper.EXTRA_FROM_PUSH, false)
    val data = NotificationRepository.KNOWN_PUSH_DATA_KEYS.mapNotNull { key ->
        getStringExtra(key)?.trim()?.takeIf { it.isNotEmpty() }?.let { key to it }
    }.toMap()
    if (data.isEmpty() && !fromPush) return null
    return data.takeIf { it.isNotEmpty() }
}

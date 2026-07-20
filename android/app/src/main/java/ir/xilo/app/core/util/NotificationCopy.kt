package ir.xilo.app.core.util

import android.content.Context
import ir.xilo.app.R

/**
 * Resolves notification title/body from [type] using localized string resources.
 * Falls back to API-provided text for unknown types and for bodies that carry user content
 * (comment excerpts, chat messages, post titles).
 */
object NotificationCopy {
    fun titleResId(type: String): Int? = when (type.trim().lowercase()) {
        "post_comment" -> R.string.notif_title_post_comment
        "comment_reply" -> R.string.notif_title_comment_reply
        "new_follower" -> R.string.notif_title_new_follower
        "post_published" -> R.string.notif_title_post_published
        "new_message" -> R.string.notif_title_new_message
        else -> null
    }

    /** Template bodies only — returns null when the API body should be kept as-is. */
    fun bodyResId(type: String): Int? = when (type.trim().lowercase()) {
        "new_follower" -> R.string.notif_body_new_follower
        else -> null
    }

    fun title(context: Context, type: String, fallback: String): String {
        val resId = titleResId(type) ?: return fallback
        return AppLocale.string(context, resId)
    }

    fun body(context: Context, type: String, fallback: String): String {
        val resId = bodyResId(type) ?: return fallback
        return AppLocale.string(context, resId)
    }
}

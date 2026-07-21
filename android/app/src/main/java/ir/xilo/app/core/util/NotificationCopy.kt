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

    fun body(
        context: Context,
        type: String,
        fallback: String,
        data: Map<String, String> = emptyMap(),
    ): String {
        if (type.trim().lowercase() != "new_follower") {
            val resId = bodyResId(type) ?: return fallback
            return AppLocale.string(context, resId)
        }
        val name = followerLabel(data)
        return if (name != null) {
            AppLocale.string(context, R.string.notif_body_new_follower, name)
        } else {
            AppLocale.string(context, R.string.notif_body_new_follower_anonymous)
        }
    }

    /** Prefer display name, else `@username` — null when actor info is missing (legacy rows). */
    fun followerLabel(data: Map<String, String>): String? {
        data["follower_display_name"]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val username = data["follower_username"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: data["username"]?.trim()?.takeIf { it.isNotEmpty() }
        return username?.let { "@$it" }
    }
}

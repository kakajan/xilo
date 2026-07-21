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
        val normalized = type.trim().lowercase()
        if (normalized == "new_follower") {
            val name = followerLabel(data)
            return if (name != null) {
                AppLocale.string(context, R.string.notif_body_new_follower, name)
            } else {
                AppLocale.string(context, R.string.notif_body_new_follower_anonymous)
            }
        }
        val actor = actorLabel(normalized, data)
        val content = fallback.trim()
        if (actor != null && content.isNotEmpty()) return "$actor: $content"
        if (actor != null) return actor
        val resId = bodyResId(normalized) ?: return fallback
        return AppLocale.string(context, resId)
    }

    /** Prefer display name, else `@username` — null when actor info is missing (legacy rows). */
    fun followerLabel(data: Map<String, String>): String? {
        data["follower_display_name"]?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val username = data["follower_username"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: data["username"]?.trim()?.takeIf { it.isNotEmpty() }
        return username?.let { "@$it" }
    }

    /** Actor for message/comment notifications — null when legacy rows lack name fields. */
    fun actorLabel(type: String, data: Map<String, String>): String? {
        return when (type.trim().lowercase()) {
            "new_message" -> {
                data["sender_display_name"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: data["sender_username"]?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }
            }
            "post_comment", "comment_reply", "comment_mention" -> {
                data["author_display_name"]?.trim()?.takeIf { it.isNotEmpty() }
                    ?: data["author_username"]?.trim()?.takeIf { it.isNotEmpty() }?.let { "@$it" }
            }
            else -> null
        }
    }
}

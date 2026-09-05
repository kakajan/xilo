package ir.xilo.app.core.util

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Canonical public website URLs shared from the Android client.
 * Paths match the Next.js routes: `/{username}/{slug}`, `/{username}`, `/p/{slug}`.
 */
object PublicWebUrls {
    private val reservedFirstSegments = setOf(
        "api",
        "bookmarks",
        "chat",
        "contacts",
        "dashboard",
        "discover",
        "login",
        "notifications",
        "p",
        "quote",
        "register",
        "saved",
        "search",
        "settings",
        "tag",
        "write",
    )

    fun normalizeOrigin(origin: String): String = origin.trim().trimEnd('/')

    fun post(origin: String, username: String, slug: String): String {
        val base = normalizeOrigin(origin)
        val s = slug.trim()
        require(s.isNotEmpty()) { "slug must not be blank" }
        val u = username.trim()
        return if (u.isEmpty()) {
            "$base/p/${enc(s)}"
        } else {
            "$base/${enc(u)}/${enc(s)}"
        }
    }

    fun comment(origin: String, username: String, slug: String, commentId: String): String {
        val id = commentId.trim()
        require(id.isNotEmpty()) { "commentId must not be blank" }
        return "${post(origin, username, slug)}?reply=${enc(id)}"
    }

    fun profile(origin: String, username: String): String {
        val u = username.trim()
        require(u.isNotEmpty()) { "username must not be blank" }
        return "${normalizeOrigin(origin)}/${enc(u)}"
    }

    fun sharePostBody(title: String, url: String): String = buildString {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isNotEmpty()) {
            append(trimmedTitle)
            append('\n')
        }
        append(url)
    }

    fun parse(url: String, allowedOrigin: String): PublicWebDestination? {
        val parsed = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val allowedHost = runCatching { URI(normalizeOrigin(allowedOrigin)) }
            .getOrNull()
            ?.host
            ?.lowercase()
            ?.removePrefix("www.")
            ?: return null
        val host = parsed.host?.lowercase()?.removePrefix("www.") ?: return null
        if (host != allowedHost) return null

        val segments = (parsed.path ?: "")
            .trim('/')
            .split('/')
            .filter { it.isNotEmpty() }
            .map { dec(it) }
        val reply = queryParam(parsed.rawQuery, "reply")

        return when {
            segments.isEmpty() -> null
            segments[0] == "p" && segments.size >= 2 ->
                PublicWebDestination.Post(slug = segments[1], replyId = reply)
            segments[0] == "tag" && segments.size >= 2 ->
                PublicWebDestination.Tag(segments[1])
            segments[0] == "chat" && segments.size >= 2 ->
                PublicWebDestination.Chat(segments[1])
            segments[0] in reservedFirstSegments -> null
            segments.size == 1 -> PublicWebDestination.Profile(segments[0])
            segments.size >= 2 && segments[1] in setOf("followers", "following") ->
                PublicWebDestination.Profile(segments[0])
            segments.size >= 2 ->
                PublicWebDestination.Post(slug = segments[1], replyId = reply)
            else -> null
        }
    }

    private fun queryParam(rawQuery: String?, name: String): String? {
        if (rawQuery.isNullOrBlank()) return null
        return rawQuery.split('&').firstNotNullOfOrNull { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@firstNotNullOfOrNull null
            val key = dec(part.substring(0, eq))
            if (key != name) return@firstNotNullOfOrNull null
            dec(part.substring(eq + 1)).takeIf { it.isNotBlank() }
        }
    }

    private fun enc(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")

    private fun dec(value: String): String =
        URLDecoder.decode(value.replace("+", "%20"), StandardCharsets.UTF_8.name())
}

sealed interface PublicWebDestination {
    data class Post(val slug: String, val replyId: String? = null) : PublicWebDestination
    data class Profile(val username: String) : PublicWebDestination
    data class Tag(val tag: String) : PublicWebDestination
    data class Chat(val chatId: String) : PublicWebDestination
}

package ir.xilo.app.data.local.entity

/**
 * Resolves a user-facing chat title.
 *
 * Direct chats store peer identity in [ChatEntity.peerDisplayName] / [ChatEntity.peerUsername]
 * because the backend leaves [ChatEntity.name] null for DMs.
 */
fun ChatEntity.displayTitle(
    fallback: String,
    savedTitle: String = fallback,
): String {
    if (type == "saved") return savedTitle
    val peerName = peerDisplayName?.takeIf { it.isNotBlank() }
    val peerUser = peerUsername?.takeIf { it.isNotBlank() }
    val chatName = name?.takeIf { it.isNotBlank() }
    return when (type) {
        "direct" -> peerName ?: peerUser ?: chatName ?: fallback
        else -> chatName ?: peerName ?: peerUser ?: fallback
    }
}

/** Prefer peer avatar for DMs; fall back to chat-level avatar (groups). */
fun ChatEntity.displayAvatarUrl(): String? =
    peerAvatarUrl?.takeIf { it.isNotBlank() } ?: avatarUrl?.takeIf { it.isNotBlank() }

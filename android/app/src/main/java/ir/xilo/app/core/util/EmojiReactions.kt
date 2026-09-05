package ir.xilo.app.core.util

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Shared emoji set for post reactions (REQ-POST-007) and chat message reactions.
 * Heart maps to the backend `like`/`heart` family; other glyphs are stored as-is.
 */
object EmojiReactions {
    val ALL = listOf("👍", "❤️", "😄", "😮", "😢", "😡", "👏", "🎉", "💡", "🔥")

    private val json = Json { ignoreUnknownKeys = true }

    fun apiKey(emoji: String): String =
        if (emoji == "❤️" || emoji == "❤") "like" else emoji

    fun displayEmoji(key: String): String = when (key) {
        "like", "heart", "❤" -> "❤️"
        else -> key
    }

    fun isLikeFamily(emoji: String): Boolean = apiKey(emoji) == "like"

    fun fromPostDto(reactions: Map<String, Int>, viewer: List<String>): String {
        val merged = linkedMapOf<String, ReactionCount>()
        reactions.forEach { (key, count) ->
            if (count <= 0) return@forEach
            val emoji = displayEmoji(key)
            val previous = merged[emoji]
            val mine = viewer.any { displayEmoji(it) == emoji }
            merged[emoji] = ReactionCount(
                reaction = emoji,
                count = (previous?.count ?: 0L) + count.toLong(),
                reacted = previous?.reacted == true || mine,
            )
        }
        viewer.forEach { key ->
            val emoji = displayEmoji(key)
            if (!merged.containsKey(emoji)) {
                merged[emoji] = ReactionCount(emoji, 1, true)
            }
        }
        return encode(merged.values.toList())
    }

    fun toggleSelf(raw: String?, emoji: String): String {
        val glyph = displayEmoji(emoji)
        val current = decode(raw).toMutableList()
        val index = current.indexOfFirst { displayEmoji(it.reaction) == glyph }
        if (index >= 0) {
            val item = current[index]
            if (item.reacted) {
                val nextCount = item.count - 1
                if (nextCount <= 0) current.removeAt(index)
                else current[index] = item.copy(count = nextCount, reacted = false)
            } else {
                current[index] = item.copy(count = item.count + 1, reacted = true)
            }
        } else {
            current.add(ReactionCount(glyph, 1, true))
        }
        return encode(current)
    }

    fun decode(raw: String?): List<ReactionCount> {
        if (raw.isNullOrBlank() || raw == "[]") return emptyList()
        return runCatching { json.decodeFromString<List<ReactionCount>>(raw) }.getOrDefault(emptyList())
    }

    fun encode(items: List<ReactionCount>): String =
        json.encodeToString(items.filter { it.count > 0 })

    fun fromMessageDto(items: List<ir.xilo.app.data.remote.dto.MessageReactionResponse>): String =
        encode(items.map { ReactionCount(it.reaction, it.count, it.reacted) })

    fun applyEvent(
        raw: String?,
        reaction: String,
        active: Boolean,
        count: Long,
        selfToggled: Boolean,
    ): String {
        val current = decode(raw).toMutableList()
        val index = current.indexOfFirst { it.reaction == reaction }
        val previousMine = current.getOrNull(index)?.reacted ?: false
        val mine = if (selfToggled) active else previousMine
        if (index >= 0) {
            if (count <= 0) {
                current.removeAt(index)
            } else {
                current[index] = ReactionCount(reaction, count, mine)
            }
        } else if (count > 0) {
            current.add(ReactionCount(reaction, count, mine && selfToggled))
        }
        return encode(current)
    }
}

@Serializable
data class ReactionCount(
    val reaction: String,
    val count: Long,
    val reacted: Boolean = false,
)

package ir.xilo.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiReactionsTest {
    @Test
    fun apiKey_mapsHeartToLike() {
        assertEquals("like", EmojiReactions.apiKey("❤️"))
        assertEquals("🔥", EmojiReactions.apiKey("🔥"))
    }

    @Test
    fun applyEvent_updatesSelfReactionAndDropsZeroCount() {
        val first = EmojiReactions.applyEvent(
            raw = null,
            reaction = "🔥",
            active = true,
            count = 1,
            selfToggled = true,
        )
        val decoded = EmojiReactions.decode(first)
        assertEquals(1, decoded.size)
        assertEquals("🔥", decoded[0].reaction)
        assertTrue(decoded[0].reacted)

        val cleared = EmojiReactions.applyEvent(
            raw = first,
            reaction = "🔥",
            active = false,
            count = 0,
            selfToggled = true,
        )
        assertTrue(EmojiReactions.decode(cleared).isEmpty())
    }

    @Test
    fun fromPostDto_mergesLikeFamilyAndViewer() {
        val json = EmojiReactions.fromPostDto(
            reactions = mapOf("like" to 3, "🔥" to 2),
            viewer = listOf("like", "🔥"),
        )
        val decoded = EmojiReactions.decode(json)
        assertEquals(2, decoded.size)
        val heart = decoded.first { it.reaction == "❤️" }
        assertEquals(3, heart.count)
        assertTrue(heart.reacted)
    }

    @Test
    fun toggleSelf_addsAndRemovesReaction() {
        val added = EmojiReactions.toggleSelf(null, "🎉")
        val once = EmojiReactions.decode(added)
        assertEquals(1, once.size)
        assertTrue(once[0].reacted)
        val removed = EmojiReactions.toggleSelf(added, "🎉")
        assertTrue(EmojiReactions.decode(removed).isEmpty())
    }
}

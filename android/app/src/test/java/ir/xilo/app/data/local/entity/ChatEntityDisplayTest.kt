package ir.xilo.app.data.local.entity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatEntityDisplayTest {

    @Test
    fun displayTitle_directPrefersPeerDisplayName() {
        val chat = chat(
            type = "direct",
            name = null,
            peerDisplayName = "Alice",
            peerUsername = "alice",
        )
        assertEquals("Alice", chat.displayTitle(fallback = "گفتگو"))
    }

    @Test
    fun displayTitle_directFallsBackToPeerUsernameThenName() {
        val byUsername = chat(
            type = "direct",
            name = "ignored-when-peer-user",
            peerDisplayName = null,
            peerUsername = "alice",
        )
        assertEquals("alice", byUsername.displayTitle(fallback = "گفتگو"))

        val byName = chat(
            type = "direct",
            name = "Legacy Name",
            peerDisplayName = null,
            peerUsername = null,
        )
        assertEquals("Legacy Name", byName.displayTitle(fallback = "گفتگو"))
    }

    @Test
    fun displayTitle_savedUsesSavedTitle() {
        val chat = chat(type = "saved", name = null)
        assertEquals(
            "پیام‌های ذخیره‌شده",
            chat.displayTitle(fallback = "گفتگو", savedTitle = "پیام‌های ذخیره‌شده"),
        )
    }

    @Test
    fun displayTitle_groupPrefersChatName() {
        val chat = chat(
            type = "group",
            name = "گروه",
            peerDisplayName = "Someone",
        )
        assertEquals("گروه", chat.displayTitle(fallback = "گفتگو"))
    }

    @Test
    fun displayAvatarUrl_prefersPeerThenChat() {
        val peer = chat(
            type = "direct",
            avatarUrl = "https://cdn.example/chat.png",
            peerAvatarUrl = "https://cdn.example/peer.png",
        )
        assertEquals("https://cdn.example/peer.png", peer.displayAvatarUrl())

        val chatOnly = chat(
            type = "group",
            avatarUrl = "https://cdn.example/group.png",
            peerAvatarUrl = null,
        )
        assertEquals("https://cdn.example/group.png", chatOnly.displayAvatarUrl())

        assertNull(chat(type = "direct").displayAvatarUrl())
    }

    private fun chat(
        type: String,
        name: String? = null,
        avatarUrl: String? = null,
        peerDisplayName: String? = null,
        peerUsername: String? = null,
        peerAvatarUrl: String? = null,
    ) = ChatEntity(
        id = "chat-1",
        type = type,
        name = name,
        avatarUrl = avatarUrl,
        lastMessageContent = null,
        lastMessageTime = null,
        peerDisplayName = peerDisplayName,
        peerUsername = peerUsername,
        peerAvatarUrl = peerAvatarUrl,
    )
}

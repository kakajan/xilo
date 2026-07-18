package ir.xilo.app.data.repository

import ir.xilo.app.data.remote.dto.ChatMemberResponse
import ir.xilo.app.data.remote.dto.ChatResponse
import ir.xilo.app.data.remote.dto.MessageReadResponse
import ir.xilo.app.data.remote.dto.MessageResponse
import ir.xilo.app.data.remote.dto.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatRepositoryMappingTest {

    @Test
    fun chatMapping_derivesPreviewAndTimeFromNestedBackendFields() {
        val lastMessage = backendMessage(
            type = MessageType.TEXT,
            content = "آخرین پیام"
        )
        val chat = ChatResponse(
            id = "chat-1",
            type = "group",
            name = "گروه",
            createdAt = "created",
            updatedAt = "updated",
            lastMessageAt = "last-message-at",
            lastMessage = lastMessage,
            unreadCount = 4,
            isMuted = true,
            isArchived = true,
            currentRole = "admin"
        )

        val entity = chat.toChatEntity { timestamp ->
            if (timestamp == "last-message-at") 1234L else error("Unexpected timestamp")
        }

        assertEquals("آخرین پیام", entity.lastMessageContent)
        assertEquals(1234L, entity.lastMessageTime)
        assertEquals(4, entity.unreadCount)
        assertTrue(entity.isMuted)
        assertTrue(entity.isArchived)
    }

    @Test
    fun chatMapping_fallsBackToNestedMessageTimestamp() {
        val chat = ChatResponse(
            id = "chat-1",
            type = "direct",
            createdAt = "created",
            updatedAt = "updated",
            lastMessage = backendMessage(
                type = MessageType.IMAGE,
                content = null
            )
        )

        val entity = chat.toChatEntity { timestamp ->
            if (timestamp == "message-created") 5678L else error("Unexpected timestamp")
        }

        assertEquals("[Media]", entity.lastMessageContent)
        assertEquals(5678L, entity.lastMessageTime)
    }

    @Test
    fun chatMapping_extractsPeerExcludingCurrentUser() {
        val chat = ChatResponse(
            id = "chat-1",
            type = "direct",
            name = null,
            createdAt = "created",
            updatedAt = "updated",
            members = listOf(
                member(userId = "me", username = "meuser", displayName = "Me"),
                member(
                    userId = "peer-1",
                    username = "alice",
                    displayName = "Alice",
                    avatarUrl = "https://cdn.example/alice.png"
                )
            )
        )

        val entity = chat.toChatEntity(currentUserId = "me") { 1L }

        assertEquals("peer-1", entity.peerUserId)
        assertEquals("alice", entity.peerUsername)
        assertEquals("Alice", entity.peerDisplayName)
        assertEquals("https://cdn.example/alice.png", entity.peerAvatarUrl)
    }

    @Test
    fun chatMapping_withoutCurrentUserId_picksFirstMemberWithUsername() {
        val chat = ChatResponse(
            id = "chat-1",
            type = "direct",
            createdAt = "created",
            updatedAt = "updated",
            members = listOf(
                member(userId = "u1", username = "", displayName = "Blank"),
                member(userId = "u2", username = "bob", displayName = "Bob")
            )
        )

        val entity = chat.toChatEntity(currentUserId = null) { 1L }

        assertEquals("u2", entity.peerUserId)
        assertEquals("bob", entity.peerUsername)
        assertEquals("Bob", entity.peerDisplayName)
    }

    @Test
    fun chatMapping_savedType_skipsPeerExtraction() {
        val chat = ChatResponse(
            id = "saved-1",
            type = "saved",
            createdAt = "created",
            updatedAt = "updated",
            members = listOf(
                member(userId = "me", username = "meuser", displayName = "Me")
            )
        )

        val entity = chat.toChatEntity(currentUserId = "me") { 1L }

        assertNull(entity.peerUserId)
        assertNull(entity.peerUsername)
    }

    @Test
    fun messageMapping_derivesReadStateAndKeepsMissingSenderDisplaySafe() {
        val message = backendMessage(
            type = MessageType.TEXT,
            content = "hello",
            readBy = listOf(
                MessageReadResponse(
                    userId = "reader-1",
                    readAt = "read-at"
                )
            )
        )

        val entity = message.toMessageEntity { 9012L }

        assertTrue(entity.isRead)
        assertNull(entity.senderName)
        assertNull(entity.senderAvatar)
        assertEquals(9012L, entity.createdAt)
    }

    private fun backendMessage(
        type: MessageType,
        content: String?,
        readBy: List<MessageReadResponse> = emptyList()
    ) = MessageResponse(
        id = "message-1",
        chatId = "chat-1",
        senderId = "sender-1",
        type = type,
        content = content,
        createdAt = "message-created",
        updatedAt = "message-updated",
        readBy = readBy
    )

    private fun member(
        userId: String,
        username: String,
        displayName: String,
        avatarUrl: String? = null
    ) = ChatMemberResponse(
        chatId = "chat-1",
        userId = userId,
        role = "member",
        username = username,
        displayName = displayName,
        avatarUrl = avatarUrl,
        joinedAt = "joined"
    )
}

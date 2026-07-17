package ir.xilo.app.data.remote.dto

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatDtosTest {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun chatCursorPage_decodesBackendEnvelopeAndUnknownFields() {
        val page = json.decodeFromString<CursorPage<ChatResponse>>(CHAT_PAGE_JSON)

        assertEquals("cursor-2", page.nextCursor)
        assertTrue(page.hasMore)
        assertEquals(1, page.data.size)

        val chat = page.data.single()
        assertEquals("chat-1", chat.id)
        assertEquals("admin", chat.currentRole)
        assertEquals(3L, chat.unreadCount)
        assertTrue(chat.isMuted)
        assertTrue(chat.isArchived)
        assertEquals("user-2", chat.members.single().userId)
        assertEquals("سلام", chat.lastMessage?.content)
        assertEquals(MessageType.TEXT, chat.lastMessage?.type)
    }

    @Test
    fun messageCursorPage_decodesBackendMetadataAndOptionalSenderFields() {
        val page = json.decodeFromString<CursorPage<MessageResponse>>(MESSAGE_PAGE_JSON)

        assertFalse(page.hasMore)
        assertNull(page.nextCursor)

        val message = page.data.single()
        assertEquals(MessageType.IMAGE, message.type)
        assertEquals("https://cdn.example.test/image.jpg", message.mediaUrl)
        assertEquals(1, message.reactions.size)
        assertEquals("👍", message.reactions.single().reaction)
        assertEquals("reader-1", message.readBy.single().userId)
        assertNull(message.senderName)
        assertNull(message.senderAvatar)
    }

    @Test
    fun emptyCursorPage_decodesWithoutNextCursor() {
        val page = json.decodeFromString<CursorPage<MessageResponse>>(
            """{"data":[],"has_more":false,"future_page_field":"ignored"}"""
        )

        assertTrue(page.data.isEmpty())
        assertNull(page.nextCursor)
        assertFalse(page.hasMore)
    }

    @Test
    fun sendMessageRequest_encodesExplicitTextAndMediaTypes() {
        val textJson = json.parseToJsonElement(
            json.encodeToString(
                SendMessageRequest(
                    type = MessageType.TEXT,
                    content = "hello"
                )
            )
        ).jsonObject
        val imageJson = json.parseToJsonElement(
            json.encodeToString(
                SendMessageRequest(
                    type = MessageType.IMAGE,
                    mediaUrl = "https://cdn.example.test/image.jpg"
                )
            )
        ).jsonObject

        assertEquals("text", textJson.getValue("type").jsonPrimitive.content)
        assertEquals("hello", textJson.getValue("content").jsonPrimitive.content)
        assertEquals("image", imageJson.getValue("type").jsonPrimitive.content)
        assertEquals(
            "https://cdn.example.test/image.jpg",
            imageJson.getValue("media_url").jsonPrimitive.content
        )
    }

    @Test
    fun createChatRequest_encodesBackendDirectAndGroupShape() {
        val directJson = json.parseToJsonElement(
            json.encodeToString(
                CreateChatRequest(
                    type = ChatType.DIRECT,
                    memberIds = listOf("member-1")
                )
            )
        ).jsonObject
        val groupJson = json.parseToJsonElement(
            json.encodeToString(
                CreateChatRequest(
                    type = ChatType.GROUP,
                    name = "Product",
                    avatarUrl = "https://cdn.example.test/group.jpg",
                    memberIds = listOf("member-1", "member-2")
                )
            )
        ).jsonObject

        assertEquals("direct", directJson.getValue("type").jsonPrimitive.content)
        assertEquals(
            "member-1",
            directJson.getValue("member_ids").jsonArray.single().jsonPrimitive.content
        )
        assertEquals("group", groupJson.getValue("type").jsonPrimitive.content)
        assertEquals("Product", groupJson.getValue("name").jsonPrimitive.content)
        assertEquals(
            "https://cdn.example.test/group.jpg",
            groupJson.getValue("avatar_url").jsonPrimitive.content
        )
        assertEquals(2, groupJson.getValue("member_ids").jsonArray.size)
    }

    private companion object {
        val CHAT_PAGE_JSON = """
            {
              "data": [
                {
                  "id": "chat-1",
                  "type": "group",
                  "name": "تیم محصول",
                  "avatar_url": null,
                  "created_at": "2026-07-16T10:00:00Z",
                  "updated_at": "2026-07-16T10:05:00Z",
                  "last_message_at": "2026-07-16T10:05:00Z",
                  "members": [
                    {
                      "chat_id": "chat-1",
                      "user_id": "user-2",
                      "role": "member",
                      "username": "sara",
                      "display_name": "سارا",
                      "avatar_url": null,
                      "joined_at": "2026-07-16T10:00:00Z",
                      "last_read_at": null,
                      "is_muted": false,
                      "is_archived": false,
                      "future_member_field": true
                    }
                  ],
                  "last_message": {
                    "id": "message-1",
                    "chat_id": "chat-1",
                    "sender_id": "user-2",
                    "type": "text",
                    "content": "سلام",
                    "media_id": null,
                    "media_url": null,
                    "reply_to_id": null,
                    "is_edited": false,
                    "is_deleted": false,
                    "created_at": "2026-07-16T10:05:00Z",
                    "updated_at": "2026-07-16T10:05:00Z",
                    "edited_at": null,
                    "deleted_at": null,
                    "reactions": [],
                    "read_by": [],
                    "future_message_field": "ignored"
                  },
                  "unread_count": 3,
                  "is_muted": true,
                  "is_archived": true,
                  "current_role": "admin",
                  "future_chat_field": 42
                }
              ],
              "next_cursor": "cursor-2",
              "has_more": true,
              "future_page_field": "ignored"
            }
        """.trimIndent()

        val MESSAGE_PAGE_JSON = """
            {
              "data": [
                {
                  "id": "message-2",
                  "chat_id": "chat-1",
                  "sender_id": "user-2",
                  "type": "image",
                  "content": null,
                  "media_id": "media-1",
                  "media_url": "https://cdn.example.test/image.jpg",
                  "reply_to_id": null,
                  "is_edited": false,
                  "is_deleted": false,
                  "created_at": "2026-07-16T10:06:00Z",
                  "updated_at": "2026-07-16T10:06:00Z",
                  "edited_at": null,
                  "deleted_at": null,
                  "reactions": [
                    {"reaction": "👍", "count": 2, "reacted": true}
                  ],
                  "read_by": [
                    {"user_id": "reader-1", "read_at": "2026-07-16T10:07:00Z"}
                  ],
                  "server_extension": {"version": 2}
                }
              ],
              "has_more": false
            }
        """.trimIndent()
    }
}

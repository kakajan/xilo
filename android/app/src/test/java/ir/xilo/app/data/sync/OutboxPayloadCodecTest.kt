package ir.xilo.app.data.sync

import ir.xilo.app.data.remote.dto.ChatType
import ir.xilo.app.data.remote.dto.CreateChatRequest
import ir.xilo.app.data.remote.dto.MessageType
import ir.xilo.app.data.remote.dto.SendMessageRequest
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class OutboxPayloadCodecTest {
    private val codec = OutboxPayloadCodec(
        Json {
            encodeDefaults = true
            namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
        }
    )

    @Test
    fun payloadIsCanonicalAndContainsNoCredentialFields() {
        val request = CreateChatRequest(
            type = ChatType.GROUP,
            name = "group",
            avatarUrl = null,
            memberIds = listOf("member-2", "member-1")
        )

        val first = codec.encode(request)
        val second = codec.encode(request)

        assertEquals(first, second)
        assertEquals(request, codec.decodeCreateChat(first))
        assertFalse(first.contains("access_token", ignoreCase = true))
        assertFalse(first.contains("refresh_token", ignoreCase = true))
        assertFalse(first.contains("authorization", ignoreCase = true))
        assertFalse(first.contains("password", ignoreCase = true))
    }

    @Test
    fun messagePayloadRoundTripsWithoutWorkManagerData() {
        val request = SendMessageRequest(
            type = MessageType.IMAGE,
            mediaUrl = "https://cdn.example.test/image.jpg",
            replyToId = "message-0"
        )

        assertEquals(request, codec.decodeSendMessage(codec.encode(request)))
    }
}

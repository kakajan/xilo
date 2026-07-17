package ir.xilo.app.data.remote.websocket

import ir.xilo.app.data.remote.dto.MessageType
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class RealtimeEnvelopeDecodeTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    @Test
    fun messageReceiveEnvelope_decodesSnakeCaseFieldsAndMessagePayload() {
        val envelope = json.decodeFromString(
            RealtimeEnvelope.serializer(),
            MESSAGE_RECEIVE_JSON,
        )

        assertEquals(REALTIME_PROTOCOL_VERSION, envelope.version)
        assertEquals(RealtimeEvents.MESSAGE_RECEIVE, envelope.event)
        assertEquals("evt-1111-2222-3333-444444444444", envelope.eventId)
        assertEquals("op-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee", envelope.operationKey)
        assertNotNull(envelope.data)

        val message = json.decodeFromJsonElement(
            ir.xilo.app.data.remote.dto.MessageResponse.serializer(),
            requireNotNull(envelope.data),
        )
        assertEquals("msg-1", message.id)
        assertEquals("chat-1", message.chatId)
        assertEquals("sender-1", message.senderId)
        assertEquals(MessageType.TEXT, message.type)
        assertEquals("سلام", message.content)
        assertFalse(message.isDeleted)
        assertTrue(message.readBy.isEmpty())
    }

    @Test
    fun ackEnvelope_decodesForEventAndResourceIdentity() {
        val envelope = json.decodeFromString(RealtimeEnvelope.serializer(), ACK_JSON)
        val ack = json.decodeFromJsonElement(
            RealtimeAckPayload.serializer(),
            requireNotNull(envelope.data),
        )

        assertEquals(RealtimeEvents.ACK, envelope.event)
        assertEquals("req-1", envelope.requestId)
        assertEquals("op-key-1", envelope.operationKey)
        assertEquals(RealtimeEvents.MESSAGE_SEND, ack.forEvent)
        assertEquals("op-key-1", ack.operationKey)
        assertEquals("message", ack.resourceType)
        assertEquals("msg-server-1", ack.resourceId)
        assertTrue(ack.accepted)
        assertFalse(ack.replayed)
    }

    @Test
    fun outboundJoinEnvelope_encodesVersionedSnakeCaseContract() {
        val encoded = json.encodeToString(
            RealtimeEnvelope.serializer(),
            RealtimeEnvelope(
                version = REALTIME_PROTOCOL_VERSION,
                event = RealtimeEvents.CHAT_JOIN,
                requestId = "req-join",
                data = kotlinx.serialization.json.buildJsonObject {
                    put("chat_id", kotlinx.serialization.json.JsonPrimitive("chat-9"))
                },
            ),
        )

        assertTrue(encoded.contains("\"version\":\"1\""))
        assertTrue(encoded.contains("\"event\":\"chat.join\""))
        assertTrue(encoded.contains("\"request_id\":\"req-join\""))
        assertTrue(encoded.contains("\"chat_id\":\"chat-9\""))
        assertFalse(encoded.contains("\"chatId\""))
    }

    companion object {
        private val MESSAGE_RECEIVE_JSON = """
            {
              "version": "1",
              "event": "message.receive",
              "event_id": "evt-1111-2222-3333-444444444444",
              "operation_key": "op-aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
              "occurred_at": "2026-07-16T12:00:00Z",
              "sequence": 1721131200000000,
              "data": {
                "id": "msg-1",
                "chat_id": "chat-1",
                "sender_id": "sender-1",
                "type": "text",
                "content": "سلام",
                "is_edited": false,
                "is_deleted": false,
                "created_at": "2026-07-16T12:00:00Z",
                "updated_at": "2026-07-16T12:00:00Z",
                "reactions": [],
                "read_by": []
              }
            }
        """.trimIndent()

        private val ACK_JSON = """
            {
              "version": "1",
              "event": "ack",
              "event_id": "evt-ack-1",
              "request_id": "req-1",
              "operation_key": "op-key-1",
              "data": {
                "for_event": "message.send",
                "operation_key": "op-key-1",
                "resource_type": "message",
                "resource_id": "msg-server-1",
                "replayed": false,
                "accepted": true
              }
            }
        """.trimIndent()
    }
}

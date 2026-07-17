package ir.xilo.app.data.sync

import ir.xilo.app.data.remote.dto.CreateChatRequest
import ir.xilo.app.data.remote.dto.SendMessageRequest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OutboxPayloadCodec @Inject constructor(
    private val json: Json
) {
    fun encode(request: CreateChatRequest): String =
        canonicalPayload(json.encodeToJsonElement(CreateChatRequest.serializer(), request))

    fun encode(request: SendMessageRequest): String =
        canonicalPayload(json.encodeToJsonElement(SendMessageRequest.serializer(), request))

    fun decodeCreateChat(payload: String): CreateChatRequest =
        json.decodeFromString(CreateChatRequest.serializer(), payload)

    fun decodeSendMessage(payload: String): SendMessageRequest =
        json.decodeFromString(SendMessageRequest.serializer(), payload)

    private fun canonicalPayload(element: JsonElement): String {
        rejectSecretFields(element.jsonObject)
        return json.encodeToString(JsonElement.serializer(), canonicalize(element))
    }

    private fun canonicalize(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> JsonObject(
            element.entries
                .sortedBy { it.key }
                .associate { (key, value) -> key to canonicalize(value) }
        )
        is JsonArray -> JsonArray(element.map(::canonicalize))
        else -> element
    }

    private fun rejectSecretFields(element: JsonElement) {
        when (element) {
            is JsonObject -> element.forEach { (key, value) ->
                require(key.lowercase() !in FORBIDDEN_FIELDS) {
                    "Secret-bearing field is not allowed in an outbox payload"
                }
                rejectSecretFields(value)
            }
            is JsonArray -> element.forEach(::rejectSecretFields)
            else -> Unit
        }
    }

    private companion object {
        val FORBIDDEN_FIELDS = setOf(
            "access_token",
            "accesstoken",
            "refresh_token",
            "refreshtoken",
            "authorization",
            "password",
            "cookie"
        )
    }
}

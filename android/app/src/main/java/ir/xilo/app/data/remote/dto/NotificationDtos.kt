package ir.xilo.app.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

@Serializable
data class NotificationResponse(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val data: JsonElement? = null,
    val isRead: Boolean = false,
    val createdAt: String,
)

@Serializable
data class NotificationListResponse(
    val data: List<NotificationResponse> = emptyList(),
)

@Serializable
data class UnreadCountResponse(
    val unread: Int = 0,
)

@Serializable
data class NotificationPreferencesResponse(
    val commentReplyWeb: Boolean = true,
    val commentReplyEmail: Boolean = false,
    val commentReplySms: Boolean = false,
    val commentReplyPush: Boolean = true,
    val commentMentionWeb: Boolean = true,
    val commentMentionEmail: Boolean = false,
    val commentMentionSms: Boolean = false,
    val postReactionWeb: Boolean = true,
    val newFollowerWeb: Boolean = true,
    val newFollowerPush: Boolean = true,
    val postPublishedWeb: Boolean = true,
    val postPublishedEmail: Boolean = false,
    val postPublishedSms: Boolean = false,
    val postPublishedPush: Boolean = true,
    val newMessageWeb: Boolean = true,
    val newMessagePush: Boolean = true,
    val systemAnnouncementSms: Boolean = false,
)

fun JsonElement?.toStringMap(json: Json = Json { ignoreUnknownKeys = true }): Map<String, String> {
    val obj = resolveJsonObject(this, json) ?: return emptyMap()
    return obj.entries.mapNotNull { (key, value) ->
        val text = when (value) {
            is JsonPrimitive -> value.contentOrNull ?: value.toString().trim('"')
            else -> value.toString()
        }
        key to text
    }.toMap()
}

private fun resolveJsonObject(element: JsonElement?, json: Json): JsonObject? {
    if (element == null) return null
    return when (element) {
        is JsonObject -> element
        is JsonPrimitive -> {
            val content = element.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
            runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()
        }
        else -> null
    }
}

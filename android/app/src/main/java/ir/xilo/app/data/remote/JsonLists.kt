package ir.xilo.app.data.remote

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.decodeFromJsonElement

/** Decodes a JSON list, treating missing or `null` payloads as empty. */
inline fun <reified T> Json.decodeListOrEmpty(element: JsonElement?): List<T> {
    if (element == null || element is JsonNull) return emptyList()
    return decodeFromJsonElement(element)
}

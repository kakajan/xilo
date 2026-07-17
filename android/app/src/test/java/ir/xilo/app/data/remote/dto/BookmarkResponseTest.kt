package ir.xilo.app.data.remote.dto

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    @Test
    fun decodeBookmarkToggle_acceptsBooleanValue() {
        val payload = """{"bookmarked":true}"""

        val map = json.decodeFromString(
            MapSerializer(String.serializer(), JsonElement.serializer()),
            payload
        )

        assertTrue(map["bookmarked"]!!.jsonPrimitive.boolean)
    }

    @Test(expected = Exception::class)
    fun decodeBookmarkToggle_asStringMap_failsOnBoolean() {
        val payload = """{"bookmarked":true}"""
        json.decodeFromString(
            MapSerializer(String.serializer(), String.serializer()),
            payload
        )
    }

    @Test
    fun decodeBookmarkToggle_false() {
        val payload = """{"bookmarked":false}"""
        val map = json.decodeFromString(
            MapSerializer(String.serializer(), JsonElement.serializer()),
            payload
        )
        assertEquals(JsonPrimitive(false), map["bookmarked"])
    }
}

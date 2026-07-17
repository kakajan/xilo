package ir.xilo.app.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FollowListResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    @Test
    fun decodeFollowListPage() {
        val payload = """
            {
              "data": [
                {
                  "id": "u1",
                  "username": "alice",
                  "display_name": "Alice",
                  "avatar_url": "",
                  "is_verified": true,
                  "is_following": false
                }
              ],
              "next_cursor": "u1",
              "has_more": true
            }
        """.trimIndent()

        val page = json.decodeFromString(CursorPage.serializer(FollowListUserResponse.serializer()), payload)
        assertEquals(1, page.data.size)
        assertEquals("alice", page.data.single().username)
        assertTrue(page.data.single().isVerified)
        assertFalse(page.data.single().isFollowing)
        assertEquals("u1", page.nextCursor)
        assertTrue(page.hasMore)
    }

    @Test
    fun decodeFollowToggle() {
        val payload = """{"following":true}"""
        val response = json.decodeFromString(FollowToggleResponse.serializer(), payload)
        assertTrue(response.following)
    }
}

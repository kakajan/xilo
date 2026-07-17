package ir.xilo.app.data.remote.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PublicProfileResponseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    @Test
    fun decodePublicProfile_mapsNestedStatsWithoutEmail() {
        val payload = """
            {
              "id":"81ab51a1-cdd1-48a9-9eb5-ebf92943d189",
              "username":"emu1784228295",
              "display_name":"",
              "avatar_url":"",
              "bio":"",
              "is_verified":false,
              "created_at":"2026-07-16T18:58:16.257028Z",
              "stats":{"posts":1,"followers":2,"following":3},
              "is_following":false
            }
        """.trimIndent()

        val profile = json.decodeFromString(PublicProfileResponse.serializer(), payload)
        assertEquals("emu1784228295", profile.username)
        assertFalse(profile.isFollowing)
        assertEquals(1, profile.stats.posts)

        val user = profile.toUserResponse()
        assertEquals("", user.email)
        assertEquals(1, user.postCount)
        assertEquals(2, user.followerCount)
        assertEquals(3, user.followingCount)
    }
}

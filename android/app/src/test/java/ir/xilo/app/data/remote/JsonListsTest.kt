package ir.xilo.app.data.remote

import ir.xilo.app.data.remote.dto.CommentResponse
import ir.xilo.app.data.remote.dto.PostResponse
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class JsonListsTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    @Test
    fun decodeListOrEmpty_nullData_isEmpty() {
        val map = json.decodeFromString<Map<String, JsonElement>>(
            """{"data":null,"next_cursor":"","has_more":false}""",
        )
        val comments = json.decodeListOrEmpty<CommentResponse>(map["data"])
        assertTrue(comments.isEmpty())
    }

    @Test
    fun decodeListOrEmpty_missingData_isEmpty() {
        val comments = json.decodeListOrEmpty<CommentResponse>(null)
        assertTrue(comments.isEmpty())
    }

    @Test
    fun decodeListOrEmpty_emptyArray_isEmpty() {
        val map = json.decodeFromString<Map<String, JsonElement>>("""{"data":[]}""")
        val comments = json.decodeListOrEmpty<CommentResponse>(map["data"])
        assertTrue(comments.isEmpty())
    }

    @Test
    fun decodeListOrEmpty_nestedComments_preservesIds() {
        val map = json.decodeFromString<Map<String, JsonElement>>(
            """
            {"data":[{"id":"c1","post_id":"p1","author_id":"u1",
              "content":"hi","created_at":"2026-01-01T00:00:00Z"}]}
            """.trimIndent(),
        )
        val comments = json.decodeListOrEmpty<CommentResponse>(map["data"])
        assertEquals(listOf("c1"), comments.map { it.id })
    }

    @Test
    fun decodePostWithoutReactions_matchesLiveEmptyCommentPost() {
        val payload = """
            {
              "id": "51a753f4-a9b1-46b7-a882-5bc2d8ae2f82",
              "author_id": "11111111-1111-1111-1111-111111111111",
              "title": "مرگ",
              "slug": "death",
              "excerpt": "",
              "content": "{}",
              "content_md": "text",
              "cover_image_url": null,
              "audio_url": null,
              "category": null,
              "tags": ["آیله"],
              "status": "published",
              "is_premium": false,
              "word_count": 1,
              "reading_time": 1,
              "language": "fa",
              "view_count": 3,
              "published_at": "2026-09-08T10:00:00Z",
              "created_at": "2026-09-08T10:00:00Z",
              "updated_at": "2026-09-08T10:00:00Z",
              "comment_count": 0,
              "repost_count": 0,
              "is_bookmarked": false,
              "is_reposted": false,
              "post_type": "article",
              "media_ids": [],
              "author": {
                "id": "11111111-1111-1111-1111-111111111111",
                "username": "usher",
                "email": "",
                "display_name": "Usher",
                "avatar_url": "",
                "bio": "",
                "role": "admin",
                "email_verified": true,
                "is_verified": true,
                "preferred_language": "fa",
                "preferred_calendar": "persian",
                "created_at": "2026-01-01T00:00:00Z",
                "updated_at": "2026-01-01T00:00:00Z",
                "username_pending": false
              }
            }
        """.trimIndent()

        val post = json.decodeFromString<PostResponse>(payload)
        assertEquals("death", post.slug)
        assertEquals("article", post.postType)
        assertTrue(post.reactions.isEmpty())
        assertEquals(0, post.commentCount)
    }
}

package ir.xilo.app.data.repository

import ir.xilo.app.data.remote.dto.CommentResponse
import ir.xilo.app.data.remote.dto.UserResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentFlattenTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    @Test
    fun flattenComments_depthFirst_preservesParentIds() {
        val nested = listOf(
            comment(
                id = "root",
                parentId = null,
                replies = listOf(
                    comment(
                        id = "child",
                        parentId = "root",
                        replies = listOf(
                            comment(id = "grandchild", parentId = "child"),
                        ),
                    ),
                    comment(id = "sibling", parentId = "root"),
                ),
            ),
            comment(id = "other-root", parentId = null),
        )

        val flat = CommentRepository.flattenComments(nested)

        assertEquals(listOf("root", "child", "grandchild", "sibling", "other-root"), flat.map { it.id })
        assertEquals("root", flat.first { it.id == "child" }.parentId)
        assertEquals("child", flat.first { it.id == "grandchild" }.parentId)
        assertNull(flat.first { it.id == "root" }.parentId)
        assertTrue(flat.all { it.replies.isEmpty() })
        assertTrue(flat.all { it.postId == "post-1" })
    }

    @Test
    fun decodeNestedListComments_thenFlatten() {
        val payload = """
            [
              {
                "id": "a",
                "post_id": "p1",
                "author_id": "u1",
                "content": "hello",
                "created_at": "2026-01-01T12:00:00Z",
                "author": { "id": "u1", "username": "alice", "display_name": "Alice" },
                "replies": [
                  {
                    "id": "a1",
                    "post_id": "p1",
                    "author_id": "u2",
                    "parent_id": "a",
                    "content": "hi",
                    "created_at": "2026-01-01T12:01:00Z",
                    "author": { "id": "u2", "username": "bob", "display_name": "Bob" },
                    "replies": []
                  }
                ]
              }
            ]
        """.trimIndent()

        val decoded = json.decodeFromString<List<CommentResponse>>(payload)
        val flat = CommentRepository.flattenComments(decoded)

        assertEquals(2, flat.size)
        assertEquals("a", flat[0].id)
        assertEquals("a1", flat[1].id)
        assertEquals("a", flat[1].parentId)
        assertEquals("bob", flat[1].author?.username)
    }

    private fun comment(
        id: String,
        parentId: String?,
        replies: List<CommentResponse> = emptyList(),
    ): CommentResponse = CommentResponse(
        id = id,
        postId = "post-1",
        authorId = "author-$id",
        author = UserResponse(id = "author-$id", username = "user-$id"),
        parentId = parentId,
        content = "body-$id",
        createdAt = "2026-01-01T00:00:00Z",
        replies = replies,
    )
}

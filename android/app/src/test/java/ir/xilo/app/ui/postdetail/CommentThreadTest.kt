package ir.xilo.app.ui.postdetail

import ir.xilo.app.data.local.entity.CommentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentThreadTest {

    @Test
    fun buildVisibleCommentThread_postRoot_showsTwoLevelsAndDrillDown() {
        val comments = listOf(
            comment("a", parentId = null, depth = 0),
            comment("a1", parentId = "a", depth = 1),
            comment("a1x", parentId = "a1", depth = 2),
            comment("a1y", parentId = "a1", depth = 2),
            comment("b", parentId = null, depth = 0),
        )

        val visible = buildVisibleCommentThread(comments, focusCommentId = null)

        assertEquals(listOf("a", "a1", "b"), visible.map { it.comment.id })
        assertTrue(visible[0].showLineBelow)
        assertFalse(visible[0].canDrillDown)
        assertTrue(visible[1].showLineAbove)
        assertFalse(visible[1].showLineBelow)
        assertTrue(visible[1].canDrillDown)
        assertEquals(2, visible[1].directChildCount)
        assertFalse(visible[2].showLineBelow)
        assertFalse(visible[2].canDrillDown)
    }

    @Test
    fun buildVisibleCommentThread_drillDown_resetsTwoLevelWindow() {
        val comments = listOf(
            comment("a", parentId = null, depth = 0),
            comment("a1", parentId = "a", depth = 1),
            comment("a1x", parentId = "a1", depth = 2),
            comment("a1x1", parentId = "a1x", depth = 3),
        )

        val visible = buildVisibleCommentThread(comments, focusCommentId = "a1")

        assertEquals(listOf("a1", "a1x"), visible.map { it.comment.id })
        assertEquals(0, visible[0].relativeDepth)
        assertEquals(1, visible[1].relativeDepth)
        assertTrue(visible[1].canDrillDown)
        assertEquals(1, visible[1].directChildCount)
    }

    @Test
    fun buildVisibleCommentThread_multipleSiblings_keepsLineBetweenThem() {
        val comments = listOf(
            comment("a", parentId = null, depth = 0),
            comment("a1", parentId = "a", depth = 1),
            comment("a2", parentId = "a", depth = 1),
        )

        val visible = buildVisibleCommentThread(comments)

        assertEquals(3, visible.size)
        assertTrue(visible[1].showLineBelow)
        assertFalse(visible[2].showLineBelow)
        assertTrue(visible[2].showLineAbove)
    }

    @Test
    fun pathToComment_buildsAncestorChain() {
        val comments = listOf(
            comment("a", parentId = null, depth = 0),
            comment("a1", parentId = "a", depth = 1),
            comment("a1x", parentId = "a1", depth = 2),
        )

        assertEquals(listOf("a", "a1", "a1x"), pathToComment(comments, "a1x"))
        assertEquals(listOf("a"), pathToComment(comments, "a"))
        assertEquals(emptyList<String>(), pathToComment(comments, "missing"))
    }

    @Test
    fun focusStackForTarget_seedsAncestorsForNestedDiscoverHit() {
        val comments = listOf(
            comment("a", parentId = null, depth = 0),
            comment("a1", parentId = "a", depth = 1),
            comment("a1x", parentId = "a1", depth = 2),
            comment("a1x1", parentId = "a1x", depth = 3),
        )

        assertEquals(emptyList<String>(), focusStackForTarget(comments, "a"))
        assertEquals(listOf("a"), focusStackForTarget(comments, "a1"))
        assertEquals(listOf("a", "a1"), focusStackForTarget(comments, "a1x"))
        assertEquals(listOf("a", "a1", "a1x"), focusStackForTarget(comments, "a1x1"))

        val visible = buildVisibleCommentThread(
            comments,
            focusCommentId = focusStackForTarget(comments, "a1x").last(),
        )
        assertEquals(listOf("a1", "a1x"), visible.map { it.comment.id })
    }

    private fun comment(
        id: String,
        parentId: String?,
        depth: Int,
    ) = CommentEntity(
        id = id,
        postId = "post",
        authorId = "u1",
        authorName = "User",
        authorUsername = "user",
        authorAvatar = null,
        parentId = parentId,
        rootId = parentId?.let { "a" } ?: id,
        depth = depth,
        content = "c-$id",
        createdAt = 0L,
    )
}

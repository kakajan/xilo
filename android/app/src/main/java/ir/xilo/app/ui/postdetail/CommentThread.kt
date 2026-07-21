package ir.xilo.app.ui.postdetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.CommentCard

private val ThreadGutterWidth = 40.dp

data class CommentThreadDisplay(
    val comment: CommentEntity,
    val relativeDepth: Int,
    val showLineAbove: Boolean,
    val showLineBelow: Boolean,
    val directChildCount: Int,
    val canDrillDown: Boolean,
)

/**
 * Builds a Twitter-style 2-level window relative to [focusCommentId].
 * - null focus: each top-level comment + its direct replies
 * - focused: that comment + its direct replies
 * Deeper replies are omitted; [CommentThreadDisplay.canDrillDown] marks level-1 nodes with children.
 */
internal fun buildVisibleCommentThread(
    comments: List<CommentEntity>,
    focusCommentId: String? = null,
): List<CommentThreadDisplay> {
    if (comments.isEmpty()) return emptyList()

    val childrenByParent = comments.groupBy { it.parentId }
    val byId = comments.associateBy { it.id }

    val roots: List<CommentEntity> = if (focusCommentId != null) {
        val focused = byId[focusCommentId] ?: return emptyList()
        listOf(focused)
    } else {
        comments.filter { it.parentId == null || it.parentId !in byId }
    }

    val result = mutableListOf<CommentThreadDisplay>()

    for (root in roots) {
        val children = childrenByParent[root.id].orEmpty()
        result += CommentThreadDisplay(
            comment = root,
            relativeDepth = 0,
            showLineAbove = false,
            showLineBelow = children.isNotEmpty(),
            directChildCount = children.size,
            canDrillDown = false,
        )
        children.forEachIndexed { index, child ->
            val grandChildren = childrenByParent[child.id].orEmpty()
            val isLast = index == children.lastIndex
            result += CommentThreadDisplay(
                comment = child,
                relativeDepth = 1,
                showLineAbove = true,
                showLineBelow = !isLast,
                directChildCount = grandChildren.size,
                canDrillDown = grandChildren.isNotEmpty(),
            )
        }
    }

    return result
}

/** Compatibility alias for post-root windowing. */
internal fun buildCommentThreadDisplayList(comments: List<CommentEntity>): List<CommentThreadDisplay> =
    buildVisibleCommentThread(comments, focusCommentId = null)

/**
 * Ancestor ids from a top-known root to [targetId] (inclusive), using flat [parentId] links.
 * Empty if [targetId] is missing. Used to seed [focusStack] from Discover / deep-links.
 */
internal fun pathToComment(
    comments: List<CommentEntity>,
    targetId: String,
): List<String> {
    if (targetId.isBlank() || comments.isEmpty()) return emptyList()
    val byId = comments.associateBy { it.id }
    if (targetId !in byId) return emptyList()
    val chain = ArrayDeque<String>()
    var current: String? = targetId
    val seen = mutableSetOf<String>()
    while (current != null && current !in seen) {
        seen += current
        chain.addFirst(current)
        current = byId[current]?.parentId?.takeIf { it in byId }
    }
    return chain.toList()
}

/** Focus ancestors so [targetId] appears in the 2-level window (path without the target). */
internal fun focusStackForTarget(
    comments: List<CommentEntity>,
    targetId: String,
): List<String> {
    val path = pathToComment(comments, targetId)
    if (path.size <= 1) return emptyList()
    return path.dropLast(1)
}

@Composable
fun CommentThreadItem(
    display: CommentThreadDisplay,
    onReplyClick: (commentId: String, authorUsername: String, authorAvatar: String?) -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onReportClick: () -> Unit,
    onBookmarkClick: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null,
    onAuthorClick: () -> Unit = {},
    onRepostClick: (() -> Unit)? = null,
    onQuoteClick: (() -> Unit)? = null,
    onPinClick: (() -> Unit)? = null,
    postAuthorUsername: String? = null,
    postSlug: String? = null,
    onDrillDownClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val comment = display.comment
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val replyCount = maxOf(comment.replyCount, display.directChildCount)

    Column(modifier = modifier.fillMaxWidth()) {
        CommentCard(
            comment = comment.copy(replyCount = replyCount),
            onClick = {},
            onReplyClick = {
                if (!comment.isDeleted) {
                    onReplyClick(comment.id, comment.authorUsername, comment.authorAvatar)
                }
            },
            onLikeClick = onLikeClick,
            onDislikeClick = onDislikeClick,
            onReportClick = onReportClick,
            onBookmarkClick = onBookmarkClick,
            onDeleteClick = onDeleteClick?.takeUnless { comment.isDeleted },
            onAuthorClick = onAuthorClick,
            onRepostClick = onRepostClick?.takeUnless { comment.isDeleted },
            onQuoteClick = onQuoteClick?.takeUnless { comment.isDeleted },
            onPinClick = onPinClick?.takeUnless { comment.isDeleted },
            postAuthorUsername = postAuthorUsername,
            postSlug = postSlug,
            contentMaxLines = Int.MAX_VALUE,
            startIndent = 0.dp,
            showThreadLineAbove = display.showLineAbove,
            showThreadLineBelow = display.showLineBelow,
            threadLineColor = lineColor,
        )

        if (display.canDrillDown) {
            Text(
                text = stringResource(R.string.comment_thread_view_replies, display.directChildCount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(
                        start = XiloSpacing.horizontal + ThreadGutterWidth + 12.dp,
                        end = XiloSpacing.horizontal,
                        bottom = 8.dp,
                    )
                    .clickable(role = Role.Button) { onDrillDownClick(comment.id) }
            )
        }
    }
}

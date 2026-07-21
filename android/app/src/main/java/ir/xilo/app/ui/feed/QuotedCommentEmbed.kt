package ir.xilo.app.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.usernameHandle

@Composable
fun QuotedCommentEmbed(
    post: PostEntity,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val content = post.quotedCommentContent?.takeIf { it.isNotBlank() } ?: return
    val name = post.quotedCommentAuthorName?.takeIf { it.isNotBlank() }
        ?: post.quotedCommentAuthorUsername.orEmpty()
    val shape = RoundedCornerShape(16.dp)
    val clickable = if (onClick != null) {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                shape,
            )
            .then(clickable)
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XiloAvatar(imageUrl = post.quotedCommentAuthorAvatar, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name.ifBlank { stringResource(R.string.feed_author_fallback) },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val username = post.quotedCommentAuthorUsername.orEmpty()
            if (username.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = usernameHandle(username),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )

        val postTitle = post.quotedCommentPostTitle.orEmpty()
        if (postTitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.discover_comment_on_post, postTitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun QuotedCommentPreview(
    comment: CommentEntity?,
    postTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    if (comment == null) return
    val name = comment.authorName?.takeIf { it.isNotBlank() } ?: comment.authorUsername
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XiloAvatar(imageUrl = comment.authorAvatar, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (comment.authorUsername.isNotBlank()) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = usernameHandle(comment.authorUsername),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = comment.content,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        val resolvedTitle = postTitle?.takeIf { it.isNotBlank() }
        if (resolvedTitle != null) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.discover_comment_on_post, resolvedTitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

fun PostEntity.hasQuotedComment(): Boolean =
    !quotedCommentId.isNullOrBlank() || !quotedCommentContent.isNullOrBlank()

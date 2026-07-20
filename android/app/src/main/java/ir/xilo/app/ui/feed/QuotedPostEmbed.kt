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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.usernameHandle

@Composable
fun QuotedPostEmbed(
    post: PostEntity,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val slug = post.quotedSlug?.takeIf { it.isNotBlank() } ?: return
    val name = post.quotedAuthorName?.takeIf { it.isNotBlank() }
        ?: post.quotedAuthorUsername.orEmpty()
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
            XiloAvatar(imageUrl = post.quotedAuthorAvatar, size = 20.dp)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name.ifBlank { "@$slug" },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            val username = post.quotedAuthorUsername.orEmpty()
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

        val title = post.quotedTitle.orEmpty()
        if (title.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val excerpt = post.quotedExcerpt.orEmpty()
        if (excerpt.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = excerpt,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val cover = post.quotedCoverImageUrl
        if (!cover.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            AsyncImage(
                model = cover,
                contentDescription = stringResource(R.string.cd_post_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    }
}

fun PostEntity.hasQuotedPost(): Boolean =
    !quotedSlug.isNullOrBlank() || !quotedPostId.isNullOrBlank()

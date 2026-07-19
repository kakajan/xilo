package ir.xilo.app.ui.feed

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.theme.ColorError
import ir.xilo.app.theme.ColorSuccess
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.ContentAwareText
import ir.xilo.app.ui.components.HashtagAwareText
import ir.xilo.app.ui.components.VerifiedBadge
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.core.util.DateFormatter

@Composable
fun PostCard(
    post: PostEntity,
    onPostClick: (String) -> Unit,
    onLikeClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onCommentClick: () -> Unit = { onPostClick(post.slug) },
    onRepostClick: () -> Unit = {},
    onShareClick: (() -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    onHashtagClick: ((String) -> Unit)? = null,
    isOwner: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    onArchiveClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val openAuthor = onAuthorClick?.takeIf { post.authorUsername.isNotBlank() }
    val context = LocalContext.current
    val sharePost = onShareClick ?: {
        val text = buildString {
            if (post.title.isNotBlank()) append(post.title).append('\n')
            append(post.excerpt?.takeIf { it.isNotBlank() } ?: post.content.take(160))
            append("\n/p/").append(post.slug)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text.trim())
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Post body navigates; owner menu + action row stay outside that clickable.
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = XiloSpacing.horizontal,
                    end = XiloSpacing.horizontal,
                    top = XiloSpacing.cardVertical
                )
        ) {
            XiloAvatar(
                imageUrl = post.authorAvatar,
                size = 40.dp,
                onClick = openAuthor,
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        role = Role.Button,
                        onClick = { onPostClick(post.slug) }
                    )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = post.authorName ?: post.authorUsername,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = if (openAuthor != null) {
                            Modifier.clickable(role = Role.Button, onClick = openAuthor)
                        } else {
                            Modifier
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 16.dp)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "@${post.authorUsername}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = if (openAuthor != null) {
                            Modifier.clickable(role = Role.Button, onClick = openAuthor)
                        } else {
                            Modifier
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "· ${getRelativeTimeSpan(context, post.createdAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (post.title.isNotBlank()) {
                    ContentAwareText(
                        text = post.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }

                val previewText = post.excerpt?.takeIf { it.isNotBlank() }
                    ?: post.content.takeIf { !it.startsWith("{") && it.isNotBlank() }
                    ?: ""
                if (previewText.isNotBlank()) {
                    if (onHashtagClick != null) {
                        HashtagAwareText(
                            text = previewText,
                            onHashtagClick = onHashtagClick,
                            onTextClick = { onPostClick(post.slug) },
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 3,
                        )
                    } else {
                        ContentAwareText(
                            text = previewText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 3,
                        )
                    }
                }

                if (!post.coverImageUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    AsyncImage(
                        model = post.coverImageUrl,
                        contentDescription = stringResource(R.string.cd_post_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
                    )
                }
            }

            if (isOwner && onEditClick != null && onArchiveClick != null && onDeleteClick != null) {
                PostOwnerMenu(
                    onEdit = onEditClick,
                    onArchive = onArchiveClick,
                    onDelete = onDeleteClick,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = XiloSpacing.horizontal + 52.dp,
                    end = XiloSpacing.horizontal,
                    top = 8.dp,
                    bottom = XiloSpacing.cardVertical
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            PostAction(
                icon = XiloIcons.Message,
                count = post.commentCount.toString(),
                contentDescription = stringResource(R.string.cd_comments),
                onClick = onCommentClick
            )
            PostAction(
                icon = XiloIcons.Repeat,
                count = post.repostCount.toString(),
                contentDescription = stringResource(R.string.cd_repost),
                tint = if (post.isReposted) ColorSuccess else MaterialTheme.colorScheme.secondary,
                countColor = if (post.isReposted) ColorSuccess else MaterialTheme.colorScheme.secondary,
                onClick = onRepostClick
            )

            val likeScale by animateFloatAsState(
                targetValue = if (post.isLiked) 1.15f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                ),
                label = "likeScale"
            )
            PostAction(
                icon = if (post.isLiked) XiloIcons.HeartFilled else XiloIcons.Heart,
                count = post.likeCount.toString(),
                contentDescription = stringResource(R.string.cd_like),
                tint = if (post.isLiked) ColorError else MaterialTheme.colorScheme.secondary,
                countColor = if (post.isLiked) ColorError else MaterialTheme.colorScheme.secondary,
                onClick = onLikeClick,
                modifier = Modifier.scale(likeScale)
            )

            PostAction(
                icon = if (post.isBookmarked) XiloIcons.BookmarkFilled else XiloIcons.Bookmark,
                count = null,
                contentDescription = stringResource(R.string.cd_bookmark),
                tint = if (post.isBookmarked) XiloBlue else MaterialTheme.colorScheme.secondary,
                onClick = onBookmarkClick
            )

            PostAction(
                icon = XiloIcons.Chart,
                count = formatViewCount(post.viewCount),
                contentDescription = stringResource(R.string.cd_views)
            )

            PostAction(
                icon = XiloIcons.Share,
                count = null,
                contentDescription = stringResource(R.string.cd_share),
                onClick = sharePost
            )
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outline,
            thickness = 1.dp
        )
    }
}

@Composable
private fun PostAction(
    @androidx.annotation.DrawableRes icon: Int,
    count: String?,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.secondary,
    countColor: Color = MaterialTheme.colorScheme.secondary,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val clickableModifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(clickableModifier)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        XiloIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(XiloSpacing.iconInline)
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = countColor
            )
        }
    }
}

fun getRelativeTimeSpan(context: android.content.Context, timestamp: Long): String =
    DateFormatter.getRelativeTimeSpan(context, timestamp)

private fun formatViewCount(n: Long): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}

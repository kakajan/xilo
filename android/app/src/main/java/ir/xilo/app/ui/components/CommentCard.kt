package ir.xilo.app.ui.components

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.theme.ColorError
import ir.xilo.app.theme.ColorSuccess
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.core.util.DateFormatter
import ir.xilo.app.ui.feed.RepostMenuButton

private val CommentAvatarSize = 40.dp
private val CommentThreadLineWidth = 2.dp

@Composable
fun CommentCard(
    comment: CommentEntity,
    onClick: () -> Unit,
    onReplyClick: () -> Unit,
    onLikeClick: () -> Unit,
    onDislikeClick: () -> Unit,
    onReportClick: () -> Unit,
    onBookmarkClick: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    /** Null hides Author+ repost menu; readers still see count when > 0. */
    onRepostClick: (() -> Unit)? = null,
    onQuoteClick: (() -> Unit)? = null,
    onShareClick: (() -> Unit)? = null,
    onPinClick: (() -> Unit)? = null,
    postAuthorUsername: String? = null,
    postSlug: String? = null,
    modifier: Modifier = Modifier,
    contentMaxLines: Int = 3,
    startIndent: Dp = 0.dp,
    showThreadLineAbove: Boolean = false,
    showThreadLineBelow: Boolean = false,
    threadLineColor: Color = Color.Unspecified,
    postTitle: String? = null,
    /** Discover: username of the comment this hit replies to. */
    replyToUsername: String? = null,
    /** Discover: short preview of the parent comment body. */
    replyToPreview: String? = null,
) {
    val contentStart = XiloSpacing.horizontal + startIndent
    val openAuthor = onAuthorClick?.takeIf { comment.authorUsername.isNotBlank() }
    val resolvedLineColor = if (threadLineColor != Color.Unspecified) {
        threadLineColor
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    }
    val useThreadGutter = showThreadLineAbove || showThreadLineBelow
    val context = LocalContext.current
    val shareComment = onShareClick ?: run {
        val author = postAuthorUsername?.takeIf { it.isNotBlank() }
        val slug = postSlug?.takeIf { it.isNotBlank() }
        if (author != null && slug != null) {
            {
                val link = "/$author/$slug?reply=${comment.id}"
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, link)
                }
                context.startActivity(Intent.createChooser(intent, null))
            }
        } else {
            null
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(
                    start = contentStart,
                    end = XiloSpacing.horizontal,
                    top = XiloSpacing.cardVertical,
                ),
            verticalAlignment = Alignment.Top,
        ) {
            if (useThreadGutter) {
                Column(
                    modifier = Modifier
                        .width(CommentAvatarSize)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (showThreadLineAbove) {
                        Box(
                            modifier = Modifier
                                .width(CommentThreadLineWidth)
                                .height(8.dp)
                                .background(resolvedLineColor, RoundedCornerShape(1.dp)),
                        )
                    }
                    XiloAvatar(
                        imageUrl = comment.authorAvatar,
                        size = CommentAvatarSize,
                        onClick = openAuthor,
                    )
                    if (showThreadLineBelow) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .width(CommentThreadLineWidth)
                                .padding(top = 4.dp)
                                .background(resolvedLineColor, RoundedCornerShape(1.dp)),
                        )
                    }
                }
            } else {
                XiloAvatar(
                    imageUrl = comment.authorAvatar,
                    size = CommentAvatarSize,
                    onClick = openAuthor,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val authorLabel = comment.authorName?.takeIf { it.isNotBlank() }
                        ?: comment.authorUsername
                    Text(
                        text = authorLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .widthIn(max = 140.dp)
                            .then(
                                if (openAuthor != null) {
                                    Modifier.clickable(role = Role.Button, onClick = openAuthor)
                                } else {
                                    Modifier
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = usernameHandle(comment.authorUsername),
                        style = MaterialTheme.typography.bodyMedium.forUsernameHandle(),
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = if (openAuthor != null) {
                            Modifier.clickable(role = Role.Button, onClick = openAuthor)
                        } else {
                            Modifier
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = DateFormatter.formatDateTime(comment.createdAt),
                        style = MaterialTheme.typography.bodyMedium.forRelativeTime(),
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1
                    )
                    if (comment.isPinned) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.comment_pinned_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = XiloBlue,
                            maxLines = 1,
                        )
                    }
                }

                if (comment.repostCount > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.comment_amplified_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = ColorSuccess,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                if (comment.isDeleted) {
                    Text(
                        text = stringResource(R.string.comment_deleted),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    ContentAwareText(
                        text = comment.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = contentMaxLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val resolvedReplyTo = replyToUsername?.takeIf { it.isNotBlank() }
                if (resolvedReplyTo != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val preview = replyToPreview?.takeIf { it.isNotBlank() }
                    Text(
                        text = if (preview != null) {
                            stringResource(
                                R.string.discover_comment_reply_to_with_preview,
                                resolvedReplyTo,
                                preview,
                            )
                        } else {
                            stringResource(R.string.discover_comment_reply_to, resolvedReplyTo)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                val resolvedPostTitle = postTitle?.takeIf { it.isNotBlank() }
                if (resolvedPostTitle != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.discover_comment_on_post, resolvedPostTitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = XiloBlue,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = XiloSpacing.cardVertical),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CommentCardAction(
                            icon = XiloIcons.Message,
                            count = comment.replyCount.toString(),
                            contentDescription = stringResource(R.string.discover_action_reply),
                            onClick = onReplyClick
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        if (onRepostClick != null && onQuoteClick != null) {
                            RepostMenuButton(
                                repostCount = comment.repostCount,
                                isReposted = comment.isReposted,
                                onRepostClick = onRepostClick,
                                onQuoteClick = onQuoteClick,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        } else if (comment.repostCount > 0) {
                            CommentCardAction(
                                icon = XiloIcons.Repeat,
                                count = comment.repostCount.toString(),
                                contentDescription = stringResource(R.string.cd_repost),
                                tint = MaterialTheme.colorScheme.secondary,
                                countColor = MaterialTheme.colorScheme.secondary,
                                onClick = null,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                        CommentCardAction(
                            icon = if (comment.isLiked) XiloIcons.ThumbUpFilled else XiloIcons.ThumbUp,
                            count = comment.likeCount.toString(),
                            contentDescription = stringResource(R.string.discover_action_like),
                            tint = if (comment.isLiked) ColorSuccess else MaterialTheme.colorScheme.secondary,
                            countColor = if (comment.isLiked) ColorSuccess else MaterialTheme.colorScheme.secondary,
                            onClick = onLikeClick
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        CommentCardAction(
                            icon = if (comment.isDisliked) XiloIcons.ThumbDownFilled else XiloIcons.ThumbDown,
                            count = comment.dislikeCount.toString(),
                            contentDescription = stringResource(R.string.discover_action_dislike),
                            tint = if (comment.isDisliked) ColorError else MaterialTheme.colorScheme.secondary,
                            countColor = if (comment.isDisliked) ColorError else MaterialTheme.colorScheme.secondary,
                            onClick = onDislikeClick
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onPinClick != null && !comment.isDeleted) {
                            CommentCardAction(
                                icon = XiloIcons.Pin,
                                count = null,
                                contentDescription = stringResource(
                                    if (comment.isPinned) {
                                        R.string.comment_action_unpin
                                    } else {
                                        R.string.comment_action_pin
                                    }
                                ),
                                tint = if (comment.isPinned) XiloBlue else MaterialTheme.colorScheme.secondary,
                                onClick = onPinClick,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (shareComment != null && !comment.isDeleted) {
                            CommentCardAction(
                                icon = XiloIcons.Share,
                                count = null,
                                contentDescription = stringResource(R.string.cd_share),
                                onClick = shareComment,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (!comment.isDeleted) {
                            CommentCardAction(
                                icon = if (comment.isBookmarked) XiloIcons.BookmarkFilled else XiloIcons.Bookmark,
                                count = null,
                                contentDescription = stringResource(
                                    if (comment.isBookmarked) {
                                        R.string.comment_action_unbookmark
                                    } else {
                                        R.string.comment_action_bookmark
                                    }
                                ),
                                tint = if (comment.isBookmarked) XiloBlue else MaterialTheme.colorScheme.secondary,
                                onClick = onBookmarkClick
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (onDeleteClick != null) {
                            CommentCardAction(
                                icon = XiloIcons.Trash,
                                count = null,
                                contentDescription = stringResource(R.string.comment_action_delete),
                                tint = ColorError,
                                onClick = onDeleteClick,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        if (!comment.isDeleted) {
                            CommentCardAction(
                                icon = XiloIcons.Report,
                                count = null,
                                contentDescription = stringResource(R.string.discover_action_report),
                                onClick = onReportClick
                            )
                        }
                    }
                }
            }
        }

        if (!showThreadLineBelow) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline,
                thickness = 1.dp
            )
        }
    }
}

@Composable
private fun CommentCardAction(
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

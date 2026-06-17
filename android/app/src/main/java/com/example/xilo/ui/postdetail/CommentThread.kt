package com.example.xilo.ui.postdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xilo.data.local.entity.CommentEntity
import com.example.xilo.theme.LightBorder
import com.example.xilo.theme.XiloBlue
import com.example.xilo.theme.XiloSpacing
import com.example.xilo.theme.XiloTheme
import com.example.xilo.ui.components.VerifiedBadge
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.feed.getRelativeTimeSpan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ThreadGutterWidth = 28.dp
private val PostAvatarColumnWidth = 48.dp
private val ThreadLineWidth = 1.5.dp
private val CommentAvatarSize = 36.dp
private val CommentCardRadius = 16.dp

data class CommentThreadDisplay(
    val comment: CommentEntity,
    val ancestorContinuations: List<Boolean>,
    val isLastSibling: Boolean,
    val hasChildReplies: Boolean,
    val connectFromPost: Boolean,
)

internal fun buildCommentThreadDisplayList(comments: List<CommentEntity>): List<CommentThreadDisplay> {
    if (comments.isEmpty()) return emptyList()

    val childrenByParent = comments.groupBy { it.parentId }

    fun isLastSibling(index: Int): Boolean {
        val parentId = comments[index].parentId
        for (i in index + 1 until comments.size) {
            if (comments[i].parentId == parentId) return false
        }
        return true
    }

    fun ancestorContinuations(index: Int): List<Boolean> {
        val result = mutableListOf<Boolean>()
        var parentId = comments[index].parentId
        while (parentId != null) {
            val parentIndex = comments.indexOfFirst { it.id == parentId }
            if (parentIndex == -1) break
            result.add(0, !isLastSibling(parentIndex))
            parentId = comments[parentIndex].parentId
        }
        return result
    }

    return comments.mapIndexed { index, comment ->
        CommentThreadDisplay(
            comment = comment,
            ancestorContinuations = ancestorContinuations(index),
            isLastSibling = isLastSibling(index),
            hasChildReplies = childrenByParent[comment.id]?.isNotEmpty() == true,
            connectFromPost = index == 0 && comment.depth == 0,
        )
    }
}

@Composable
fun CommentThreadItem(
    display: CommentThreadDisplay,
    isPostAuthor: Boolean,
    onReplyClick: (String, String) -> Unit,
    onLikeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val comment = display.comment
    val depth = comment.depth.coerceAtLeast(0)
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    val bubbleColors = XiloTheme.bubbleColors
    val cardBackground = if (isPostAuthor) {
        bubbleColors.ownBubble
    } else if (depth > 0) {
        MaterialTheme.colorScheme.surface
    } else {
        bubbleColors.othersBubble
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .padding(
                start = XiloSpacing.horizontal,
                end = XiloSpacing.horizontal,
                top = if (display.connectFromPost) 4.dp else 8.dp,
                bottom = 4.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        CommentThreadGutter(
            depth = depth,
            ancestorContinuations = display.ancestorContinuations,
            connectFromPost = display.connectFromPost,
            hasChildReplies = display.hasChildReplies,
            isLastSibling = display.isLastSibling,
            lineColor = lineColor,
        )

        Column(modifier = Modifier.weight(1f)) {
            Surface(
                shape = RoundedCornerShape(CommentCardRadius),
                color = cardBackground,
                shadowElevation = 1.dp,
                tonalElevation = 0.5.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XiloAvatar(
                            imageUrl = comment.authorAvatar,
                            size = CommentAvatarSize,
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = comment.authorName?.takeIf { it.isNotBlank() }
                                        ?: comment.authorUsername,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPostAuthor) XiloBlue else MaterialTheme.colorScheme.onSurface,
                                )
                                if (isPostAuthor) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    VerifiedBadge(size = 14.dp)
                                }
                            }
                            Text(
                                text = "@${comment.authorUsername} · ${getRelativeTimeSpan(comment.createdAt)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = comment.content,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (comment.isLiked) {
                                XiloBlue.copy(alpha = 0.12f)
                            } else {
                                MaterialTheme.colorScheme.background
                            },
                            border = androidx.compose.foundation.BorderStroke(
                                width = 0.5.dp,
                                color = LightBorder,
                            ),
                            modifier = Modifier
                                .clickable { onLikeClick() }
                                .height(24.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("❤️", fontSize = 11.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = comment.likeCount.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (comment.isLiked) XiloBlue else MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }

                        val timeFormatted = remember(comment.createdAt) {
                            SimpleDateFormat("h:mm a", Locale("fa")).format(Date(comment.createdAt))
                        }
                        Text(
                            text = timeFormatted,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                    }
                }
            }

            Text(
                text = "پاسخ دادن",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = XiloBlue,
                modifier = Modifier
                    .clickable { onReplyClick(comment.id, comment.authorUsername) }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun CommentThreadGutter(
    depth: Int,
    ancestorContinuations: List<Boolean>,
    connectFromPost: Boolean,
    hasChildReplies: Boolean,
    isLastSibling: Boolean,
    lineColor: Color,
) {
    val avatarCenterY = CommentAvatarSize / 2
    val strokeWidth = ThreadLineWidth

    if (connectFromPost) {
        ThreadGutterColumn(
            lineColor = lineColor,
            strokeWidth = strokeWidth,
            avatarCenterY = avatarCenterY,
            drawElbow = true,
            continueBelow = hasChildReplies || !isLastSibling,
            columnWidth = PostAvatarColumnWidth,
        )
    }

    ancestorContinuations.forEach { continues ->
        ThreadGutterColumn(
            lineColor = lineColor,
            strokeWidth = strokeWidth,
            avatarCenterY = avatarCenterY,
            drawElbow = false,
            continueBelow = continues,
        )
    }

    if (depth > 0) {
        ThreadGutterColumn(
            lineColor = lineColor,
            strokeWidth = strokeWidth,
            avatarCenterY = avatarCenterY,
            drawElbow = true,
            continueBelow = hasChildReplies,
        )
    }
}

@Composable
private fun ThreadGutterColumn(
    lineColor: Color,
    strokeWidth: androidx.compose.ui.unit.Dp,
    avatarCenterY: androidx.compose.ui.unit.Dp,
    drawElbow: Boolean,
    continueBelow: Boolean,
    columnWidth: androidx.compose.ui.unit.Dp = ThreadGutterWidth,
) {
    Box(
        modifier = Modifier
            .width(columnWidth)
            .fillMaxHeight(),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val x = size.width / 2f
            val stroke = strokeWidth.toPx()
            val avatarY = avatarCenterY.toPx()

            if (drawElbow) {
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, avatarY),
                    strokeWidth = stroke,
                )
                drawLine(
                    color = lineColor,
                    start = Offset(x, avatarY),
                    end = Offset(size.width, avatarY),
                    strokeWidth = stroke,
                )
                if (continueBelow) {
                    drawLine(
                        color = lineColor,
                        start = Offset(x, avatarY),
                        end = Offset(x, size.height),
                        strokeWidth = stroke,
                    )
                }
            } else if (continueBelow) {
                drawLine(
                    color = lineColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = stroke,
                )
            }
        }
    }
}

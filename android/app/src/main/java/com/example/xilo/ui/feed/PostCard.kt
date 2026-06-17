package com.example.xilo.ui.feed

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.theme.ColorError
import com.example.xilo.theme.XiloBlue
import com.example.xilo.theme.XiloSpacing
import com.example.xilo.ui.components.VerifiedBadge
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.XiloIcon
import com.example.xilo.ui.components.XiloIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PostCard(
    post: PostEntity,
    onPostClick: (String) -> Unit,
    onLikeClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        shape = RoundedCornerShape(0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clickable { onPostClick(post.slug) }
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = XiloSpacing.horizontal,
                vertical = XiloSpacing.cardVertical
            )
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                XiloAvatar(imageUrl = post.authorAvatar, size = 40.dp)

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = post.authorName ?: post.authorUsername,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        VerifiedBadge(size = 16.dp)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "@${post.authorUsername}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "· ${getRelativeTimeSpan(post.createdAt)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    if (post.title.isNotBlank()) {
                        Text(
                            text = post.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }

                    val previewText = post.excerpt?.takeIf { it.isNotBlank() }
                        ?: post.content.takeIf { !it.startsWith("{") && it.isNotBlank() }
                        ?: ""
                    if (previewText.isNotBlank()) {
                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 3
                        )
                    }

                    if (!post.coverImageUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AsyncImage(
                            model = post.coverImageUrl,
                            contentDescription = "تصویر پست",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PostAction(
                            icon = XiloIcons.Message,
                            count = post.commentCount.toString(),
                            contentDescription = "نظرات"
                        )
                        PostAction(
                            icon = XiloIcons.Repeat,
                            count = "12",
                            contentDescription = "بازنشر"
                        )

                        val likeScale by animateFloatAsState(
                            targetValue = if (post.isLiked) 1.25f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "likeScale"
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { onLikeClick() }
                                .scale(likeScale)
                        ) {
                            XiloIcon(
                                icon = if (post.isLiked) XiloIcons.HeartFilled else XiloIcons.Heart,
                                contentDescription = "پسندیدن",
                                tint = if (post.isLiked) ColorError else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(XiloSpacing.iconInline)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = post.likeCount.toString(),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (post.isLiked) ColorError else MaterialTheme.colorScheme.secondary
                            )
                        }

                        IconButton(
                            onClick = onBookmarkClick,
                            modifier = Modifier.size(XiloSpacing.iconAction)
                        ) {
                            XiloIcon(
                                icon = if (post.isBookmarked) XiloIcons.BookmarkFilled else XiloIcons.Bookmark,
                                contentDescription = "ذخیره",
                                tint = if (post.isBookmarked) XiloBlue else MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(XiloSpacing.iconInline)
                            )
                        }

                        PostAction(
                            icon = XiloIcons.Chart,
                            count = formatViewCount(post.likeCount * 18),
                            contentDescription = "بازدید"
                        )

                        XiloIcon(
                            icon = XiloIcons.Share,
                            contentDescription = "اشتراک‌گذاری",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(XiloSpacing.iconInline)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun PostAction(
    @androidx.annotation.DrawableRes icon: Int,
    count: String,
    contentDescription: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        XiloIcon(
            icon = icon,
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(XiloSpacing.iconInline)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

fun getRelativeTimeSpan(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24

    return when {
        seconds < 60 -> "الان"
        minutes < 60 -> "${minutes}د"
        hours < 24 -> "${hours}س"
        days < 7 -> "${days}روز"
        else -> SimpleDateFormat("MMM dd", Locale("fa")).format(Date(timestamp))
    }
}

private fun formatViewCount(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}

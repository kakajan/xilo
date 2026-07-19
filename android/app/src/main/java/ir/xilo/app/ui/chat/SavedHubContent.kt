package ir.xilo.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.xilo.app.R
import ir.xilo.app.core.util.DateFormatter
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.data.remote.dto.BookmarkedCommentResponse
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.ChromeVisibilityState
import ir.xilo.app.ui.components.ContentAwareText
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.trackChromeVisibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedHubSegmentRow(
    selected: SavedHubSegment,
    onSelect: (SavedHubSegment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        SavedHubSegment.Messages to stringResource(R.string.saved_hub_segment_messages),
        SavedHubSegment.Posts to stringResource(R.string.saved_hub_segment_posts),
        SavedHubSegment.Comments to stringResource(R.string.saved_hub_segment_comments),
    )
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, (segment, label) ->
            SegmentedButton(
                selected = selected == segment,
                onClick = { onSelect(segment) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = XiloBlue,
                    activeContentColor = Color.White,
                )
            ) {
                Text(label, fontSize = 13.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun SavedHubBody(
    segment: SavedHubSegment,
    loading: Boolean,
    messages: List<MessageEntity>,
    posts: List<PostEntity>,
    comments: List<BookmarkedCommentResponse>,
    listState: LazyListState,
    onPostClick: (slug: String) -> Unit,
    onCommentClick: (slug: String, commentId: String) -> Unit,
    onAuthorClick: (username: String) -> Unit = {},
    modifier: Modifier = Modifier,
    chromeState: ChromeVisibilityState? = null,
    contentBottomPadding: androidx.compose.ui.unit.Dp = XiloSpacing.bottomNavPadding,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = XiloBlue
            )
            return
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (chromeState != null) {
                        Modifier.trackChromeVisibility(chromeState, listState)
                    } else {
                        Modifier
                    }
                ),
            contentPadding = PaddingValues(
                start = XiloSpacing.horizontal,
                end = XiloSpacing.horizontal,
                top = 12.dp,
                bottom = contentBottomPadding
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (segment) {
                SavedHubSegment.Messages -> {
                    if (messages.isEmpty()) {
                        item(key = "saved_messages_empty") {
                            SavedHubEmptyState(
                                title = stringResource(R.string.saved_messages_empty_title),
                                subtitle = stringResource(R.string.saved_messages_empty_subtitle),
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .height(320.dp)
                            )
                        }
                    } else {
                        items(messages, key = { it.id }) { message ->
                            SavedMessageCard(message = message)
                        }
                    }
                }
                SavedHubSegment.Posts -> {
                    if (posts.isEmpty()) {
                        item(key = "saved_posts_empty") {
                            SavedHubEmptyState(
                                title = stringResource(R.string.saved_hub_posts_empty_title),
                                subtitle = stringResource(R.string.saved_hub_posts_empty_subtitle),
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .height(320.dp)
                            )
                        }
                    } else {
                        items(posts, key = { it.id }) { post ->
                            SavedPostCard(
                                post = post,
                                onClick = { onPostClick(post.slug) },
                                onAuthorClick = { onAuthorClick(post.authorUsername) },
                            )
                        }
                    }
                }
                SavedHubSegment.Comments -> {
                    if (comments.isEmpty()) {
                        item(key = "saved_comments_empty") {
                            SavedHubEmptyState(
                                title = stringResource(R.string.saved_hub_comments_empty_title),
                                subtitle = stringResource(R.string.saved_hub_comments_empty_subtitle),
                                modifier = Modifier
                                    .fillParentMaxWidth()
                                    .height(320.dp)
                            )
                        }
                    } else {
                        items(comments, key = { it.id }) { comment ->
                            SavedCommentCard(
                                comment = comment,
                                onClick = {
                                    val slug = comment.post?.slug
                                    if (!slug.isNullOrBlank()) {
                                        onCommentClick(slug, comment.id)
                                    }
                                },
                                onAuthorClick = {
                                    comment.author?.username?.let(onAuthorClick)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SavedPostCard(
    post: PostEntity,
    onClick: () -> Unit,
    onAuthorClick: (() -> Unit)? = null,
) {
    val openAuthor = onAuthorClick?.takeIf { post.authorUsername.isNotBlank() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XiloAvatar(
                imageUrl = post.authorAvatar,
                size = 32.dp,
                onClick = openAuthor,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = post.authorName?.takeIf { it.isNotBlank() } ?: post.authorUsername,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (openAuthor != null) {
                        Modifier.clickable(role = Role.Button, onClick = openAuthor)
                    } else {
                        Modifier
                    }
                )
                Text(
                    text = DateFormatter.getRelativeTimeSpan(androidx.compose.ui.platform.LocalContext.current, post.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            XiloIcon(
                icon = XiloIcons.BookmarkFilled,
                contentDescription = null,
                tint = XiloBlue,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val excerpt = post.excerpt?.takeIf { it.isNotBlank() } ?: post.content
        if (excerpt.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = excerpt,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SavedCommentCard(
    comment: BookmarkedCommentResponse,
    onClick: () -> Unit,
    onAuthorClick: (() -> Unit)? = null,
) {
    val openAuthor = onAuthorClick?.takeIf { !comment.author?.username.isNullOrBlank() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XiloAvatar(
                imageUrl = comment.author?.avatarUrl,
                size = 32.dp,
                onClick = openAuthor,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = comment.author?.displayName?.takeIf { it.isNotBlank() }
                        ?: comment.author?.username.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (openAuthor != null) {
                        Modifier.clickable(role = Role.Button, onClick = openAuthor)
                    } else {
                        Modifier
                    }
                )
                comment.post?.title?.takeIf { it.isNotBlank() }?.let { title ->
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            XiloIcon(
                icon = XiloIcons.BookmarkFilled,
                contentDescription = null,
                tint = XiloBlue,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        ContentAwareText(
            text = comment.content,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

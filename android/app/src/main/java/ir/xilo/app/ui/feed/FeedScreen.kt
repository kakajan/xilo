package ir.xilo.app.ui.feed

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.FeedSkeletonList
import ir.xilo.app.ui.components.LocalChromeVisibility
import ir.xilo.app.ui.components.VerifiedBadge
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloLogo
import ir.xilo.app.ui.components.XiloSnackbarHost
import ir.xilo.app.ui.components.trackChromeVisibility

@Composable
fun FeedScreen(
    onPostClick: (String) -> Unit,
    onReplyToPost: (String) -> Unit = onPostClick,
    onEditPost: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoading by viewModel.isInitialLoading.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val selectedCategory by viewModel.selectedCategoryIndex.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentUserAvatarUrl by viewModel.currentUserAvatarUrl.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val chromeState = LocalChromeVisibility.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val isTopChromeVisible = chromeState?.isVisible != false
    val density = LocalDensity.current
    var stickyHeaderHeight by remember { mutableStateOf(112.dp) }
    val animatedHeaderHeight by animateDpAsState(
        targetValue = if (isTopChromeVisible) stickyHeaderHeight else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "stickyHeaderHeight"
    )
    val topContentPadding by animateDpAsState(
        targetValue = if (isTopChromeVisible) stickyHeaderHeight else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "topContentPadding"
    )

    val listModifier = Modifier
        .fillMaxSize()
        .then(
            if (chromeState != null) {
                Modifier.trackChromeVisibility(chromeState, listState)
            } else {
                Modifier
            }
        )

    Box(modifier = modifier.fillMaxSize()) {
        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshFeed() },
            modifier = Modifier.fillMaxSize(),
            state = pullRefreshState,
            // Drawn in the outer Box below so it sits above the sticky header chips.
            indicator = {},
        ) {
            LazyColumn(
                state = listState,
                modifier = listModifier,
                contentPadding = PaddingValues(top = topContentPadding, bottom = XiloSpacing.feedBottomChromePadding)
            ) {
                if (isLoading && posts.isEmpty()) {
                    item(key = "feed_skeleton") {
                        FeedSkeletonList(modifier = Modifier.fillMaxWidth())
                    }
                } else if (posts.isEmpty() && !isRefreshing) {
                    item(key = "feed_empty") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .height(320.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isOnline) stringResource(R.string.feed_empty_online) else stringResource(R.string.feed_empty_offline),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.common_refresh),
                                    color = XiloBlue,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { viewModel.refreshFeed() }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(posts, key = { it.id }) { post ->
                        if (post.id.endsWith("-chat")) {
                            TelegramNotificationCard(
                                post = post,
                                onClick = { onPostClick(post.slug) }
                            )
                        } else {
                            val owner = isPostOwner(
                                authorId = post.authorId,
                                authorUsername = post.authorUsername,
                                currentUserId = currentUserId,
                                currentUsername = currentUsername,
                            )
                            PostCard(
                                post = post,
                                onPostClick = onPostClick,
                                onCommentClick = { onReplyToPost(post.slug) },
                                onLikeClick = { viewModel.toggleLike(post.id, post.isLiked) },
                                onBookmarkClick = { viewModel.toggleBookmark(post.id, post.isBookmarked) },
                                onRepostClick = { viewModel.toggleRepost(post.id, post.isReposted) },
                                onAuthorClick = { onAuthorClick(post.authorUsername) },
                                onHashtagClick = onHashtagClick,
                                isOwner = owner,
                                onEditClick = if (owner) ({ onEditPost(post.id) }) else null,
                                onArchiveClick = if (owner) ({ viewModel.archivePost(post.id) }) else null,
                                onDeleteClick = if (owner) ({ viewModel.deletePost(post.id) }) else null,
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(animatedHeaderHeight)
                .clip(RectangleShape)
                .background(Color.Transparent)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        val measured = with(density) { size.height.toDp() }
                        if (measured > stickyHeaderHeight - 1.dp) {
                            stickyHeaderHeight = measured
                        }
                    }
            ) {
                FeedHeader(
                    avatarUrl = currentUserAvatarUrl,
                    onSettingsClick = onSettingsClick,
                    onProfileClick = onProfileClick
                )
                FeedCategoryTabs(
                    categories = viewModel.categoryResIds.map { stringResource(it) },
                    selectedCategory = selectedCategory,
                    onCategorySelected = viewModel::selectCategory
                )
            }
        }

        // Keep the refresh spinner at the top edge, above search + category chips.
        Indicator(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(2f),
            isRefreshing = isRefreshing,
            state = pullRefreshState,
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            color = MaterialTheme.colorScheme.primary,
        )

        XiloSnackbarHost(snackbarHostState)
    }
}

@Composable
private fun FeedHeader(
    avatarUrl: String?,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(XiloSpacing.topAppBarHeight)
            .padding(horizontal = XiloSpacing.horizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        XiloAvatar(
            imageUrl = avatarUrl,
            size = 36.dp,
            onClick = onProfileClick
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(22.dp)
                )
                .clickable { },
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                XiloIcon(
                    icon = XiloIcons.Search,
                    contentDescription = stringResource(R.string.feed_search_cd),
                    modifier = Modifier.size(XiloSpacing.iconInline),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.feed_search_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSettingsClick,
            modifier = Modifier
                .size(40.dp)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
        ) {
            XiloIcon(
                icon = XiloIcons.Settings,
                contentDescription = stringResource(R.string.feed_settings_cd),
                modifier = Modifier.size(XiloSpacing.iconInline)
            )
        }
    }
}

@Composable
private fun FeedCategoryTabs(
    categories: List<String>,
    selectedCategory: Int,
    onCategorySelected: (Int) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = XiloSpacing.horizontal, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(categories.size) { index ->
            val isSelected = selectedCategory == index
            val scale by animateFloatAsState(
                targetValue = if (isSelected) 1.05f else 1f,
                label = "tabScale"
            )
            Box(
                modifier = Modifier
                    .scale(scale)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                    .clickable { onCategorySelected(index) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = categories[index],
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
fun TelegramNotificationCard(
    post: PostEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = XiloSpacing.horizontal, vertical = XiloSpacing.vertical),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(XiloBlue),
            contentAlignment = Alignment.Center
        ) {
            XiloLogo(size = 28.dp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.authorName ?: stringResource(R.string.feed_author_fallback),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 16.dp)
                }
                Text(
                    text = getRelativeTimeSpan(androidx.compose.ui.platform.LocalContext.current, post.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = post.excerpt ?: post.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
            )
        }
        if (post.commentCount > 0) {
            Spacer(modifier = Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(XiloBlue),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = post.commentCount.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        IconButton(onClick = { }) {
            XiloIcon(
                icon = XiloIcons.More,
                contentDescription = stringResource(R.string.cd_options),
                modifier = Modifier.size(XiloSpacing.iconInline)
            )
        }
    }
}

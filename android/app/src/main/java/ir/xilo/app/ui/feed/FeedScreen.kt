package ir.xilo.app.ui.feed

import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
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
    onQuotePost: (String) -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    unreadNotificationCount: Int = 0,
    onProfileClick: () -> Unit = {},
    onAuthorClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val pagingItems = viewModel.posts.collectAsLazyPagingItems()
    val isContentLoading by viewModel.isContentLoading.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val undoMessageRes by viewModel.undoMessage.collectAsStateWithLifecycle()
    val currentUserAvatarUrl by viewModel.currentUserAvatarUrl.collectAsStateWithLifecycle()
    val currentUserId by viewModel.currentUserId.collectAsStateWithLifecycle()
    val currentUsername by viewModel.currentUsername.collectAsStateWithLifecycle()
    val canRepost by viewModel.canRepost.collectAsStateWithLifecycle()
    val chromeState = LocalChromeVisibility.current
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val refreshLoadState = pagingItems.loadState.refresh
    val isRefreshing = refreshLoadState is LoadState.Loading && pagingItems.itemCount > 0
    val showFeedSkeleton =
        (refreshLoadState is LoadState.Loading && pagingItems.itemCount == 0) || isContentLoading
    val showEmptyFeed =
        pagingItems.itemCount == 0 &&
            refreshLoadState is LoadState.NotLoading &&
            !isContentLoading

    LaunchedEffect(viewModel.pagingRefreshRequests) {
        viewModel.pagingRefreshRequests.collect {
            pagingItems.refresh()
        }
    }

    LaunchedEffect(refreshLoadState) {
        when (refreshLoadState) {
            is LoadState.NotLoading -> viewModel.clearContentLoading()
            is LoadState.Error -> {
                viewModel.reportPagingRefreshError(refreshLoadState.error)
            }
            else -> Unit
        }
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val undoText = undoMessageRes?.let { stringResource(it) }
    val undoAction = stringResource(R.string.feed_undo_action)
    LaunchedEffect(undoText) {
        undoText?.let { message ->
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (undoMessageRes == R.string.feed_undo_deleted) {
                    null
                } else {
                    undoAction
                },
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.undoLast()
            }
            viewModel.consumeUndoMessage()
        }
    }

    val isTopChromeVisible = chromeState?.isVisible != false
    val density = LocalDensity.current
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var stickyHeaderHeight by remember { mutableStateOf(XiloSpacing.topAppBarHeight) }
    val totalHeaderHeight = stickyHeaderHeight + statusBarPadding
    val animatedHeaderHeight by animateDpAsState(
        targetValue = if (isTopChromeVisible) totalHeaderHeight else 0.dp,
        animationSpec = tween(durationMillis = 250),
        label = "stickyHeaderHeight"
    )
    val topContentPadding by animateDpAsState(
        targetValue = if (isTopChromeVisible) totalHeaderHeight else 0.dp,
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
            onRefresh = { pagingItems.refresh() },
            modifier = Modifier.fillMaxSize(),
            state = pullRefreshState,
            // Indicator is drawn in the outer Box, below the sticky header.
            indicator = {},
        ) {
            LazyColumn(
                state = listState,
                modifier = listModifier,
                contentPadding = PaddingValues(top = topContentPadding, bottom = XiloSpacing.feedBottomChromePadding)
            ) {
                if (showFeedSkeleton) {
                    item(key = "feed_skeleton") {
                        Box(
                            modifier = Modifier
                                .fillParentMaxWidth()
                                .fillParentMaxHeight(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            FeedSkeletonList(modifier = Modifier.fillMaxWidth())
                        }
                    }
                } else if (showEmptyFeed) {
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
                                    text = stringResource(R.string.feed_empty_topics_cta),
                                    color = XiloBlue,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable(onClick = onSearchClick)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                                Text(
                                    text = stringResource(R.string.common_refresh),
                                    color = XiloBlue,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { pagingItems.refresh() }
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                } else {
                    items(
                        count = pagingItems.itemCount,
                        key = { index -> pagingItems.peek(index)?.id ?: "ph-$index" },
                        contentType = { "post" },
                    ) { index ->
                        val post = pagingItems[index] ?: return@items
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
                                onReact = { emoji -> viewModel.react(post.id, emoji) },
                                onBookmarkClick = { viewModel.toggleBookmark(post.id, post.isBookmarked) },
                                onRepostClick = if (canRepost) {
                                    { viewModel.toggleRepost(post.id, post.isReposted) }
                                } else {
                                    null
                                },
                                onQuoteClick = if (canRepost) {
                                    { onQuotePost(post.id) }
                                } else {
                                    null
                                },
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
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { size ->
                            val measured = with(density) { size.height.toDp() }
                            if (kotlin.math.abs((measured - stickyHeaderHeight).value) > 0.5f) {
                                stickyHeaderHeight = measured
                            }
                        }
                ) {
                    FeedHeader(
                        avatarUrl = currentUserAvatarUrl,
                        onSettingsClick = onSettingsClick,
                        onNotificationsClick = onNotificationsClick,
                        unreadNotificationCount = unreadNotificationCount,
                        onProfileClick = onProfileClick,
                        onSearchClick = onSearchClick,
                    )
                }
            }
        }

        // Refresh spinner starts below the sticky search header.
        Indicator(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = animatedHeaderHeight)
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
    onNotificationsClick: () -> Unit,
    unreadNotificationCount: Int,
    onProfileClick: () -> Unit,
    onSearchClick: () -> Unit,
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
                .clickable(onClick = onSearchClick),
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
        Box {
            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier
                    .size(40.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), CircleShape)
            ) {
                XiloIcon(
                    icon = XiloIcons.Notification,
                    contentDescription = stringResource(R.string.notifications_inbox_title),
                    modifier = Modifier.size(XiloSpacing.iconInline)
                )
            }
            if (unreadNotificationCount > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 4.dp, end = 4.dp)
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.error),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString(),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = Color.White,
                        maxLines = 1,
                    )
                }
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

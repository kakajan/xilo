package com.example.xilo.ui.feed

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.theme.XiloBlue
import com.example.xilo.theme.XiloSpacing
import com.example.xilo.ui.components.FeedSkeletonList
import com.example.xilo.ui.components.LocalChromeVisibility
import com.example.xilo.ui.components.VerifiedBadge
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.XiloIcon
import com.example.xilo.ui.components.XiloIcons
import com.example.xilo.ui.components.XiloLogo
import com.example.xilo.ui.components.XiloSnackbarHost
import com.example.xilo.ui.components.trackChromeVisibility

@Composable
fun FeedScreen(
    onPostClick: (String) -> Unit,
    onSettingsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoading by viewModel.isInitialLoading.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val selectedCategory by viewModel.selectedCategoryIndex.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
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
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshFeed() },
            modifier = Modifier.fillMaxSize()
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
                                    text = if (isOnline) "هنوز پستی منتشر نشده است" else "پستی در حافظه محلی نیست",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "بروزرسانی",
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
                            PostCard(
                                post = post,
                                onPostClick = onPostClick,
                                onLikeClick = { viewModel.toggleLike(post.id, post.isLiked) },
                                onBookmarkClick = { viewModel.toggleBookmark(post.id, post.isBookmarked) }
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
                .background(MaterialTheme.colorScheme.background)
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
                FeedHeader(onSettingsClick = onSettingsClick)
                FeedCategoryTabs(
                    categories = viewModel.categories,
                    selectedCategory = selectedCategory,
                    onCategorySelected = viewModel::selectCategory
                )
            }
        }

        XiloSnackbarHost(snackbarHostState)
    }
}

@Composable
private fun FeedHeader(onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(XiloSpacing.topAppBarHeight)
            .padding(horizontal = XiloSpacing.horizontal),
        verticalAlignment = Alignment.CenterVertically
    ) {
        XiloAvatar(imageUrl = null, size = 36.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
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
                    contentDescription = "جستجو",
                    modifier = Modifier.size(XiloSpacing.iconInline),
                    tint = MaterialTheme.colorScheme.secondary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "جستجو در همه چیز...",
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
                contentDescription = "تنظیمات",
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
                            Brush.radialGradient(
                                colors = listOf(XiloBlue, XiloBlue.copy(alpha = 0.7f))
                            )
                        } else {
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
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
                        text = post.authorName ?: "تلگرام",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 16.dp)
                }
                Text(
                    text = getRelativeTimeSpan(post.createdAt),
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
                contentDescription = "گزینه‌های بیشتر",
                modifier = Modifier.size(XiloSpacing.iconInline)
            )
        }
    }
}

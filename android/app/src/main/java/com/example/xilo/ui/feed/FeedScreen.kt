package com.example.xilo.ui.feed

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.xilo.theme.XiloBlue
import com.example.xilo.ui.components.LocalChromeVisibility
import com.example.xilo.ui.components.XiloLogo
import com.example.xilo.ui.components.trackChromeVisibility

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    onPostClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FeedViewModel = hiltViewModel()
) {
    val posts by viewModel.posts.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val chromeState = LocalChromeVisibility.current
    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = chromeState?.isVisible != false,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            CenterAlignedTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        XiloLogo(size = 28.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Xilo",
                            fontWeight = FontWeight.Bold,
                            color = XiloBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshFeed() },
            modifier = Modifier.fillMaxSize()
        ) {
            if (posts.isEmpty() && !isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isOnline) "هنوز پستی منتشر نشده است" else "پستی در حافظه محلی نیست",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.refreshFeed() }) {
                            Text("بروزرسانی")
                        }
                    }
                }
            } else {
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
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    items(posts, key = { it.id }) { post ->
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
}

package com.example.xilo.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import com.example.xilo.ChatConversationKey
import com.example.xilo.CreatePostKey
import com.example.xilo.PostDetailKey
import com.example.xilo.theme.XiloBlue
import com.example.xilo.ui.auth.AuthScreen
import com.example.xilo.ui.chat.ChatListScreen
import com.example.xilo.ui.chat.ChatViewModel
import com.example.xilo.ui.components.FloatingBottomNavigation
import com.example.xilo.ui.components.LocalChromeVisibility
import com.example.xilo.ui.components.NavigationItem
import com.example.xilo.ui.components.OfflineBanner
import com.example.xilo.ui.components.XiloIcons
import com.example.xilo.ui.components.rememberChromeVisibilityState
import com.example.xilo.ui.discover.DiscoverScreen
import com.example.xilo.ui.feed.FeedScreen
import com.example.xilo.ui.profile.ProfileScreen
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    if (!isAuthenticated) {
        AuthScreen(
            onAuthSuccess = { viewModel.updateAuthStatus() },
            modifier = modifier
        )
    } else {
        val pagerState = rememberPagerState(pageCount = { 4 })
        val selectedTab = pagerState.currentPage
        val coroutineScope = rememberCoroutineScope()

        val navItems = listOf(
            NavigationItem("فید", XiloIcons.FeedSelected, XiloIcons.FeedUnselected),
            NavigationItem("اکتشاف", XiloIcons.DiscoverSelected, XiloIcons.DiscoverUnselected),
            NavigationItem("پیام‌ها", XiloIcons.ChatSelected, XiloIcons.ChatUnselected),
            NavigationItem("پروفایل", XiloIcons.ProfileSelected, XiloIcons.ProfileUnselected)
        )

        val fabScale by animateFloatAsState(
            targetValue = if (selectedTab == 0) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "fabScale"
        )

        val chromeState = rememberChromeVisibilityState()

        LaunchedEffect(selectedTab) {
            chromeState.show()
        }

        CompositionLocalProvider(LocalChromeVisibility provides chromeState) {
            Scaffold(
                topBar = {
                    OfflineBanner(isOffline = !isOnline)
                },
                bottomBar = {
                    AnimatedVisibility(
                        visible = chromeState.isVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                                            MaterialTheme.colorScheme.background
                                        )
                                    )
                                )
                                .padding(top = 24.dp)
                        ) {
                            FloatingBottomNavigation(
                                selectedTab = selectedTab,
                                onTabSelected = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(it)
                                    }
                                },
                                items = navItems
                            )
                        }
                    }
                },
                floatingActionButton = {
                    AnimatedVisibility(
                        visible = selectedTab == 0 && chromeState.isVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + scaleIn() + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + scaleOut() + fadeOut()
                    ) {
                        FloatingActionButton(
                            onClick = { onItemClick(CreatePostKey) },
                            shape = CircleShape,
                            containerColor = XiloBlue,
                            contentColor = Color.White,
                            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                            modifier = Modifier
                                .offset(y = 12.dp)
                                .scale(fabScale)
                                .border(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.7f),
                                            Color.White.copy(alpha = 0.1f)
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Create Post",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                modifier = modifier
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = innerPadding.calculateTopPadding())
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> FeedScreen(
                                onPostClick = { slug -> onItemClick(PostDetailKey(slug)) },
                                modifier = Modifier.fillMaxSize()
                            )
                            1 -> DiscoverScreen(
                                onCommentClick = { slug -> onItemClick(PostDetailKey(slug)) },
                                modifier = Modifier.fillMaxSize()
                            )
                            2 -> {
                                val chatViewModel: ChatViewModel = hiltViewModel()
                                ChatListScreen(
                                    onChatClick = { chatId -> onItemClick(ChatConversationKey(chatId)) },
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = chatViewModel
                                )
                            }
                            3 -> {
                                val currentUsername by viewModel.currentUsername.collectAsState()
                                if (currentUsername != null) {
                                    ProfileScreen(
                                        username = currentUsername!!,
                                        onBackClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(0)
                                            }
                                        },
                                        onPostClick = { slug -> onItemClick(PostDetailKey(slug)) },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

package ir.xilo.app.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation3.runtime.NavKey
import ir.xilo.app.R
import ir.xilo.app.ChatConversationKey
import ir.xilo.app.CreatePostKey
import ir.xilo.app.FollowListKey
import ir.xilo.app.ContactsKey
import ir.xilo.app.NewChatKey
import ir.xilo.app.NotificationsKey
import ir.xilo.app.PostDetailKey
import ir.xilo.app.ProfileKey
import ir.xilo.app.EditProfileKey
import ir.xilo.app.SettingsKey
import ir.xilo.app.TagFeedKey
import ir.xilo.app.ui.profile.FollowListMode
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.auth.AuthScreen
import ir.xilo.app.ui.chat.ChatListScreen
import ir.xilo.app.ui.chat.ChatViewModel
import ir.xilo.app.ui.components.FloatingBottomNavigation
import ir.xilo.app.ui.components.LocalChromeVisibility
import ir.xilo.app.ui.components.NavigationItem
import ir.xilo.app.ui.components.OfflineBanner
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.rememberChromeVisibilityState
import ir.xilo.app.ui.discover.DiscoverScreen
import ir.xilo.app.ui.feed.FeedScreen
import ir.xilo.app.ui.profile.ProfileScreen
import ir.xilo.app.push.RequestNotificationPermissionEffect
import kotlinx.coroutines.launch

@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = hiltViewModel()
) {
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val onboardingCompleted by viewModel.onboardingCompleted.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val canCreatePost by viewModel.canCreatePost.collectAsState()
    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsState()

    if (!isAuthenticated) {
        AuthScreen(
            onAuthSuccess = { viewModel.updateAuthStatus() },
            modifier = modifier
        )
    } else if (!onboardingCompleted) {
        ir.xilo.app.ui.onboarding.OnboardingScreen(
            onOnboardingComplete = { viewModel.completeOnboarding() },
            modifier = modifier
        )
    } else {
        RequestNotificationPermissionEffect(enabled = true)

        val pagerState = rememberPagerState(pageCount = { 4 })
        val selectedTab = pagerState.currentPage
        val coroutineScope = rememberCoroutineScope()
        val pendingTab by viewModel.pendingTab.collectAsState()
        val openSettingsForUsername by viewModel.openSettingsForUsername.collectAsState()

        val navItems = listOf(
            NavigationItem(stringResource(R.string.nav_feed), XiloIcons.FeedSelected, XiloIcons.FeedUnselected),
            NavigationItem(stringResource(R.string.nav_discover), XiloIcons.DiscoverSelected, XiloIcons.DiscoverUnselected),
            NavigationItem(stringResource(R.string.nav_messages), XiloIcons.ChatSelected, XiloIcons.ChatUnselected),
            NavigationItem(stringResource(R.string.nav_profile), XiloIcons.ProfileSelected, XiloIcons.ProfileUnselected),
        )

        val chromeState = rememberChromeVisibilityState()

        LaunchedEffect(selectedTab) {
            chromeState.show()
        }

        LaunchedEffect(pendingTab) {
            val tab = pendingTab ?: return@LaunchedEffect
            pagerState.animateScrollToPage(tab)
            viewModel.consumePendingTab()
        }

        LaunchedEffect(openSettingsForUsername) {
            if (!openSettingsForUsername) return@LaunchedEffect
            onItemClick(SettingsKey)
            viewModel.consumeOpenSettingsForUsername()
        }

        CompositionLocalProvider(LocalChromeVisibility provides chromeState) {
            // Status-bar insets are handled by each tab's top chrome / TopAppBar so we do not
            // stack a second gap here. Keep only bottom safe insets for the floating nav.
            Scaffold(
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                topBar = {
                    OfflineBanner(isOffline = !isOnline)
                },
                bottomBar = {
                    val showFab = selectedTab == 0 && chromeState.isVisible && canCreatePost
                    val chromeAnimSpec = tween<Float>(durationMillis = 300)
                    val chromeAnimSpecDp = tween<Dp>(durationMillis = 300)

                    val fabScale by animateFloatAsState(
                        targetValue = if (showFab) 1f else 0f,
                        animationSpec = chromeAnimSpec,
                        label = "fabScale"
                    )
                    val fabAlpha by animateFloatAsState(
                        targetValue = if (showFab) 1f else 0f,
                        animationSpec = chromeAnimSpec,
                        label = "fabAlpha"
                    )
                    val fabSectionHeight by animateDpAsState(
                        targetValue = if (showFab) {
                            XiloSpacing.fabSize + XiloSpacing.fabGapAboveNav
                        } else {
                            0.dp
                        },
                        animationSpec = chromeAnimSpecDp,
                        label = "fabSectionHeight"
                    )

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
                                .navigationBarsPadding()
                                .padding(top = XiloSpacing.bottomNavGradientTopPadding)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(fabSectionHeight)
                                        .padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.BottomEnd
                                ) {
                                    if (fabScale > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .size(XiloSpacing.fabSize)
                                                .graphicsLayer {
                                                    scaleX = fabScale
                                                    scaleY = fabScale
                                                    alpha = fabAlpha
                                                    transformOrigin = TransformOrigin(0.5f, 1f)
                                                }
                                        ) {
                                            FloatingActionButton(
                                                onClick = { onItemClick(CreatePostKey()) },
                                                shape = CircleShape,
                                                containerColor = XiloBlue,
                                                contentColor = Color.White,
                                                elevation = FloatingActionButtonDefaults.elevation(
                                                    defaultElevation = 0.dp,
                                                    pressedElevation = 0.dp,
                                                    focusedElevation = 0.dp,
                                                    hoveredElevation = 0.dp
                                                ),
                                                modifier = Modifier.border(
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
                                                XiloIcon(
                                                    icon = XiloIcons.Add,
                                                    contentDescription = "ایجاد پست جدید",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    }
                                }
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
                    }
                },
                modifier = modifier
            ) { innerPadding ->
                // Profile paints under the status bar; keep inset when offline banner is showing.
                val topInset = if (selectedTab == 3 && isOnline) {
                    0.dp
                } else {
                    innerPadding.calculateTopPadding()
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topInset)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = false,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> FeedScreen(
                                onPostClick = { slug -> onItemClick(PostDetailKey(slug)) },
                                onReplyToPost = { slug ->
                                    onItemClick(PostDetailKey(slug = slug, replyToPost = true))
                                },
                                onEditPost = { postId ->
                                    onItemClick(CreatePostKey(editPostId = postId))
                                },
                                onQuotePost = { postId ->
                                    onItemClick(CreatePostKey(quotedPostId = postId))
                                },
                                onSettingsClick = { onItemClick(SettingsKey) },
                                onNotificationsClick = { onItemClick(NotificationsKey) },
                                unreadNotificationCount = unreadNotificationCount,
                                onProfileClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(3)
                                    }
                                },
                                onAuthorClick = { username ->
                                    if (username.isNotBlank()) onItemClick(ProfileKey(username))
                                },
                                onHashtagClick = { tag ->
                                    if (tag.isNotBlank()) onItemClick(TagFeedKey(tag = tag))
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            1 -> DiscoverScreen(
                                onCommentClick = { slug -> onItemClick(PostDetailKey(slug)) },
                                onReplyToPost = { slug ->
                                    onItemClick(PostDetailKey(slug = slug, replyToPost = true))
                                },
                                onReplyToComment = { slug, commentId, authorUsername, authorAvatar ->
                                    onItemClick(
                                        PostDetailKey(
                                            slug = slug,
                                            replyToCommentId = commentId,
                                            replyToAuthor = authorUsername,
                                            replyToAuthorAvatar = authorAvatar,
                                        )
                                    )
                                },
                                onAuthorClick = { username ->
                                    if (username.isNotBlank()) onItemClick(ProfileKey(username))
                                },
                                onEditPost = { postId ->
                                    onItemClick(CreatePostKey(editPostId = postId))
                                },
                                onQuotePost = { postId ->
                                    onItemClick(CreatePostKey(quotedPostId = postId))
                                },
                                onQuoteComment = { commentId ->
                                    onItemClick(CreatePostKey(quotedCommentId = commentId))
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                            2 -> {
                                val chatViewModel: ChatViewModel = hiltViewModel()
                                ChatListScreen(
                                    onChatClick = { chatId ->
                                        onItemClick(ChatConversationKey(chatId = chatId))
                                    },
                                    onNewChatClick = { onItemClick(NewChatKey) },
                                    onContactsClick = { onItemClick(ContactsKey) },
                                    modifier = Modifier.fillMaxSize(),
                                    viewModel = chatViewModel
                                )
                            }
                            3 -> {
                                val currentUsername by viewModel.currentUsername.collectAsState()
                                LaunchedEffect(Unit) {
                                    if (currentUsername.isNullOrBlank()) {
                                        viewModel.refreshUsername()
                                    }
                                }
                                if (!currentUsername.isNullOrBlank()) {
                                    ProfileScreen(
                                        username = currentUsername!!,
                                        onBackClick = {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(0)
                                            }
                                        },
                                        onSettingsClick = { onItemClick(SettingsKey) },
                                        onEditProfileClick = { onItemClick(EditProfileKey) },
                                        onCreatePostClick = {
                                            if (canCreatePost) onItemClick(CreatePostKey())
                                        },
                                        onPostClick = { slug -> onItemClick(PostDetailKey(slug)) },
                                        onChatClick = { chatId ->
                                            onItemClick(ChatConversationKey(chatId = chatId))
                                        },
                                        onFollowersClick = { username ->
                                            onItemClick(
                                                FollowListKey(
                                                    username = username,
                                                    mode = FollowListMode.Followers.name
                                                )
                                            )
                                        },
                                        onFollowingClick = { username ->
                                            onItemClick(
                                                FollowListKey(
                                                    username = username,
                                                    mode = FollowListMode.Following.name
                                                )
                                            )
                                        },
                                        onReplyClick = { slug, commentId ->
                                            onItemClick(
                                                PostDetailKey(slug = slug, replyToCommentId = commentId)
                                            )
                                        },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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

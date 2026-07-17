package ir.xilo.app.ui.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.CommentCard
import ir.xilo.app.ui.components.LocalChromeVisibility
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTextField
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.ui.components.trackChromeVisibility
import ir.xilo.app.ui.feed.PostCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onCommentClick: (String) -> Unit,
    onReplyToPost: (String) -> Unit = onCommentClick,
    onReplyToComment: (slug: String, commentId: String, authorUsername: String) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val recentComments by viewModel.recentComments.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var reportTargetId by remember { mutableStateOf<String?>(null) }
    val chromeState = LocalChromeVisibility.current
    val discoverListState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val infoText = infoMessage?.let { stringResource(it) }
    LaunchedEffect(infoText) {
        infoText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearInfoMessage()
        }
    }

    if (reportTargetId != null) {
        AlertDialog(
            onDismissRequest = { reportTargetId = null },
            title = { Text(stringResource(R.string.discover_report_title)) },
            text = { Text(stringResource(R.string.discover_report_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        reportTargetId = null
                        viewModel.reportComment()
                    }
                ) {
                    Text(stringResource(R.string.discover_report_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { reportTargetId = null }) {
                    Text(stringResource(R.string.discover_report_cancel))
                }
            }
        )
    }

    // Nested under MainScreen, which already pads for the status bar. Zero content
    // window insets here so Scaffold + TopAppBar do not stack a second (or third) gap.
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
    Column(modifier = Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = chromeState?.isVisible != false,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            if (isSearchActive) {
                XiloTopAppBar(
                    title = {
                        XiloTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = "جستجو در موضوعات، پست‌ها...",
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            ),
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.updateSearchQuery("")
                        }) {
                            XiloIcon(icon = XiloIcons.Close, contentDescription = "بستن جستجو")
                        }
                    }
                )
            } else {
                XiloTopAppBar(
                    title = "اکتشاف",
                    centered = true,
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            XiloIcon(icon = XiloIcons.Search, contentDescription = "جستجو")
                        }
                    }
                )
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = XiloBlue)
            }

            AnimatedContent(
                targetState = when {
                    searchResults.isNotEmpty() -> "results"
                    searchQuery.isNotBlank() && !isSearching -> "empty"
                    isSearchActive -> "searching"
                    else -> "discover"
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "discoverContent"
            ) { state ->
                when (state) {
                    "results" -> {
                        LazyColumn(
                            state = searchListState,
                            modifier = Modifier
                                .fillMaxSize()
                                .then(
                                    if (chromeState != null) {
                                        Modifier.trackChromeVisibility(chromeState, searchListState)
                                    } else {
                                        Modifier
                                    }
                                ),
                            contentPadding = PaddingValues(bottom = XiloSpacing.bottomNavPadding)
                        ) {
                            items(searchResults, key = { it.id }) { post ->
                                PostCard(
                                    post = post,
                                    onPostClick = onCommentClick,
                                    onCommentClick = { onReplyToPost(post.slug) },
                                    onLikeClick = { viewModel.toggleLike(post.id, post.isLiked) },
                                    onBookmarkClick = { viewModel.toggleBookmark(post.id, post.isBookmarked) },
                                    onRepostClick = { viewModel.toggleRepost(post.id, post.isReposted) },
                                    onAuthorClick = { onAuthorClick(post.authorUsername) },
                                )
                            }
                        }
                    }
                    "empty" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                "نتیجه‌ای برای '$searchQuery' یافت نشد.",
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    "discover" -> {
                        if (recentComments.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "برای کشف موضوعات روی دکمه جستجو کلیک کنید.",
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TextButton(onClick = { viewModel.refreshDiscoverComments() }) {
                                        Text("بروزرسانی")
                                    }
                                }
                            }
                        } else {
                            LazyColumn(
                                state = discoverListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .then(
                                        if (chromeState != null) {
                                            Modifier.trackChromeVisibility(chromeState, discoverListState)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentPadding = PaddingValues(bottom = XiloSpacing.bottomNavPadding)
                            ) {
                                items(recentComments, key = { it.id }) { comment ->
                                    CommentCard(
                                        comment = comment,
                                        onClick = {
                                            viewModel.openCommentPost(comment.postId, onCommentClick)
                                        },
                                        onReplyClick = {
                                            viewModel.openCommentReply(
                                                postId = comment.postId,
                                                commentId = comment.id,
                                                authorUsername = comment.authorUsername,
                                                onNavigate = onReplyToComment,
                                            )
                                        },
                                        onLikeClick = { viewModel.toggleCommentLike(comment) },
                                        onDislikeClick = { viewModel.toggleCommentDislike(comment) },
                                        onReportClick = { reportTargetId = comment.id },
                                        onBookmarkClick = { viewModel.toggleCommentBookmark(comment) },
                                        onAuthorClick = { onAuthorClick(comment.authorUsername) },
                                    )
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

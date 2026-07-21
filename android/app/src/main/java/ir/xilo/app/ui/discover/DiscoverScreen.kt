package ir.xilo.app.ui.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.AppLocale
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
import ir.xilo.app.ui.feed.isPostOwner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onCommentClick: (String) -> Unit,
    onReplyToPost: (String) -> Unit = onCommentClick,
    onReplyToComment: (slug: String, commentId: String, authorUsername: String) -> Unit,
    onAuthorClick: (String) -> Unit = {},
    onEditPost: (String) -> Unit = {},
    onQuotePost: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val discoverComments by viewModel.discoverComments.collectAsState()
    val topicInterests by viewModel.topicInterests.collectAsState()
    val selectedInterestSlug by viewModel.selectedInterestSlug.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val canRepost by viewModel.canRepost.collectAsState()
    val languageCode = AppLocale.languageCode(LocalContext.current)

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

    // Nested under MainScreen (zero scaffold insets). TopAppBar applies status-bar padding;
    // zero content insets here so Scaffold does not stack a second gap below the bar.
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { _ ->
    val chromeVisible = chromeState?.isVisible != false
    val showTopicChips = topicInterests.isNotEmpty() &&
        !isSearchActive &&
        searchQuery.isBlank() &&
        searchResults.isEmpty()

    Column(modifier = Modifier.fillMaxSize()) {
        // Match ChatListScreen: top bar owns status-bar padding while visible; when chrome
        // hides, only the sticky chips pad below the status bar. A shared parent
        // statusBarsPadding + collapsing top bar left a blank toolbar-sized gap above chips.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut()
        ) {
            if (isSearchActive) {
                XiloTopAppBar(
                    title = {
                        XiloTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.updateSearchQuery(it) },
                            placeholder = stringResource(R.string.discover_search_placeholder),
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
                            XiloIcon(icon = XiloIcons.Close, contentDescription = stringResource(R.string.discover_close_search))
                        }
                    },
                )
            } else {
                XiloTopAppBar(
                    title = stringResource(R.string.discover_title),
                    centered = true,
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            XiloIcon(icon = XiloIcons.Search, contentDescription = stringResource(R.string.common_search))
                        }
                    }
                )
            }
        }

        if (showTopicChips) {
            val chipsBelowStatusBar = !chromeVisible
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(1f)
                    .background(MaterialTheme.colorScheme.background)
                    .then(
                        if (chipsBelowStatusBar) {
                            Modifier.statusBarsPadding()
                        } else {
                            Modifier
                        }
                    ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = selectedInterestSlug == null,
                        onClick = { viewModel.selectInterest(null) },
                        label = { Text(stringResource(R.string.discover_topic_all)) },
                    )
                }
                items(topicInterests, key = { it.id }) { interest ->
                    FilterChip(
                        selected = selectedInterestSlug == interest.slug,
                        onClick = { viewModel.selectInterest(interest.slug) },
                        label = { Text(interest.labelFor(languageCode)) },
                    )
                }
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
                                val owner = isPostOwner(
                                    authorId = post.authorId,
                                    authorUsername = post.authorUsername,
                                    currentUserId = currentUserId,
                                    currentUsername = currentUsername,
                                )
                                PostCard(
                                    post = post,
                                    onPostClick = onCommentClick,
                                    onCommentClick = { onReplyToPost(post.slug) },
                                    onLikeClick = { viewModel.toggleLike(post.id, post.isLiked) },
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
                                    isOwner = owner,
                                    onEditClick = if (owner) ({ onEditPost(post.id) }) else null,
                                    onArchiveClick = if (owner) ({ viewModel.archivePost(post.id) }) else null,
                                    onDeleteClick = if (owner) ({ viewModel.deletePost(post.id) }) else null,
                                )
                            }
                        }
                    }
                    "empty" -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                stringResource(R.string.discover_no_results, searchQuery),
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    "discover" -> {
                        val discoverPullState = rememberPullToRefreshState()
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = { viewModel.refreshDiscoverComments() },
                            modifier = Modifier.fillMaxSize(),
                            state = discoverPullState,
                        ) {
                            // Always use LazyColumn so PTR nested-scroll works in the empty state too.
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
                                if (discoverComments.isEmpty()) {
                                    item(key = "discover_empty") {
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxSize(),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    stringResource(R.string.discover_prompt_search),
                                                    color = MaterialTheme.colorScheme.secondary
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                TextButton(onClick = { viewModel.refreshDiscoverComments() }) {
                                                    Text(stringResource(R.string.common_refresh))
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    items(discoverComments, key = { it.id }) { comment ->
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
}

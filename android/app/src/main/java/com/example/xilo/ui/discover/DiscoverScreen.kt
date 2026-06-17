package com.example.xilo.ui.discover

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.xilo.data.local.entity.CommentEntity
import com.example.xilo.theme.XiloBlue
import com.example.xilo.theme.XiloSpacing
import com.example.xilo.theme.XiloTheme
import com.example.xilo.ui.components.LocalChromeVisibility
import com.example.xilo.ui.components.XiloIcon
import com.example.xilo.ui.components.XiloIcons
import com.example.xilo.ui.components.XiloTextField
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.trackChromeVisibility
import com.example.xilo.ui.feed.PostCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    onCommentClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DiscoverViewModel = hiltViewModel()
) {
    val searchResults by viewModel.searchResults.collectAsState()
    val recentComments by viewModel.recentComments.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
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

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
        AnimatedVisibility(
            visible = chromeState?.isVisible != false,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
        ) {
            if (isSearchActive) {
                TopAppBar(
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
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("اکتشاف", fontWeight = FontWeight.Bold) },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            XiloIcon(icon = XiloIcons.Search, contentDescription = "جستجو")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
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
                                    onLikeClick = {},
                                    onBookmarkClick = {}
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
                                contentPadding = PaddingValues(bottom = XiloSpacing.bottomNavPadding, top = 8.dp)
                            ) {
                                item {
                                    Text(
                                        text = "آخرین نظرات",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                                items(recentComments, key = { it.id }) { comment ->
                                    DiscoverCommentCard(
                                        comment = comment,
                                        onClick = {
                                            viewModel.openCommentPost(comment.postId, onCommentClick)
                                        }
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

@Composable
fun DiscoverCommentCard(
    comment: CommentEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleColors = XiloTheme.bubbleColors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        XiloAvatar(imageUrl = comment.authorAvatar, size = 36.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColors.othersBubble)
                .padding(12.dp)
        ) {
            Text(
                text = comment.authorName ?: comment.authorUsername,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = XiloBlue
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = comment.content,
                fontSize = 14.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            val timeFormatted = SimpleDateFormat("h:mm a", Locale.US).format(Date(comment.createdAt))
            Text(
                text = timeFormatted,
                fontSize = 11.sp,
                color = Color.Gray,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

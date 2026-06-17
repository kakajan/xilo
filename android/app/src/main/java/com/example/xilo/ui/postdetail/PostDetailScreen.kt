package com.example.xilo.ui.postdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.theme.XiloBlue
import com.example.xilo.theme.XiloSpacing
import com.example.xilo.ui.components.ChatInput
import com.example.xilo.ui.components.FeedSkeletonList
import com.example.xilo.ui.components.VerifiedBadge
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.XiloIcon
import com.example.xilo.ui.components.XiloIcons
import com.example.xilo.ui.components.XiloLogo
import com.example.xilo.ui.components.XiloTopAppBar

fun extractPlainText(json: String): String {
    if (json.isBlank() || json == "{}") return ""
    return try {
        val sb = StringBuilder()
        extractTextFromJson(org.json.JSONObject(json), sb)
        sb.toString().trim()
    } catch (e: Exception) {
        if (!json.startsWith("{") && !json.startsWith("[")) json.trim() else ""
    }
}

private fun extractTextFromJson(obj: org.json.JSONObject, sb: StringBuilder) {
    val type = obj.optString("type")
    if (type == "text") {
        val text = obj.optString("text")
        if (text.isNotEmpty()) sb.append(text)
        return
    }
    val contentArray = obj.optJSONArray("content") ?: return
    for (i in 0 until contentArray.length()) {
        val child = contentArray.optJSONObject(i) ?: continue
        extractTextFromJson(child, sb)
        val childType = child.optString("type")
        if (childType in listOf("paragraph", "heading", "blockquote", "listItem", "bulletList", "orderedList")) {
            if (sb.isNotEmpty() && sb.last() != '\n') sb.append('\n')
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostDetailScreen(
    slug: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var newCommentText by remember { mutableStateOf("") }
    var replyingToCommentId by remember { mutableStateOf<String?>(null) }
    var replyingToAuthor by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(slug) {
        viewModel.loadPost(slug)
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = "",
                showBack = true,
                onBackClick = onBackClick,
                centered = true,
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        XiloLogo(size = 24.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Xilo",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = XiloBlue
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (post != null) {
                Column {
                    if (replyingToCommentId != null) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = XiloSpacing.horizontal, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "در حال پاسخ به @${replyingToAuthor}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "انصراف",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.clickable {
                                    replyingToCommentId = null
                                    replyingToAuthor = null
                                }
                            )
                        }
                    }
                    ChatInput(
                        value = newCommentText,
                        onValueChange = { newCommentText = it },
                        onSend = {
                            if (newCommentText.isNotBlank()) {
                                viewModel.addComment(newCommentText, replyingToCommentId)
                                newCommentText = ""
                                replyingToCommentId = null
                                replyingToAuthor = null
                            }
                        },
                        placeholder = "نوشتن نظر...",
                        showAttach = true,
                        showEmoji = true
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                post == null && isLoading -> {
                    FeedSkeletonList(itemCount = 3, modifier = Modifier.fillMaxSize())
                }
                post != null -> {
                    val threadDisplays = remember(comments) {
                        buildCommentThreadDisplayList(comments)
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            PostDetailHeader(post = post!!)
                        }

                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                thickness = 0.5.dp,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                            Text(
                                text = "نظرات و پاسخ‌ها",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = XiloSpacing.horizontal, vertical = 8.dp)
                            )
                        }

                        if (comments.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "اولین کسی باشید که نظر خود را ثبت می‌کند.",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        } else {
                            item {
                                PostToCommentsConnector()
                            }
                            items(threadDisplays, key = { it.comment.id }) { display ->
                                CommentThreadItem(
                                    display = display,
                                    isPostAuthor = display.comment.authorId == post?.authorId,
                                    onReplyClick = { commentId, authorName ->
                                        replyingToCommentId = commentId
                                        replyingToAuthor = authorName
                                    },
                                    onLikeClick = {
                                        viewModel.toggleCommentLike(
                                            display.comment.id,
                                            display.comment.isLiked,
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PostDetailHeader(post: PostEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = XiloSpacing.horizontal, vertical = XiloSpacing.vertical)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XiloAvatar(imageUrl = post.authorAvatar, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.authorName ?: post.authorUsername,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 18.dp)
                }
                Text(
                    text = "@${post.authorUsername} · ${getRelativeTimeSpan(post.createdAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (post.title.isNotBlank()) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        val displayContent = remember(post.content) {
            extractPlainText(post.content).ifBlank { post.excerpt ?: "" }
        }
        if (displayContent.isNotBlank()) {
            Text(
                text = displayContent,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (!post.coverImageUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            AsyncImage(
                model = post.coverImageUrl,
                contentDescription = "تصویر پست",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            DetailAction(XiloIcons.Message, post.commentCount.toString(), "نظرات")
            DetailAction(XiloIcons.Repeat, "342", "بازنشر")
            DetailAction(XiloIcons.Heart, post.likeCount.toString(), "پسند")
            DetailAction(XiloIcons.Chart, "48.6K", "بازدید")
            XiloIcon(
                icon = XiloIcons.Share,
                contentDescription = "اشتراک‌گذاری",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(XiloSpacing.iconInline)
            )
        }
    }
}

@Composable
private fun DetailAction(
    @androidx.annotation.DrawableRes icon: Int,
    count: String,
    description: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        XiloIcon(
            icon = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(XiloSpacing.iconInline)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = count, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun PostToCommentsConnector() {
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = XiloSpacing.horizontal)
            .height(16.dp),
    ) {
        Box(
            modifier = Modifier
                .width(48.dp)
                .fillMaxHeight(),
        ) {
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val x = size.width / 2f
                drawLine(
                    color = lineColor,
                    start = androidx.compose.ui.geometry.Offset(x, 0f),
                    end = androidx.compose.ui.geometry.Offset(x, size.height),
                    strokeWidth = 1.5.dp.toPx(),
                )
            }
        }
    }
}

private fun getRelativeTimeSpan(timestamp: Long): String =
    com.example.xilo.ui.feed.getRelativeTimeSpan(timestamp)

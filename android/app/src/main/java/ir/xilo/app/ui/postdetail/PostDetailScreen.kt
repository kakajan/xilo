package ir.xilo.app.ui.postdetail

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.theme.ColorError
import ir.xilo.app.theme.ColorSuccess
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.FeedSkeletonList
import ir.xilo.app.ui.components.VerifiedBadge
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloLogo
import ir.xilo.app.ui.components.XiloTopAppBar

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
    onAuthorClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    replyToCommentId: String? = null,
    replyToAuthor: String? = null,
    replyToPost: Boolean = false,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var replyDraftText by remember { mutableStateOf("") }
    var replyingToCommentId by remember(replyToCommentId) { mutableStateOf(replyToCommentId) }
    var replyingToAuthor by remember(replyToAuthor) { mutableStateOf(replyToAuthor) }
    var isReplyingToPost by remember(replyToPost) { mutableStateOf(replyToPost) }
    var reportTargetId by remember { mutableStateOf<String?>(null) }
    var focusStack by remember { mutableStateOf<List<String>>(emptyList()) }
    val focusCommentId = focusStack.lastOrNull()
    val currentUserAvatarUrl by viewModel.currentUserAvatarUrl.collectAsState()
    val infoMessage by viewModel.infoMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var didScrollToReplyTarget by remember(replyToCommentId) { mutableStateOf(false) }

    val handleBack: () -> Unit = {
        if (focusStack.isNotEmpty()) {
            focusStack = focusStack.dropLast(1)
        } else {
            onBackClick()
        }
    }

    val replyParentComment = remember(comments, replyingToCommentId) {
        replyingToCommentId?.let { id -> comments.find { it.id == id } }
    }
    val activeReplyParent = remember(replyParentComment, isReplyingToPost, post) {
        when {
            replyParentComment != null -> replyParentComment.toReplyComposeParent()
            isReplyingToPost && post != null -> post!!.toReplyComposeParent()
            else -> null
        }
    }

    LaunchedEffect(slug) {
        viewModel.loadPost(slug)
    }

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

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = "",
                showBack = true,
                onBackClick = handleBack,
                centered = true,
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        XiloLogo(size = 24.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = XiloBlue
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                    val threadDisplays = remember(comments, focusCommentId) {
                        buildVisibleCommentThread(comments, focusCommentId)
                    }

                    LaunchedEffect(threadDisplays, replyToCommentId, focusCommentId) {
                        val targetId = replyToCommentId ?: return@LaunchedEffect
                        if (focusCommentId != null) return@LaunchedEffect
                        if (didScrollToReplyTarget || threadDisplays.isEmpty()) return@LaunchedEffect
                        val commentIndex = threadDisplays.indexOfFirst { it.comment.id == targetId }
                        if (commentIndex < 0) return@LaunchedEffect
                        // Lazy items: header(0), divider(1), comments from 2 (focus bar only when drilled in).
                        listState.animateScrollToItem(index = commentIndex + 2)
                        didScrollToReplyTarget = true
                    }

                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            PostDetailHeader(
                                post = post!!,
                                onReplyClick = {
                                    replyDraftText = ""
                                    replyingToCommentId = null
                                    replyingToAuthor = null
                                    isReplyingToPost = true
                                },
                                onRepostClick = {
                                    viewModel.toggleRepost(post!!.id, post!!.isReposted)
                                },
                                onAuthorClick = {
                                    onAuthorClick(post!!.authorUsername)
                                },
                            )
                        }

                        item {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                thickness = 0.5.dp,
                            )
                        }

                        if (focusCommentId != null) {
                            item(key = "thread-focus-bar") {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(
                                            horizontal = XiloSpacing.horizontal,
                                            vertical = 10.dp,
                                        ),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = stringResource(R.string.comment_thread_back),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.clickable(role = Role.Button) {
                                            focusStack = focusStack.dropLast(1)
                                        },
                                    )
                                    Text(
                                        text = stringResource(R.string.comment_thread_branch_label),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
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
                            items(threadDisplays, key = { it.comment.id }) { display ->
                                CommentThreadItem(
                                    display = display,
                                    onReplyClick = { commentId, authorName ->
                                        replyDraftText = ""
                                        isReplyingToPost = false
                                        replyingToCommentId = commentId
                                        replyingToAuthor = authorName
                                    },
                                    onLikeClick = {
                                        viewModel.toggleCommentLike(display.comment)
                                    },
                                    onDislikeClick = {
                                        viewModel.toggleCommentDislike(display.comment)
                                    },
                                    onReportClick = {
                                        reportTargetId = display.comment.id
                                    },
                                    onBookmarkClick = {
                                        viewModel.toggleCommentBookmark(display.comment)
                                    },
                                    onAuthorClick = {
                                        onAuthorClick(display.comment.authorUsername)
                                    },
                                    onDrillDownClick = { commentId ->
                                        focusStack = focusStack + commentId
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    activeReplyParent?.let { parent ->
        ReplyComposeSheet(
            parent = parent,
            replyText = replyDraftText,
            onReplyTextChange = { replyDraftText = it },
            currentUserAvatarUrl = currentUserAvatarUrl,
            onAuthorClick = { onAuthorClick(parent.authorUsername) },
            onDismiss = {
                replyingToCommentId = null
                replyingToAuthor = null
                isReplyingToPost = false
                replyDraftText = ""
            },
            onSubmit = {
                if (replyDraftText.isNotBlank()) {
                    viewModel.addComment(replyDraftText, replyParentComment?.id)
                    replyDraftText = ""
                    replyingToCommentId = null
                    replyingToAuthor = null
                    isReplyingToPost = false
                }
            },
        )
    }
}

@Composable
fun PostDetailHeader(
    post: PostEntity,
    onReplyClick: () -> Unit = {},
    onRepostClick: () -> Unit = {},
    onAuthorClick: (() -> Unit)? = null,
) {
    val openAuthor = onAuthorClick?.takeIf { post.authorUsername.isNotBlank() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = XiloSpacing.horizontal, vertical = XiloSpacing.vertical)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XiloAvatar(
                imageUrl = post.authorAvatar,
                size = 48.dp,
                onClick = openAuthor,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = post.authorName ?: post.authorUsername,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = if (openAuthor != null) {
                            Modifier.clickable(role = Role.Button, onClick = openAuthor)
                        } else {
                            Modifier
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    VerifiedBadge(size = 18.dp)
                }
                Text(
                    text = "@${post.authorUsername} · ${getRelativeTimeSpan(post.createdAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = if (openAuthor != null) {
                        Modifier.clickable(role = Role.Button, onClick = openAuthor)
                    } else {
                        Modifier
                    }
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
            DetailAction(
                icon = XiloIcons.Message,
                count = post.commentCount.toString(),
                description = "نظرات",
                onClick = onReplyClick,
            )
            DetailAction(
                icon = XiloIcons.Repeat,
                count = post.repostCount.toString(),
                description = "بازنشر",
                tint = if (post.isReposted) ColorSuccess else MaterialTheme.colorScheme.secondary,
                onClick = onRepostClick,
            )
            DetailAction(
                icon = if (post.isLiked) XiloIcons.HeartFilled else XiloIcons.Heart,
                count = post.likeCount.toString(),
                description = "پسند",
                tint = if (post.isLiked) ColorError else MaterialTheme.colorScheme.secondary,
            )
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
    description: String,
    tint: Color = MaterialTheme.colorScheme.secondary,
    onClick: (() -> Unit)? = null,
) {
    val rowModifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = rowModifier.padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        XiloIcon(
            icon = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(XiloSpacing.iconInline)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = count,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
        )
    }
}

private fun getRelativeTimeSpan(timestamp: Long): String =
    ir.xilo.app.ui.feed.getRelativeTimeSpan(timestamp)

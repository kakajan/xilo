package ir.xilo.app.ui.postdetail

import android.content.Intent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import androidx.compose.ui.graphics.Color
import ir.xilo.app.R
import ir.xilo.app.core.util.AppLocale
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.theme.ColorError
import ir.xilo.app.theme.ColorSuccess
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.ContentAwareText
import ir.xilo.app.ui.components.HashtagAwareText
import ir.xilo.app.ui.components.FeedSkeletonList
import ir.xilo.app.ui.components.VerifiedBadge
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.AileBrandLogo
import ir.xilo.app.ui.components.AileLogoVariant
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.ui.components.forRelativeTime
import ir.xilo.app.ui.components.forUsernameHandle
import ir.xilo.app.ui.components.usernameHandle
import ir.xilo.app.ui.feed.PostOwnerMenu
import ir.xilo.app.ui.feed.QuotedPostEmbed
import ir.xilo.app.ui.feed.RepostMenuButton
import ir.xilo.app.ui.feed.hasQuotedPost
import ir.xilo.app.ui.feed.isPostOwner

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
    onEditPost: (String) -> Unit = {},
    onQuotePost: (String) -> Unit = {},
    onQuotedPostClick: (String) -> Unit = {},
    onHashtagClick: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    replyToCommentId: String? = null,
    replyToAuthor: String? = null,
    replyToPost: Boolean = false,
    viewModel: PostDetailViewModel = hiltViewModel()
) {
    val post by viewModel.post.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val currentUserId by viewModel.currentUserId.collectAsState()
    val currentUsername by viewModel.currentUsername.collectAsState()
    val canRepost by viewModel.canRepost.collectAsState()
    val postRemoved by viewModel.postRemoved.collectAsState()

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
    val activeReplyParent = remember(
        replyParentComment,
        isReplyingToPost,
        post,
        replyingToCommentId,
        replyingToAuthor,
    ) {
        when {
            replyParentComment != null -> replyParentComment.toReplyComposeParent()
            !replyingToCommentId.isNullOrBlank() -> ReplyComposeParent(
                authorUsername = replyingToAuthor.orEmpty(),
                authorName = replyingToAuthor,
                authorAvatar = null,
                content = "",
                createdAt = 0L,
            )
            isReplyingToPost && post != null -> post!!.toReplyComposeParent()
            else -> null
        }
    }

    LaunchedEffect(slug) {
        viewModel.loadPost(slug)
    }

    LaunchedEffect(postRemoved) {
        if (postRemoved) onBackClick()
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
                    AileBrandLogo(
                        variant = AileLogoVariant.Wordmark,
                        languageCode = AppLocale.languageCode(LocalContext.current),
                        height = 28.dp,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            )
        },
        bottomBar = {
            val audio = post?.audioUrl
            if (!audio.isNullOrBlank()) {
                PostAudioPlayer(
                    audioUrl = audio,
                    title = post?.title.orEmpty(),
                )
            }
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
                    val pullRefreshState = rememberPullToRefreshState()

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

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() },
                        modifier = Modifier.fillMaxSize(),
                        state = pullRefreshState,
                    ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item {
                            val detailPost = post!!
                            val owner = isPostOwner(
                                authorId = detailPost.authorId,
                                authorUsername = detailPost.authorUsername,
                                currentUserId = currentUserId,
                                currentUsername = currentUsername,
                            )
                            PostDetailHeader(
                                post = detailPost,
                                onReplyClick = {
                                    replyDraftText = ""
                                    replyingToCommentId = null
                                    replyingToAuthor = null
                                    isReplyingToPost = true
                                },
                                onLikeClick = {
                                    viewModel.toggleLike(detailPost.id, detailPost.isLiked)
                                },
                                onBookmarkClick = {
                                    viewModel.toggleBookmark(detailPost.id, detailPost.isBookmarked)
                                },
                                onRepostClick = if (canRepost) {
                                    { viewModel.toggleRepost(detailPost.id, detailPost.isReposted) }
                                } else {
                                    null
                                },
                                onQuoteClick = if (canRepost) {
                                    { onQuotePost(detailPost.id) }
                                } else {
                                    null
                                },
                                onQuotedPostClick = detailPost.quotedSlug?.takeIf { it.isNotBlank() }
                                    ?.let { quotedSlug -> { onQuotedPostClick(quotedSlug) } },
                                onAuthorClick = {
                                    onAuthorClick(detailPost.authorUsername)
                                },
                                onHashtagClick = onHashtagClick,
                                isOwner = owner,
                                onEditClick = if (owner) ({ onEditPost(detailPost.id) }) else null,
                                onArchiveClick = if (owner) {
                                    ({ viewModel.archivePost(detailPost.id) })
                                } else {
                                    null
                                },
                                onDeleteClick = if (owner) {
                                    ({ viewModel.deletePost(detailPost.id) })
                                } else {
                                    null
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
                                        stringResource(R.string.post_detail_empty_comments),
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
                    viewModel.addComment(replyDraftText, replyingToCommentId)
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
    onLikeClick: () -> Unit = {},
    onBookmarkClick: () -> Unit = {},
    onShareClick: (() -> Unit)? = null,
    /** Null hides the repost control (readers / non-authors). */
    onRepostClick: (() -> Unit)? = null,
    onQuoteClick: (() -> Unit)? = null,
    onQuotedPostClick: (() -> Unit)? = null,
    onAuthorClick: (() -> Unit)? = null,
    onHashtagClick: (String) -> Unit = {},
    isOwner: Boolean = false,
    onEditClick: (() -> Unit)? = null,
    onArchiveClick: (() -> Unit)? = null,
    onDeleteClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val sharePost = onShareClick ?: {
        val text = buildString {
            if (post.title.isNotBlank()) append(post.title).append('\n')
            append(post.excerpt?.takeIf { it.isNotBlank() } ?: extractPlainText(post.content).take(160))
            append("\n/p/").append(post.slug)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text.trim())
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
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
            Column(modifier = Modifier.weight(1f)) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = if (openAuthor != null) {
                        Modifier.clickable(role = Role.Button, onClick = openAuthor)
                    } else {
                        Modifier
                    }
                ) {
                    Text(
                        text = usernameHandle(post.authorUsername),
                        style = MaterialTheme.typography.bodyMedium.forUsernameHandle(),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = getRelativeTimeSpan(
                            androidx.compose.ui.platform.LocalContext.current,
                            post.createdAt,
                        ),
                        style = MaterialTheme.typography.bodyMedium.forRelativeTime(),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
            if (isOwner && onEditClick != null && onArchiveClick != null && onDeleteClick != null) {
                PostOwnerMenu(
                    onEdit = onEditClick,
                    onArchive = onArchiveClick,
                    onDelete = onDeleteClick,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (post.title.isNotBlank()) {
            ContentAwareText(
                text = post.title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        val displayContent = remember(post.content) {
            extractPlainText(post.content).ifBlank { post.excerpt ?: "" }
        }
        if (displayContent.isNotBlank()) {
            HashtagAwareText(
                text = displayContent,
                onHashtagClick = onHashtagClick,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (!post.coverImageUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            AsyncImage(
                model = post.coverImageUrl,
                contentDescription = stringResource(R.string.cd_post_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(XiloSpacing.mediaRadius))
            )
        }

        if (post.hasQuotedPost()) {
            Spacer(modifier = Modifier.height(12.dp))
            QuotedPostEmbed(
                post = post,
                onClick = onQuotedPostClick,
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailAction(
                icon = XiloIcons.Message,
                count = post.commentCount.toString(),
                description = stringResource(R.string.cd_comments),
                onClick = onReplyClick,
            )
            if (onRepostClick != null && onQuoteClick != null) {
                RepostMenuButton(
                    repostCount = post.repostCount,
                    isReposted = post.isReposted,
                    onRepostClick = onRepostClick,
                    onQuoteClick = onQuoteClick,
                    compact = false,
                )
            } else if (onRepostClick != null) {
                DetailAction(
                    icon = XiloIcons.Repeat,
                    count = post.repostCount.toString(),
                    description = stringResource(R.string.cd_repost),
                    tint = if (post.isReposted) ColorSuccess else MaterialTheme.colorScheme.secondary,
                    onClick = onRepostClick,
                )
            }
            val likeScale by animateFloatAsState(
                targetValue = if (post.isLiked) 1.15f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "detailLikeScale",
            )
            DetailAction(
                icon = if (post.isLiked) XiloIcons.HeartFilled else XiloIcons.Heart,
                count = post.likeCount.toString(),
                description = stringResource(R.string.cd_like),
                tint = if (post.isLiked) ColorError else MaterialTheme.colorScheme.secondary,
                onClick = onLikeClick,
                modifier = Modifier.scale(likeScale),
            )
            DetailAction(
                icon = if (post.isBookmarked) XiloIcons.BookmarkFilled else XiloIcons.Bookmark,
                count = null,
                description = stringResource(R.string.cd_bookmark),
                tint = if (post.isBookmarked) XiloBlue else MaterialTheme.colorScheme.secondary,
                onClick = onBookmarkClick,
            )
            DetailAction(
                icon = XiloIcons.Chart,
                count = formatDetailViewCount(post.viewCount),
                description = stringResource(R.string.cd_views),
            )
            DetailAction(
                icon = XiloIcons.Share,
                count = null,
                description = stringResource(R.string.cd_share),
                onClick = sharePost,
            )
        }
    }
}

@Composable
private fun DetailAction(
    @androidx.annotation.DrawableRes icon: Int,
    count: String?,
    description: String,
    tint: Color = MaterialTheme.colorScheme.secondary,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val rowModifier = if (onClick != null) {
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .then(rowModifier)
            .padding(horizontal = 4.dp, vertical = 4.dp),
    ) {
        XiloIcon(
            icon = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(XiloSpacing.iconInline)
        )
        if (count != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = count,
                style = MaterialTheme.typography.labelMedium,
                color = tint,
            )
        }
    }
}

private fun getRelativeTimeSpan(context: android.content.Context, timestamp: Long): String =
    ir.xilo.app.ui.feed.getRelativeTimeSpan(context, timestamp)

private fun formatDetailViewCount(n: Long): String = when {
    n >= 1_000_000L -> "${n / 1_000_000L}M"
    n >= 1_000L -> "${n / 1_000L}K"
    else -> n.toString()
}

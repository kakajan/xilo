package com.example.xilo.ui.postdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.xilo.data.local.entity.CommentEntity
import com.example.xilo.data.local.entity.PostEntity
import com.example.xilo.theme.XiloBlue
import com.example.xilo.theme.XiloTheme
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.XiloButton
import com.example.xilo.ui.components.XiloTextField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Extracts plain readable text from Tiptap/ProseMirror JSON content.
 * Returns blank string if content is not valid JSON or has no text nodes.
 *
 * Tiptap structure:
 * { "type": "doc", "content": [ { "type": "paragraph", "content": [ { "type": "text", "text": "Hello" } ] } ] }
 */
fun extractPlainText(json: String): String {
    if (json.isBlank() || json == "{}") return ""
    return try {
        val sb = StringBuilder()
        extractTextFromJson(org.json.JSONObject(json), sb)
        sb.toString().trim()
    } catch (e: Exception) {
        // Not JSON — return as-is if it looks like plain text, otherwise empty
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
        // Add newline after block elements
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

    var newCommentText by remember { mutableStateOf("") }
    var replyingToCommentId by remember { mutableStateOf<String?>(null) }
    var replyingToAuthor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(slug) {
        viewModel.loadPost(slug)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("جزئیات پست", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            if (post != null) {
                Surface(
                    tonalElevation = 2.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Column {
                        // Replying Indicator
                        if (replyingToCommentId != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "در حال پاسخ به @${replyingToAuthor}",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "انصراف",
                                    fontSize = 13.sp,
                                    color = Color.Red,
                                    modifier = Modifier.clickable {
                                        replyingToCommentId = null
                                        replyingToAuthor = null
                                    }
                                )
                            }
                        }

                        // Input Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            XiloTextField(
                                value = newCommentText,
                                onValueChange = { newCommentText = it },
                                placeholder = "افزودن پاسخ...",
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            IconButton(
                                onClick = {
                                    if (newCommentText.isNotBlank()) {
                                        viewModel.addComment(newCommentText, replyingToCommentId)
                                        newCommentText = ""
                                        replyingToCommentId = null
                                        replyingToAuthor = null
                                    }
                                },
                                enabled = newCommentText.isNotBlank()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Send,
                                    contentDescription = "Send",
                                    tint = if (newCommentText.isNotBlank()) XiloBlue else Color.Gray
                                )
                            }
                        }
                    }
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
            if (post == null && isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (post != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        PostDetailHeader(post = post!!)
                    }

                    item {
                        Divider(
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                            thickness = 0.5.dp,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                        Text(
                            text = "نظرات و پاسخ‌ها",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
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
                                Text("اولین کسی باشید که نظر خود را ثبت می‌کند.", color = Color.Gray)
                            }
                        }
                    } else {
                        items(comments) { comment ->
                            CommentItem(
                                comment = comment,
                                isPostAuthor = comment.authorId == post?.authorId,
                                onReplyClick = { commentId, authorName ->
                                    replyingToCommentId = commentId
                                    replyingToAuthor = authorName
                                },
                                onLikeClick = {
                                    viewModel.toggleCommentLike(comment.id, comment.isLiked)
                                }
                            )
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
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XiloAvatar(imageUrl = post.authorAvatar, size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = post.authorName ?: post.authorUsername,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = "@${post.authorUsername}",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (post.title.isNotBlank()) {
            Text(
                text = post.title,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 26.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        // Render content — backend stores Tiptap JSON, extract readable text
        val displayContent = remember(post.content) {
            extractPlainText(post.content).ifBlank { post.excerpt ?: "" }
        }
        if (displayContent.isNotBlank()) {
            Text(
                text = displayContent,
                fontSize = 16.sp,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        if (!post.coverImageUrl.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            AsyncImage(
                model = post.coverImageUrl,
                contentDescription = "Cover Image",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val dateFormatted = SimpleDateFormat("HH:mm · dd MMM yyyy", Locale.US).format(Date(post.createdAt))
        Text(
            text = dateFormatted,
            color = Color.Gray,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 4.dp)
        )
    }
}

@Composable
fun CommentItem(
    comment: CommentEntity,
    isPostAuthor: Boolean,
    onReplyClick: (String, String) -> Unit,
    onLikeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar with Thread Line
        Box(
            modifier = Modifier
                .width(40.dp)
                .fillMaxHeight(),
            contentAlignment = Alignment.TopCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Draw vertical thread connector line
                drawLine(
                    color = Color.LightGray.copy(alpha = 0.5f),
                    start = Offset(size.width / 2, 40.dp.toPx()),
                    end = Offset(size.width / 2, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }

            XiloAvatar(imageUrl = comment.authorAvatar, size = 32.dp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Telegram-Style Chat Bubble Content
        Column(
            modifier = Modifier.weight(1f)
        ) {
            // Bubble background — post author vs external commenters (REQ-AND-004)
            val bubbleColors = XiloTheme.bubbleColors
            val bubbleBg = if (isPostAuthor) bubbleColors.ownBubble else bubbleColors.othersBubble

            Column(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 0.dp, // Flat corner for chat bubble style
                            topEnd = 16.dp,
                            bottomEnd = 16.dp,
                            bottomStart = 16.dp
                        )
                    )
                    .background(bubbleBg)
                    .padding(12.dp)
            ) {
                // Author name
                Text(
                    text = comment.authorName ?: comment.authorUsername,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isPostAuthor) XiloBlue else Color.DarkGray
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Content
                Text(
                    text = comment.content,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    color = Color.Black
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom row inside bubble (Reactions & Time)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Reactions pills (Telegram style)
                    Row(
                        modifier = Modifier.clickable { onLikeClick() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = (if (comment.isLiked) XiloBlue.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.05f)),
                            modifier = Modifier.height(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("❤️", fontSize = 10.sp)
                                Spacer(modifier = Modifier.width(2.dp))
                                Text(
                                    text = comment.likeCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (comment.isLiked) XiloBlue else Color.Gray
                                )
                            }
                        }
                    }

                    // Timestamp
                    val timeFormatted = SimpleDateFormat("h:mm a", Locale.US).format(Date(comment.createdAt))
                    Text(
                        text = timeFormatted,
                        color = Color.Gray,
                        fontSize = 11.sp
                    )
                }
            }

            // Quick reply action button
            Row(
                modifier = Modifier.padding(top = 4.dp, start = 4.dp)
            ) {
                Text(
                    text = "پاسخ دادن",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = XiloBlue,
                    modifier = Modifier
                        .clickable {
                            onReplyClick(comment.id, comment.authorUsername)
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
    }
}

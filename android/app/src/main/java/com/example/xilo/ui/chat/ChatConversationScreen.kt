package com.example.xilo.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xilo.data.local.entity.MessageEntity
import com.example.xilo.theme.ColorSuccess
import com.example.xilo.theme.XiloBlue
import com.example.xilo.theme.XiloSpacing
import com.example.xilo.ui.components.ChatInput
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.XiloIcon
import com.example.xilo.ui.components.XiloIcons
import com.example.xilo.ui.components.XiloTopAppBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    chatId: String,
    onBackClick: () -> Unit,
    onContactClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel
) {
    val currentChat by viewModel.currentChat.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }

    LaunchedEffect(chatId) {
        viewModel.selectChat(chatId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val chatName = currentChat?.name ?: "گفتگو"
    val isSavedMessages = chatId == "saved"

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = chatName,
                showBack = true,
                onBackClick = onBackClick,
                actions = {
                    if (!isSavedMessages) {
                        androidx.compose.material3.TextButton(onClick = onContactClick) {
                            XiloAvatar(
                                imageUrl = currentChat?.avatarUrl,
                                size = 32.dp
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            ChatInput(
                value = textInput,
                onValueChange = { textInput = it },
                onSend = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendMessage(textInput)
                        textInput = ""
                    }
                },
                placeholder = if (isSavedMessages) "ذخیره پیام..." else "نوشتن پیام..."
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE8F5FE).copy(alpha = 0.5f))
        ) {
            val displayMessages = if (messages.isEmpty()) {
                if (isSavedMessages) {
                    listOf(
                        MessageEntity("1", "saved", "me", "Me", null, "اینجا پیام‌های خودت رو ذخیره کن", null, null, false, true, System.currentTimeMillis() - 86400000),
                        MessageEntity("2", "saved", "me", "Me", null, "فایل‌ها و تصاویرت هم اینجا قابل ذخیره‌اند ✈", null, null, false, true, System.currentTimeMillis() - 3600000)
                    )
                } else {
                    listOf(
                        MessageEntity("1", chatId, "other", "Other", null, "سلام! چطوری؟ پروژه در چه حاله؟", null, null, false, true, System.currentTimeMillis() - 3600000),
                        MessageEntity("2", chatId, "me", "Me", null, "سلام امیر. عالیه! نسخه اندروید کاتلین رو زدم.", null, null, false, true, System.currentTimeMillis() - 1800000),
                        MessageEntity("3", chatId, "other", "Other", null, "کارت فوق‌العاده‌ست! وب‌ساکت هم وصل شد؟", null, null, false, true, System.currentTimeMillis() - 600000),
                        MessageEntity("4", chatId, "me", "Me", null, "آره، همه چیز به خوبی داره هماهنگ کار میکنه 🚀", null, null, false, true, System.currentTimeMillis() - 60000)
                    )
                }
            } else {
                messages
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(XiloSpacing.horizontal)
            ) {
                items(displayMessages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        isMe = msg.senderId == "me" || msg.senderId == viewModel.currentUserId
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bubbleShape = if (isMe) {
        if (isRtl) {
            RoundedCornerShape(
                topStart = XiloSpacing.bubbleRadius,
                topEnd = XiloSpacing.bubbleRadius,
                bottomEnd = XiloSpacing.bubbleRadius,
                bottomStart = 0.dp
            )
        } else {
            RoundedCornerShape(
                topStart = XiloSpacing.bubbleRadius,
                topEnd = XiloSpacing.bubbleRadius,
                bottomEnd = 0.dp,
                bottomStart = XiloSpacing.bubbleRadius
            )
        }
    } else {
        if (isRtl) {
            RoundedCornerShape(
                topStart = XiloSpacing.bubbleRadius,
                topEnd = XiloSpacing.bubbleRadius,
                bottomEnd = 0.dp,
                bottomStart = XiloSpacing.bubbleRadius
            )
        } else {
            RoundedCornerShape(
                topStart = XiloSpacing.bubbleRadius,
                topEnd = XiloSpacing.bubbleRadius,
                bottomEnd = XiloSpacing.bubbleRadius,
                bottomStart = 0.dp
            )
        }
    }

    val bubbleBg = if (isMe) XiloBlue else Color.White
    val contentColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
    val timeColor = if (isMe) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.secondary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .animateContentSize()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.content ?: "",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeFormatted = SimpleDateFormat("h:mm a", Locale("fa")).format(Date(message.createdAt))
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = timeColor
                )

                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    XiloIcon(
                        icon = XiloIcons.MessageTick,
                        contentDescription = "تحویل داده شد",
                        tint = ColorSuccess,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

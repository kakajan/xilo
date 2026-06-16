package com.example.xilo.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xilo.data.local.entity.MessageEntity
import com.example.xilo.theme.XiloBlue
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.XiloTextField
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    chatId: String,
    onBackClick: () -> Unit,
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

    // Scroll to bottom on new messages
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Standard fallback if currentChat hasn't loaded yet
    val chatName = currentChat?.name ?: "گفتگو"
    val isSavedMessages = chatId == "saved"

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isSavedMessages) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(XiloBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("🔖", fontSize = 16.sp)
                            }
                        } else {
                            XiloAvatar(imageUrl = currentChat?.avatarUrl, size = 36.dp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(chatName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(
                                text = if (isSavedMessages) "فضای ذخیره‌سازی ابری" else "آخرین بازدید اخیرا",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }
                    }
                },
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
            Surface(
                tonalElevation = 2.dp,
                modifier = Modifier.navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    XiloTextField(
                        value = textInput,
                        onValueChange = { textInput = it },
                        placeholder = "نوشتن پیام...",
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            if (textInput.isNotBlank()) {
                                viewModel.sendMessage(textInput)
                                textInput = ""
                            }
                        },
                        enabled = textInput.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = if (textInput.isNotBlank()) XiloBlue else Color.Gray
                        )
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
                .background(Color(0xFFE8F5FE).copy(alpha = 0.5f)) // Blue tinted chat wallpaper
        ) {
            if (messages.isEmpty()) {
                // Mock message listing if database is empty
                val mockMessages = if (isSavedMessages) {
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

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(mockMessages) { msg ->
                        MessageBubble(message = msg, isMe = msg.senderId == "me")
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        MessageBubble(message = msg, isMe = msg.senderId == viewModel.currentUserId)
                    }
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
    val bubbleShape = if (isMe) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomEnd = 0.dp, // Flat corner for own bubble
            bottomStart = 16.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomEnd = 16.dp,
            bottomStart = 0.dp // Flat corner for others' bubble
        )
    }

    val bubbleBg = if (isMe) XiloBlue else Color.White
    val contentColor = if (isMe) Color.White else Color.Black
    val timeColor = if (isMe) Color.White.copy(alpha = 0.7f) else Color.Gray

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
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = message.content ?: "",
                color = contentColor,
                fontSize = 15.sp,
                lineHeight = 19.sp
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val timeFormatted = SimpleDateFormat("h:mm a", Locale.US).format(Date(message.createdAt))
                Text(
                    text = timeFormatted,
                    color = timeColor,
                    fontSize = 10.sp
                )
                
                if (isMe) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Delivered",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

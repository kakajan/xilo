package com.example.xilo.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.xilo.data.local.entity.ChatEntity
import com.example.xilo.theme.XiloBlue
import com.example.xilo.ui.components.LocalChromeVisibility
import com.example.xilo.ui.components.XiloAvatar
import com.example.xilo.ui.components.trackChromeVisibility
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel
) {
    val chats by viewModel.chats.collectAsState()
    val categories = listOf("همه گفتگوها", "جدید", "خانواده", "دوستان", "کاری")
    var selectedCategoryIndex by remember { mutableStateOf(0) }
    val chromeState = LocalChromeVisibility.current
    val chatListState = rememberLazyListState()

    // Mock active users / stories matching REQ-MOB-009
    val activeUsers = remember {
        listOf(
            ActiveUserMock("1", "آرش", null, true),
            ActiveUserMock("2", "ثنا", null, true),
            ActiveUserMock("3", "نیما", null, false),
            ActiveUserMock("4", "تارا", null, true),
            ActiveUserMock("5", "پویا", null, false)
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFE8F5FE), // Light blue paper-plane gradient
                        Color(0xFFFFFFFF)
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = chromeState?.isVisible != false,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                CenterAlignedTopAppBar(
                    title = { Text("پیام‌ها (Chats)", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )
            }

            // 1. Stories / Active Users Row
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Add story item
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { }
                    ) {
                        Box(contentAlignment = Alignment.BottomEnd) {
                            XiloAvatar(imageUrl = null, size = 52.dp)
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(XiloBlue),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("+", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("داستان من", fontSize = 11.sp)
                    }
                }

                // Other active users with gradient story rings
                items(activeUsers) { user ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { }
                    ) {
                        XiloAvatar(
                            imageUrl = user.avatar,
                            size = 52.dp,
                            hasStory = user.hasUnseenStory,
                            isOnline = true
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(user.name, fontSize = 11.sp)
                    }
                }
            }

            // 2. Categories / Filter Tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories.size) { index ->
                    val isSelected = selectedCategoryIndex == index
                    val tabColor = if (isSelected) XiloBlue else Color.Transparent
                    val textColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(tabColor)
                            .clickable { selectedCategoryIndex = index }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = categories[index],
                            color = textColor,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Divider(color = Color.Black.copy(alpha = 0.05f), thickness = 0.5.dp)

            // 3. Chat List Items
            if (chats.isEmpty()) {
                // Mock default items if DB is empty to showcase the design
                val mockChats = listOf(
                    ChatEntity("saved", "direct", "پیام‌های ذخیره‌شده", null, "عکسی فرستادم.", System.currentTimeMillis() - 600000, 0, false),
                    ChatEntity("1", "direct", "امیر محمدی", null, "ساعت چند جلسه داریم؟", System.currentTimeMillis() - 3600000, 3, false),
                    ChatEntity("2", "direct", "سارا کریمی", null, "پیش‌نویس نهایی شد 👍", System.currentTimeMillis() - 14400000, 0, false)
                )

                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (chromeState != null) {
                                Modifier.trackChromeVisibility(chromeState, chatListState)
                            } else {
                                Modifier
                            }
                        ),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    items(mockChats) { chat ->
                        ChatListItem(chat = chat, onClick = { onChatClick(chat.id) })
                    }
                }
            } else {
                LazyColumn(
                    state = chatListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (chromeState != null) {
                                Modifier.trackChromeVisibility(chromeState, chatListState)
                            } else {
                                Modifier
                            }
                        ),
                    contentPadding = PaddingValues(bottom = 112.dp)
                ) {
                    items(chats, key = { it.id }) { chat ->
                        ChatListItem(chat = chat, onClick = { onChatClick(chat.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun ChatListItem(
    chat: ChatEntity,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar or specialized Saved Messages icon
        if (chat.id == "saved") {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(XiloBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Bookmark,
                    contentDescription = "Saved Messages",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        } else {
            XiloAvatar(imageUrl = chat.avatarUrl, size = 48.dp)
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Chat info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.name ?: "گفتگو",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                
                val timeFormatted = chat.lastMessageTime?.let {
                    SimpleDateFormat("h:mm a", Locale.US).format(java.util.Date(it))
                } ?: ""
                Text(
                    text = timeFormatted,
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chat.lastMessageContent ?: "",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Indicators: Unread Badge or Double tick read receipt
                if (chat.unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(XiloBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = chat.unreadCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else if (chat.id != "saved") {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Read",
                        tint = Color(0xFF00BA7C),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
    Divider(color = Color.Black.copy(alpha = 0.04f), thickness = 0.5.dp, modifier = Modifier.padding(start = 76.dp))
}

data class ActiveUserMock(
    val id: String,
    val name: String,
    val avatar: String?,
    val hasUnseenStory: Boolean
)

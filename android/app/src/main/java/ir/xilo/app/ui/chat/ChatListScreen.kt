package ir.xilo.app.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.ChatEntity
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.ChromeVisibilityState
import ir.xilo.app.ui.components.LocalChromeVisibility
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.trackChromeVisibility
import ir.xilo.app.core.util.DateFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (chatId: String) -> Unit,
    onNewChatClick: () -> Unit = {},
    onContactsClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel
) {
    val chats by viewModel.filteredChats.collectAsState()
    val folders by viewModel.folders.collectAsState()
    val selectedFolderId by viewModel.selectedFolderId.collectAsState()
    val listMode by viewModel.listMode.collectAsState()
    val savedMessages by viewModel.savedMessages.collectAsState()
    val savedHubLoading by viewModel.savedHubLoading.collectAsState()
    val archivedChats by viewModel.archivedChats.collectAsState()
    var showArchived by remember { mutableStateOf(false) }
    val chromeState = LocalChromeVisibility.current
    val chatListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val allChatsLabel = stringResource(R.string.saved_hub_all_chats)
    val savedChipLabel = stringResource(R.string.saved_hub_chip)

    LaunchedEffect(Unit) {
        viewModel.composerError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val filterChips = remember(folders, allChatsLabel, savedChipLabel) {
        buildList {
            add(FilterChipItem.All(allChatsLabel))
            add(FilterChipItem.Saved(savedChipLabel))
            folders.forEach { add(FilterChipItem.Folder(it.id, it.name)) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .zIndex(2f),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = chromeState?.isVisible != false,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                XiloTopAppBar(
                    title = stringResource(if (showArchived) R.string.chat_list_archived_title else R.string.chat_list_title),
                    centered = true,
                    actions = {
                        if (!showArchived && listMode == ChatListMode.Chats) {
                            IconButton(onClick = onContactsClick) {
                                XiloIcon(
                                    icon = XiloIcons.UserAdd,
                                    contentDescription = stringResource(R.string.cd_contacts),
                                    tint = XiloBlue
                                )
                            }
                            IconButton(onClick = onNewChatClick) {
                                XiloIcon(
                                    icon = XiloIcons.Edit,
                                    contentDescription = stringResource(R.string.cd_new_chat),
                                    tint = XiloBlue
                                )
                            }
                        }
                        if (listMode == ChatListMode.Chats) {
                            IconButton(onClick = { showArchived = !showArchived }) {
                                XiloIcon(
                                    icon = if (showArchived) XiloIcons.Folder else XiloIcons.Archive,
                                    contentDescription = stringResource(R.string.cd_archive),
                                    tint = XiloBlue
                                )
                            }
                        }
                    }
                )
            }

            if (!showArchived) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filterChips.size) { index ->
                        val chip = filterChips[index]
                        val isSelected = when (chip) {
                            is FilterChipItem.All ->
                                listMode == ChatListMode.Chats && selectedFolderId == null
                            is FilterChipItem.Saved -> listMode == ChatListMode.Saved
                            is FilterChipItem.Folder ->
                                listMode == ChatListMode.Chats && selectedFolderId == chip.id
                        }
                        FilterChipPill(
                            label = chip.label,
                            selected = isSelected,
                            onClick = {
                                showArchived = false
                                when (chip) {
                                    is FilterChipItem.All -> viewModel.selectFolder(null)
                                    is FilterChipItem.Saved -> viewModel.openSavedMessagesFilter()
                                    is FilterChipItem.Folder -> viewModel.selectFolder(chip.id)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color.Black.copy(alpha = 0.05f), thickness = 0.5.dp)

            when {
                showArchived -> {
                    ChatListBody(
                        chats = archivedChats,
                        emptyText = stringResource(R.string.chat_list_archived_empty),
                        listState = chatListState,
                        chromeState = chromeState,
                        onChatClick = onChatClick,
                        viewModel = viewModel,
                    )
                }
                listMode == ChatListMode.Saved -> {
                    // Messages tab: saved chat messages only (no post/comment segments).
                    SavedHubBody(
                        segment = SavedHubSegment.Messages,
                        loading = savedHubLoading,
                        messages = savedMessages,
                        posts = emptyList(),
                        comments = emptyList(),
                        listState = chatListState,
                        chromeState = chromeState,
                        onPostClick = {},
                        onCommentClick = { _, _ -> },
                    )
                }
                else -> {
                    ChatListBody(
                        chats = chats,
                        emptyText = stringResource(R.string.chat_list_empty),
                        listState = chatListState,
                        chromeState = chromeState,
                        onChatClick = onChatClick,
                        onNewChatClick = onNewChatClick,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

private sealed interface FilterChipItem {
    val label: String

    data class All(override val label: String) : FilterChipItem
    data class Saved(override val label: String) : FilterChipItem
    data class Folder(val id: String, override val label: String) : FilterChipItem
}

@Composable
private fun FilterChipPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tabColor = if (selected) XiloBlue else Color.Transparent
    val textColor = if (selected) {
        Color.White
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(tabColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun ChatListBody(
    chats: List<ChatEntity>,
    emptyText: String,
    listState: LazyListState,
    chromeState: ChromeVisibilityState?,
    onChatClick: (chatId: String) -> Unit,
    onNewChatClick: () -> Unit = {},
    viewModel: ChatViewModel,
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (chromeState != null) {
                    Modifier.trackChromeVisibility(chromeState, listState)
                } else {
                    Modifier
                }
            ),
        contentPadding = PaddingValues(bottom = XiloSpacing.bottomNavPadding)
    ) {
        if (chats.isEmpty()) {
            item(key = "chat_list_empty") {
                Column(
                    modifier = Modifier
                        .fillParentMaxWidth()
                        .height(320.dp)
                        .padding(horizontal = 32.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = emptyText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onNewChatClick) {
                        XiloIcon(
                            icon = XiloIcons.Edit,
                            contentDescription = null,
                            tint = XiloBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.chat_new_cta),
                            color = XiloBlue,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        } else {
            items(chats, key = { it.id }) { chat ->
                ChatListItem(
                    chat = chat,
                    onClick = { onChatClick(chat.id) },
                    onArchiveToggle = {
                        if (chat.isArchived) viewModel.unarchiveChat(chat.id) else viewModel.archiveChat(chat.id)
                    },
                    onDelete = if (chat.type == "saved") {
                        null
                    } else {
                        { viewModel.deleteChat(chat.id) }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatListItem(
    chat: ChatEntity,
    onClick: () -> Unit,
    onArchiveToggle: () -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    var isActionsVisible by remember { mutableStateOf(false) }
    val chatTitle = chat.name ?: stringResource(R.string.chat_default_title)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isActionsVisible) {
                            isActionsVisible = false
                        } else {
                            onClick()
                        }
                    },
                    onLongClick = {
                        isActionsVisible = !isActionsVisible
                    }
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            XiloAvatar(imageUrl = chat.avatarUrl, size = 48.dp)

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chatTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    val timeFormatted = chat.lastMessageTime?.let {
                        DateFormatter.formatTime(it)
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

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                        } else {
                            XiloIcon(
                                icon = XiloIcons.MessageTickBold,
                                contentDescription = stringResource(R.string.chat_list_read),
                                tint = Color(0xFF00BA7C),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = { isActionsVisible = !isActionsVisible },
                            modifier = Modifier.size(24.dp)
                        ) {
                            XiloIcon(
                                icon = XiloIcons.MoreHorizontal,
                                contentDescription = stringResource(R.string.cd_archive),
                                tint = Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = isActionsVisible,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp, start = 76.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = {
                        onArchiveToggle()
                        isActionsVisible = false
                    }
                ) {
                    XiloIcon(
                        icon = if (chat.isArchived) XiloIcons.Folder else XiloIcons.Archive,
                        contentDescription = stringResource(R.string.cd_archive),
                        tint = XiloBlue,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(if (chat.isArchived) R.string.chat_list_unarchive else R.string.chat_list_archive), color = XiloBlue, fontSize = 12.sp)
                }

                if (onDelete != null) {
                    TextButton(
                        onClick = {
                            onDelete()
                            isActionsVisible = false
                        }
                    ) {
                        XiloIcon(
                            icon = XiloIcons.Close,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = Color.Red,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.chat_list_delete), color = Color.Red, fontSize = 12.sp)
                    }
                }
            }
        }
        HorizontalDivider(
            color = Color.Black.copy(alpha = 0.04f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(start = 76.dp)
        )
    }
}

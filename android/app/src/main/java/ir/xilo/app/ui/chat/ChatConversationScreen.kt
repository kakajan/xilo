package ir.xilo.app.ui.chat

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.MessageDeliveryState
import ir.xilo.app.data.local.entity.MessageEntity
import ir.xilo.app.data.local.entity.displayAvatarUrl
import ir.xilo.app.data.local.entity.displayTitle
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.theme.XiloTheme
import ir.xilo.app.ui.components.ChatInput
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.core.util.DateFormatter

private const val ChatEditWindowMs = 48L * 60L * 60L * 1000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatConversationScreen(
    chatId: String,
    onBackClick: () -> Unit,
    onContactClick: () -> Unit = {},
    isSavedMessages: Boolean = false,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel
) {
    val currentChat by viewModel.currentChat.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val peerTyping by viewModel.peerTyping.collectAsState()
    val peerOnline by viewModel.peerOnline.collectAsState()
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }
    val editingMessage by viewModel.editingMessage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sendFailureMessage = stringResource(R.string.chat_send_failed)

    var wasEditing by remember { mutableStateOf(false) }
    LaunchedEffect(editingMessage) {
        val editing = editingMessage
        if (editing != null) {
            textInput = editing.content.orEmpty()
            wasEditing = true
        } else if (wasEditing) {
            textInput = ""
            wasEditing = false
        }
    }

    val showAsSavedMessages = isSavedMessages ||
        currentChat?.type == "saved" ||
        chatId == "saved"

    if (showAsSavedMessages) {
        SavedMessagesScreen(
            chatId = chatId,
            onBackClick = onBackClick,
            modifier = modifier,
            viewModel = viewModel
        )
        return
    }

    LaunchedEffect(chatId) {
        viewModel.selectChat(chatId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(viewModel, sendFailureMessage) {
        viewModel.sendEvents.collect { event ->
            if (event is ChatSendEvent.Failed) {
                if (textInput.isBlank() && event.draft != null) {
                    textInput = event.draft
                }
                snackbarHostState.showSnackbar(sendFailureMessage)
            }
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.composerError.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val defaultTitle = stringResource(R.string.chat_default_title)
    val savedTitle = stringResource(R.string.saved_messages_title)
    val chatName = currentChat?.displayTitle(
        fallback = defaultTitle,
        savedTitle = savedTitle,
    ) ?: if (isSavedMessages) savedTitle else defaultTitle
    val statusText = when {
        peerTyping -> stringResource(R.string.chat_typing)
        peerOnline == true -> stringResource(R.string.chat_online)
        peerOnline == false -> stringResource(R.string.chat_offline)
        else -> null
    }

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = {
                    Column {
                        Text(
                            text = chatName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (statusText != null) {
                            Text(
                                text = statusText,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (peerTyping) {
                                    XiloBlue
                                } else {
                                    MaterialTheme.colorScheme.secondary
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        XiloIcon(
                            icon = XiloIcons.Back,
                            contentDescription = "بازگشت"
                        )
                    }
                },
                actions = {
                    androidx.compose.material3.TextButton(onClick = onContactClick) {
                        XiloAvatar(
                            imageUrl = currentChat?.displayAvatarUrl(),
                            size = 32.dp
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (editingMessage != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                            .padding(horizontal = XiloSpacing.horizontal, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        XiloIcon(
                            icon = XiloIcons.Edit,
                            contentDescription = null,
                            tint = XiloBlue,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.chat_editing_banner),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(
                            onClick = {
                                viewModel.cancelEditMessage()
                                textInput = ""
                                viewModel.onComposerTextChanged("")
                            },
                        ) {
                            Text(stringResource(R.string.chat_edit_cancel))
                        }
                    }
                }
                ChatInput(
                    value = textInput,
                    onValueChange = {
                        textInput = it
                        if (editingMessage == null) {
                            viewModel.onComposerTextChanged(it)
                        }
                    },
                    onSend = {
                        if (textInput.isBlank()) return@ChatInput
                        if (editingMessage != null) {
                            viewModel.commitEditMessage(textInput)
                        } else {
                            viewModel.sendMessage(textInput)
                            textInput = ""
                            viewModel.onComposerTextChanged("")
                        }
                    },
                    onSendImage = { uri, caption ->
                        if (editingMessage != null) return@ChatInput
                        viewModel.sendImageMessage(uri, caption)
                        textInput = ""
                        viewModel.onComposerTextChanged("")
                    },
                    showAttach = editingMessage == null,
                    placeholder = stringResource(R.string.chat_message_placeholder),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(XiloSpacing.horizontal)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isMe = msg.senderId == "me" || msg.senderId == viewModel.currentUserId
                    MessageBubble(
                        message = msg,
                        isMe = isMe,
                        onRetry = {
                            msg.clientOperationKey?.let(viewModel::retryMessage)
                        },
                        onDeleteFailed = {
                            msg.clientOperationKey?.let(viewModel::deleteFailedMessage)
                        },
                        onEdit = if (isMe) {
                            {
                                viewModel.beginEditMessage(msg)
                            }
                        } else {
                            null
                        },
                        onDeleteDelivered = if (isMe) {
                            { viewModel.deleteDeliveredMessage(msg.id) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    onRetry: () -> Unit = {},
    onDeleteFailed: () -> Unit = {},
    onEdit: (() -> Unit)? = null,
    onDeleteDelivered: (() -> Unit)? = null,
) {
    // Chat sides follow Telegram physical alignment (own = right), independent of app RTL.
    val contentLayoutDirection = LocalLayoutDirection.current
    val isDeleted = message.isDeleted
    val isPending = !isDeleted && message.deliveryState == MessageDeliveryState.PENDING
    val isFailed = !isDeleted && message.deliveryState == MessageDeliveryState.PERMANENT_FAILURE
    val isDelivered = !isDeleted &&
        !isPending &&
        !isFailed &&
        message.deliveryState == MessageDeliveryState.DELIVERED &&
        !message.id.startsWith("local-")
    val withinEditWindow = System.currentTimeMillis() - message.createdAt < ChatEditWindowMs
    val canEdit = isMe && isDelivered && withinEditWindow && onEdit != null &&
        !message.content.isNullOrBlank()
    val canDeleteDelivered = isMe && isDelivered && onDeleteDelivered != null
    var menuExpanded by remember(message.id) { mutableStateOf(false) }
    val deletedLabel = stringResource(R.string.chat_message_deleted)
    val deliveryStateDescription = when {
        isDeleted -> stringResource(R.string.chat_message_deleted_accessibility)
        isPending -> stringResource(R.string.chat_message_state_pending_accessibility)
        isFailed -> stringResource(R.string.chat_message_state_failed_accessibility)
        else -> stringResource(R.string.chat_message_state_sent_accessibility)
    }
    // LTR corner tails: own bubble points bottom-end (physical right), peer bottom-start.
    val bubbleShape = if (isMe) {
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

    val bubbleColors = XiloTheme.bubbleColors
    val bubbleBg = when {
        isDeleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        isFailed -> MaterialTheme.colorScheme.errorContainer
        isMe -> XiloBlue
        else -> bubbleColors.othersBubble
    }
    val contentColor = when {
        isDeleted -> MaterialTheme.colorScheme.onSurfaceVariant
        isFailed -> MaterialTheme.colorScheme.onErrorContainer
        isMe -> Color.White
        else -> MaterialTheme.colorScheme.onSurface
    }
    val timeColor = when {
        isDeleted -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
        isFailed -> MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
        isMe -> Color.White.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.secondary
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
    CompositionLocalProvider(LocalLayoutDirection provides contentLayoutDirection) {
        Box {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(bubbleShape)
                .background(bubbleBg)
                .animateContentSize()
                .then(
                    if (canEdit || canDeleteDelivered) {
                        Modifier.combinedClickable(
                            onClick = {},
                            onLongClick = { menuExpanded = true },
                        )
                    } else {
                        Modifier
                    }
                )
                .semantics {
                    if (isMe) {
                        stateDescription = deliveryStateDescription
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            val senderLabel = message.senderName?.trim()?.takeIf { it.isNotEmpty() }
            if (!isMe && !isDeleted && senderLabel != null) {
                Text(
                    text = senderLabel,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = XiloBlue,
                    maxLines = 1,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            Text(
                text = if (isDeleted) deletedLabel else (message.content ?: ""),
                style = if (isDeleted) {
                    MaterialTheme.typography.bodyMedium
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = contentColor
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isDeleted && message.isEdited) {
                    Text(
                        text = stringResource(R.string.chat_message_edited),
                        style = MaterialTheme.typography.labelSmall,
                        color = timeColor,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "·",
                        style = MaterialTheme.typography.labelSmall,
                        color = timeColor,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                val timeFormatted = DateFormatter.formatTime(message.createdAt)
                Text(
                    text = timeFormatted,
                    style = MaterialTheme.typography.labelSmall,
                    color = timeColor
                )

                if (isDeleted) {
                    // Tombstone: no delivery ticks.
                } else if (isMe && isPending) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XiloIcon(
                            icon = XiloIcons.CloudOff,
                            contentDescription = null,
                            tint = timeColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.chat_message_pending),
                            style = MaterialTheme.typography.labelSmall,
                            color = timeColor
                        )
                    }
                } else if (isMe && !isFailed) {
                    Spacer(modifier = Modifier.width(4.dp))
                    XiloIcon(
                        icon = XiloIcons.MessageTick,
                        contentDescription = stringResource(R.string.chat_message_sent),
                        tint = timeColor,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (isMe && isFailed) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    XiloIcon(
                        icon = XiloIcons.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.chat_message_failed),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val retryDescription =
                        stringResource(R.string.chat_message_retry_accessibility)
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = retryDescription
                            }
                    ) {
                        XiloIcon(
                            icon = XiloIcons.Repeat,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.chat_message_retry))
                    }
                    val deleteDescription =
                        stringResource(R.string.chat_message_delete_accessibility)
                    TextButton(
                        onClick = onDeleteFailed,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics {
                                contentDescription = deleteDescription
                            }
                    ) {
                        XiloIcon(
                            icon = XiloIcons.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.chat_message_delete))
                    }
                }
            }
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
        ) {
            if (canEdit) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_message_edit)) },
                    onClick = {
                        menuExpanded = false
                        onEdit?.invoke()
                    },
                    leadingIcon = {
                        XiloIcon(
                            icon = XiloIcons.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }
            if (canDeleteDelivered) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chat_message_delete)) },
                    onClick = {
                        menuExpanded = false
                        onDeleteDelivered?.invoke()
                    },
                    leadingIcon = {
                        XiloIcon(
                            icon = XiloIcons.Trash,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
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

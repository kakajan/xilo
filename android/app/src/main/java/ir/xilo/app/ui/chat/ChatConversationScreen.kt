package ir.xilo.app.ui.chat

import androidx.compose.animation.animateContentSize
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
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.ChatInput
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.core.util.DateFormatter

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
    val snackbarHostState = remember { SnackbarHostState() }
    val sendFailureMessage = stringResource(R.string.chat_send_failed)

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

    val chatName = currentChat?.name ?: stringResource(R.string.chat_default_title)
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
                            imageUrl = currentChat?.avatarUrl,
                            size = 32.dp
                        )
                    }
                }
            )
        },
        bottomBar = {
            ChatInput(
                value = textInput,
                onValueChange = {
                    textInput = it
                    viewModel.onComposerTextChanged(it)
                },
                onSend = {
                    if (textInput.isNotBlank()) {
                        viewModel.sendMessage(textInput)
                        textInput = ""
                        viewModel.onComposerTextChanged("")
                    }
                },
                placeholder = stringResource(R.string.chat_message_placeholder)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFFE8F5FE).copy(alpha = 0.5f))
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(XiloSpacing.horizontal)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(
                        message = msg,
                        isMe = msg.senderId == "me" || msg.senderId == viewModel.currentUserId,
                        onRetry = {
                            msg.clientOperationKey?.let(viewModel::retryMessage)
                        },
                        onDelete = {
                            msg.clientOperationKey?.let(viewModel::deleteFailedMessage)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    onRetry: () -> Unit = {},
    onDelete: () -> Unit = {}
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val isDeleted = message.isDeleted
    val isPending = !isDeleted && message.deliveryState == MessageDeliveryState.PENDING
    val isFailed = !isDeleted && message.deliveryState == MessageDeliveryState.PERMANENT_FAILURE
    val deletedLabel = stringResource(R.string.chat_message_deleted)
    val deliveryStateDescription = when {
        isDeleted -> stringResource(R.string.chat_message_deleted_accessibility)
        isPending -> stringResource(R.string.chat_message_state_pending_accessibility)
        isFailed -> stringResource(R.string.chat_message_state_failed_accessibility)
        else -> stringResource(R.string.chat_message_state_sent_accessibility)
    }
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

    val bubbleBg = when {
        isDeleted -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        isFailed -> MaterialTheme.colorScheme.errorContainer
        isMe -> XiloBlue
        else -> Color.White
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
                .semantics {
                    if (isMe) {
                        stateDescription = deliveryStateDescription
                    }
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
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
                        onClick = onDelete,
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
    }
}

package ir.xilo.app.ui.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.core.util.DateFormatter
import ir.xilo.app.data.repository.NotificationDeepLink
import ir.xilo.app.data.repository.NotificationItem
import ir.xilo.app.data.repository.NotificationRepository
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationInboxUiState(
    val notifications: List<NotificationItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
)

sealed interface NotificationInboxNavEvent {
    data class PostDetail(val slug: String, val commentId: String?) : NotificationInboxNavEvent
    data class ChatConversation(val chatId: String) : NotificationInboxNavEvent
    data class Profile(val username: String) : NotificationInboxNavEvent
}

@HiltViewModel
class NotificationInboxViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationInboxUiState())
    val uiState: StateFlow<NotificationInboxUiState> = _uiState.asStateFlow()

    val unreadCount: StateFlow<Int> = notificationRepository.unreadCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    private val _navEvents = MutableSharedFlow<NotificationInboxNavEvent>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<NotificationInboxNavEvent> = _navEvents.asSharedFlow()

    init {
        refresh(showLoading = true)
        viewModelScope.launch {
            notificationRepository.inboxInvalidated.collect { invalidated ->
                if (invalidated) {
                    refresh(showLoading = false)
                    notificationRepository.consumeInboxInvalidation()
                }
            }
        }
    }

    fun refresh(showLoading: Boolean = false) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = showLoading && it.notifications.isEmpty(),
                    isRefreshing = !showLoading || it.notifications.isNotEmpty(),
                    errorMessage = null,
                )
            }
            notificationRepository.refreshInbox()
                .onSuccess { items ->
                    _uiState.update {
                        it.copy(
                            notifications = items,
                            isLoading = false,
                            isRefreshing = false,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            errorMessage = errorMessageResolver.fromThrowable(error, R.string.error_unknown),
                        )
                    }
                }
            notificationRepository.refreshUnreadCount()
        }
    }

    fun markAllRead() {
        viewModelScope.launch {
            notificationRepository.markAllRead()
                .onSuccess { refresh(showLoading = false) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            errorMessage = errorMessageResolver.fromThrowable(error, R.string.error_unknown),
                        )
                    }
                }
        }
    }

    fun onNotificationClick(item: NotificationItem) {
        viewModelScope.launch {
            if (!item.isRead) {
                notificationRepository.markRead(item.id)
            }
            when (val link = notificationRepository.resolveDeepLink(item)) {
                is NotificationDeepLink.PostDetail -> {
                    _navEvents.emit(
                        NotificationInboxNavEvent.PostDetail(
                            slug = link.slug,
                            commentId = link.commentId,
                        )
                    )
                }
                is NotificationDeepLink.ChatConversation -> {
                    _navEvents.emit(NotificationInboxNavEvent.ChatConversation(link.chatId))
                }
                is NotificationDeepLink.Profile -> {
                    _navEvents.emit(NotificationInboxNavEvent.Profile(link.username))
                }
                NotificationDeepLink.Inbox -> Unit
                NotificationDeepLink.None -> Unit
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

@Composable
fun NotificationInboxScreen(
    onBackClick: () -> Unit,
    onPostClick: (slug: String, commentId: String?) -> Unit,
    onChatClick: (chatId: String) -> Unit,
    onProfileClick: (username: String) -> Unit,
    onPreferencesClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: NotificationInboxViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val pullRefreshState = rememberPullToRefreshState()

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is NotificationInboxNavEvent.PostDetail ->
                    onPostClick(event.slug, event.commentId)
                is NotificationInboxNavEvent.ChatConversation ->
                    onChatClick(event.chatId)
                is NotificationInboxNavEvent.Profile ->
                    onProfileClick(event.username)
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            XiloTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.notifications_inbox_title),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        XiloIcon(
                            icon = XiloIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = onPreferencesClick) {
                        XiloIcon(
                            icon = XiloIcons.Settings,
                            contentDescription = stringResource(R.string.notifications_prefs_title),
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    if (uiState.notifications.any { !it.isRead }) {
                        TextButton(onClick = viewModel::markAllRead) {
                            Text(stringResource(R.string.notifications_mark_all_read))
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                uiState.notifications.isEmpty() && !uiState.isRefreshing -> {
                    Text(
                        text = stringResource(R.string.notifications_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                else -> {
                    PullToRefreshBox(
                        isRefreshing = uiState.isRefreshing,
                        onRefresh = { viewModel.refresh(showLoading = false) },
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(uiState.notifications, key = { it.id }) { item ->
                                NotificationRow(
                                    item = item,
                                    timeLabel = DateFormatter.getRelativeTimeSpan(context, item.createdAtMs),
                                    onClick = { viewModel.onNotificationClick(item) },
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
private fun NotificationRow(
    item: NotificationItem,
    timeLabel: String,
    onClick: () -> Unit,
) {
    val background = if (item.isRead) {
        MaterialTheme.colorScheme.background
    } else {
        XiloBlue.copy(alpha = 0.06f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(background)
            .padding(horizontal = XiloSpacing.horizontal, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        XiloIcon(
            icon = XiloIcons.Notification,
            contentDescription = null,
            tint = if (item.isRead) MaterialTheme.colorScheme.secondary else XiloBlue,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = timeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            if (item.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!item.isRead) {
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(XiloBlue.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.notifications_unread_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = XiloBlue,
                    )
                }
            }
        }
    }
}

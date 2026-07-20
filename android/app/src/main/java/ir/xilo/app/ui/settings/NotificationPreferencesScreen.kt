package ir.xilo.app.ui.settings

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.remote.dto.NotificationPreferencesResponse
import ir.xilo.app.data.repository.NotificationRepository
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.push.RequestNotificationPermissionEffect
import ir.xilo.app.util.ErrorMessageResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotificationPrefRow(
    val apiKey: String,
    val labelRes: Int,
    val groupRes: Int? = null,
)

data class NotificationPreferencesUiState(
    val prefs: NotificationPreferencesResponse? = null,
    val isLoading: Boolean = false,
    val updatingKey: String? = null,
    val errorMessage: String? = null,
)

@HiltViewModel
class NotificationPreferencesViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val errorMessageResolver: ErrorMessageResolver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotificationPreferencesUiState())
    val uiState: StateFlow<NotificationPreferencesUiState> = _uiState.asStateFlow()

    val prefRows: List<NotificationPrefRow> = listOf(
        NotificationPrefRow("comment_reply_web", R.string.notif_pref_comment_reply_inapp, R.string.notif_pref_group_comments),
        NotificationPrefRow("comment_reply_push", R.string.notif_pref_comment_reply_push),
        NotificationPrefRow("comment_mention_web", R.string.notif_pref_comment_mention_inapp),
        NotificationPrefRow("post_reaction_web", R.string.notif_pref_post_reaction_inapp, R.string.notif_pref_group_posts),
        NotificationPrefRow("post_published_web", R.string.notif_pref_post_published_inapp),
        NotificationPrefRow("post_published_push", R.string.notif_pref_post_published_push),
        NotificationPrefRow("new_follower_web", R.string.notif_pref_new_follower_inapp, R.string.notif_pref_group_social),
        NotificationPrefRow("new_follower_push", R.string.notif_pref_new_follower_push),
        NotificationPrefRow("new_message_web", R.string.notif_pref_new_message_inapp, R.string.notif_pref_group_messages),
        NotificationPrefRow("new_message_push", R.string.notif_pref_new_message_push),
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            notificationRepository.getPreferences()
                .onSuccess { prefs ->
                    _uiState.update { it.copy(isLoading = false, prefs = prefs) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = errorMessageResolver.fromThrowable(error, R.string.error_unknown),
                        )
                    }
                }
        }
    }

    fun toggle(apiKey: String, enabled: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(updatingKey = apiKey, errorMessage = null) }
            notificationRepository.updatePreference(apiKey, enabled)
                .onSuccess {
                    refresh()
                    _uiState.update { it.copy(updatingKey = null) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            updatingKey = null,
                            errorMessage = errorMessageResolver.fromThrowable(error, R.string.error_unknown),
                        )
                    }
                }
        }
    }

    fun isEnabled(prefs: NotificationPreferencesResponse, apiKey: String): Boolean {
        return when (apiKey) {
            "comment_reply_web" -> prefs.commentReplyWeb
            "comment_reply_push" -> prefs.commentReplyPush
            "comment_mention_web" -> prefs.commentMentionWeb
            "post_reaction_web" -> prefs.postReactionWeb
            "post_published_web" -> prefs.postPublishedWeb
            "post_published_push" -> prefs.postPublishedPush
            "new_follower_web" -> prefs.newFollowerWeb
            "new_follower_push" -> prefs.newFollowerPush
            "new_message_web" -> prefs.newMessageWeb
            "new_message_push" -> prefs.newMessagePush
            else -> true
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}

@Composable
fun NotificationPreferencesScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationPreferencesViewModel = hiltViewModel(),
) {
    RequestNotificationPermissionEffect(enabled = true)

    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            XiloTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        XiloIcon(
                            icon = XiloIcons.Notification,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = stringResource(R.string.notifications_prefs_title),
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBackClick) {
                        XiloIcon(
                            icon = XiloIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                        )
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
            val prefs = uiState.prefs
            when {
                uiState.isLoading && prefs == null -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                prefs == null -> {
                    Text(
                        text = stringResource(R.string.notifications_prefs_empty),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        items(viewModel.prefRows, key = { it.apiKey }) { row ->
                            if (row.groupRes != null) {
                                Text(
                                    text = stringResource(row.groupRes),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(
                                        horizontal = XiloSpacing.horizontal,
                                        vertical = 12.dp,
                                    ),
                                )
                            }
                            PrefToggleRow(
                                label = stringResource(row.labelRes),
                                checked = viewModel.isEnabled(prefs, row.apiKey),
                                enabled = uiState.updatingKey == null,
                                onCheckedChange = { enabled ->
                                    viewModel.toggle(row.apiKey, enabled)
                                },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = XiloSpacing.horizontal),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(96.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrefToggleRow(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = XiloSpacing.horizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
        )
    }
}

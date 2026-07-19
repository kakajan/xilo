package ir.xilo.app.ui.contacts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.remote.dto.ContactUserDto
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.VerifiedBadge
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTextField
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.ui.components.forUsernameHandle
import ir.xilo.app.ui.components.usernameHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBackClick: () -> Unit,
    onProfileClick: (username: String) -> Unit,
    onChatStarted: (chatId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ContactsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onContactsPermissionResult(granted)
    }

    LaunchedEffect(viewModel) {
        viewModel.openChatId.collect(onChatStarted)
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(uiState.needsContactsPermission) {
        if (!uiState.needsContactsPermission) return@LaunchedEffect
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.clearNeedsPermission()
            viewModel.syncContacts()
        } else {
            permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            viewModel.clearNeedsPermission()
        }
    }

    val filtered = remember(uiState.contacts, uiState.query) {
        val needle = uiState.query.trim().removePrefix("@").lowercase()
        if (needle.isBlank()) {
            uiState.contacts
        } else {
            uiState.contacts.filter {
                it.username.lowercase().contains(needle) ||
                    it.displayName.lowercase().contains(needle)
            }
        }
    }

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = stringResource(R.string.contacts_title),
                showBack = true,
                onBackClick = onBackClick,
                actions = {
                    IconButton(
                        onClick = viewModel::requestSyncContacts,
                        enabled = !uiState.isSyncing,
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = XiloBlue,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            XiloIcon(
                                icon = XiloIcons.UserAdd,
                                contentDescription = stringResource(R.string.contacts_sync),
                                tint = XiloBlue,
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White),
        ) {
            Text(
                text = stringResource(R.string.contacts_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(
                    horizontal = XiloSpacing.horizontal,
                    vertical = 4.dp,
                ),
            )

            XiloTextField(
                value = uiState.query,
                onValueChange = viewModel::updateQuery,
                placeholder = stringResource(R.string.contacts_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = XiloSpacing.horizontal, vertical = 8.dp),
            )

            HorizontalDivider(color = Color.Black.copy(alpha = 0.06f), thickness = 0.5.dp)

            PullToRefreshBox(
                isRefreshing = uiState.isRefreshing || uiState.isSyncing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = XiloBlue)
                        }
                    }
                    uiState.isStartingChat -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = XiloBlue)
                        }
                    }
                    filtered.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = XiloSpacing.horizontal, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = if (uiState.contacts.isEmpty()) {
                                    stringResource(R.string.contacts_empty)
                                } else {
                                    stringResource(R.string.contacts_empty_search)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                            if (uiState.contacts.isEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.contacts_sync_hint),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 24.dp),
                        ) {
                            items(filtered, key = { it.id }) { contact ->
                                ContactRow(
                                    contact = contact,
                                    onProfileClick = { onProfileClick(contact.username) },
                                    onMessageClick = { viewModel.startChat(contact) },
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
private fun ContactRow(
    contact: ContactUserDto,
    onProfileClick: () -> Unit,
    onMessageClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onProfileClick)
            .padding(horizontal = XiloSpacing.horizontal, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XiloAvatar(
            imageUrl = contact.avatarUrl,
            size = 48.dp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = contact.displayName.ifBlank { contact.username },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (contact.isVerified) {
                    VerifiedBadge(size = 16.dp)
                }
                if (contact.fromContacts) {
                    Text(
                        text = stringResource(R.string.contacts_from_badge),
                        color = XiloBlue,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(XiloBlue.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            Text(
                text = usernameHandle(contact.username),
                style = MaterialTheme.typography.bodySmall.forUsernameHandle(),
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onMessageClick) {
            XiloIcon(
                icon = XiloIcons.Message,
                contentDescription = stringResource(R.string.contacts_message),
                tint = XiloBlue,
            )
        }
        IconButton(onClick = onProfileClick) {
            XiloIcon(
                icon = XiloIcons.User,
                contentDescription = stringResource(R.string.contacts_profile),
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

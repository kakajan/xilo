package ir.xilo.app.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloTextField
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.ui.components.forUsernameHandle
import ir.xilo.app.ui.components.usernameHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupInfoScreen(
    chatId: String,
    onBackClick: () -> Unit,
    onLeftGroup: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GroupInfoViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(chatId) {
        viewModel.load(chatId)
    }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is GroupInfoEvent.Message -> snackbarHostState.showSnackbar(event.text)
                GroupInfoEvent.Left -> onLeftGroup()
            }
        }
    }

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = stringResource(R.string.chat_group_info_title),
                showBack = true,
                onBackClick = onBackClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.White,
        modifier = modifier,
    ) { innerPadding ->
        if (state.loading && state.chat == null) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(innerPadding)
                    .padding(32.dp),
            )
            return@Scaffold
        }
        val chat = state.chat
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = XiloSpacing.horizontal, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    XiloAvatar(imageUrl = chat?.avatarUrl, size = 88.dp)
                    Spacer(modifier = Modifier.height(12.dp))
                    if (state.isAdmin) {
                        XiloTextField(
                            value = state.editName,
                            onValueChange = viewModel::updateEditName,
                            placeholder = stringResource(R.string.chat_new_group_name_placeholder),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::saveName,
                            enabled = !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(stringResource(R.string.chat_group_save_name))
                        }
                    } else {
                        Text(
                            text = chat?.name.orEmpty(),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.chat_group_members_count,
                            state.members.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = XiloSpacing.horizontal, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(R.string.chat_group_mute))
                    Switch(
                        checked = chat?.isMuted == true,
                        onCheckedChange = viewModel::setMuted,
                        enabled = !state.busy,
                    )
                }
                HorizontalDivider()
            }

            if (state.isAdmin) {
                item {
                    Column(
                        modifier = Modifier.padding(
                            horizontal = XiloSpacing.horizontal,
                            vertical = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.chat_group_invite),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!state.inviteToken.isNullOrBlank()) {
                            Text(
                                text = state.inviteToken.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            OutlinedButton(
                                onClick = viewModel::revokeInvite,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.chat_group_revoke_invite))
                            }
                        } else {
                            Button(
                                onClick = viewModel::createInvite,
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(stringResource(R.string.chat_group_create_invite))
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }

            if (state.pins.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.chat_group_pins),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(
                            horizontal = XiloSpacing.horizontal,
                            vertical = 12.dp,
                        ),
                    )
                }
                items(state.pins, key = { it.messageId }) { pin ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = XiloSpacing.horizontal, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = pin.content?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.chat_group_pinned_message),
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.isAdmin) {
                            TextButton(onClick = { viewModel.unpin(pin.messageId) }) {
                                Text(stringResource(R.string.chat_group_unpin))
                            }
                        }
                    }
                }
                item { HorizontalDivider() }
            }

            item {
                Text(
                    text = stringResource(R.string.chat_group_members),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        horizontal = XiloSpacing.horizontal,
                        vertical = 12.dp,
                    ),
                )
            }
            items(state.members, key = { it.userId }) { member ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = XiloSpacing.horizontal, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    XiloAvatar(imageUrl = member.avatarUrl, size = 40.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = member.displayName.ifBlank { member.username },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = usernameHandle(member.username),
                            style = MaterialTheme.typography.bodySmall.forUsernameHandle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (member.role == "admin") {
                            Text(
                                text = stringResource(R.string.chat_group_role_admin),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (state.isAdmin && member.userId != state.selfId) {
                        Column(horizontalAlignment = Alignment.End) {
                            TextButton(
                                onClick = {
                                    viewModel.setRole(
                                        member.userId,
                                        if (member.role == "admin") "member" else "admin",
                                    )
                                },
                            ) {
                                Text(
                                    if (member.role == "admin") {
                                        stringResource(R.string.chat_group_demote)
                                    } else {
                                        stringResource(R.string.chat_group_promote)
                                    },
                                )
                            }
                            TextButton(onClick = { viewModel.removeMember(member.userId) }) {
                                Text(stringResource(R.string.chat_group_remove))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = viewModel::leaveGroup,
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = XiloSpacing.horizontal),
                ) {
                    Text(stringResource(R.string.chat_group_leave))
                }
            }
        }
    }
}

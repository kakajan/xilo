package ir.xilo.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
fun NewGroupScreen(
    onBackClick: () -> Unit,
    onGroupCreated: (chatId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewGroupViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsState()
    val query by viewModel.query.collectAsState()
    val contacts by viewModel.contacts.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val groupName by viewModel.groupName.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCreating by viewModel.isCreating.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.openChatId.collect(onGroupCreated)
    }
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = stringResource(
                    if (step == NewGroupStep.Name) {
                        R.string.chat_new_group_name_title
                    } else {
                        R.string.chat_new_group_title
                    },
                ),
                showBack = true,
                onBackClick = {
                    if (step == NewGroupStep.Name) {
                        viewModel.backToMembers()
                    } else {
                        onBackClick()
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.White,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.White),
        ) {
            when (step) {
                NewGroupStep.Members -> MembersStep(
                    query = query,
                    contacts = contacts,
                    selectedIds = selectedIds,
                    isLoading = isLoading,
                    isCreating = isCreating,
                    onQueryChange = viewModel::updateQuery,
                    onToggle = viewModel::toggleMember,
                    onNext = viewModel::goToNameStep,
                )

                NewGroupStep.Name -> NameStep(
                    groupName = groupName,
                    selectedCount = selectedIds.size,
                    isCreating = isCreating,
                    onNameChange = viewModel::updateGroupName,
                    onCreate = viewModel::createGroup,
                )
            }
        }
    }
}

@Composable
private fun ColumnScope.MembersStep(
    query: String,
    contacts: List<NewChatContact>,
    selectedIds: Set<String>,
    isLoading: Boolean,
    isCreating: Boolean,
    onQueryChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onNext: () -> Unit,
) {
    XiloTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = stringResource(R.string.chat_new_search_placeholder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = XiloSpacing.horizontal, vertical = 12.dp),
    )
    if (isLoading) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 88.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
        ) {
            items(contacts, key = { it.id }) { contact ->
                val selected = contact.id in selectedIds
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggle(contact.id) }
                        .padding(
                            horizontal = XiloSpacing.horizontal,
                            vertical = 10.dp,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = selected,
                        onCheckedChange = { onToggle(contact.id) },
                    )
                    XiloAvatar(imageUrl = contact.avatarUrl, size = 44.dp)
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .fillMaxWidth(),
                    ) {
                        Text(
                            text = contact.displayName?.takeIf { it.isNotBlank() }
                                ?: usernameHandle(contact.username),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = usernameHandle(contact.username),
                            style = MaterialTheme.typography.bodySmall.forUsernameHandle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    Button(
        onClick = onNext,
        enabled = selectedIds.isNotEmpty() && !isCreating,
        modifier = Modifier
            .fillMaxWidth()
            .padding(XiloSpacing.horizontal)
            .padding(bottom = 16.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Text(stringResource(R.string.chat_new_group_next))
    }
}

@Composable
private fun ColumnScope.NameStep(
    groupName: String,
    selectedCount: Int,
    isCreating: Boolean,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = XiloSpacing.horizontal),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(modifier = Modifier.height(8.dp))
        XiloTextField(
            value = groupName,
            onValueChange = onNameChange,
            placeholder = stringResource(R.string.chat_new_group_name_placeholder),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.chat_new_group_members_count, selectedCount),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box(modifier = Modifier.weight(1f))
        Button(
            onClick = onCreate,
            enabled = groupName.isNotBlank() && !isCreating,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (isCreating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text(stringResource(R.string.chat_new_group_create))
            }
        }
    }
}

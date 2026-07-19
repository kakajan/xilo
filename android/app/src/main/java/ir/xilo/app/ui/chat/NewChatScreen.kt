package ir.xilo.app.ui.chat

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
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
fun NewChatScreen(
    onBackClick: () -> Unit,
    onChatStarted: (chatId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NewChatViewModel = hiltViewModel(),
) {
    val query by viewModel.query.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val searchResult by viewModel.searchResult.collectAsState()
    val isLoadingSuggestions by viewModel.isLoadingSuggestions.collectAsState()
    val isSearching by viewModel.isSearching.collectAsState()
    val isStartingChat by viewModel.isStartingChat.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.openChatId.collect(onChatStarted)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    val filteredSuggestions = remember(query, suggestions) {
        val needle = query.trim().removePrefix("@").lowercase()
        if (needle.isBlank()) {
            suggestions
        } else {
            suggestions.filter {
                it.username.lowercase().contains(needle) ||
                    (it.displayName?.lowercase()?.contains(needle) == true)
            }
        }
    }

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = stringResource(R.string.chat_new_title),
                showBack = true,
                onBackClick = onBackClick,
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
            XiloTextField(
                value = query,
                onValueChange = viewModel::updateQuery,
                placeholder = stringResource(R.string.chat_new_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = XiloSpacing.horizontal, vertical = 12.dp),
            )

            HorizontalDivider(color = Color.Black.copy(alpha = 0.06f), thickness = 0.5.dp)

            when {
                isStartingChat -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = XiloBlue)
                    }
                }
                isSearching -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = XiloBlue,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 24.dp),
                    ) {
                        if (searchResult != null) {
                            item(key = "search_header") {
                                SectionLabel(stringResource(R.string.chat_new_search_result))
                            }
                            item(key = "search_${searchResult!!.id}") {
                                NewChatContactRow(
                                    contact = searchResult!!,
                                    onClick = { viewModel.startChat(searchResult!!) },
                                )
                            }
                        }

                        item(key = "following_header") {
                            SectionLabel(stringResource(R.string.chat_new_following_section))
                        }

                        when {
                            isLoadingSuggestions -> {
                                item(key = "loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(160.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(color = XiloBlue)
                                    }
                                }
                            }
                            filteredSuggestions.isEmpty() -> {
                                item(key = "empty") {
                                    Text(
                                        text = stringResource(R.string.chat_new_empty),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(
                                            horizontal = XiloSpacing.horizontal,
                                            vertical = 24.dp,
                                        ),
                                    )
                                }
                            }
                            else -> {
                                items(filteredSuggestions, key = { it.id }) { contact ->
                                    NewChatContactRow(
                                        contact = contact,
                                        onClick = { viewModel.startChat(contact) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        modifier = Modifier.padding(
            horizontal = XiloSpacing.horizontal,
            vertical = 12.dp,
        ),
    )
}

@Composable
private fun NewChatContactRow(
    contact: NewChatContact,
    onClick: () -> Unit,
) {
    val title = contact.displayName?.takeIf { it.isNotBlank() } ?: contact.username
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = XiloSpacing.horizontal, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XiloAvatar(imageUrl = contact.avatarUrl, size = 48.dp)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (contact.isVerified) {
                    VerifiedBadge(size = 16.dp)
                }
            }
            Text(
                text = usernameHandle(contact.username),
                style = MaterialTheme.typography.bodyMedium.forUsernameHandle(),
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(XiloBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            XiloIcon(
                icon = XiloIcons.Sms,
                contentDescription = stringResource(R.string.cd_new_chat),
                tint = XiloBlue,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

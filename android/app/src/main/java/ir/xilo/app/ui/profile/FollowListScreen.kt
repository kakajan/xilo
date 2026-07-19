package ir.xilo.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.remote.dto.FollowListUserResponse
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.VerifiedBadge
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloTopAppBar
import ir.xilo.app.ui.components.forUsernameHandle
import ir.xilo.app.ui.components.usernameHandle
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FollowListScreen(
    username: String,
    mode: FollowListMode,
    onBackClick: () -> Unit,
    onUserClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FollowListViewModel = hiltViewModel(),
) {
    val users by viewModel.users.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    LaunchedEffect(username, mode) {
        viewModel.load(username, mode)
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    LaunchedEffect(listState, users) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= users.lastIndex - 3
        }
            .distinctUntilChanged()
            .collect { nearEnd ->
                if (nearEnd) viewModel.loadMore()
            }
    }

    val title = when (mode) {
        FollowListMode.Followers -> stringResource(R.string.profile_followers_title)
        FollowListMode.Following -> stringResource(R.string.profile_following_title)
    }

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = title,
                showBack = true,
                onBackClick = onBackClick,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading && users.isEmpty() -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = XiloBlue,
                    )
                }
                users.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.profile_follow_list_empty),
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(users, key = { it.id }) { user ->
                            FollowListRow(
                                user = user,
                                onClick = { onUserClick(user.username) },
                                onFollowClick = { viewModel.toggleFollow(user) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FollowListRow(
    user: FollowListUserResponse,
    onClick: () -> Unit,
    onFollowClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        XiloAvatar(imageUrl = user.avatarUrl, size = 48.dp, onClick = onClick)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = user.displayName?.takeIf { it.isNotBlank() } ?: user.username,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.isVerified) {
                    VerifiedBadge(size = 14.dp)
                }
            }
            Text(
                text = usernameHandle(user.username),
                style = MaterialTheme.typography.bodyMedium.forUsernameHandle(),
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        if (user.isFollowing) {
            TextButton(onClick = onFollowClick) {
                Text(stringResource(R.string.profile_unfollow))
            }
        } else {
            Button(
                onClick = onFollowClick,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = XiloBlue),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.size(width = 108.dp, height = 36.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_follow),
                    maxLines = 1,
                )
            }
        }
    }
}

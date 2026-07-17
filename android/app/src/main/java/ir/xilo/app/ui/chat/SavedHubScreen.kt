package ir.xilo.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ir.xilo.app.R
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTopAppBar

/**
 * Dedicated Saved hub from Settings: Messages / Posts / Comments segments.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedHubScreen(
    onBackClick: () -> Unit,
    onPostClick: (slug: String) -> Unit,
    onCommentClick: (slug: String, commentId: String) -> Unit,
    onAuthorClick: (username: String) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel,
) {
    val savedSegment by viewModel.savedSegment.collectAsState()
    val savedMessages by viewModel.savedMessages.collectAsState()
    val bookmarkedPosts by viewModel.bookmarkedPosts.collectAsState()
    val bookmarkedComments by viewModel.bookmarkedComments.collectAsState()
    val savedHubLoading by viewModel.savedHubLoading.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        viewModel.openSavedHub(SavedHubSegment.Messages)
    }

    Scaffold(
        topBar = {
            XiloTopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        BoxIcon()
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.saved_messages_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        XiloIcon(
                            icon = XiloIcons.Back,
                            contentDescription = stringResource(R.string.saved_messages_back)
                        )
                    }
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            SavedHubSegmentRow(
                selected = savedSegment,
                onSelect = viewModel::selectSavedSegment,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SavedHubBody(
                segment = savedSegment,
                loading = savedHubLoading,
                messages = savedMessages,
                posts = bookmarkedPosts,
                comments = bookmarkedComments,
                listState = listState,
                onPostClick = onPostClick,
                onCommentClick = onCommentClick,
                onAuthorClick = onAuthorClick,
                contentBottomPadding = 16.dp,
            )
        }
    }
}

@Composable
private fun BoxIcon() {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(XiloBlue),
        contentAlignment = Alignment.Center
    ) {
        XiloIcon(
            icon = XiloIcons.BookmarkFilled,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(18.dp)
        )
    }
}

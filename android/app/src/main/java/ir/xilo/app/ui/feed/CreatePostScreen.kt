package ir.xilo.app.ui.feed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.PostField
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTextArea
import ir.xilo.app.ui.components.XiloTextField
import ir.xilo.app.ui.components.usernameHandle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreatePostScreen(
    onBackClick: () -> Unit,
    onPostCreated: () -> Unit,
    editPostId: String? = null,
    quotedPostId: String? = null,
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(
        key = "create-post-${editPostId.orEmpty()}-${quotedPostId.orEmpty()}",
    ),
) {
    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val audioUrl by viewModel.audioUrl.collectAsState()
    val isUploadingAudio by viewModel.isUploadingAudio.collectAsState()
    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val isLoadingEdit by viewModel.isLoadingEdit.collectAsState()
    val error by viewModel.error.collectAsState()
    val fieldErrors by viewModel.fieldErrors.collectAsState()
    val success by viewModel.success.collectAsState()
    val allowed by viewModel.allowed.collectAsState()
    val tagSuggestions by viewModel.tagSuggestions.collectAsState()
    val quotedPost by viewModel.quotedPost.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = !editPostId.isNullOrBlank()
    val isQuote = !quotedPostId.isNullOrBlank()

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.uploadAudio(uri)
    }

    LaunchedEffect(editPostId, quotedPostId) {
        viewModel.prepare(editPostId, quotedPostId)
    }

    LaunchedEffect(allowed) {
        if (allowed == false) {
            onBackClick()
        }
    }

    LaunchedEffect(success) {
        if (success) {
            viewModel.consumeSuccess()
            onPostCreated()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearErrors()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            when {
                                isEditing -> R.string.post_edit_title
                                isQuote -> R.string.quote_compose_title
                                else -> R.string.post_create_title
                            }
                        ),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        XiloIcon(icon = XiloIcons.Close, contentDescription = stringResource(R.string.common_close))
                    }
                },
                actions = {
                    Button(
                        onClick = { viewModel.submit() },
                        enabled = !isSubmitting && !isLoadingEdit && !isUploadingAudio,
                        colors = ButtonDefaults.buttonColors(containerColor = XiloBlue),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (isEditing) R.string.post_edit_save else R.string.post_create_publish
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                windowInsets = WindowInsets(0, 0, 0, 0),
                modifier = Modifier.statusBarsPadding(),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            if (isSubmitting || isLoadingEdit || isUploadingAudio) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = XiloBlue)
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (!isQuote) {
                XiloTextField(
                    value = title,
                    onValueChange = viewModel::updateTitle,
                    placeholder = stringResource(R.string.post_title_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                    isError = fieldErrors.containsKey(PostField.Title),
                    errorText = fieldErrors[PostField.Title],
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (!isQuote && audioUrl.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    XiloIcon(
                        icon = XiloIcons.Music,
                        contentDescription = null,
                        tint = XiloBlue,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = audioUrl.substringAfterLast('/').ifBlank {
                            stringResource(R.string.post_audio_attached)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::clearAudio) {
                        XiloIcon(
                            icon = XiloIcons.Close,
                            contentDescription = stringResource(R.string.post_audio_remove),
                        )
                    }
                }
            } else if (!isQuote) {
                OutlinedButton(
                    onClick = { audioPicker.launch("audio/*") },
                    enabled = !isUploadingAudio && !isSubmitting,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    XiloIcon(
                        icon = XiloIcons.Music,
                        contentDescription = null,
                        tint = XiloBlue,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(end = 0.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(
                        text = stringResource(
                            if (isUploadingAudio) R.string.post_audio_uploading else R.string.post_audio_attach
                        ),
                    )
                }
            }

            if (tagSuggestions.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    items(tagSuggestions, key = { it.tag }) { item ->
                        AssistChip(
                            onClick = { viewModel.applyTagSuggestion(item.tag) },
                            label = {
                                Text(
                                    text = "#${item.tag}",
                                    color = XiloBlue,
                                )
                            },
                        )
                    }
                }
            }

            XiloTextArea(
                value = content,
                onValueChange = viewModel::updateContent,
                placeholder = stringResource(
                    if (isQuote) R.string.quote_compose_hint else R.string.post_body_placeholder
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                minLines = if (isQuote) 4 else 6,
                isError = fieldErrors.containsKey(PostField.Content),
                errorText = fieldErrors[PostField.Content],
                transparentBorder = fieldErrors[PostField.Content] == null,
            )

            if (isQuote) {
                Spacer(modifier = Modifier.height(12.dp))
                QuotedPostPreview(post = quotedPost)
            }
        }
    }
}

@Composable
private fun QuotedPostPreview(post: PostEntity?) {
    if (post == null) return
    val name = post.authorName?.takeIf { it.isNotBlank() } ?: post.authorUsername
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                RoundedCornerShape(16.dp),
            )
            .padding(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            XiloAvatar(imageUrl = post.authorAvatar, size = 20.dp)
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (post.authorUsername.isNotBlank()) {
                Spacer(modifier = Modifier.size(4.dp))
                Text(
                    text = usernameHandle(post.authorUsername),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = post.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val preview = post.excerpt?.takeIf { it.isNotBlank() } ?: post.content
        if (preview.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = preview,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

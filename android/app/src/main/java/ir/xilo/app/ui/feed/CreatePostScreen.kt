package ir.xilo.app.ui.feed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.height
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import java.util.Calendar
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    quotedCommentId: String? = null,
    composeKind: String = ComposeKind.ARTICLE,
    modifier: Modifier = Modifier,
    viewModel: CreatePostViewModel = hiltViewModel(
        key = "create-post-${editPostId.orEmpty()}-${quotedPostId.orEmpty()}-${quotedCommentId.orEmpty()}-$composeKind",
    ),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val audioUrl by viewModel.audioUrl.collectAsStateWithLifecycle()
    val coverImageUrl by viewModel.coverImageUrl.collectAsStateWithLifecycle()
    val isUploadingCover by viewModel.isUploadingCover.collectAsStateWithLifecycle()
    val scheduledAtEpoch by viewModel.scheduledAtEpoch.collectAsStateWithLifecycle()
    val isUploadingAudio by viewModel.isUploadingAudio.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val isLoadingEdit by viewModel.isLoadingEdit.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val fieldErrors by viewModel.fieldErrors.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val allowed by viewModel.allowed.collectAsStateWithLifecycle()
    val tagSuggestions by viewModel.tagSuggestions.collectAsStateWithLifecycle()
    val quotedPost by viewModel.quotedPost.collectAsStateWithLifecycle()
    val quotedComment by viewModel.quotedComment.collectAsStateWithLifecycle()
    val quotedCommentPostTitle by viewModel.quotedCommentPostTitle.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = !editPostId.isNullOrBlank()
    val isQuote = !quotedPostId.isNullOrBlank() || !quotedCommentId.isNullOrBlank()
    val hideTitle = !isEditing && (composeKind == ComposeKind.TEXT || isQuote)

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.uploadAudio(uri)
    }
    val coverPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.uploadCover(uri)
    }
    val context = LocalContext.current
    var audioPickerRequested by remember { mutableStateOf(false) }
    LaunchedEffect(composeKind) {
        if (composeKind == ComposeKind.AUDIO && !audioPickerRequested && audioUrl.isBlank()) {
            audioPickerRequested = true
            audioPicker.launch("audio/*")
        }
    }

    LaunchedEffect(editPostId, quotedPostId, quotedCommentId, composeKind) {
        viewModel.prepare(editPostId, quotedPostId, quotedCommentId, composeKind)
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
                                composeKind == ComposeKind.TEXT -> R.string.compose_kind_text
                                composeKind == ComposeKind.AUDIO -> R.string.compose_kind_audio
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

            if (!hideTitle) {
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

            if (!isQuote && coverImageUrl.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    AsyncImage(
                        model = coverImageUrl,
                        contentDescription = stringResource(R.string.cd_post_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Text(
                        text = stringResource(R.string.post_cover_attached),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::clearCover) {
                        XiloIcon(
                            icon = XiloIcons.Close,
                            contentDescription = stringResource(R.string.post_cover_remove),
                        )
                    }
                }
            } else if (!isQuote) {
                OutlinedButton(
                    onClick = { coverPicker.launch("image/*") },
                    enabled = !isUploadingCover && !isSubmitting,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (isUploadingCover) R.string.post_cover_uploading else R.string.post_cover_attach
                        ),
                    )
                }
            }

            if (!isQuote) {
                OutlinedButton(
                    onClick = {
                        val now = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, day ->
                                TimePickerDialog(
                                    context,
                                    { _, hour, minute ->
                                        val picked = Calendar.getInstance().apply {
                                            set(year, month, day, hour, minute, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        viewModel.setScheduledAt(picked.timeInMillis)
                                    },
                                    now.get(Calendar.HOUR_OF_DAY),
                                    now.get(Calendar.MINUTE),
                                    true,
                                ).show()
                            },
                            now.get(Calendar.YEAR),
                            now.get(Calendar.MONTH),
                            now.get(Calendar.DAY_OF_MONTH),
                        ).show()
                    },
                    enabled = !isSubmitting,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        text = if (scheduledAtEpoch != null) {
                            stringResource(R.string.post_schedule_set)
                        } else {
                            stringResource(R.string.post_schedule)
                        },
                    )
                }
                if (scheduledAtEpoch != null) {
                    Text(
                        text = stringResource(R.string.post_schedule_clear),
                        color = XiloBlue,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .clickable { viewModel.setScheduledAt(null) },
                    )
                }
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

            if (!isQuote) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    AssistChip(
                        onClick = { viewModel.wrapMarkdown("**") },
                        label = { Text(stringResource(R.string.post_format_bold)) },
                    )
                    AssistChip(
                        onClick = { viewModel.wrapMarkdown("_") },
                        label = { Text(stringResource(R.string.post_format_italic)) },
                    )
                    AssistChip(
                        onClick = { viewModel.wrapMarkdown("## ", "") },
                        label = { Text(stringResource(R.string.post_format_heading)) },
                    )
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
                if (!quotedCommentId.isNullOrBlank()) {
                    QuotedCommentPreview(
                        comment = quotedComment,
                        postTitle = quotedCommentPostTitle,
                    )
                } else {
                    QuotedPostPreview(post = quotedPost)
                }
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

package ir.xilo.app.ui.feed

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import ir.xilo.app.data.remote.dto.PostSearchHit
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.text.input.ImeAction
import ir.xilo.app.core.util.HashtagParser
import ir.xilo.app.data.remote.dto.TagSuggestion
import ir.xilo.app.ui.components.PostField
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.ui.components.RemovableHashtagChip
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.XiloTextArea
import ir.xilo.app.ui.components.XiloTextField
import ir.xilo.app.ui.components.usernameHandle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        key = "create-post-${editPostId.orEmpty()}-${quotedPostId.orEmpty()}-${quotedCommentId.orEmpty()}",
    ),
) {
    val title by viewModel.title.collectAsStateWithLifecycle()
    val content by viewModel.content.collectAsStateWithLifecycle()
    val audioUrl by viewModel.audioUrl.collectAsStateWithLifecycle()
    val coverImageUrl by viewModel.coverImageUrl.collectAsStateWithLifecycle()
    val linkUrl by viewModel.linkUrl.collectAsStateWithLifecycle()
    val photoMedia by viewModel.photoMedia.collectAsStateWithLifecycle()
    val videoMedia by viewModel.videoMedia.collectAsStateWithLifecycle()
    val isUploadingCover by viewModel.isUploadingCover.collectAsStateWithLifecycle()
    val isUploadingPhoto by viewModel.isUploadingPhoto.collectAsStateWithLifecycle()
    val isUploadingVideo by viewModel.isUploadingVideo.collectAsStateWithLifecycle()
    val scheduledAtEpoch by viewModel.scheduledAtEpoch.collectAsStateWithLifecycle()
    val isUploadingAudio by viewModel.isUploadingAudio.collectAsStateWithLifecycle()
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val isLoadingEdit by viewModel.isLoadingEdit.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val fieldErrors by viewModel.fieldErrors.collectAsStateWithLifecycle()
    val success by viewModel.success.collectAsStateWithLifecycle()
    val allowed by viewModel.allowed.collectAsStateWithLifecycle()
    val tagSuggestions by viewModel.tagSuggestions.collectAsStateWithLifecycle()
    val tags by viewModel.tags.collectAsStateWithLifecycle()
    val tagInput by viewModel.tagInput.collectAsStateWithLifecycle()
    val tagInputSuggestions by viewModel.tagInputSuggestions.collectAsStateWithLifecycle()
    val quotedPost by viewModel.quotedPost.collectAsStateWithLifecycle()
    val quotedComment by viewModel.quotedComment.collectAsStateWithLifecycle()
    val quotedCommentPostTitle by viewModel.quotedCommentPostTitle.collectAsStateWithLifecycle()
    val selectedKind by viewModel.composeKind.collectAsStateWithLifecycle()
    val quoteQuery by viewModel.quoteQuery.collectAsStateWithLifecycle()
    val quoteResults by viewModel.quoteResults.collectAsStateWithLifecycle()
    val isSearchingQuote by viewModel.isSearchingQuote.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEditing = !editPostId.isNullOrBlank()
    val isQuote = selectedKind == ComposeKind.QUOTE
    val hideTitle = !isEditing && (
        selectedKind == ComposeKind.TEXT ||
            selectedKind == ComposeKind.PHOTO ||
            selectedKind == ComposeKind.VIDEO ||
            selectedKind == ComposeKind.LINK ||
            isQuote
        )
    val isArticleKind = selectedKind == ComposeKind.ARTICLE
    val isAudioKind = selectedKind == ComposeKind.AUDIO
    val isPhotoKind = selectedKind == ComposeKind.PHOTO
    val isVideoKind = selectedKind == ComposeKind.VIDEO
    val isLinkKind = selectedKind == ComposeKind.LINK
    val showCover = !isQuote && isArticleKind
    val showAudio = !isQuote && (isAudioKind || isArticleKind || audioUrl.isNotBlank())
    val showSchedule = !isQuote && (isArticleKind || isAudioKind)
    val showMarkdown = !isQuote
    var typePickerOpen by remember { mutableStateOf(false) }

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
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10),
    ) { uris ->
        uris.forEach { viewModel.uploadPhoto(it) }
    }
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) viewModel.uploadVideo(uri)
    }
    val context = LocalContext.current
    var audioPickerRequested by remember { mutableStateOf(false) }
    LaunchedEffect(selectedKind, isEditing, audioUrl) {
        if (selectedKind == ComposeKind.AUDIO &&
            !isEditing &&
            !audioPickerRequested &&
            audioUrl.isBlank()
        ) {
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

    LaunchedEffect(fieldErrors[PostField.Media]) {
        fieldErrors[PostField.Media]?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFieldError(PostField.Media)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(
                                when {
                                    isEditing -> R.string.post_edit_title
                                    isQuote -> R.string.quote_compose_title
                                    selectedKind == ComposeKind.TEXT -> R.string.compose_kind_text
                                    selectedKind == ComposeKind.AUDIO -> R.string.compose_kind_audio
                                    selectedKind == ComposeKind.PHOTO -> R.string.compose_kind_photo
                                    selectedKind == ComposeKind.VIDEO -> R.string.compose_kind_video
                                    selectedKind == ComposeKind.LINK -> R.string.compose_kind_link
                                    else -> R.string.post_create_title
                                }
                            ),
                            fontWeight = FontWeight.Bold,
                        )
                        AssistChip(
                            onClick = { typePickerOpen = true },
                            label = {
                                Text(stringResource(composeKindLabel(selectedKind)))
                            },
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        XiloIcon(icon = XiloIcons.Close, contentDescription = stringResource(R.string.common_close))
                    }
                },
                actions = {
                    if (!isQuote && !isEditing) {
                        OutlinedButton(
                            onClick = { viewModel.submitDraft() },
                            enabled = !isSubmitting && !isLoadingEdit &&
                                !isUploadingAudio && !isUploadingPhoto && !isUploadingVideo,
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            Text(stringResource(R.string.post_create_save_draft))
                        }
                    }
                    Button(
                        onClick = { viewModel.submit() },
                        enabled = !isSubmitting && !isLoadingEdit &&
                            !isUploadingAudio && !isUploadingPhoto && !isUploadingVideo,
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
            if (isSubmitting || isLoadingEdit || isUploadingAudio || isUploadingPhoto || isUploadingVideo) {
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

            if (isLinkKind && !isQuote) {
                XiloTextField(
                    value = linkUrl,
                    onValueChange = viewModel::updateLinkUrl,
                    placeholder = stringResource(R.string.post_link_url_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                    isError = fieldErrors.containsKey(PostField.LinkUrl),
                    errorText = fieldErrors[PostField.LinkUrl],
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isPhotoKind && !isQuote) {
                OutlinedButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = !isUploadingPhoto && !isSubmitting && photoMedia.size < 10,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                ) {
                    Text(
                        text = stringResource(
                            if (isUploadingPhoto) R.string.post_cover_uploading else R.string.post_photo_attach
                        ),
                    )
                }
                if (photoMedia.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        itemsIndexed(photoMedia, key = { _, item -> item.id }) { index, media ->
                            Box {
                                AsyncImage(
                                    model = media.url,
                                    contentDescription = stringResource(R.string.cd_post_image),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(72.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                                IconButton(
                                    onClick = { viewModel.removePhoto(index) },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(24.dp),
                                ) {
                                    XiloIcon(
                                        icon = XiloIcons.Close,
                                        contentDescription = stringResource(R.string.common_close),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (isVideoKind && !isQuote) {
                if (videoMedia != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        XiloIcon(
                            icon = XiloIcons.Chart,
                            contentDescription = null,
                            tint = XiloBlue,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            text = videoMedia!!.url.substringAfterLast('/').ifBlank {
                                stringResource(R.string.post_video_attach)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::clearVideo) {
                            XiloIcon(
                                icon = XiloIcons.Close,
                                contentDescription = stringResource(R.string.common_close),
                            )
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { videoPicker.launch("video/*") },
                        enabled = !isUploadingVideo && !isSubmitting,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (isUploadingVideo) R.string.post_cover_uploading else R.string.post_video_attach
                            ),
                        )
                    }
                }
            }

            if (showCover && coverImageUrl.isNotBlank()) {
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
            } else if (showCover) {
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

            if (showSchedule) {
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

            if (showAudio && audioUrl.isNotBlank()) {
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
            } else if (showAudio) {
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

            HashtagManagementSection(
                tags = tags,
                tagInput = tagInput,
                tagSuggestions = tagInputSuggestions,
                onTagInputChange = viewModel::updateTagInput,
                onAddTag = viewModel::addTag,
                onRemoveTag = viewModel::removeTag,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )

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

            if (showMarkdown) {
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
                    AssistChip(
                        onClick = { viewModel.wrapMarkdown("> ", "") },
                        label = { Text(stringResource(R.string.post_format_quote)) },
                    )
                    AssistChip(
                        onClick = { viewModel.wrapMarkdown("`") },
                        label = { Text(stringResource(R.string.post_format_code)) },
                    )
                }
            }

            XiloTextArea(
                value = content,
                onValueChange = viewModel::updateContent,
                placeholder = stringResource(
                    when {
                        isQuote -> R.string.quote_compose_hint
                        isPhotoKind || isVideoKind -> R.string.post_body_placeholder
                        isLinkKind -> R.string.post_body_placeholder
                        else -> R.string.post_body_placeholder
                    }
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
                QuoteSourceSection(
                    quotedPost = quotedPost,
                    quotedComment = quotedComment,
                    quotedCommentPostTitle = quotedCommentPostTitle,
                    quotedCommentId = quotedCommentId,
                    query = quoteQuery,
                    results = quoteResults,
                    searching = isSearchingQuote,
                    onQueryChange = viewModel::updateQuoteQuery,
                    onSelect = viewModel::selectQuotedPost,
                )
            }
        }
    }

    if (typePickerOpen) {
        ModalBottomSheet(
            onDismissRequest = { typePickerOpen = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            Text(
                text = stringResource(R.string.compose_type_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            ComposeKind.all.forEach { kind ->
                Text(
                    text = stringResource(composeKindLabel(kind)),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setComposeKind(kind)
                            typePickerOpen = false
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    color = if (kind == selectedKind) XiloBlue else MaterialTheme.colorScheme.onBackground,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
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

private fun composeKindLabel(kind: String): Int = when (kind) {
    ComposeKind.TEXT -> R.string.compose_kind_text
    ComposeKind.AUDIO -> R.string.compose_kind_audio
    ComposeKind.PHOTO -> R.string.compose_kind_photo
    ComposeKind.VIDEO -> R.string.compose_kind_video
    ComposeKind.LINK -> R.string.compose_kind_link
    ComposeKind.QUOTE -> R.string.compose_kind_quote
    else -> R.string.compose_kind_article
}

@Composable
private fun QuoteSourceSection(
    quotedPost: PostEntity?,
    quotedComment: ir.xilo.app.data.local.entity.CommentEntity?,
    quotedCommentPostTitle: String?,
    quotedCommentId: String?,
    query: String,
    results: List<PostSearchHit>,
    searching: Boolean,
    onQueryChange: (String) -> Unit,
    onSelect: (PostSearchHit) -> Unit,
) {
    if (!quotedCommentId.isNullOrBlank()) {
        QuotedCommentPreview(
            comment = quotedComment,
            postTitle = quotedCommentPostTitle,
        )
        return
    }
    if (quotedPost != null) {
        QuotedPostPreview(post = quotedPost)
        return
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        XiloTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = stringResource(R.string.quote_search_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        if (searching) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                color = XiloBlue,
            )
        }
        results.forEach { hit ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(hit) }
                    .padding(vertical = 10.dp),
            ) {
                Text(
                    text = hit.title.ifBlank { hit.slug },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = hit.authorName.ifBlank { hit.authorUsername }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
        if (!searching && query.trim().length >= 2 && results.isEmpty()) {
            Text(
                text = stringResource(R.string.quote_search_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HashtagManagementSection(
    tags: List<String>,
    tagInput: String,
    tagSuggestions: List<TagSuggestion>,
    onTagInputChange: (String) -> Unit,
    onAddTag: (String) -> Boolean,
    onRemoveTag: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        if (tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            ) {
                tags.forEach { tag ->
                    RemovableHashtagChip(
                        tag = tag,
                        onRemove = { onRemoveTag(tag) },
                    )
                }
            }
        }

        if (tags.size < HashtagParser.MAX_TAGS) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = onTagInputChange,
                    placeholder = {
                        Text(
                            text = stringResource(R.string.post_hashtag_placeholder),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (tagInput.isNotBlank()) {
                                onAddTag(tagInput)
                            }
                        },
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = XiloBlue,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Button(
                    onClick = {
                        if (tagInput.isNotBlank()) {
                            onAddTag(tagInput)
                        }
                    },
                    enabled = tagInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = XiloBlue),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(text = stringResource(R.string.post_hashtag_add))
                }
            }
        } else {
            Text(
                text = stringResource(R.string.post_hashtag_limit),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (tagSuggestions.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            ) {
                items(tagSuggestions, key = { it.tag }) { item ->
                    AssistChip(
                        onClick = { onAddTag(item.tag) },
                        label = {
                            Text(
                                text = "#${item.tag}",
                                color = XiloBlue,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        },
                    )
                }
            }
        }
    }
}


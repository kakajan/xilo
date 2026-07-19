package ir.xilo.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import ir.xilo.app.R
import ir.xilo.app.theme.XiloBlue

private val ComposerEmojis = listOf(
    "😀", "😁", "😂", "🤣", "😊", "😍", "🥰", "😘",
    "😎", "🤔", "😢", "😭", "😡", "👍", "👎", "👏",
    "🙏", "🔥", "❤️", "💙", "✨", "🎉", "💯", "⭐",
    "🤝", "💪", "🙌", "👌", "✅", "❌", "📌", "📎",
)

/**
 * Modern X-style composer: emoji, attach, pill field, and send share one baseline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatInput(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    showAttach: Boolean = true,
    showEmoji: Boolean = true,
    onSendImage: (uri: Uri, caption: String) -> Unit = { _, _ -> },
    focusRequester: FocusRequester? = null,
) {
    val resolvedPlaceholder = placeholder ?: stringResource(R.string.chat_message_placeholder)
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    var pendingAttachment by remember { mutableStateOf<Uri?>(null) }
    var showEmojiSheet by remember { mutableStateOf(false) }
    val emojiSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            textFieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) pendingAttachment = uri
    }

    fun syncText(next: TextFieldValue) {
        textFieldValue = next
        onValueChange(next.text)
    }

    fun insertEmoji(emoji: String) {
        val selection = textFieldValue.selection
        val start = selection.min.coerceIn(0, textFieldValue.text.length)
        val end = selection.max.coerceIn(0, textFieldValue.text.length)
        val nextText = textFieldValue.text.replaceRange(start, end, emoji)
        val cursor = (start + emoji.length).coerceAtMost(nextText.length)
        syncText(TextFieldValue(nextText, TextRange(cursor)))
    }

    fun submit() {
        val caption = textFieldValue.text.trim()
        val attachment = pendingAttachment
        if (attachment != null) {
            onSendImage(attachment, caption)
            pendingAttachment = null
            syncText(TextFieldValue(""))
            return
        }
        if (caption.isNotEmpty()) {
            onSend()
        }
    }

    val canSend = textFieldValue.text.isNotBlank() || pendingAttachment != null
    val sendScale by animateFloatAsState(
        targetValue = if (canSend) 1f else 0.92f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "sendScale",
    )
    val sendContainer by animateColorAsState(
        targetValue = if (canSend) XiloBlue else Color(0xFFE7E9EA),
        label = "sendContainer",
    )
    val sendContent by animateColorAsState(
        targetValue = if (canSend) Color.White else Color(0xFFB0B8BF),
        label = "sendContent",
    )
    val textStyle = MaterialTheme.typography.bodyLarge.forInput()
    val actionSize = 40.dp
    val iconSize = 22.dp
    val fieldBg = Color(0xFFF7F9FA)
    val iconTint = Color(0xFF536471)

    Surface(
        color = Color.White,
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                color = Color(0xFFEFF3F4),
                thickness = 1.dp,
            )

            pendingAttachment?.let { uri ->
                AttachmentPreview(
                    uri = uri,
                    onClear = { pendingAttachment = null },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (showEmoji) {
                    ComposerAction(
                        onClick = { showEmojiSheet = true },
                        size = actionSize,
                    ) {
                        XiloIcon(
                            icon = XiloIcons.Emoji,
                            contentDescription = stringResource(R.string.cd_emoji),
                            modifier = Modifier.size(iconSize),
                            tint = iconTint,
                        )
                    }
                }

                if (showAttach) {
                    ComposerAction(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                )
                            )
                        },
                        size = actionSize,
                    ) {
                        XiloIcon(
                            icon = XiloIcons.Attach,
                            contentDescription = stringResource(R.string.cd_attach),
                            modifier = Modifier.size(iconSize),
                            tint = iconTint,
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = actionSize)
                        .clip(RoundedCornerShape(22.dp))
                        .background(fieldBg)
                        .border(
                            width = 1.dp,
                            color = Color(0xFFEFF3F4),
                            shape = RoundedCornerShape(22.dp),
                        )
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (textFieldValue.text.isEmpty()) {
                        Text(
                            text = resolvedPlaceholder,
                            style = textStyle,
                            color = Color(0xFF8295A3),
                        )
                    }
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = ::syncText,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (focusRequester != null) {
                                    Modifier.focusRequester(focusRequester)
                                } else {
                                    Modifier
                                }
                            ),
                        textStyle = textStyle.copy(color = Color(0xFF0F1419)),
                        cursorBrush = SolidColor(XiloBlue),
                        maxLines = 6,
                    )
                }

                ComposerAction(
                    onClick = ::submit,
                    size = actionSize,
                    enabled = canSend,
                    containerColor = sendContainer,
                    modifier = Modifier.scale(sendScale),
                ) {
                    XiloSendIcon(
                        contentDescription = stringResource(R.string.cd_send),
                        modifier = Modifier.size(iconSize),
                        tint = sendContent,
                    )
                }
            }
        }
    }

    if (showEmojiSheet) {
        ModalBottomSheet(
            onDismissRequest = { showEmojiSheet = false },
            sheetState = emojiSheetState,
            containerColor = Color.White,
        ) {
            Text(
                text = stringResource(R.string.chat_emoji_picker_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .navigationBarsPadding(),
            ) {
                items(ComposerEmojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                insertEmoji(emoji)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentPreview(
    uri: Uri,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFF7F9FA))
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = uri,
            contentDescription = stringResource(R.string.cd_attach),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.chat_attachment_ready),
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF0F1419),
            modifier = Modifier.weight(1f),
        )
        ComposerAction(
            onClick = onClear,
            size = 32.dp,
            containerColor = Color.White,
        ) {
            XiloIcon(
                icon = XiloIcons.Close,
                contentDescription = stringResource(R.string.common_delete),
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF536471),
            )
        }
    }
}

@Composable
private fun ComposerAction(
    onClick: () -> Unit,
    size: Dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

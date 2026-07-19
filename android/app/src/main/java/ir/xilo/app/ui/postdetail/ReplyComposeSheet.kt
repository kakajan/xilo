package ir.xilo.app.ui.postdetail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.CommentEntity
import ir.xilo.app.data.local.entity.PostEntity
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.XiloSpacing
import ir.xilo.app.ui.components.ContentAwareText
import ir.xilo.app.ui.components.XiloAvatar
import ir.xilo.app.ui.components.XiloIcon
import ir.xilo.app.ui.components.XiloIcons
import ir.xilo.app.ui.components.forInput
import ir.xilo.app.ui.components.forRelativeTime
import ir.xilo.app.ui.components.forUsernameHandle
import ir.xilo.app.ui.components.usernameHandle
import ir.xilo.app.ui.feed.getRelativeTimeSpan
import kotlinx.coroutines.delay

private val ReplyAvatarSize = 40.dp
private val ReplyGutterWidth = 40.dp
private val ReplyThreadLineWidth = 2.dp
private const val ParentCollapsedMaxLines = 3

/** Parent message shown above the reply composer (post or comment). */
data class ReplyComposeParent(
    val authorUsername: String,
    val authorName: String?,
    val authorAvatar: String?,
    val content: String,
    val createdAt: Long,
)

fun CommentEntity.toReplyComposeParent(): ReplyComposeParent = ReplyComposeParent(
    authorUsername = authorUsername,
    authorName = authorName,
    authorAvatar = authorAvatar,
    content = content,
    createdAt = createdAt,
)

fun PostEntity.toReplyComposeParent(): ReplyComposeParent {
    val body = extractPlainText(content).ifBlank { excerpt.orEmpty() }
    val display = buildString {
        if (title.isNotBlank()) {
            append(title)
            if (body.isNotBlank()) append("\n\n")
        }
        append(body)
    }
    return ReplyComposeParent(
        authorUsername = authorUsername,
        authorName = authorName,
        authorAvatar = authorAvatar,
        content = display,
        createdAt = createdAt,
    )
}

@Composable
fun ReplyComposeSheet(
    parent: ReplyComposeParent,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    currentUserAvatarUrl: String?,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
    onAuthorClick: (() -> Unit)? = null,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .imePadding(),
            ) {
                ReplyComposeTopBar(
                    canSubmit = replyText.isNotBlank(),
                    onDismiss = onDismiss,
                    onSubmit = onSubmit,
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = XiloSpacing.horizontal, vertical = 8.dp),
                ) {
                    TwitterStyleReplyThread(
                        parent = parent,
                        replyText = replyText,
                        onReplyTextChange = onReplyTextChange,
                        currentUserAvatarUrl = currentUserAvatarUrl,
                        focusRequester = focusRequester,
                        onAuthorClick = onAuthorClick,
                    )
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(120)
        runCatching {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
}

@Composable
private fun ReplyComposeTopBar(
    canSubmit: Boolean,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onDismiss) {
            XiloIcon(
                icon = XiloIcons.Close,
                contentDescription = stringResource(R.string.reply_compose_close),
                tint = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            shape = RoundedCornerShape(20.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = XiloBlue,
                disabledContainerColor = XiloBlue.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
                disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
            ),
            modifier = Modifier
                .padding(end = 8.dp)
                .height(36.dp),
        ) {
            Text(
                text = stringResource(R.string.reply_compose_submit),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun TwitterStyleReplyThread(
    parent: ReplyComposeParent,
    replyText: String,
    onReplyTextChange: (String) -> Unit,
    currentUserAvatarUrl: String?,
    focusRequester: FocusRequester,
    onAuthorClick: (() -> Unit)? = null,
) {
    val lineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
    val parentName = parent.authorName?.takeIf { it.isNotBlank() } ?: parent.authorUsername
    val placeholder = stringResource(R.string.reply_compose_placeholder)
    val textColor = MaterialTheme.colorScheme.onBackground
    val placeholderColor = MaterialTheme.colorScheme.secondary
    val openAuthor = onAuthorClick?.takeIf { parent.authorUsername.isNotBlank() }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Parent message with thread line running under the avatar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier
                    .width(ReplyGutterWidth)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                XiloAvatar(
                    imageUrl = parent.authorAvatar,
                    size = ReplyAvatarSize,
                    onClick = openAuthor,
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .width(ReplyThreadLineWidth)
                        .padding(top = 4.dp)
                        .background(lineColor, RoundedCornerShape(1.dp)),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = parentName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = if (openAuthor != null) {
                            Modifier.clickable(role = Role.Button, onClick = openAuthor)
                        } else {
                            Modifier
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = if (openAuthor != null) {
                            Modifier.clickable(role = Role.Button, onClick = openAuthor)
                        } else {
                            Modifier
                        }
                    ) {
                        Text(
                            text = usernameHandle(parent.authorUsername),
                            style = MaterialTheme.typography.bodyMedium.forUsernameHandle(),
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = getRelativeTimeSpan(
                                androidx.compose.ui.platform.LocalContext.current,
                                parent.createdAt,
                            ),
                            style = MaterialTheme.typography.bodyMedium.forRelativeTime(),
                            color = MaterialTheme.colorScheme.secondary,
                            maxLines = 1,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                ExpandableBodyText(
                    text = parent.content,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    collapsedMaxLines = ParentCollapsedMaxLines,
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // Reply composer — line continues into the current-user avatar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.width(ReplyGutterWidth),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .width(ReplyThreadLineWidth)
                        .height(8.dp)
                        .background(lineColor, RoundedCornerShape(1.dp)),
                )
                XiloAvatar(imageUrl = currentUserAvatarUrl, size = ReplyAvatarSize)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Align with avatar (below the 8.dp connector stub)
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.reply_compose_replying_to_prefix),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        text = usernameHandle(parent.authorUsername),
                        style = MaterialTheme.typography.bodyMedium
                            .forUsernameHandle()
                            .copy(fontWeight = FontWeight.SemiBold),
                        color = XiloBlue,
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                BasicTextField(
                    value = replyText,
                    onValueChange = onReplyTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .padding(bottom = 24.dp),
                    // Content direction resolves per paragraph while typing.
                    textStyle = MaterialTheme.typography.bodyLarge
                        .forInput()
                        .copy(color = textColor),
                    cursorBrush = SolidColor(XiloBlue),
                    decorationBox = { innerTextField ->
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (replyText.isEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = placeholderColor,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun ExpandableBodyText(
    text: String,
    style: TextStyle,
    color: Color,
    collapsedMaxLines: Int,
) {
    var expanded by remember(text) { mutableStateOf(false) }
    var canExpand by remember(text) { mutableStateOf(false) }

    ContentAwareText(
        text = text,
        style = style,
        color = color,
        maxLines = if (expanded) Int.MAX_VALUE else collapsedMaxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.then(
            if (canExpand || expanded) {
                Modifier.clickable(role = Role.Button) { expanded = !expanded }
            } else {
                Modifier
            }
        ),
        onTextLayout = { result ->
            if (!expanded) {
                canExpand = result.hasVisualOverflow
            }
        },
    )
}

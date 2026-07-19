package ir.xilo.app.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT
import java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING
import java.lang.Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE
import java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT
import java.lang.Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC
import java.lang.Character.getDirectionality

/**
 * Resolves paragraph direction from the first strong directional character
 * (Unicode first-strong), so Persian/Arabic bodies stay RTL inside an LTR app UI.
 */
fun layoutDirectionForContent(
    text: String,
    emptyDefault: LayoutDirection = LayoutDirection.Ltr,
): LayoutDirection {
    for (char in text) {
        if (char.isWhitespace()) continue
        when (getDirectionality(char)) {
            DIRECTIONALITY_RIGHT_TO_LEFT,
            DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> return LayoutDirection.Rtl

            DIRECTIONALITY_LEFT_TO_RIGHT,
            DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return LayoutDirection.Ltr
        }
    }
    return emptyDefault
}

/**
 * Test helper: paragraph styles for newline-separated segments.
 * Runtime UI uses [ContentAwareText] (one Text per paragraph) for reliable alignment.
 */
fun buildContentAwareAnnotatedString(
    text: String,
    emptyDefault: LayoutDirection = LayoutDirection.Ltr,
): AnnotatedString {
    if (text.isEmpty()) return AnnotatedString("")
    val paragraphs = text.split('\n')
    return buildAnnotatedString {
        paragraphs.forEachIndexed { index, paragraph ->
            val direction = layoutDirectionForContent(paragraph, emptyDefault)
            withStyle(
                ParagraphStyle(
                    textDirection = when (direction) {
                        LayoutDirection.Rtl -> TextDirection.Rtl
                        LayoutDirection.Ltr -> TextDirection.Ltr
                    },
                    textAlign = when (direction) {
                        LayoutDirection.Rtl -> TextAlign.Right
                        LayoutDirection.Ltr -> TextAlign.Left
                    },
                ),
            ) {
                append(paragraph)
                if (index < paragraphs.lastIndex) {
                    append('\n')
                }
            }
        }
    }
}

fun TextStyle.withContentDirection(direction: LayoutDirection): TextStyle =
    copy(
        textDirection = when (direction) {
            LayoutDirection.Rtl -> TextDirection.Rtl
            LayoutDirection.Ltr -> TextDirection.Ltr
        },
        textAlign = when (direction) {
            LayoutDirection.Rtl -> TextAlign.Right
            LayoutDirection.Ltr -> TextAlign.Left
        },
    )

fun TextStyle.forInput(): TextStyle = copy(textDirection = TextDirection.Content)

/**
 * Forces LTR so "@handle" never renders as "handle@" inside RTL chrome.
 */
fun TextStyle.forUsernameHandle(): TextStyle = copy(textDirection = TextDirection.Ltr)

/**
 * Keeps relative-time phrases (e.g. "۱ ساعت پیش") as one directional unit.
 */
fun TextStyle.forRelativeTime(): TextStyle = copy(textDirection = TextDirection.Content)

/** Username with leading @, always intended for [forUsernameHandle] text. */
fun usernameHandle(username: String): String = "@$username"

/**
 * Body/title text that follows each paragraph's script, not the app chrome direction.
 * Multi-line bodies are split on `\n` so an English line after Persian stays left-aligned.
 */
@Composable
fun ContentAwareText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
) {
    val appDirection = LocalLayoutDirection.current
    val paragraphs = remember(text) { text.split('\n') }

    if (paragraphs.size <= 1) {
        val direction = remember(text, appDirection) {
            layoutDirectionForContent(text, emptyDefault = appDirection)
        }
        Text(
            text = text,
            modifier = modifier.fillMaxWidth(),
            style = style.withContentDirection(direction),
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            softWrap = softWrap,
            onTextLayout = onTextLayout ?: {},
        )
        return
    }

    val unlimited = maxLines == Int.MAX_VALUE
    val visible = remember(paragraphs, maxLines, unlimited) {
        if (unlimited) paragraphs else paragraphs.take(maxLines.coerceAtLeast(1))
    }
    val clipped = !unlimited && paragraphs.size > visible.size

    Column(modifier = modifier.fillMaxWidth()) {
        visible.forEachIndexed { index, paragraph ->
            val direction = layoutDirectionForContent(paragraph, emptyDefault = appDirection)
            val last = index == visible.lastIndex
            Text(
                text = if (paragraph.isEmpty()) "\u00A0" else paragraph,
                modifier = Modifier.fillMaxWidth(),
                style = style.withContentDirection(direction),
                color = color,
                maxLines = if (unlimited) Int.MAX_VALUE else 1,
                overflow = when {
                    last && clipped -> TextOverflow.Ellipsis
                    last -> overflow
                    else -> TextOverflow.Clip
                },
                softWrap = softWrap,
                onTextLayout = if (last) (onTextLayout ?: {}) else ({}),
            )
        }
    }
}

package ir.xilo.app.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import ir.xilo.app.core.util.HashtagParser
import ir.xilo.app.theme.XiloBlue

private const val HASHTAG_TAG = "HASHTAG"

fun buildHashtagAnnotatedString(
    text: String,
    linkColor: Color = XiloBlue,
): androidx.compose.ui.text.AnnotatedString {
    if (text.isEmpty()) return androidx.compose.ui.text.AnnotatedString("")
    val matches = HashtagParser.findMatches(text)
    if (matches.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (m in matches) {
            if (m.start > cursor) {
                append(text.substring(cursor, m.start))
            }
            pushStringAnnotation(HASHTAG_TAG, m.tag)
            withStyle(SpanStyle(color = linkColor, fontWeight = FontWeight.Medium)) {
                append(text.substring(m.start, m.end))
            }
            pop()
            cursor = m.end
        }
        if (cursor < text.length) {
            append(text.substring(cursor))
        }
    }
}

/**
 * Body text with clickable #hashtags. Multi-paragraph content keeps first-strong direction.
 */
@Composable
fun HashtagAwareText(
    text: String,
    onHashtagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    linkColor: Color = XiloBlue,
    /** Invoked when the user taps non-hashtag text (e.g. open post). */
    onTextClick: (() -> Unit)? = null,
) {
    val appDirection = LocalLayoutDirection.current
    val paragraphs = remember(text) { text.split('\n') }
    // When maxLines applies to the whole body, only the first paragraph is shown.
    val visible = remember(paragraphs, maxLines) {
        if (maxLines == Int.MAX_VALUE) paragraphs else listOf(paragraphs.firstOrNull().orEmpty())
    }

    Column(modifier = modifier.fillMaxWidth()) {
        visible.forEach { paragraph ->
            val direction = remember(paragraph, appDirection) {
                layoutDirectionForContent(paragraph, emptyDefault = appDirection)
            }
            val annotated = remember(paragraph, linkColor) {
                buildHashtagAnnotatedString(paragraph, linkColor)
            }
            var layoutResult: TextLayoutResult? = null
            Text(
                text = annotated,
                style = style.withContentDirection(direction).let {
                    if (color != Color.Unspecified) it.copy(color = color) else it
                },
                maxLines = maxLines,
                overflow = overflow,
                softWrap = true,
                onTextLayout = { layoutResult = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(annotated, onHashtagClick, onTextClick) {
                        detectTapGestures { pos ->
                            val layout = layoutResult ?: return@detectTapGestures
                            val offset = layout.getOffsetForPosition(pos)
                            val tag = annotated
                                .getStringAnnotations(HASHTAG_TAG, offset, offset)
                                .firstOrNull()
                                ?.item
                            if (tag != null) {
                                onHashtagClick(tag)
                            } else {
                                onTextClick?.invoke()
                            }
                        }
                    },
            )
        }
    }
}

package ir.xilo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.xilo.app.R
import ir.xilo.app.theme.XiloBlue
import ir.xilo.app.theme.YekanBakhFontFamily
import ir.xilo.app.ui.postdetail.extractPlainText

private val CoverPalette = listOf(
    listOf(XiloBlue, Color(0xFF0A4A6E)),
    listOf(Color(0xFF14919B), Color(0xFF0A4A52)),
    listOf(Color(0xFF2D6A4F), Color(0xFF081C15)),
    listOf(Color(0xFF7B2D8E), Color(0xFF2D0A3A)),
    listOf(Color(0xFFC2410C), Color(0xFF431407)),
    listOf(Color(0xFF1E3A5F), Color(0xFF0B1220)),
)

/**
 * Attractive text cover for profile grid cells when a post has no cover image.
 * Background colors are deterministic from [seed] so cells stay stable across recompositions.
 */
@Composable
fun PostCoverPlaceholder(
    title: String,
    excerpt: String?,
    content: String,
    seed: String,
    modifier: Modifier = Modifier,
) {
    val colors = remember(seed) {
        CoverPalette[kotlin.math.abs(seed.hashCode()) % CoverPalette.size]
    }
    val label = remember(title, excerpt, content) {
        resolveCoverLabel(title, excerpt, content)
    }
    val fallback = stringResource(R.string.post_cover_untitled)
    val display = label.ifBlank { fallback }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = listOf(colors[0], colors[1]),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = display,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = TextStyle(
                fontFamily = YekanBakhFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp),
        )
    }
}

internal fun resolveCoverLabel(title: String, excerpt: String?, content: String): String {
    title.takeIf { it.isNotBlank() }?.let { return it.trim() }
    excerpt?.takeIf { it.isNotBlank() }?.let { return it.trim() }
    return extractPlainText(content)
        .lineSequence()
        .map { it.trim() }
        .firstOrNull { it.isNotEmpty() }
        .orEmpty()
}

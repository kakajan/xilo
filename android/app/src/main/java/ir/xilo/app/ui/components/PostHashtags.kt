package ir.xilo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ir.xilo.app.R
import ir.xilo.app.theme.IranSansXFontFamily
import ir.xilo.app.theme.XiloBlue

/**
 * Renders a list of tags as clickable #hashtags.
 * Supports all post modes (audio, text, article, photo, video, link, quote).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PostHashtagsFlow(
    tags: List<String>,
    onHashtagClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (tags.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        tags.forEach { rawTag ->
            val cleanTag = remember(rawTag) { rawTag.trim().removePrefix("#") }
            if (cleanTag.isNotBlank()) {
                PostHashtagChip(
                    tag = cleanTag,
                    onClick = { onHashtagClick(cleanTag) },
                )
            }
        }
    }
}

/**
 * Clickable hashtag chip with # prefix, styled cleanly for feed & detail.
 */
@Composable
fun PostHashtagChip(
    tag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipShape = RoundedCornerShape(10.dp)
    val displayTag = remember(tag) { "#${tag.trim().removePrefix("#")}" }
    val direction = remember(tag) { layoutDirectionForContent(tag, emptyDefault = LayoutDirection.Rtl) }

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Box(
            modifier = modifier
                .clip(chipShape)
                .background(XiloBlue.copy(alpha = 0.08f))
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = displayTag,
                color = XiloBlue,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = IranSansXFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Removable hashtag chip for create and edit post forms.
 */
@Composable
fun RemovableHashtagChip(
    tag: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chipShape = RoundedCornerShape(10.dp)
    val displayTag = remember(tag) { "#${tag.trim().removePrefix("#")}" }
    val direction = remember(tag) { layoutDirectionForContent(tag, emptyDefault = LayoutDirection.Rtl) }

    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = modifier
                .clip(chipShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
                .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(
                text = displayTag,
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = IranSansXFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                ),
                maxLines = 1,
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        role = Role.Button,
                        onClick = onRemove,
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                XiloIcon(
                    icon = XiloIcons.Close,
                    contentDescription = stringResource(R.string.post_hashtag_remove, displayTag),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

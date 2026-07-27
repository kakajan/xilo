package ir.xilo.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Global translucent scrim drawn in the system navigation-bar inset:
 * transparent at the top, fading into [MaterialTheme.colorScheme.background].
 *
 * Decorative only — no pointer input, so touches pass through to content below.
 */
@Composable
fun SystemNavigationBarScrim(modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsBottomHeight(WindowInsets.navigationBars)
            .background(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.0f to Color.Transparent,
                        0.35f to background.copy(alpha = 0.45f),
                        0.75f to background.copy(alpha = 0.88f),
                        1.0f to background,
                    ),
                ),
            ),
    )
}

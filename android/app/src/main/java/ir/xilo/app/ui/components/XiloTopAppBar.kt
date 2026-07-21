package ir.xilo.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import ir.xilo.app.R

/**
 * Horizontal safe-drawing insets for top chrome. Top (status bar) clearance is applied
 * via [Modifier.statusBarsPadding] so Material3's fixed bar height cannot clip into the
 * system status bar under edge-to-edge.
 */
@Composable
fun xiloTopAppBarWindowInsets(): WindowInsets =
    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiloTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBackClick: () -> Unit = {},
    centered: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.background,
    windowInsets: WindowInsets = xiloTopAppBarWindowInsets(),
    applyStatusBarsPadding: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {}
) {
    XiloTopAppBar(
        title = {
            if (title.isNotBlank()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        modifier = modifier,
        navigationIcon = {
            if (showBack) {
                IconButton(onClick = onBackClick) {
                    XiloIcon(
                        icon = XiloIcons.Back,
                        contentDescription = stringResource(R.string.common_back)
                    )
                }
            }
        },
        actions = actions,
        containerColor = containerColor,
        centerAligned = centered,
        windowInsets = windowInsets,
        applyStatusBarsPadding = applyStatusBarsPadding,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiloTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    containerColor: Color = MaterialTheme.colorScheme.background,
    centerAligned: Boolean = false,
    windowInsets: WindowInsets = xiloTopAppBarWindowInsets(),
    applyStatusBarsPadding: Boolean = true,
) {
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
    // Pad below the status bar outside TopAppBar so the bar's fixed 64dp row is not
    // squeezed into the system chrome. Parent sticky headers may already clear the
    // status bar — pass applyStatusBarsPadding = false to avoid a double gap.
    val barModifier = if (applyStatusBarsPadding) {
        modifier.statusBarsPadding()
    } else {
        modifier
    }

    if (centerAligned) {
        CenterAlignedTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            windowInsets = windowInsets,
            modifier = barModifier
        )
    } else {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            windowInsets = windowInsets,
            modifier = barModifier
        )
    }
}

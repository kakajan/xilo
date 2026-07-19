package ir.xilo.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
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
import ir.xilo.app.theme.XiloSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun XiloTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    showBack: Boolean = false,
    onBackClick: () -> Unit = {},
    centered: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.background,
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
        centerAligned = centered
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
    centerAligned: Boolean = false
) {
    val colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor)
    // MainScreen already applies status-bar padding for tab content; avoid a second inset here.
    val barModifier = modifier.height(XiloSpacing.topAppBarHeight)
    val noWindowInsets = WindowInsets(0, 0, 0, 0)

    if (centerAligned) {
        CenterAlignedTopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            windowInsets = noWindowInsets,
            modifier = barModifier
        )
    } else {
        TopAppBar(
            title = title,
            navigationIcon = navigationIcon,
            actions = actions,
            colors = colors,
            windowInsets = noWindowInsets,
            modifier = barModifier
        )
    }
}

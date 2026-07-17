package ir.xilo.app.ui.components

import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection

@Composable
fun XiloSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        // Keep Latin error copy LTR so trailing periods don't flip to the start in RTL UI.
        val direction = if (data.visuals.message.any {
                Character.UnicodeBlock.of(it) == Character.UnicodeBlock.ARABIC
            }
        ) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Snackbar(snackbarData = data)
        }
    }
}

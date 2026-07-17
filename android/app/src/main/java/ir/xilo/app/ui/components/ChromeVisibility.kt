package ir.xilo.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

@Stable
class ChromeVisibilityState {
    var isVisible by mutableStateOf(true)
        private set

    private var accumulatedDelta = 0f

    val nestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.SideEffect) return Offset.Zero
            updateFromScrollDelta(available.y)
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            updateFromScrollDelta(available.y * 0.08f)
            return Velocity.Zero
        }
    }

    fun show() {
        isVisible = true
        accumulatedDelta = 0f
    }

    fun hide() {
        isVisible = false
        accumulatedDelta = 0f
    }

    private fun updateFromScrollDelta(delta: Float) {
        if (delta == 0f) return

        accumulatedDelta += delta
        when {
            accumulatedDelta < -SCROLL_THRESHOLD -> hide()
            accumulatedDelta > SCROLL_THRESHOLD -> show()
        }
    }

    private companion object {
        const val SCROLL_THRESHOLD = 16f
    }
}

val LocalChromeVisibility = staticCompositionLocalOf<ChromeVisibilityState?> { null }

@Composable
fun rememberChromeVisibilityState(): ChromeVisibilityState = remember { ChromeVisibilityState() }

@Composable
fun Modifier.trackChromeVisibility(
    state: ChromeVisibilityState,
    listState: LazyListState? = null
): Modifier {
    if (listState != null) {
        LaunchedEffect(listState, state) {
            snapshotFlow {
                listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
            }.collect { atTop ->
                if (atTop) state.show()
            }
        }
    }
    return nestedScroll(state.nestedScrollConnection)
}

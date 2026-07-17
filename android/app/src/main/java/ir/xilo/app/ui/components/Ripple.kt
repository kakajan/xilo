package ir.xilo.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

fun Modifier.xiloClickableRipple(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = true),
        enabled = enabled,
        onClick = onClick
    )
}

fun Modifier.xiloClickableRippleUnbounded(
    enabled: Boolean = true,
    radius: Dp = 24.dp,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    clickable(
        interactionSource = interactionSource,
        indication = ripple(bounded = false, radius = radius),
        enabled = enabled,
        onClick = onClick
    )
}

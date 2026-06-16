package com.example.xilo.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = XiloBlue,
    secondary = DarkTextSecondary,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Color.White,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    outline = DarkBorder,
    error = ColorError
)

private val LightColorScheme = lightColorScheme(
    primary = XiloBlue,
    secondary = LightTextSecondary,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    outline = LightBorder,
    error = ColorError
)

data class BubbleColors(
    val ownBubble: Color,
    val othersBubble: Color
)

val LocalBubbleColors = staticCompositionLocalOf {
    BubbleColors(
        ownBubble = Color.Unspecified,
        othersBubble = Color.Unspecified
    )
}

object XiloTheme {
    val bubbleColors: BubbleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBubbleColors.current
}

@Composable
fun XiloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val bubbleColors = if (darkTheme) {
        BubbleColors(ownBubble = BubbleOwnDark, othersBubble = BubbleOthersDark)
    } else {
        BubbleColors(ownBubble = BubbleOwnLight, othersBubble = BubbleOthersLight)
    }

    CompositionLocalProvider(
        LocalBubbleColors provides bubbleColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

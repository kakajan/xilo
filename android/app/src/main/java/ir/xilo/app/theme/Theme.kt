package ir.xilo.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

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

val LocalPlatformTheme = staticCompositionLocalOf { DefaultPlatformTheme }

object XiloTheme {
    val bubbleColors: BubbleColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBubbleColors.current

    val platform: PlatformTheme
        @Composable
        @ReadOnlyComposable
        get() = LocalPlatformTheme.current
}

fun ThemePalette.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primarySurface,
    onPrimaryContainer = textPrimary,
    secondary = textSecondary,
    onSecondary = Color.White,
    secondaryContainer = backgroundSecondary,
    onSecondaryContainer = textPrimary,
    tertiary = primaryHover,
    onTertiary = Color.White,
    tertiaryContainer = primarySurface,
    onTertiaryContainer = textPrimary,
    background = background,
    onBackground = textPrimary,
    surface = background,
    onSurface = textPrimary,
    surfaceVariant = backgroundTertiary,
    onSurfaceVariant = textSecondary,
    surfaceTint = primary,
    // Explicit cool-gray containers — Material defaults are lavender-tinted.
    surfaceBright = background,
    surfaceDim = backgroundSecondary,
    surfaceContainerLowest = background,
    surfaceContainerLow = backgroundSecondary,
    surfaceContainer = backgroundSecondary,
    surfaceContainerHigh = backgroundTertiary,
    surfaceContainerHighest = backgroundTertiary,
    outline = border,
    outlineVariant = borderStrong,
    error = error,
    onError = Color.White,
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = error,
    inverseSurface = textPrimary,
    inverseOnSurface = background,
    inversePrimary = primaryHover,
    scrim = Color.Black,
)

fun ThemePalette.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = Color.White,
    primaryContainer = primarySurface,
    onPrimaryContainer = textPrimary,
    secondary = textSecondary,
    onSecondary = Color.White,
    secondaryContainer = backgroundSecondary,
    onSecondaryContainer = textPrimary,
    tertiary = primaryHover,
    onTertiary = Color.White,
    tertiaryContainer = primarySurface,
    onTertiaryContainer = textPrimary,
    background = background,
    onBackground = textPrimary,
    surface = backgroundSecondary,
    onSurface = textPrimary,
    surfaceVariant = backgroundTertiary,
    onSurfaceVariant = textSecondary,
    surfaceTint = primary,
    surfaceBright = backgroundTertiary,
    surfaceDim = background,
    surfaceContainerLowest = background,
    surfaceContainerLow = backgroundSecondary,
    surfaceContainer = backgroundSecondary,
    surfaceContainerHigh = backgroundTertiary,
    surfaceContainerHighest = backgroundTertiary,
    outline = border,
    outlineVariant = borderStrong,
    error = error,
    onError = Color.White,
    errorContainer = Color(0xFF3D1A1E),
    onErrorContainer = Color(0xFFFFB4AB),
    inverseSurface = textPrimary,
    inverseOnSurface = background,
    inversePrimary = primaryHover,
    scrim = Color.Black,
)

@Composable
fun XiloTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    platformTheme: PlatformTheme = DefaultPlatformTheme,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) platformTheme.dark else platformTheme.light
    val colorScheme = if (darkTheme) palette.toDarkColorScheme() else palette.toLightColorScheme()
    val bubbleColors = BubbleColors(
        ownBubble = palette.bubbleOwn,
        othersBubble = palette.bubbleOthers
    )

    CompositionLocalProvider(
        LocalBubbleColors provides bubbleColors,
        LocalPlatformTheme provides platformTheme,
        LocalLayoutDirection provides LayoutDirection.Rtl
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

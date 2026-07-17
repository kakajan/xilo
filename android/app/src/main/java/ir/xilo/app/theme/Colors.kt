package ir.xilo.app.theme

import androidx.compose.ui.graphics.Color

/** Convenience aliases for call sites; source of truth is [DefaultPlatformTheme]. */
val XiloBlue = DefaultPlatformTheme.light.primary
val TelegramBlue = Color(0xFF0088CC)

val LightBackground = DefaultPlatformTheme.light.background
val LightSurface = DefaultPlatformTheme.light.backgroundSecondary
val LightCard = DefaultPlatformTheme.light.background
val LightTextPrimary = DefaultPlatformTheme.light.textPrimary
val LightTextSecondary = DefaultPlatformTheme.light.textSecondary
val LightBorder = DefaultPlatformTheme.light.border

val BubbleOwnLight = DefaultPlatformTheme.light.bubbleOwn
val BubbleOthersLight = DefaultPlatformTheme.light.bubbleOthers

val DarkBackground = DefaultPlatformTheme.dark.background
val DarkSurface = DefaultPlatformTheme.dark.backgroundSecondary
val DarkCard = DefaultPlatformTheme.dark.backgroundSecondary
val DarkTextPrimary = DefaultPlatformTheme.dark.textPrimary
val DarkTextSecondary = DefaultPlatformTheme.dark.textSecondary
val DarkBorder = DefaultPlatformTheme.dark.border

val BubbleOwnDark = DefaultPlatformTheme.dark.bubbleOwn
val BubbleOthersDark = DefaultPlatformTheme.dark.bubbleOthers

val ColorError = DefaultPlatformTheme.light.error
val ColorSuccess = DefaultPlatformTheme.light.success
val ColorWarning = DefaultPlatformTheme.light.warning
val LikePink = Color(0xFFF91880)

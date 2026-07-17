package ir.xilo.app.theme

import androidx.compose.ui.graphics.Color

/**
 * Platform-wide theme palette from GET /api/platform/settings.
 * Defaults match ui-ux-spec §2 (cool gray + Xilo blue — never Material purple).
 */
data class ThemePalette(
    val primary: Color,
    val primaryHover: Color,
    val primaryPressed: Color,
    val primarySurface: Color,
    val background: Color,
    val backgroundSecondary: Color,
    val backgroundTertiary: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val border: Color,
    val borderStrong: Color,
    val error: Color,
    val success: Color,
    val warning: Color,
    val bubbleOwn: Color,
    val bubbleOthers: Color,
)

data class PlatformTheme(
    val light: ThemePalette,
    val dark: ThemePalette,
)

val DefaultPlatformTheme = PlatformTheme(
    light = ThemePalette(
        primary = Color(0xFF1D9BF0),
        primaryHover = Color(0xFF1A8CD8),
        primaryPressed = Color(0xFF1A7BC5),
        primarySurface = Color(0xFFE8F5FE),
        background = Color(0xFFFFFFFF),
        backgroundSecondary = Color(0xFFF7F9FA),
        backgroundTertiary = Color(0xFFEFF3F4),
        textPrimary = Color(0xFF0F1419),
        textSecondary = Color(0xFF536471),
        textTertiary = Color(0xFF8295A3),
        border = Color(0xFFEFF3F4),
        borderStrong = Color(0xFFCFD9DE),
        error = Color(0xFFF4212E),
        success = Color(0xFF00BA7C),
        warning = Color(0xFFFFAD1F),
        bubbleOwn = Color(0xFFE8F5FE),
        bubbleOthers = Color(0xFFF7F9FA),
    ),
    dark = ThemePalette(
        primary = Color(0xFF1D9BF0),
        primaryHover = Color(0xFF4DB8F5),
        primaryPressed = Color(0xFF6BC9F7),
        primarySurface = Color(0xFF1A2A3A),
        background = Color(0xFF15202B),
        backgroundSecondary = Color(0xFF192734),
        backgroundTertiary = Color(0xFF22303C),
        textPrimary = Color(0xFFE7E9EA),
        textSecondary = Color(0xFF71767B),
        textTertiary = Color(0xFF536471),
        border = Color(0xFF38444D),
        borderStrong = Color(0xFF4A5A66),
        error = Color(0xFFF4212E),
        success = Color(0xFF00BA7C),
        warning = Color(0xFFFFAD1F),
        bubbleOwn = Color(0xFF1E3A5F),
        bubbleOthers = Color(0xFF2C2C2E),
    ),
)

fun parseHexColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        val normalized = hex.trim().let { if (it.startsWith("#")) it else "#$it" }
        Color(android.graphics.Color.parseColor(normalized))
    } catch (_: IllegalArgumentException) {
        fallback
    }
}

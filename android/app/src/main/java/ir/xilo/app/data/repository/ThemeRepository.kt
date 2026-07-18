package ir.xilo.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.ThemePaletteDto
import ir.xilo.app.data.remote.dto.ThemeSettingsDto
import ir.xilo.app.theme.DefaultPlatformTheme
import ir.xilo.app.theme.PlatformTheme
import ir.xilo.app.theme.ThemePalette
import ir.xilo.app.theme.parseHexColor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    ;

    companion object {
        fun fromStorage(value: String?): ThemeMode = when (value) {
            "light" -> LIGHT
            "dark" -> DARK
            else -> SYSTEM
        }
    }

    fun storageValue(): String = when (this) {
        SYSTEM -> "system"
        LIGHT -> "light"
        DARK -> "dark"
    }
}

@Singleton
class ThemeRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val apiService: XiloApiService,
    private val json: Json,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(loadCachedOrDefault())
    val theme: StateFlow<PlatformTheme> = _theme.asStateFlow()

    private val _themeMode = MutableStateFlow(loadThemeMode())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.storageValue()).apply()
        _themeMode.value = mode
    }

    private fun loadThemeMode(): ThemeMode =
        ThemeMode.fromStorage(prefs.getString(KEY_THEME_MODE, null))

    suspend fun syncTheme(): Result<PlatformTheme> {
        return try {
            val response = apiService.getPlatformSettings()
            val dto = response.theme
            val next = if (dto != null) {
                cacheTheme(dto)
                dto.toPlatformTheme()
            } else {
                DefaultPlatformTheme
            }
            _theme.value = next
            Result.success(next)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadCachedOrDefault(): PlatformTheme {
        val raw = prefs.getString(KEY_THEME_JSON, null) ?: return DefaultPlatformTheme
        return try {
            json.decodeFromString<ThemeSettingsDto>(raw).toPlatformTheme()
        } catch (_: Exception) {
            DefaultPlatformTheme
        }
    }

    private fun cacheTheme(dto: ThemeSettingsDto) {
        prefs.edit().putString(KEY_THEME_JSON, json.encodeToString(dto)).apply()
    }

    private fun ThemeSettingsDto.toPlatformTheme(): PlatformTheme {
        return PlatformTheme(
            light = light.toPalette(DefaultPlatformTheme.light),
            dark = dark.toPalette(DefaultPlatformTheme.dark),
        )
    }

    private fun ThemePaletteDto.toPalette(fallback: ThemePalette): ThemePalette {
        return ThemePalette(
            primary = parseHexColor(primary, fallback.primary),
            primaryHover = parseHexColor(primaryHover, fallback.primaryHover),
            primaryPressed = parseHexColor(primaryPressed, fallback.primaryPressed),
            primarySurface = parseHexColor(primarySurface, fallback.primarySurface),
            background = parseHexColor(background, fallback.background),
            backgroundSecondary = parseHexColor(backgroundSecondary, fallback.backgroundSecondary),
            backgroundTertiary = parseHexColor(backgroundTertiary, fallback.backgroundTertiary),
            textPrimary = parseHexColor(textPrimary, fallback.textPrimary),
            textSecondary = parseHexColor(textSecondary, fallback.textSecondary),
            textTertiary = parseHexColor(textTertiary, fallback.textTertiary),
            border = parseHexColor(border, fallback.border),
            borderStrong = parseHexColor(borderStrong, fallback.borderStrong),
            error = parseHexColor(error, fallback.error),
            success = parseHexColor(success, fallback.success),
            warning = parseHexColor(warning, fallback.warning),
            bubbleOwn = parseHexColor(bubbleOwn, fallback.bubbleOwn),
            bubbleOthers = parseHexColor(bubbleOthers, fallback.bubbleOthers),
        )
    }

    companion object {
        private const val PREFS_NAME = "xilo_theme"
        private const val KEY_THEME_JSON = "platform_theme_json"
        private const val KEY_THEME_MODE = "theme_mode"
    }
}

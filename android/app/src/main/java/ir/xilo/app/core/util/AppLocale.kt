package ir.xilo.app.core.util

import android.content.Context
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.ui.unit.LayoutDirection
import ir.xilo.app.data.local.prefs.TokenManager
import java.util.Locale

object AppLocale {
    fun languageCode(context: Context): String =
        TokenManager.preferredLanguageFrom(context)

    fun localeForLanguage(code: String): Locale = when (code.lowercase(Locale.US)) {
        "fa" -> Locale.forLanguageTag("fa-IR")
        "ar" -> Locale.forLanguageTag("ar")
        "ru" -> Locale.forLanguageTag("ru")
        "tr" -> Locale.forLanguageTag("tr")
        "en" -> Locale.forLanguageTag("en")
        else -> Locale.forLanguageTag("fa-IR")
    }

    fun isRtlLanguage(code: String): Boolean =
        when (code.lowercase(Locale.US)) {
            "fa", "ar" -> true
            else -> false
        }

    fun layoutDirection(code: String): LayoutDirection =
        if (isRtlLanguage(code)) LayoutDirection.Rtl else LayoutDirection.Ltr

    fun wrap(context: Context, languageCode: String = languageCode(context)): Context {
        val locale = localeForLanguage(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocales(LocaleList(locale))
        config.setLayoutDirection(locale)
        return context.createConfigurationContext(config)
    }

    fun string(context: Context, resId: Int): String =
        wrap(context).getString(resId)
}

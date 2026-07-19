package ir.xilo.app.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ir.xilo.app.R

enum class AileLogoVariant {
    /** Gradient mark — compact chrome (feed, top bars). */
    MarkColored,

    /** Single-color mark — tintable contexts. */
    MarkMono,

    /** Locale wordmark — auth hero (replaces brand text). */
    Wordmark,

    /** Full lockup — splash / marketing. */
    Lockup,
}

/** Shared FA/EN wordmark canvas (~2.07:1) — keeps layout stable across locales. */
private const val WordmarkAspectRatio = 2.07f

private const val LockupAspectRatio = 2.2f

/**
 * Aile brand art for in-app surfaces.
 * Prefer [Wordmark] / [Lockup] over plain brand text on auth and splash.
 */
@Composable
fun AileBrandLogo(
    variant: AileLogoVariant,
    modifier: Modifier = Modifier,
    languageCode: String = "fa",
    height: Dp = when (variant) {
        AileLogoVariant.MarkColored, AileLogoVariant.MarkMono -> 32.dp
        AileLogoVariant.Wordmark -> 56.dp
        AileLogoVariant.Lockup -> 96.dp
    },
    contentDescription: String? = stringResource(R.string.app_name),
) {
    val resId = drawableFor(variant, languageCode)
    Image(
        painter = painterResource(resId),
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        alignment = Alignment.Center,
        modifier = when (variant) {
            AileLogoVariant.MarkColored, AileLogoVariant.MarkMono ->
                modifier.size(height)
            AileLogoVariant.Wordmark ->
                // Fixed box for every locale so language switches do not resize the logo.
                modifier
                    .height(height)
                    .width(height * WordmarkAspectRatio)
            AileLogoVariant.Lockup ->
                modifier
                    .height(height)
                    .width(height * LockupAspectRatio)
        },
    )
}

@DrawableRes
private fun drawableFor(variant: AileLogoVariant, languageCode: String): Int {
    return when (variant) {
        AileLogoVariant.MarkColored -> R.drawable.ic_aile_mark_colored
        AileLogoVariant.MarkMono -> R.drawable.ic_aile_mark_mono
        AileLogoVariant.Lockup -> R.drawable.ic_aile_lockup
        AileLogoVariant.Wordmark -> {
            val rtl = languageCode.equals("fa", ignoreCase = true) ||
                languageCode.equals("ar", ignoreCase = true)
            if (rtl) R.drawable.ic_aile_wordmark_fa else R.drawable.ic_aile_wordmark_en
        }
    }
}

package ir.xilo.app.core.util

import android.content.Context
import android.content.Intent
import ir.xilo.app.BuildConfig

object ShareActions {
    fun origin(): String = PublicWebUrls.normalizeOrigin(BuildConfig.PUBLIC_WEB_URL)

    fun postUrl(username: String, slug: String): String =
        PublicWebUrls.post(origin(), username, slug)

    fun commentUrl(username: String, slug: String, commentId: String): String =
        PublicWebUrls.comment(origin(), username, slug, commentId)

    fun profileUrl(username: String): String = PublicWebUrls.profile(origin(), username)

    fun sendText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }
}

package ir.xilo.app.data.local.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stable anonymous session id for post-view deduplication when the user is logged out.
 */
@Singleton
class AnalyticsSessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSessionId(): String {
        val existing = prefs.getString(KEY_SESSION_ID, null)
        if (!existing.isNullOrBlank() && existing.length >= 16) {
            return existing
        }
        val next = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_SESSION_ID, next).apply()
        return next
    }

    private companion object {
        const val PREFS_NAME = "xilo_analytics"
        const val KEY_SESSION_ID = "session_id"
    }
}

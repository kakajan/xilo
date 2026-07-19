package ir.xilo.app.data.local.prefs

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local compose drafts so title/body survive process death and navigation.
 * Keyed by `"new"` for create, or post id for edit.
 */
@Singleton
class ComposeDraftStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    data class Draft(
        val title: String,
        val content: String,
        val updatedAtMs: Long,
    ) {
        val isEmpty: Boolean get() = title.isBlank() && content.isBlank()
    }

    fun load(key: String = KEY_NEW): Draft? {
        val title = prefs.getString(titleKey(key), null) ?: return null
        val content = prefs.getString(contentKey(key), null) ?: return null
        val updatedAt = prefs.getLong(updatedKey(key), 0L)
        val draft = Draft(title = title, content = content, updatedAtMs = updatedAt)
        return draft.takeUnless { it.isEmpty }
    }

    fun save(title: String, content: String, key: String = KEY_NEW) {
        if (title.isBlank() && content.isBlank()) {
            clear(key)
            return
        }
        prefs.edit()
            .putString(titleKey(key), title)
            .putString(contentKey(key), content)
            .putLong(updatedKey(key), System.currentTimeMillis())
            .apply()
    }

    fun clear(key: String = KEY_NEW) {
        prefs.edit()
            .remove(titleKey(key))
            .remove(contentKey(key))
            .remove(updatedKey(key))
            .apply()
    }

    fun draftKey(editPostId: String?): String =
        if (editPostId.isNullOrBlank()) KEY_NEW else editPostId

    private fun titleKey(key: String) = "title_$key"
    private fun contentKey(key: String) = "content_$key"
    private fun updatedKey(key: String) = "updated_$key"

    companion object {
        const val KEY_NEW = "new"
        private const val PREFS_NAME = "xilo_compose_drafts"
    }
}

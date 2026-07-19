package ir.xilo.app.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeDraftStoreTest {

    @Test
    fun saveAndLoadRoundTripForNewDraft() {
        val prefs = FakePreferences()
        val store = ComposeDraftStore(contextWith(prefs))

        store.save(title = "عنوان", content = "متن پیش‌نویس")

        val draft = store.load()
        assertEquals("عنوان", draft!!.title)
        assertEquals("متن پیش‌نویس", draft.content)
        assertTrue(draft.updatedAtMs > 0L)
    }

    @Test
    fun editDraftUsesPostIdKey() {
        val prefs = FakePreferences()
        val store = ComposeDraftStore(contextWith(prefs))
        val key = store.draftKey("post-42")

        store.save(title = "ویرایش", content = "بدنه", key = key)

        assertNull(store.load(ComposeDraftStore.KEY_NEW))
        val draft = store.load(key)
        assertEquals("ویرایش", draft!!.title)
        assertEquals("بدنه", draft.content)
    }

    @Test
    fun blankDraftClearsStorage() {
        val prefs = FakePreferences()
        val store = ComposeDraftStore(contextWith(prefs))
        store.save(title = "x", content = "y")
        store.save(title = "  ", content = "")

        assertNull(store.load())
    }

    @Test
    fun clearRemovesKeys() {
        val prefs = FakePreferences()
        val store = ComposeDraftStore(contextWith(prefs))
        store.save(title = "a", content = "b")
        store.clear()
        assertNull(store.load())
    }

    private fun contextWith(prefs: FakePreferences): Context {
        val context = mockk<Context>()
        every { context.getSharedPreferences("xilo_compose_drafts", Context.MODE_PRIVATE) } returns prefs
        return context
    }

    private class FakePreferences(
        initial: Map<String, Any?> = emptyMap(),
    ) : SharedPreferences {
        val values: MutableMap<String, Any?> = initial.toMutableMap()
        val editor = FakeEditor()

        override fun getAll(): MutableMap<String, *> = values
        override fun getString(key: String?, defValue: String?): String? =
            values[key] as String? ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            @Suppress("UNCHECKED_CAST")
            (values[key] as MutableSet<String>?) ?: defValues
        override fun getInt(key: String?, defValue: Int): Int = values[key] as Int? ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = values[key] as Long? ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = values[key] as Float? ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            values[key] as Boolean? ?: defValue
        override fun contains(key: String?): Boolean = values.containsKey(key)
        override fun edit(): SharedPreferences.Editor = editor
        override fun registerOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(
            listener: SharedPreferences.OnSharedPreferenceChangeListener?,
        ) = Unit

        inner class FakeEditor : SharedPreferences.Editor {
            override fun putString(key: String?, value: String?): SharedPreferences.Editor {
                if (key != null) values[key] = value
                return this
            }
            override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor {
                if (key != null) this@FakePreferences.values[key] = values
                return this
            }
            override fun putInt(key: String?, value: Int): SharedPreferences.Editor {
                if (key != null) values[key] = value
                return this
            }
            override fun putLong(key: String?, value: Long): SharedPreferences.Editor {
                if (key != null) values[key] = value
                return this
            }
            override fun putFloat(key: String?, value: Float): SharedPreferences.Editor {
                if (key != null) values[key] = value
                return this
            }
            override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor {
                if (key != null) values[key] = value
                return this
            }
            override fun remove(key: String?): SharedPreferences.Editor {
                if (key != null) values.remove(key)
                return this
            }
            override fun clear(): SharedPreferences.Editor {
                values.clear()
                return this
            }
            override fun commit(): Boolean = true
            override fun apply() = Unit
        }
    }
}

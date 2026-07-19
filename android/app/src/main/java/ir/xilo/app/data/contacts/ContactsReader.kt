package ir.xilo.app.data.contacts

import android.content.Context
import android.provider.ContactsContract
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ContactHashPayload(
    val phoneHashes: List<String>,
    val emailHashes: List<String>,
)

/**
 * Reads device contacts via ContentResolver, normalizes phones/emails,
 * and returns SHA-256 hex digests for the match API (no raw PII).
 */
@Singleton
class ContactsReader @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun collectHashes(maxHashes: Int = ContactHashUtils.MAX_HASHES): ContactHashPayload {
        val phoneHashes = linkedSetOf<String>()
        val emailHashes = linkedSetOf<String>()

        readPhones(phoneHashes)
        readEmails(emailHashes)

        val (phones, emails) = ContactHashUtils.cappedHashLists(phoneHashes, emailHashes, maxHashes)
        return ContactHashPayload(phoneHashes = phones, emailHashes = emails)
    }

    private fun readPhones(out: MutableSet<String>) {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            if (idx < 0) return
            while (cursor.moveToNext()) {
                val raw = cursor.getString(idx) ?: continue
                ContactHashUtils.hashPhone(raw)?.let { out.add(it) }
                if (out.size >= ContactHashUtils.MAX_HASHES) return
            }
        }
    }

    private fun readEmails(out: MutableSet<String>) {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Email.ADDRESS)
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Email.CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
            if (idx < 0) return
            while (cursor.moveToNext()) {
                val raw = cursor.getString(idx) ?: continue
                ContactHashUtils.hashEmail(raw)?.let { out.add(it) }
                if (out.size >= ContactHashUtils.MAX_HASHES) return
            }
        }
    }
}

package ir.xilo.app.data.contacts

import java.security.MessageDigest

/**
 * Client-side contact normalization and hashing for match API.
 * Uploads SHA-256 hex of normalized values only — never raw phones/emails.
 * Server applies HMAC with pepper before lookup.
 */
object ContactHashUtils {
    const val MAX_HASHES = 500

    fun normalizePhone(raw: String): String? {
        var digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        while (digits.startsWith("00") && digits.length > 2) {
            digits = digits.removePrefix("00")
        }
        if (digits.length == 11 && digits.startsWith("09")) {
            digits = "98" + digits.substring(1)
        }
        return digits.takeIf { it.isNotEmpty() }
    }

    fun normalizeEmail(raw: String): String? {
        val normalized = raw.trim().lowercase()
        if (normalized.isEmpty() || !normalized.contains('@')) return null
        return normalized
    }

    fun sha256Hex(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { b -> "%02x".format(b) }
    }

    fun hashPhone(raw: String): String? =
        normalizePhone(raw)?.let { sha256Hex(it) }

    fun hashEmail(raw: String): String? =
        normalizeEmail(raw)?.let { sha256Hex(it) }

    /**
     * Deduplicate and cap combined phone+email hashes at [MAX_HASHES].
     * Phones are preferred when trimming.
     */
    fun cappedHashLists(
        phoneHashes: Collection<String>,
        emailHashes: Collection<String>,
        max: Int = MAX_HASHES,
    ): Pair<List<String>, List<String>> {
        val phones = phoneHashes.filter { it.isNotBlank() }.distinct()
        val emails = emailHashes.filter { it.isNotBlank() }.distinct()
        if (phones.size + emails.size <= max) {
            return phones to emails
        }
        val phoneTake = phones.size.coerceAtMost(max)
        val emailTake = (max - phoneTake).coerceAtLeast(0)
        return phones.take(phoneTake) to emails.take(emailTake)
    }
}

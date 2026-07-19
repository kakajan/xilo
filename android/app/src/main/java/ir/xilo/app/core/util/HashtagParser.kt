package ir.xilo.app.core.util

import java.text.Normalizer

/** Instagram/X-style hashtag helpers (mirrors backend/pkg/hashtag). */
object HashtagParser {
    const val MAX_TAGS = 10
    const val MAX_TAG_LEN = 30

    private val urlRegex = Regex("""(?i)https?://[^\s<>"']+|www\.[^\s<>"']+""")
    private val tagBodyRegex = Regex("""^[\p{L}\p{N}_-]{1,30}$""")

    data class Match(val start: Int, val end: Int, val tag: String)

    fun normalize(raw: String): String {
        var s = raw.trim().removePrefix("#")
        s = Normalizer.normalize(s, Normalizer.Form.NFC)
        if (s.isEmpty() || s.length > MAX_TAG_LEN) return ""
        if (!tagBodyRegex.matches(s)) return ""
        if (s.all { it.isDigit() }) return ""
        return foldLatin(s)
    }

    private fun foldLatin(s: String): String =
        buildString(s.length) {
            for (ch in s) {
                append(if (ch.code <= 0x7F && ch.isLetter()) ch.lowercaseChar() else ch)
            }
        }

    fun extract(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val matches = findMatches(text)
        val out = ArrayList<String>(matches.size.coerceAtMost(MAX_TAGS))
        val seen = HashSet<String>()
        for (m in matches) {
            val key = m.tag.lowercase()
            if (!seen.add(key)) continue
            out.add(m.tag)
            if (out.size >= MAX_TAGS) break
        }
        return out
    }

    fun merge(extracted: List<String>, explicit: List<String>): List<String> {
        val out = ArrayList<String>()
        val seen = HashSet<String>()
        fun add(raw: String) {
            val n = normalize(raw)
            if (n.isEmpty()) return
            val key = n.lowercase()
            if (!seen.add(key)) return
            out.add(n)
        }
        for (t in extracted) {
            if (out.size >= MAX_TAGS) return out
            add(t)
        }
        for (t in explicit) {
            if (out.size >= MAX_TAGS) return out
            add(t)
        }
        return out
    }

    fun findMatches(text: String): List<Match> {
        if (text.isEmpty()) return emptyList()
        val masked = maskUrls(text)
        val matches = ArrayList<Match>()
        var i = 0
        while (i < masked.length) {
            if (masked[i] != '#') {
                i++
                continue
            }
            if (i > 0 && !isBoundary(masked[i - 1])) {
                i++
                continue
            }
            var j = i + 1
            while (j < masked.length && isTagChar(masked[j])) j++
            if (j == i + 1) {
                i++
                continue
            }
            val body = masked.substring(i + 1, j)
            if (body.isNotEmpty() && body.all { it == ' ' }) {
                i = j
                continue
            }
            val norm = normalize(body)
            if (norm.isNotEmpty()) {
                matches.add(Match(i, j, norm))
            }
            i = j
        }
        return matches
    }

    /** Active `#query` ending at [cursor] (inclusive end index in text). */
    fun activeQuery(text: String, cursor: Int): Triple<String, Int, Int>? {
        if (cursor < 0 || cursor > text.length) return null
        var i = cursor - 1
        while (i >= 0 && isTagChar(text[i])) i--
        if (i < 0 || text[i] != '#') return null
        if (i > 0 && !isBoundary(text[i - 1])) return null
        val from = i
        val query = text.substring(from + 1, cursor)
        if (query.length > MAX_TAG_LEN) return null
        return Triple(query, from, cursor)
    }

    private fun maskUrls(text: String): String =
        urlRegex.replace(text) { m -> " ".repeat(m.value.length) }

    private fun isBoundary(ch: Char): Boolean =
        ch.isWhitespace() || ch.isPunctuation() || ch.isSymbol()

    private fun isTagChar(ch: Char): Boolean =
        ch.isLetterOrDigit() || ch == '_' || ch == '-'

    private fun Char.isPunctuation(): Boolean =
        Character.getType(this).let {
            it == Character.CONNECTOR_PUNCTUATION.toInt() ||
                it == Character.DASH_PUNCTUATION.toInt() ||
                it == Character.START_PUNCTUATION.toInt() ||
                it == Character.END_PUNCTUATION.toInt() ||
                it == Character.INITIAL_QUOTE_PUNCTUATION.toInt() ||
                it == Character.FINAL_QUOTE_PUNCTUATION.toInt() ||
                it == Character.OTHER_PUNCTUATION.toInt()
        }

    private fun Char.isSymbol(): Boolean =
        Character.getType(this).let {
            it == Character.MATH_SYMBOL.toInt() ||
                it == Character.CURRENCY_SYMBOL.toInt() ||
                it == Character.MODIFIER_SYMBOL.toInt() ||
                it == Character.OTHER_SYMBOL.toInt()
        }
}

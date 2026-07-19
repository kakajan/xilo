package ir.xilo.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HashtagParserTest {

    @Test
    fun extract_persianAndLatin() {
        assertEquals(
            listOf("خبر", "xilo_app"),
            HashtagParser.extract("سلام #خبر و #Xilo_App"),
        )
    }

    @Test
    fun extract_skipsUrlFragment() {
        assertEquals(
            listOf("real"),
            HashtagParser.extract("see https://example.com/path#fragment and #real"),
        )
    }

    @Test
    fun extract_rejectsDigitsOnly() {
        assertEquals(listOf("ok1"), HashtagParser.extract("#123 #ok1"))
    }

    @Test
    fun merge_capsAtTen() {
        val extracted = (0 until 8).map { "${('a' + it)}tag" }
        val merged = HashtagParser.merge(extracted, listOf("x1", "x2", "x3"))
        assertEquals(10, merged.size)
        assertEquals("x1", merged[8])
    }

    @Test
    fun activeQuery_atCursor() {
        val text = "hello #ne"
        val active = HashtagParser.activeQuery(text, text.length)
        assertEquals("ne", active?.first)
        assertTrue(active != null)
    }
}

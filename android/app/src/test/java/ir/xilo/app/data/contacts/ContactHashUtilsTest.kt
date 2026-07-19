package ir.xilo.app.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContactHashUtilsTest {

    @Test
    fun normalizePhone_iranMobile09_becomes98WithoutLeadingZero() {
        assertEquals("989123456789", ContactHashUtils.normalizePhone("09123456789"))
        assertEquals("989123456789", ContactHashUtils.normalizePhone("+98 912 345 6789"))
    }

    @Test
    fun normalizePhone_stripsLeading00() {
        assertEquals("989123456789", ContactHashUtils.normalizePhone("00989123456789"))
    }

    @Test
    fun normalizePhone_rejectsEmpty() {
        assertNull(ContactHashUtils.normalizePhone("abc"))
        assertNull(ContactHashUtils.normalizePhone(""))
    }

    @Test
    fun normalizeEmail_trimsAndLowercases() {
        assertEquals("a@b.com", ContactHashUtils.normalizeEmail("  A@B.Com "))
    }

    @Test
    fun normalizeEmail_rejectsInvalid() {
        assertNull(ContactHashUtils.normalizeEmail("not-an-email"))
        assertNull(ContactHashUtils.normalizeEmail("   "))
    }

    @Test
    fun sha256Hex_isStableLowercaseHex() {
        val hash = ContactHashUtils.sha256Hex("989123456789")
        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
        assertEquals(hash, ContactHashUtils.hashPhone("09123456789"))
    }

    @Test
    fun cappedHashLists_prefersPhonesWhenTrimming() {
        val phones = (1..400).map { "p$it" }
        val emails = (1..200).map { "e$it" }
        val (p, e) = ContactHashUtils.cappedHashLists(phones, emails, max = 500)
        assertEquals(400, p.size)
        assertEquals(100, e.size)
    }
}

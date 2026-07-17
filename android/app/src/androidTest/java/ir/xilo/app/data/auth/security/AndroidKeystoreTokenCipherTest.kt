package ir.xilo.app.data.auth.security

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AndroidKeystoreTokenCipherTest {

    @Test
    fun roundTripEncryptDecryptUsesDistinctCiphertexts() {
        val alias = "ir.xilo.app.auth.test." + UUID.randomUUID()
        val cipher = AndroidKeystoreTokenCipher(alias)
        val plaintext = """{"v":1,"a":"access-token","r":"refresh-token"}""".toByteArray()

        try {
            val first = cipher.encrypt(plaintext)
            val second = cipher.encrypt(plaintext)

            assertNotEquals(first, second)
            assertArrayEquals(plaintext, cipher.decrypt(first))
            assertArrayEquals(plaintext, cipher.decrypt(second))
        } finally {
            cipher.clearKey()
        }
    }

    @Test
    fun clearKeyMakesExistingCiphertextUnreadable() {
        val alias = "ir.xilo.app.auth.test." + UUID.randomUUID()
        val cipher = AndroidKeystoreTokenCipher(alias)
        val plaintext = "session-material".toByteArray()
        val ciphertext = cipher.encrypt(plaintext)

        cipher.clearKey()

        var failed = false
        try {
            cipher.decrypt(ciphertext)
        } catch (_: TokenCipherException) {
            failed = true
        }
        assertTrue(failed)

        // A fresh key may be created on encrypt after clear; decrypt of old blob must still fail.
        val replacement = AndroidKeystoreTokenCipher(alias)
        try {
            val newCiphertext = replacement.encrypt(plaintext)
            assertFalse(newCiphertext == ciphertext)
            var oldStillFails = false
            try {
                replacement.decrypt(ciphertext)
            } catch (_: TokenCipherException) {
                oldStillFails = true
            }
            assertTrue(oldStillFails)
        } finally {
            replacement.clearKey()
        }
    }
}

package ir.xilo.app.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verifyOrder
import ir.xilo.app.data.auth.refresh.AuthTokens
import ir.xilo.app.data.auth.security.TokenCipher
import ir.xilo.app.data.auth.security.TokenCipherException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Base64

class TokenManagerTest {

    @Test
    fun saveTokensCommitsEncryptedBlobAndRemovesLegacyKeys() {
        val preferences = FakePreferences(
            "access_token" to "stale-access",
            "refresh_token" to "stale-refresh",
        )
        val cipher = FakeTokenCipher()
        val manager = tokenManager(preferences, cipher)
        // Init may migrate legacy plaintext with a commit; count only saveTokens.
        preferences.applyCount = 0
        preferences.commitCount = 0

        manager.saveTokens("new-access", "new-refresh")

        assertEquals("new-access", manager.getAccessToken())
        assertEquals("new-refresh", manager.getRefreshToken())
        assertTrue(manager.isAuthenticatedFlow.value)
        assertNull(preferences.values["access_token"])
        assertNull(preferences.values["refresh_token"])
        assertTrue(preferences.values["encrypted_session_v1"]!!.startsWith("enc:"))
        assertEquals(1, preferences.commitCount)
        assertEquals(0, preferences.applyCount)
        verifyOrder {
            preferences.editor.putString("encrypted_session_v1", any())
            preferences.editor.remove("access_token")
            preferences.editor.remove("refresh_token")
            preferences.editor.commit()
        }
    }

    @Test
    fun saveTokensThrowsAndLeavesUnauthenticatedWhenEncryptFails() {
        val preferences = FakePreferences()
        val cipher = FakeTokenCipher(failEncrypt = true)
        val manager = tokenManager(preferences, cipher)

        try {
            manager.saveTokens("new-access", "new-refresh")
            fail("Expected TokenCipherException")
        } catch (_: TokenCipherException) {
            // expected
        }

        assertFalse(manager.isAuthenticatedFlow.value)
        assertNull(manager.getAccessToken())
        assertNull(preferences.values["encrypted_session_v1"])
    }

    @Test
    fun saveTokensThrowsAndLeavesUnauthenticatedWhenCommitFails() {
        val preferences = FakePreferences(commitSucceeds = false)
        val cipher = FakeTokenCipher()
        val manager = tokenManager(preferences, cipher)

        try {
            manager.saveTokens("new-access", "new-refresh")
            fail("Expected TokenCipherException")
        } catch (_: TokenCipherException) {
            // expected
        }

        assertFalse(manager.isAuthenticatedFlow.value)
        assertNull(manager.getAccessToken())
        assertNull(preferences.values["encrypted_session_v1"])
        assertEquals(1, preferences.commitCount)
    }

    @Test
    fun blockingReplacementCommitsEncryptedBlobOnce() {
        val preferences = FakePreferences()
        val cipher = FakeTokenCipher()
        val manager = tokenManager(preferences, cipher)
        manager.saveTokens("old-access", "old-refresh")
        preferences.applyCount = 0
        preferences.commitCount = 0

        assertTrue(
            manager.replaceTokensBlocking(
                expectedRefreshToken = "old-refresh",
                replacement = AuthTokens("new-access", "new-refresh"),
            )
        )

        assertEquals("new-access", manager.getAccessToken())
        assertEquals("new-refresh", manager.getRefreshToken())
        assertEquals(1, preferences.commitCount)
        assertEquals(0, preferences.applyCount)
        verifyOrder {
            preferences.editor.putString("encrypted_session_v1", any())
            preferences.editor.remove("access_token")
            preferences.editor.remove("refresh_token")
            preferences.editor.commit()
        }
    }

    @Test
    fun terminalClearRemovesBlobAndClearsCipherKey() {
        val preferences = FakePreferences()
        val cipher = FakeTokenCipher()
        val manager = tokenManager(preferences, cipher)
        manager.saveTokens("old-access", "old-refresh")

        assertTrue(manager.isAuthenticatedFlow.value)
        assertTrue(manager.clearTokensBlocking("old-refresh"))

        assertNull(manager.getAccessToken())
        assertNull(manager.getRefreshToken())
        assertFalse(manager.isAuthenticatedFlow.value)
        assertNull(preferences.values["encrypted_session_v1"])
        assertTrue(cipher.keyCleared)
    }

    @Test
    fun legacyPlaintextTokensAreMigratedToEncryptedBlob() {
        val preferences = FakePreferences(
            "access_token" to "legacy-access",
            "refresh_token" to "legacy-refresh",
        )
        val cipher = FakeTokenCipher()
        val manager = tokenManager(preferences, cipher)

        assertEquals("legacy-access", manager.getAccessToken())
        assertEquals("legacy-refresh", manager.getRefreshToken())
        assertTrue(manager.isAuthenticatedFlow.value)
        assertNull(preferences.values["access_token"])
        assertNull(preferences.values["refresh_token"])
        assertTrue(preferences.values.containsKey("encrypted_session_v1"))
    }

    @Test
    fun corruptEncryptedBlobClearsSessionAndKey() {
        val invalidBlob = Base64.getEncoder().encodeToString("""{"v":1}""".toByteArray())
        val preferences = FakePreferences(
            "encrypted_session_v1" to "enc:$invalidBlob",
        )
        val cipher = FakeTokenCipher()
        val manager = tokenManager(preferences, cipher)

        assertNull(manager.getTokens())
        assertFalse(manager.isAuthenticatedFlow.value)
        assertNull(preferences.values["encrypted_session_v1"])
        assertTrue(cipher.keyCleared)
    }

    @Test
    fun decryptFailureClearsSession() {
        val preferences = FakePreferences(
            "encrypted_session_v1" to "broken",
        )
        val cipher = FakeTokenCipher(failDecrypt = true)
        val manager = tokenManager(preferences, cipher)

        assertNull(manager.getTokens())
        assertNull(preferences.values["encrypted_session_v1"])
        assertTrue(cipher.keyCleared)
    }

    private fun tokenManager(
        preferences: FakePreferences,
        cipher: TokenCipher,
    ): TokenManager {
        val context = mockk<Context>()
        every {
            context.getSharedPreferences("xilo_auth_prefs", Context.MODE_PRIVATE)
        } returns preferences.preferences
        return TokenManager(context, cipher)
    }

    private class FakeTokenCipher(
        private val failDecrypt: Boolean = false,
        private val failEncrypt: Boolean = false,
    ) : TokenCipher {
        var keyCleared = false
            private set

        override fun encrypt(plaintext: ByteArray): String {
            if (failEncrypt) {
                throw TokenCipherException("Unable to encrypt authentication tokens")
            }
            if (keyCleared) {
                throw TokenCipherException("Authentication token key was cleared")
            }
            return "enc:" + Base64.getEncoder().encodeToString(plaintext)
        }

        override fun decrypt(ciphertext: String): ByteArray {
            if (failDecrypt || keyCleared || !ciphertext.startsWith("enc:")) {
                throw TokenCipherException("Unable to decrypt authentication tokens")
            }
            return Base64.getDecoder().decode(ciphertext.removePrefix("enc:"))
        }

        override fun clearKey() {
            keyCleared = true
        }
    }

    private class FakePreferences(
        vararg initialValues: Pair<String, String>,
        private val commitSucceeds: Boolean = true,
    ) {
        val values = initialValues.toMap().toMutableMap()
        private val pendingValues = mutableMapOf<String, String?>()
        val preferences = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        var commitCount = 0
        var applyCount = 0

        init {
            every { preferences.getString(any(), any()) } answers {
                values[firstArg()] ?: secondArg()
            }
            every { preferences.edit() } answers {
                pendingValues.clear()
                editor
            }
            every { editor.putString(any(), any()) } answers {
                pendingValues[firstArg()] = secondArg()
                editor
            }
            every { editor.remove(any()) } answers {
                pendingValues[firstArg()] = null
                editor
            }
            every { editor.commit() } answers {
                commitCount += 1
                if (commitSucceeds) {
                    flushPending()
                } else {
                    pendingValues.clear()
                }
                commitSucceeds
            }
            every { editor.apply() } answers {
                flushPending()
                applyCount += 1
            }
        }

        private fun flushPending() {
            pendingValues.forEach { (key, value) ->
                if (value == null) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
            }
            pendingValues.clear()
        }
    }
}

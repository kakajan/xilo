package ir.xilo.app.data.local.prefs

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.xilo.app.data.auth.refresh.AuthTokenStore
import ir.xilo.app.data.auth.refresh.AuthTokens
import ir.xilo.app.data.auth.security.TokenCipher
import ir.xilo.app.data.auth.security.TokenCipherException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the access/refresh pair as one Keystore-backed ciphertext blob.
 *
 * Plaintext blob format (UTF-8 JSON):
 * `{"v":1,"a":"<access>","r":"<refresh>"}`
 *
 * Legacy plaintext SharedPreferences keys are migrated once, then removed.
 */
@Singleton
class TokenManager @Inject constructor(
    @ApplicationContext context: Context,
    private val tokenCipher: TokenCipher,
) : AuthTokenStore {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val tokenLock = Any()
    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticatedFlow: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    init {
        _isAuthenticated.value = synchronized(tokenLock) { readTokensLocked() != null }
    }

    fun saveTokens(accessToken: String, refreshToken: String) {
        require(accessToken.isNotBlank() && refreshToken.isNotBlank()) {
            "Authentication tokens must not be blank"
        }
        synchronized(tokenLock) {
            val persisted = persistEncryptedLocked(
                AuthTokens(accessToken, refreshToken),
                useCommit = true,
            )
            if (!persisted) {
                _isAuthenticated.value = false
                throw TokenCipherException("Failed to persist encrypted authentication tokens")
            }
            _isAuthenticated.value = true
        }
    }

    fun getAccessToken(): String? = getTokens()?.accessToken

    fun getRefreshToken(): String? = getTokens()?.refreshToken

    override fun getTokens(): AuthTokens? {
        return synchronized(tokenLock) {
            readTokensLocked()
        }
    }

    override fun replaceTokensBlocking(
        expectedRefreshToken: String,
        replacement: AuthTokens,
    ): Boolean {
        if (replacement.accessToken.isBlank() || replacement.refreshToken.isBlank()) {
            return false
        }
        return synchronized(tokenLock) {
            val current = readTokensLocked() ?: return@synchronized false
            if (current.refreshToken != expectedRefreshToken) {
                return@synchronized false
            }
            val committed = persistEncryptedLocked(replacement, useCommit = true)
            if (committed) {
                _isAuthenticated.value = true
            }
            committed
        }
    }

    fun clearTokens() {
        synchronized(tokenLock) {
            wipeSessionLocked(useCommit = false)
            _isAuthenticated.value = false
        }
    }

    override fun clearTokensBlocking(expectedRefreshToken: String): Boolean {
        return synchronized(tokenLock) {
            val current = readTokensLocked() ?: return@synchronized false
            if (current.refreshToken != expectedRefreshToken) {
                return@synchronized false
            }
            val cleared = wipeSessionLocked(useCommit = true)
            _isAuthenticated.value = false
            cleared
        }
    }

    fun saveUser(id: String, username: String, role: String = "reader") {
        prefs.edit()
            .putString("user_id", id)
            .putString("username", username)
            .putString("user_role", role)
            .commit()
    }

    fun clearUser() {
        prefs.edit()
            .remove("user_id")
            .remove("username")
            .remove("user_role")
            .remove(USERNAME_PENDING_KEY)
            .remove(PREFERRED_LANGUAGE_KEY)
            .apply()
    }

    fun getUserId(): String? {
        return prefs.getString("user_id", null)
    }

    fun getUsername(): String? {
        return prefs.getString("username", null)
    }

    fun getRole(): String? {
        return prefs.getString("user_role", null)
    }

    fun isOnboardingCompleted(): Boolean {
        return prefs.getBoolean("onboarding_completed", false)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("onboarding_completed", completed).apply()
    }

    fun getPreferredCalendar(): String {
        return prefs.getString(PREFERRED_CALENDAR_KEY, "auto") ?: "auto"
    }

    fun setPreferredCalendar(value: String) {
        prefs.edit().putString(PREFERRED_CALENDAR_KEY, value).apply()
    }

    fun isUsernamePending(): Boolean =
        prefs.getBoolean(USERNAME_PENDING_KEY, false)

    fun setUsernamePending(pending: Boolean) {
        prefs.edit().putBoolean(USERNAME_PENDING_KEY, pending).apply()
    }

    fun getPreferredLanguage(): String =
        prefs.getString(PREFERRED_LANGUAGE_KEY, "fa") ?: "fa"

    fun setPreferredLanguage(value: String) {
        prefs.edit().putString(PREFERRED_LANGUAGE_KEY, value.ifBlank { "fa" }).apply()
    }

    private fun readTokensLocked(): AuthTokens? {
        val encrypted = prefs.getString(ENCRYPTED_SESSION_KEY, null)
        if (!encrypted.isNullOrBlank()) {
            return decryptSessionOrWipe(encrypted)
        }

        val legacyAccess = prefs.getString(LEGACY_ACCESS_TOKEN_KEY, null)
        val legacyRefresh = prefs.getString(LEGACY_REFRESH_TOKEN_KEY, null)
        if (legacyAccess.isNullOrBlank() || legacyRefresh.isNullOrBlank()) {
            if (!legacyAccess.isNullOrBlank() || !legacyRefresh.isNullOrBlank()) {
                // Partial legacy state is unusable; clear it without touching Keystore if unused.
                prefs.edit()
                    .remove(LEGACY_ACCESS_TOKEN_KEY)
                    .remove(LEGACY_REFRESH_TOKEN_KEY)
                    .commit()
            }
            return null
        }

        val migrated = AuthTokens(legacyAccess, legacyRefresh)
        return if (persistEncryptedLocked(migrated, useCommit = true)) {
            migrated
        } else {
            null
        }
    }

    private fun decryptSessionOrWipe(ciphertext: String): AuthTokens? {
        return try {
            val plaintext = tokenCipher.decrypt(ciphertext)
            parseSessionBlob(plaintext)
        } catch (_: TokenCipherException) {
            wipeSessionLocked(useCommit = true)
            null
        } catch (_: IllegalArgumentException) {
            wipeSessionLocked(useCommit = true)
            null
        }
    }

    private fun persistEncryptedLocked(tokens: AuthTokens, useCommit: Boolean): Boolean {
        val ciphertext = try {
            tokenCipher.encrypt(encodeSessionBlob(tokens))
        } catch (_: TokenCipherException) {
            wipeSessionLocked(useCommit = true)
            return false
        }

        val editor = prefs.edit()
            .putString(ENCRYPTED_SESSION_KEY, ciphertext)
            .remove(LEGACY_ACCESS_TOKEN_KEY)
            .remove(LEGACY_REFRESH_TOKEN_KEY)
        return if (useCommit) {
            editor.commit()
        } else {
            editor.apply()
            true
        }
    }

    private fun wipeSessionLocked(useCommit: Boolean): Boolean {
        val editor = prefs.edit()
            .remove(ENCRYPTED_SESSION_KEY)
            .remove(LEGACY_ACCESS_TOKEN_KEY)
            .remove(LEGACY_REFRESH_TOKEN_KEY)
        val cleared = if (useCommit) {
            editor.commit()
        } else {
            editor.apply()
            true
        }
        try {
            tokenCipher.clearKey()
        } catch (_: TokenCipherException) {
            // Session material is already removed from prefs; key wipe is best-effort.
        }
        return cleared
    }

    private fun encodeSessionBlob(tokens: AuthTokens): ByteArray {
        return sessionJson.encodeToString(
            SessionBlob(
                v = BLOB_VERSION,
                a = tokens.accessToken,
                r = tokens.refreshToken,
            )
        ).toByteArray(StandardCharsets.UTF_8)
    }

    private fun parseSessionBlob(plaintext: ByteArray): AuthTokens {
        val blob = try {
            sessionJson.decodeFromString(SessionBlob.serializer(), String(plaintext, StandardCharsets.UTF_8))
        } catch (error: Exception) {
            throw IllegalArgumentException("Authentication session blob is malformed", error)
        }
        require(blob.v == BLOB_VERSION) {
            "Unsupported authentication session blob version"
        }
        require(blob.a.isNotBlank() && blob.r.isNotBlank()) {
            "Authentication session blob is incomplete"
        }
        return AuthTokens(accessToken = blob.a, refreshToken = blob.r)
    }

    @Serializable
    private data class SessionBlob(
        val v: Int,
        val a: String,
        val r: String,
    )

    private companion object {
        const val PREFS_NAME = "xilo_auth_prefs"
        const val ENCRYPTED_SESSION_KEY = "encrypted_session_v1"
        const val LEGACY_ACCESS_TOKEN_KEY = "access_token"
        const val LEGACY_REFRESH_TOKEN_KEY = "refresh_token"
        const val PREFERRED_CALENDAR_KEY = "preferred_calendar"
        const val USERNAME_PENDING_KEY = "username_pending"
        const val PREFERRED_LANGUAGE_KEY = "preferred_language"
        const val BLOB_VERSION = 1

        private val sessionJson = Json {
            ignoreUnknownKeys = false
            encodeDefaults = true
        }
    }
}

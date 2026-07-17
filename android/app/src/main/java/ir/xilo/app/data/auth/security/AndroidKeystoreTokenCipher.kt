package ir.xilo.app.data.auth.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidKeystoreTokenCipher private constructor(
    private val keyAlias: String,
    private val secureRandom: SecureRandom,
) : TokenCipher {
    @Inject
    constructor() : this(DEFAULT_KEY_ALIAS, SecureRandom())

    internal constructor(keyAlias: String) : this(keyAlias, SecureRandom())

    private val cipherLock = Any()

    override fun encrypt(plaintext: ByteArray): String = synchronized(cipherLock) {
        cryptoOperation("Unable to encrypt authentication tokens") {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            // With setRandomizedEncryptionRequired(true), Android Keystore must generate
            // the GCM IV. Providing CALLER_NONCE is rejected (CALLER_NONCE_PROHIBITED).
            cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
            val iv = cipher.iv
            require(iv != null && iv.size == GCM_IV_BYTES) {
                "Keystore did not return a valid GCM IV"
            }
            val encrypted = cipher.doFinal(plaintext)
            listOf(
                CIPHERTEXT_VERSION,
                Base64.encodeToString(iv, BASE64_FLAGS),
                Base64.encodeToString(encrypted, BASE64_FLAGS),
            ).joinToString(CIPHERTEXT_SEPARATOR)
        }
    }

    override fun decrypt(ciphertext: String): ByteArray = synchronized(cipherLock) {
        cryptoOperation("Unable to decrypt authentication tokens") {
            require(ciphertext.length <= MAX_CIPHERTEXT_CHARS) {
                "Ciphertext exceeds the supported size"
            }
            val parts = ciphertext.split(CIPHERTEXT_SEPARATOR, limit = CIPHERTEXT_PARTS)
            require(parts.size == CIPHERTEXT_PARTS && parts[0] == CIPHERTEXT_VERSION) {
                "Unsupported ciphertext format"
            }
            val iv = Base64.decode(parts[1], BASE64_FLAGS)
            val encrypted = Base64.decode(parts[2], BASE64_FLAGS)
            require(iv.size == GCM_IV_BYTES && encrypted.size >= GCM_TAG_BYTES) {
                "Invalid ciphertext payload"
            }

            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                loadExistingKey(),
                GCMParameterSpec(GCM_TAG_BITS, iv),
            )
            cipher.doFinal(encrypted)
        }
    }

    override fun clearKey() = synchronized(cipherLock) {
        cryptoOperation("Unable to clear authentication token key") {
            val keyStore = loadKeyStore()
            if (keyStore.containsAlias(keyAlias)) {
                keyStore.deleteEntry(keyAlias)
            }
        }
    }

    private fun loadOrCreateKey(): SecretKey {
        val keyStore = loadKeyStore()
        val existingKey = keyStore.getKey(keyAlias, null)
        if (existingKey != null) {
            return existingKey as? SecretKey
                ?: throw IllegalStateException("Authentication token key has an invalid type")
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE,
        )
        keyGenerator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return keyGenerator.generateKey()
    }

    private fun loadExistingKey(): SecretKey {
        val key = loadKeyStore().getKey(keyAlias, null)
            ?: throw IllegalStateException("Authentication token key is unavailable")
        return key as? SecretKey
            ?: throw IllegalStateException("Authentication token key has an invalid type")
    }

    private fun loadKeyStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private inline fun <T> cryptoOperation(
        message: String,
        operation: () -> T,
    ): T {
        return try {
            operation()
        } catch (error: TokenCipherException) {
            throw error
        } catch (error: Exception) {
            throw TokenCipherException(message, error)
        }
    }

    companion object {
        // v2: encrypt uses Keystore-generated IVs (caller nonce is prohibited on API 33+).
        internal const val DEFAULT_KEY_ALIAS = "ir.xilo.app.auth.token_pair.v2"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val AES_KEY_BITS = 256
        private const val GCM_IV_BYTES = 12
        private const val GCM_TAG_BITS = 128
        private const val GCM_TAG_BYTES = GCM_TAG_BITS / Byte.SIZE_BITS
        private const val CIPHERTEXT_VERSION = "v1"
        private const val CIPHERTEXT_SEPARATOR = "."
        private const val CIPHERTEXT_PARTS = 3
        private const val MAX_CIPHERTEXT_CHARS = 256 * 1024
        private const val BASE64_FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
    }
}

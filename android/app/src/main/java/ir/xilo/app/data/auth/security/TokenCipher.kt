package ir.xilo.app.data.auth.security

/**
 * Authenticated encryption boundary for the persisted access/refresh token pair.
 *
 * Implementations must never expose or persist raw key material.
 */
interface TokenCipher {
    @Throws(TokenCipherException::class)
    fun encrypt(plaintext: ByteArray): String

    @Throws(TokenCipherException::class)
    fun decrypt(ciphertext: String): ByteArray

    /**
     * Removes only this token cipher's non-exportable key after a session becomes unusable.
     */
    @Throws(TokenCipherException::class)
    fun clearKey()
}

class TokenCipherException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

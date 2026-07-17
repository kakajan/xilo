package ir.xilo.app.data.auth.refresh

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
)

/**
 * Minimal synchronous token contract used by OkHttp's blocking Authenticator API.
 */
interface AuthTokenStore {
    fun getTokens(): AuthTokens?

    /**
     * Atomically replaces both rotated tokens only if the expected session is still current.
     */
    fun replaceTokensBlocking(
        expectedRefreshToken: String,
        replacement: AuthTokens,
    ): Boolean

    /**
     * Clears only the session which produced the terminal refresh failure.
     */
    fun clearTokensBlocking(expectedRefreshToken: String): Boolean
}

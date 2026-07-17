package ir.xilo.app.data.auth.refresh

import ir.xilo.app.data.remote.dto.AuthResponse
import ir.xilo.app.data.remote.dto.RefreshTokenRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.io.IOException

class SerializedTokenAuthenticator(
    private val tokenStore: AuthTokenStore,
    private val refreshClient: OkHttpClient,
    private val refreshUrl: HttpUrl,
    private val json: Json,
    private val maxResponseChainCount: Int = DEFAULT_MAX_RESPONSE_CHAIN_COUNT,
) : Authenticator {
    private val refreshLock = Any()

    init {
        require(maxResponseChainCount >= 1)
    }

    override fun authenticate(route: Route?, response: Response): Request? {
        if (!response.request.url.hasSameOrigin(refreshUrl) ||
            response.request.url.isRefreshUrl(refreshUrl) ||
            response.responseChainCount() >= maxResponseChainCount
        ) {
            return null
        }

        val failedAccessToken = response.request.header(AUTHORIZATION_HEADER)
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?.takeIf { it.isNotBlank() }
            ?: return null

        return synchronized(refreshLock) {
            val currentTokens = tokenStore.getTokens() ?: return@synchronized null

            if (currentTokens.accessToken != failedAccessToken) {
                return@synchronized response.retryWith(currentTokens.accessToken)
            }

            when (val result = refresh(currentTokens.refreshToken)) {
                is RefreshResult.Success -> {
                    if (tokenStore.replaceTokensBlocking(currentTokens.refreshToken, result.tokens)) {
                        response.retryWith(result.tokens.accessToken)
                    } else {
                        tokenStore.getTokens()
                            ?.takeIf { it.accessToken != failedAccessToken }
                            ?.let { response.retryWith(it.accessToken) }
                    }
                }

                RefreshResult.TerminalFailure -> {
                    tokenStore.clearTokensBlocking(currentTokens.refreshToken)
                    null
                }
            }
        }
    }

    private fun refresh(refreshToken: String): RefreshResult {
        val requestBody = json.encodeToString(RefreshTokenRequest(refreshToken))
            .toRequestBody(JSON_MEDIA_TYPE)
        val request = Request.Builder()
            .url(refreshUrl)
            .post(requestBody)
            .build()

        return try {
            refreshClient.newCall(request).execute().use { response ->
                when {
                    response.isSuccessful -> {
                        val responseBody = response.body?.string()
                            ?.takeIf { it.isNotBlank() }
                            ?: throw TransientTokenRefreshException()
                        val authResponse = runCatching {
                            json.decodeFromString(AuthResponse.serializer(), responseBody)
                        }.getOrNull()
                            ?: throw TransientTokenRefreshException()

                        if (authResponse.accessToken.isBlank() || authResponse.refreshToken.isBlank()) {
                            throw TransientTokenRefreshException()
                        } else {
                            RefreshResult.Success(
                                AuthTokens(
                                    accessToken = authResponse.accessToken,
                                    refreshToken = authResponse.refreshToken,
                                )
                            )
                        }
                    }

                    response.code == 400 || response.code == 401 -> RefreshResult.TerminalFailure
                    else -> throw TransientTokenRefreshException()
                }
            }
        } catch (_: IOException) {
            throw TransientTokenRefreshException()
        }
    }

    private fun Response.retryWith(accessToken: String): Request =
        request.newBuilder()
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
            .build()

    private fun Response.responseChainCount(): Int {
        var count = 1
        var previous = priorResponse
        while (previous != null) {
            count += 1
            previous = previous.priorResponse
        }
        return count
    }

    private sealed interface RefreshResult {
        data class Success(val tokens: AuthTokens) : RefreshResult
        data object TerminalFailure : RefreshResult
    }

    private companion object {
        const val DEFAULT_MAX_RESPONSE_CHAIN_COUNT = 2
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

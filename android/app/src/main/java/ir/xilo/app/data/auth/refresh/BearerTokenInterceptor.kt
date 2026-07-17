package ir.xilo.app.data.auth.refresh

import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.Response

class BearerTokenInterceptor(
    private val tokenStore: AuthTokenStore,
    private val approvedApiOrigin: HttpUrl,
    private val refreshUrl: HttpUrl,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val requestBuilder = original.newBuilder()

        if (!original.url.hasSameOrigin(approvedApiOrigin) || original.url.isRefreshUrl(refreshUrl)) {
            requestBuilder.removeHeader(AUTHORIZATION_HEADER)
        } else {
            tokenStore.getTokens()?.accessToken?.let { accessToken ->
                requestBuilder.header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
            }
        }

        return chain.proceed(requestBuilder.build())
    }
}

internal const val AUTHORIZATION_HEADER = "Authorization"
internal const val BEARER_PREFIX = "Bearer "

internal fun HttpUrl.hasSameOrigin(other: HttpUrl): Boolean =
    scheme == other.scheme && host == other.host && port == other.port

internal fun HttpUrl.isRefreshUrl(refreshUrl: HttpUrl): Boolean =
    hasSameOrigin(refreshUrl) && encodedPath.trimEnd('/') == refreshUrl.encodedPath.trimEnd('/')

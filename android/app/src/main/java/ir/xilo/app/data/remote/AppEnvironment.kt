package ir.xilo.app.data.remote

import ir.xilo.app.BuildConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runtime view of Gradle/BuildConfig-backed API endpoints.
 * Values are injected at build time; do not hardcode hosts in network code.
 */
@Singleton
class AppEnvironment @Inject constructor() {
    val apiBaseUrl: String = BuildConfig.API_BASE_URL
    val wsBaseUrl: String = BuildConfig.WS_BASE_URL
    val isDebuggable: Boolean = BuildConfig.DEBUG

    fun websocketUrlWithToken(accessToken: String): String {
        val separator = if (wsBaseUrl.contains("?")) "&" else "?"
        return "$wsBaseUrl${separator}token=$accessToken"
    }
}

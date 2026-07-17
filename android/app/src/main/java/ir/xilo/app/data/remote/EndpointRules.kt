package ir.xilo.app.data.remote

/**
 * Pure helpers for endpoint normalization/validation (unit-testable).
 */
object EndpointRules {
    fun normalizeApiBaseUrl(url: String): String {
        require(url.isNotBlank()) { "API base URL must not be blank" }
        return if (url.endsWith("/")) url else "$url/"
    }

    fun isCleartextLocalhost(url: String): Boolean {
        val lower = url.lowercase()
        val cleartext = lower.startsWith("http://") || lower.startsWith("ws://")
        val localhost = listOf("10.0.2.2", "localhost", "127.0.0.1").any { lower.contains(it) }
        return cleartext && localhost
    }

    fun redactedHeaders(): Set<String> = setOf(
        "Authorization",
        "Cookie",
        "Set-Cookie",
        "Proxy-Authorization",
        "X-Refresh-Token",
    )
}

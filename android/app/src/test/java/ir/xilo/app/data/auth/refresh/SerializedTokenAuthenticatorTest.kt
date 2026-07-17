package ir.xilo.app.data.auth.refresh

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalSerializationApi::class)
class SerializedTokenAuthenticatorTest {
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }

    @Test
    fun concurrentUnauthorizedResponsesPerformOneRefreshAndPersistRotatedTokens() {
        val server = MockWebServer()
        val oldRequestsReady = CountDownLatch(CONCURRENT_REQUESTS)
        val refreshCount = AtomicInteger()
        val persistedBeforeRetry = AtomicBoolean()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when {
                    request.path == REFRESH_PATH -> {
                        refreshCount.incrementAndGet()
                        MockResponse().setBody(successfulRefreshBody())
                    }

                    request.path == RESOURCE_PATH &&
                        request.getHeader(AUTHORIZATION_HEADER) == "$BEARER_PREFIX$OLD_ACCESS" -> {
                        oldRequestsReady.countDown()
                        if (oldRequestsReady.await(5, TimeUnit.SECONDS)) {
                            MockResponse().setResponseCode(401)
                        } else {
                            MockResponse().setResponseCode(500)
                        }
                    }

                    request.path == RESOURCE_PATH &&
                        request.getHeader(AUTHORIZATION_HEADER) == "$BEARER_PREFIX$NEW_ACCESS" -> {
                        persistedBeforeRetry.set(
                            store.getTokens() == AuthTokens(NEW_ACCESS, NEW_REFRESH)
                        )
                        MockResponse().setResponseCode(200)
                    }

                    else -> MockResponse().setResponseCode(400)
                }
            }
        }
        server.start()

        val executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS)
        try {
            val client = authenticatedClient(server.url("/"), store)
            val request = Request.Builder().url(server.url(RESOURCE_PATH)).build()
            val futures = (1..CONCURRENT_REQUESTS).map {
                executor.submit<Int> {
                    client.newCall(request).execute().use { it.code }
                }
            }

            assertEquals(
                List(CONCURRENT_REQUESTS) { 200 },
                futures.map { it.get(10, TimeUnit.SECONDS) },
            )
            assertEquals(1, refreshCount.get())
            assertEquals(1, store.replaceCount.get())
            assertEquals(AuthTokens(NEW_ACCESS, NEW_REFRESH), store.getTokens())
            assertTrue(persistedBeforeRetry.get())
        } finally {
            executor.shutdownNow()
            server.shutdown()
        }
    }

    @Test
    fun staleUnauthorizedResponseReusesTokenAlreadyRefreshedByAnotherRequest() {
        val server = MockWebServer()
        server.start()
        val store = FakeTokenStore(AuthTokens(NEW_ACCESS, NEW_REFRESH))
        try {
            val authenticator = authenticator(server.url("/"), store)
            unauthorizedResponse(server.url(RESOURCE_PATH), OLD_ACCESS).use { response ->
                val retry = authenticator.authenticate(null, response)

                assertEquals("$BEARER_PREFIX$NEW_ACCESS", retry?.header(AUTHORIZATION_HEADER))
                assertEquals(0, server.requestCount)
                assertEquals(0, store.replaceCount.get())
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun terminalRefreshUnauthorizedClearsCurrentSessionExactlyOnce() {
        val server = MockWebServer()
        server.dispatcher = refreshDispatcher(MockResponse().setResponseCode(401))
        server.start()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        try {
            authenticatedClient(server.url("/"), store)
                .newCall(Request.Builder().url(server.url(RESOURCE_PATH)).build())
                .execute()
                .use { assertEquals(401, it.code) }

            assertNull(store.getTokens())
            assertEquals(1, store.clearCount.get())
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun refreshEndpointIsNeverAuthenticatedOrRetried() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        try {
            authenticatedClient(server.url("/"), store)
                .newCall(Request.Builder().url(server.url(REFRESH_PATH)).build())
                .execute()
                .use { assertEquals(401, it.code) }

            assertNull(server.takeRequest().getHeader(AUTHORIZATION_HEADER))
            assertEquals(1, server.requestCount)
            assertEquals(0, store.replaceCount.get())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun responseChainCapStopsRepeatedAuthentication() {
        val server = MockWebServer()
        val resourceCount = AtomicInteger()
        val refreshCount = AtomicInteger()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == REFRESH_PATH) {
                    refreshCount.incrementAndGet()
                    MockResponse().setBody(successfulRefreshBody())
                } else {
                    resourceCount.incrementAndGet()
                    MockResponse().setResponseCode(401)
                }
        }
        server.start()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        try {
            authenticatedClient(server.url("/"), store)
                .newCall(Request.Builder().url(server.url(RESOURCE_PATH)).build())
                .execute()
                .use { assertEquals(401, it.code) }

            assertEquals(1, refreshCount.get())
            assertEquals(2, resourceCount.get())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun requestWithoutBearerIsNotAuthenticated() {
        val server = MockWebServer()
        server.start()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        try {
            val authenticator = authenticator(server.url("/"), store)
            val request = Request.Builder().url(server.url(RESOURCE_PATH)).build()
            response(request, 401).use { unauthorized ->
                assertNull(authenticator.authenticate(null, unauthorized))
            }
            assertEquals(0, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun foreignOriginNeverReceivesBearerCredentials() {
        val approvedServer = MockWebServer()
        val foreignServer = MockWebServer()
        approvedServer.start()
        foreignServer.enqueue(MockResponse().setResponseCode(200))
        foreignServer.start()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        try {
            val client = authenticatedClient(approvedServer.url("/"), store)
            val request = Request.Builder()
                .url(foreignServer.url("/foreign"))
                .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$OLD_ACCESS")
                .build()

            client.newCall(request).execute().use { assertEquals(200, it.code) }

            assertNull(foreignServer.takeRequest().getHeader(AUTHORIZATION_HEADER))
        } finally {
            approvedServer.shutdown()
            foreignServer.shutdown()
        }
    }

    @Test
    fun crossOriginRedirectDoesNotLeakBearerCredentials() {
        val approvedServer = MockWebServer()
        val foreignServer = MockWebServer()
        foreignServer.enqueue(MockResponse().setResponseCode(200))
        foreignServer.start()
        approvedServer.enqueue(
            MockResponse()
                .setResponseCode(302)
                .addHeader("Location", foreignServer.url("/redirected")),
        )
        approvedServer.start()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        try {
            authenticatedClient(approvedServer.url("/"), store)
                .newCall(Request.Builder().url(approvedServer.url("/redirect")).build())
                .execute()
                .use { assertEquals(200, it.code) }

            assertNull(foreignServer.takeRequest().getHeader(AUTHORIZATION_HEADER))
        } finally {
            approvedServer.shutdown()
            foreignServer.shutdown()
        }
    }

    private fun authenticatedClient(
        apiBaseUrl: HttpUrl,
        store: AuthTokenStore,
        refreshClient: OkHttpClient = noRedirectClient(),
    ): OkHttpClient {
        val refreshUrl = apiBaseUrl.resolve(REFRESH_PATH)!!
        return OkHttpClient.Builder()
            .addInterceptor(BearerTokenInterceptor(store, apiBaseUrl, refreshUrl))
            .authenticator(
                SerializedTokenAuthenticator(
                    tokenStore = store,
                    refreshClient = refreshClient,
                    refreshUrl = refreshUrl,
                    json = json,
                )
            )
            .build()
    }

    private fun authenticator(
        apiBaseUrl: HttpUrl,
        store: AuthTokenStore,
    ): SerializedTokenAuthenticator =
        SerializedTokenAuthenticator(
            tokenStore = store,
            refreshClient = noRedirectClient(),
            refreshUrl = apiBaseUrl.resolve(REFRESH_PATH)!!,
            json = json,
        )

    private fun noRedirectClient(): OkHttpClient =
        OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .build()

    private fun refreshDispatcher(refreshResponse: MockResponse): Dispatcher =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path == REFRESH_PATH) {
                    refreshResponse
                } else {
                    MockResponse().setResponseCode(401)
                }
        }

    private fun unauthorizedResponse(url: HttpUrl, accessToken: String): Response {
        val request = Request.Builder()
            .url(url)
            .header(AUTHORIZATION_HEADER, "$BEARER_PREFIX$accessToken")
            .build()
        return response(request, 401)
    }

    private fun response(request: Request, code: Int): Response =
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("test")
            .body("".toResponseBody())
            .build()

    private fun successfulRefreshBody(): String =
        """{"access_token":"$NEW_ACCESS","refresh_token":"$NEW_REFRESH","expires_in":3600}"""

    private class FakeTokenStore(initialTokens: AuthTokens?) : AuthTokenStore {
        private val lock = Any()
        private var tokens = initialTokens
        val replaceCount = AtomicInteger()
        val clearCount = AtomicInteger()

        override fun getTokens(): AuthTokens? = synchronized(lock) { tokens }

        override fun replaceTokensBlocking(
            expectedRefreshToken: String,
            replacement: AuthTokens,
        ): Boolean = synchronized(lock) {
            if (tokens?.refreshToken != expectedRefreshToken) {
                false
            } else {
                tokens = replacement
                replaceCount.incrementAndGet()
                true
            }
        }

        override fun clearTokensBlocking(expectedRefreshToken: String): Boolean =
            synchronized(lock) {
                if (tokens?.refreshToken != expectedRefreshToken) {
                    false
                } else {
                    tokens = null
                    clearCount.incrementAndGet()
                    true
                }
            }
    }

    private companion object {
        const val CONCURRENT_REQUESTS = 4
        const val RESOURCE_PATH = "/resource"
        const val REFRESH_PATH = "/api/auth/refresh"
        const val OLD_ACCESS = "old-access"
        const val OLD_REFRESH = "old-refresh"
        const val NEW_ACCESS = "new-access"
        const val NEW_REFRESH = "new-refresh"
    }
}

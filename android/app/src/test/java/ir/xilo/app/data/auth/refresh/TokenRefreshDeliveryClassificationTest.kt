package ir.xilo.app.data.auth.refresh

import ir.xilo.app.data.sync.DeliveryDecision
import ir.xilo.app.data.sync.OutboxRetryPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Header
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalSerializationApi::class)
class TokenRefreshDeliveryClassificationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        namingStrategy = kotlinx.serialization.json.JsonNamingStrategy.SnakeCase
    }
    private val retryPolicy = OutboxRetryPolicy()

    @Test
    fun refresh503PropagatesIOExceptionAndOutboxKeepsOperationRetryable() {
        verifyTransientRefresh(
            MockResponse()
                .setResponseCode(503)
                .setBody("""{"error":"sensitive refresh detail"}"""),
        ) { failure, decision ->
            assertSanitized(failure)
            assertRetryDecision(decision)
        }
    }

    @Test
    fun refreshIOExceptionPropagatesAndOutboxKeepsOperationRetryable() {
        val server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(401))
        server.start()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        val failingRefreshClient = OkHttpClient.Builder()
            .addInterceptor { throw IOException("network detail must not escape") }
            .build()
        try {
            val failure = executeMutationFailure(
                apiBaseUrl = server.url("/"),
                store = store,
                refreshClient = failingRefreshClient,
            )
            val decision = retryPolicy.classify(failure, attemptCount = 1, now = NOW)

            assertTrue(failure is TransientTokenRefreshException)
            assertTrue(decision is DeliveryDecision.Retry)
            assertSanitized(failure as TransientTokenRefreshException)
            assertRetryDecision(decision as DeliveryDecision.Retry)
            assertEquals(AuthTokens(OLD_ACCESS, OLD_REFRESH), store.getTokens())
            assertEquals(0, store.clearCount.get())
            assertEquals(OPERATION_KEY, server.takeRequest().getHeader(IDEMPOTENCY_HEADER))
            assertEquals(1, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun malformedRefreshSuccessPropagatesIOExceptionAndRemainsRetryable() {
        verifyTransientRefresh(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"access_token":"truncated""""),
        ) { failure, decision ->
            assertSanitized(failure)
            assertRetryDecision(decision)
        }
    }

    @Test
    fun refresh429UsesNormalBoundedOutboxBackoff() {
        verifyTransientRefresh(
            MockResponse()
                .setResponseCode(429)
                .addHeader("Retry-After", "120"),
        ) { failure, decision ->
            assertRetryDecision(decision)
            assertEquals(NOW + OutboxRetryPolicy.BASE_DELAY_MS, decision.nextAttemptAt)
        }
    }

    @Test
    fun terminalRefresh401ClearsSessionAndIsPermanentAuthRequired() {
        val server = MockWebServer()
        server.dispatcher = refreshDispatcher(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"error":"sensitive terminal detail"}"""),
        )
        server.start()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        try {
            val failure = executeMutationFailure(server.url("/"), store)
            val decision = retryPolicy.classify(failure, attemptCount = 1, now = NOW)

            assertTrue(failure is HttpException)
            assertTrue(decision is DeliveryDecision.Permanent)
            decision as DeliveryDecision.Permanent
            assertEquals("auth_required", decision.metadata.code)
            assertEquals(401, decision.metadata.httpStatus)
            assertFalse(decision.metadata.message.contains("sensitive"))
            assertNull(store.getTokens())
            assertEquals(1, store.clearCount.get())
            assertEquals(OPERATION_KEY, server.takeRequest().getHeader(IDEMPOTENCY_HEADER))
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun verifyTransientRefresh(
        refreshResponse: MockResponse,
        assertions: (TransientTokenRefreshException, DeliveryDecision.Retry) -> Unit,
    ) {
        val server = MockWebServer()
        server.dispatcher = refreshDispatcher(refreshResponse)
        server.start()
        val store = FakeTokenStore(AuthTokens(OLD_ACCESS, OLD_REFRESH))
        try {
            val failure = executeMutationFailure(server.url("/"), store)
            val decision = retryPolicy.classify(failure, attemptCount = 1, now = NOW)

            assertTrue(failure is TransientTokenRefreshException)
            assertTrue(failure is IOException)
            assertTrue(decision is DeliveryDecision.Retry)
            assertions(
                failure as TransientTokenRefreshException,
                decision as DeliveryDecision.Retry,
            )
            assertEquals(AuthTokens(OLD_ACCESS, OLD_REFRESH), store.getTokens())
            assertEquals(0, store.clearCount.get())
            assertEquals(OPERATION_KEY, server.takeRequest().getHeader(IDEMPOTENCY_HEADER))
            assertEquals(2, server.requestCount)
        } finally {
            server.shutdown()
        }
    }

    private fun executeMutationFailure(
        apiBaseUrl: HttpUrl,
        store: AuthTokenStore,
        refreshClient: OkHttpClient = noRedirectClient(),
    ): Throwable {
        val api = Retrofit.Builder()
            .baseUrl(apiBaseUrl)
            .client(authenticatedClient(apiBaseUrl, store, refreshClient))
            .addConverterFactory(
                json.asConverterFactory("application/json".toMediaType())
            )
            .build()
            .create(MutationApi::class.java)

        return runBlocking {
            try {
                api.mutate(OPERATION_KEY)
                throw AssertionError("Expected mutation delivery to fail")
            } catch (failure: Throwable) {
                if (failure is AssertionError) {
                    throw failure
                }
                failure
            }
        }
    }

    private fun authenticatedClient(
        apiBaseUrl: HttpUrl,
        store: AuthTokenStore,
        refreshClient: OkHttpClient,
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

    private fun assertRetryDecision(decision: DeliveryDecision.Retry) {
        assertEquals("network_io", decision.metadata.code)
        assertEquals(NOW + OutboxRetryPolicy.BASE_DELAY_MS, decision.nextAttemptAt)
    }

    private fun assertSanitized(failure: TransientTokenRefreshException) {
        generateSequence<Throwable>(failure) { it.cause }.forEach { error ->
            assertTrue(error is TransientTokenRefreshException)
            assertEquals("Token refresh is temporarily unavailable", error.message)
            assertFalse(error.message.orEmpty().contains("503"))
            assertFalse(error.message.orEmpty().contains("sensitive"))
        }
    }

    @Serializable
    private data class MutationResponse(val id: String)

    private interface MutationApi {
        @POST("resource")
        suspend fun mutate(
            @Header(IDEMPOTENCY_HEADER) operationKey: String,
        ): MutationResponse
    }

    private class FakeTokenStore(initialTokens: AuthTokens?) : AuthTokenStore {
        private val lock = Any()
        private var tokens = initialTokens
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
        const val RESOURCE_PATH = "/resource"
        const val REFRESH_PATH = "/api/auth/refresh"
        const val IDEMPOTENCY_HEADER = "Idempotency-Key"
        const val OPERATION_KEY = "123e4567-e89b-42d3-a456-426614174000"
        const val OLD_ACCESS = "old-access"
        const val OLD_REFRESH = "old-refresh"
        const val NOW = 1_721_299_200_000L
    }
}

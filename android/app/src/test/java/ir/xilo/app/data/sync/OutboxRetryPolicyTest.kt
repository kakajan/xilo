package ir.xilo.app.data.sync

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class OutboxRetryPolicyTest {
    private val policy = OutboxRetryPolicy()

    @Test
    fun ioAndServerFailures_areTransientButBounded() {
        val ioDecision = policy.classify(IOException("contains sensitive URL"), 1, NOW)
        val serverDecision = policy.classify(httpError(503), 2, NOW)
        val exhausted = policy.classify(IOException("offline"), 6, NOW)

        assertTrue(ioDecision is DeliveryDecision.Retry)
        assertEquals(NOW + 10_000L, (ioDecision as DeliveryDecision.Retry).nextAttemptAt)
        assertTrue(serverDecision is DeliveryDecision.Retry)
        assertEquals(NOW + 20_000L, (serverDecision as DeliveryDecision.Retry).nextAttemptAt)
        assertTrue(exhausted is DeliveryDecision.Permanent)
        assertEquals(
            "retry_exhausted",
            (exhausted as DeliveryDecision.Permanent).metadata.code
        )
    }

    @Test
    fun stable4xxAndIdempotencyConflict_arePermanentAndSanitized() {
        val badRequest = policy.classify(httpError(400), 1, NOW)
        val conflict = policy.classify(httpError(409), 1, NOW)

        assertTrue(badRequest is DeliveryDecision.Permanent)
        assertEquals("http_400", (badRequest as DeliveryDecision.Permanent).metadata.code)
        assertTrue(conflict is DeliveryDecision.Permanent)
        assertEquals(
            "idempotency_conflict",
            (conflict as DeliveryDecision.Permanent).metadata.code
        )
        assertTrue(conflict.metadata.message.contains("secret").not())
    }

    @Test
    fun retryAfter_isHonoredForRateLimits() {
        val decision = policy.classify(httpError(429, retryAfter = "120"), 1, NOW)

        assertTrue(decision is DeliveryDecision.Retry)
        assertEquals(NOW + 120_000L, (decision as DeliveryDecision.Retry).nextAttemptAt)
    }

    @Test
    fun localReconciliationFailure_isTransient() {
        val decision = policy.classify(
            OutboxReconciliationException(IllegalStateException("local transaction")),
            1,
            NOW
        )

        assertTrue(decision is DeliveryDecision.Retry)
        assertEquals(
            "local_reconciliation",
            (decision as DeliveryDecision.Retry).metadata.code
        )
    }

    @Test
    fun terminal401_afterAuthenticator_isExplicitAuthRequired() {
        val decision = policy.classify(httpError(401), 1, NOW)

        assertTrue(decision is DeliveryDecision.Permanent)
        assertEquals(
            "auth_required",
            (decision as DeliveryDecision.Permanent).metadata.code
        )
    }

    private fun httpError(code: Int, retryAfter: String? = null): HttpException {
        val raw = okhttp3.Response.Builder()
            .request(Request.Builder().url("https://api.example.test/").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("error")
            .apply {
                retryAfter?.let { header("Retry-After", it) }
            }
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
        val errorBody = "{}".toResponseBody("application/json".toMediaType())
        return HttpException(Response.error<Any>(errorBody, raw))
    }

    private companion object {
        const val NOW = 1_721_299_200_000L
    }
}

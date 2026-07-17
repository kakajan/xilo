package ir.xilo.app.data.sync

import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class OutboxFailureMetadata(
    val code: String,
    val httpStatus: Int?,
    val message: String
)

sealed interface DeliveryDecision {
    data class Retry(
        val nextAttemptAt: Long,
        val metadata: OutboxFailureMetadata
    ) : DeliveryDecision

    data class Permanent(
        val metadata: OutboxFailureMetadata
    ) : DeliveryDecision
}

class OutboxDeliveryException(
    val metadata: OutboxFailureMetadata,
    val isPermanent: Boolean
) : Exception(metadata.message)

class OutboxReconciliationException(
    cause: Throwable
) : Exception("Local reconciliation failed after server delivery", cause)

@Singleton
class OutboxRetryPolicy @Inject constructor() {
    fun classify(
        error: Throwable,
        attemptCount: Int,
        now: Long
    ): DeliveryDecision {
        val http = error as? HttpException
        val status = http?.code()
        val transient =
            error is IOException ||
                error is OutboxReconciliationException ||
                status == 429 ||
                status in 500..599
        val metadata = sanitizedMetadata(error, status)

        if (!transient) {
            return DeliveryDecision.Permanent(metadata)
        }
        if (attemptCount >= MAX_ATTEMPTS) {
            return DeliveryDecision.Permanent(
                metadata.copy(
                    code = "retry_exhausted",
                    message = "Delivery failed after $MAX_ATTEMPTS attempts"
                )
            )
        }

        val exponent = (attemptCount - 1).coerceAtLeast(0).coerceAtMost(20)
        val exponentialDelay = (BASE_DELAY_MS shl exponent).coerceAtMost(MAX_DELAY_MS)
        val retryAfterDelay = http
            ?.response()
            ?.headers()
            ?.get("Retry-After")
            ?.let { parseRetryAfter(it, now) }
            ?: 0L
        val delay = max(exponentialDelay, retryAfterDelay).coerceAtMost(MAX_RETRY_AFTER_MS)
        return DeliveryDecision.Retry(
            nextAttemptAt = now + delay,
            metadata = metadata
        )
    }

    private fun sanitizedMetadata(
        error: Throwable,
        status: Int?
    ): OutboxFailureMetadata = when {
        error is OutboxReconciliationException -> OutboxFailureMetadata(
            code = "local_reconciliation",
            httpStatus = null,
            message = "Server delivery succeeded but local reconciliation must be retried"
        )
        error is IOException -> OutboxFailureMetadata(
            code = "network_io",
            httpStatus = null,
            message = "Network delivery failed"
        )
        status == 401 -> OutboxFailureMetadata(
            code = "auth_required",
            httpStatus = status,
            message = "Authentication is required after refresh was exhausted"
        )
        status == 409 -> OutboxFailureMetadata(
            code = "idempotency_conflict",
            httpStatus = status,
            message = "The saved operation conflicts with the server replay record"
        )
        status != null -> OutboxFailureMetadata(
            code = "http_$status",
            httpStatus = status,
            message = if (status == 429 || status >= 500) {
                "Server temporarily rejected delivery"
            } else {
                "Server permanently rejected delivery"
            }
        )
        else -> OutboxFailureMetadata(
            code = "invalid_operation",
            httpStatus = null,
            message = "The saved operation could not be delivered"
        )
    }

    private fun parseRetryAfter(value: String, now: Long): Long? {
        value.trim().toLongOrNull()?.let { seconds ->
            val boundedSeconds = seconds.coerceIn(0L, MAX_RETRY_AFTER_MS / 1_000L)
            return boundedSeconds * 1_000L
        }
        return runCatching {
            val parser = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("GMT")
                isLenient = false
            }
            (parser.parse(value)?.time ?: return null) - now
        }.getOrNull()?.coerceAtLeast(0L)
    }

    companion object {
        const val MAX_ATTEMPTS = 6
        const val BASE_DELAY_MS = 10_000L
        const val MAX_DELAY_MS = 6 * 60 * 60 * 1_000L
        const val MAX_RETRY_AFTER_MS = 24 * 60 * 60 * 1_000L
    }
}

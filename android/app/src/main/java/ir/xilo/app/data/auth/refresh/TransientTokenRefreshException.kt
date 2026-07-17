package ir.xilo.app.data.auth.refresh

import java.io.IOException

/**
 * Signals a retryable refresh failure without exposing response status, body, or credentials.
 *
 * Retry-After is intentionally not propagated: the outbox classifies this as network I/O and
 * applies its existing bounded exponential backoff.
 */
class TransientTokenRefreshException :
    IOException("Token refresh is temporarily unavailable")

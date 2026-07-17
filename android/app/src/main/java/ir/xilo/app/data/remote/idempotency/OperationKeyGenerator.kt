package ir.xilo.app.data.remote.idempotency

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class OperationKeyGenerator @Inject constructor() {

    open fun generate(): String = UUID.randomUUID().toString()

    fun isValid(value: String): Boolean {
        val trimmed = value.trim()
        val uuid = runCatching { UUID.fromString(trimmed) }.getOrNull() ?: return false
        return trimmed.equals(uuid.toString(), ignoreCase = true) &&
            uuid.version() == UUID_VERSION_4 &&
            uuid.variant() == UUID_VARIANT_RFC_4122
    }

    fun requireValid(value: String): String {
        require(isValid(value)) {
            "Operation key must be a canonical RFC 4122 UUIDv4"
        }
        return value.trim()
    }

    private companion object {
        const val UUID_VERSION_4 = 4
        const val UUID_VARIANT_RFC_4122 = 2
    }
}

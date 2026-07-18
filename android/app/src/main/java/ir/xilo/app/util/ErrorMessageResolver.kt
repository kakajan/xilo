package ir.xilo.app.util

import android.content.Context
import ir.xilo.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

data class ParsedFormError(
    val fieldErrors: Map<String, String> = emptyMap(),
    val generalError: String? = null,
)

@Singleton
class ErrorMessageResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun fromThrowable(
        throwable: Throwable,
        fallbackResId: Int = R.string.error_unknown,
    ): String {
        val root = throwable.rootCause()
        return when (root) {
            is HttpException -> fromHttpException(root, fallbackResId)
            is SocketTimeoutException -> string(R.string.error_timeout)
            is UnknownHostException, is ConnectException -> string(R.string.error_network)
            is IOException -> string(R.string.error_network)
            else -> root.message?.let { mapApiMessage(it)?.let(::string) }
                ?: string(fallbackResId)
        }
    }

    fun string(resId: Int): String = context.getString(resId)

    fun parseFormErrors(
        throwable: Throwable,
        fallbackResId: Int = R.string.error_unknown,
    ): ParsedFormError {
        val root = throwable.rootCause()
        if (root is HttpException) {
            val apiMessage = parseApiErrorMessage(root)
            if (!apiMessage.isNullOrBlank()) {
                val parsed = parseFieldErrorsFromMessage(apiMessage)
                if (parsed.fieldErrors.isNotEmpty() || parsed.generalError != null) {
                    return parsed
                }
            }
            return ParsedFormError(generalError = fromHttpException(root, fallbackResId))
        }
        return ParsedFormError(
            generalError = fromThrowable(throwable, fallbackResId),
        )
    }

    private fun fromHttpException(
        exception: HttpException,
        fallbackResId: Int,
    ): String {
        val apiMessage = parseApiErrorMessage(exception)
        if (!apiMessage.isNullOrBlank()) {
            mapApiMessage(apiMessage)?.let { return string(it) }
            if (looksLocalized(apiMessage)) return apiMessage
        }
        return when (exception.code()) {
            400 -> string(R.string.error_bad_request)
            401 -> string(R.string.error_unauthorized)
            403 -> string(R.string.error_forbidden)
            404 -> string(R.string.error_not_found)
            408 -> string(R.string.error_timeout)
            429 -> string(R.string.error_too_many_requests)
            in 500..599 -> string(R.string.error_server)
            else -> string(fallbackResId)
        }
    }

    private fun parseApiErrorMessage(exception: HttpException): String? {
        val body = exception.response()?.errorBody()?.string().orEmpty()
        if (body.isBlank()) return null
        return runCatching {
            json.decodeFromString<ApiErrorBody>(body).resolvedMessage()
        }.getOrNull()
    }

    private fun mapApiMessage(message: String): Int? {
        val normalized = message.trim().lowercase()
        val fieldMessage = normalized.substringAfter(":", normalized).trim()

        API_ERROR_MAP[normalized]?.let { return it }
        API_ERROR_MAP[fieldMessage]?.let { return it }
        VALIDATION_MAP[normalized]?.let { return it }
        VALIDATION_MAP[fieldMessage]?.let { return it }

        if (normalized.contains("unsupported mime type") ||
            normalized.contains("process avatar") ||
            normalized.contains("decode image") ||
            normalized.contains("decode config") ||
            normalized.contains("save avatar record") ||
            normalized.contains("save media record") ||
            normalized.startsWith("upload avatar")
        ) {
            return R.string.error_avatar_upload
        }

        if (isSmsUnavailableMessage(normalized)) {
            return R.string.error_otp_sms_unavailable
        }

        return null
    }

    private fun isSmsUnavailableMessage(message: String): Boolean {
        val normalized = message.trim().lowercase()
        return normalized.contains("failed to send otp") ||
            normalized.contains("sms") ||
            normalized.contains("not initialized") ||
            (normalized.contains("otp") && normalized.contains("unavailable"))
    }

    private fun resolveMessage(message: String): String? {
        mapApiMessage(message)?.let { return string(it) }
        if (looksLocalized(message)) return message.trim()
        return null
    }

    private fun parseFieldErrorsFromMessage(message: String): ParsedFormError {
        val trimmed = message.trim()
        val colonIndex = trimmed.indexOf(':')
        if (colonIndex in 1 until trimmed.lastIndex) {
            val field = trimmed.substring(0, colonIndex).trim().lowercase()
            val detail = trimmed.substring(colonIndex + 1).trim()
            if (field in FORM_FIELDS) {
                val resolved = resolveMessage(detail) ?: resolveMessage(trimmed)
                if (resolved != null) {
                    return ParsedFormError(fieldErrors = mapOf(field to resolved))
                }
            }
        }

        return when (trimmed.lowercase()) {
            "email already exists" -> ParsedFormError(
                fieldErrors = mapOf("email" to string(R.string.api_email_exists))
            )
            "username already exists" -> ParsedFormError(
                fieldErrors = mapOf("username" to string(R.string.api_username_exists))
            )
            "invalid email or password" -> {
                val hint = string(R.string.api_invalid_credentials)
                ParsedFormError(
                    fieldErrors = mapOf(
                        "email" to hint,
                        "password" to hint,
                    )
                )
            }
            else -> {
                // Bare password policy messages (without "password:" prefix) still belong on the password field.
                if (trimmed.lowercase() in VALIDATION_MAP && trimmed.lowercase().startsWith("password ")) {
                    val resolved = resolveMessage(trimmed)
                    if (resolved != null) {
                        return ParsedFormError(fieldErrors = mapOf("password" to resolved))
                    }
                }
                if (isSmsUnavailableMessage(trimmed)) {
                    return ParsedFormError(generalError = string(R.string.error_otp_sms_unavailable))
                }
                ParsedFormError(generalError = resolveMessage(trimmed) ?: trimmed)
            }
        }
    }

    private fun looksLocalized(message: String): Boolean =
        message.any { Character.UnicodeBlock.of(it) == Character.UnicodeBlock.ARABIC }

    private fun Throwable.rootCause(): Throwable {
        var current = this
        while (current.cause != null) {
            current = current.cause!!
        }
        return current
    }

    @Serializable
    private data class ApiErrorBody(
        val error: String? = null,
        val message: String? = null,
        @SerialName("error_message") val errorMessage: String? = null,
    ) {
        fun resolvedMessage(): String? =
            error?.takeIf { it.isNotBlank() }
                ?: message?.takeIf { it.isNotBlank() }
                ?: errorMessage?.takeIf { it.isNotBlank() }
    }

    private companion object {
        private val FORM_FIELDS = setOf(
            "username",
            "email",
            "password",
            "display_name",
            "title",
            "content",
            "text",
        )

        private val API_ERROR_MAP = mapOf(
            "invalid request body" to R.string.api_invalid_request_body,
            "registration failed" to R.string.api_registration_failed,
            "invalid email or password" to R.string.api_invalid_credentials,
            "email already exists" to R.string.api_email_exists,
            "username already exists" to R.string.api_username_exists,
            "internal server error" to R.string.api_internal_server_error,
            "unauthorized" to R.string.api_unauthorized,
            "missing authorization token" to R.string.api_unauthorized,
            "invalid or expired token" to R.string.api_unauthorized,
            "update failed" to R.string.api_update_failed,
            "too many requests, please try again later" to R.string.api_too_many_requests,
            "user not found" to R.string.api_user_not_found,
            "post not found" to R.string.error_not_found,
            "failed" to R.string.api_failed,
            "failed to send otp" to R.string.error_otp_sms_unavailable,
            "no file provided" to R.string.error_avatar_upload,
            "unable to read image" to R.string.error_avatar_upload,
        )

        private val VALIDATION_MAP = mapOf(
            "email is required" to R.string.validation_email_required,
            "email must be at most 254 characters" to R.string.validation_email_too_long,
            "invalid email format" to R.string.validation_email_invalid,
            "username is required" to R.string.validation_username_required,
            "username must be between 3 and 32 characters" to R.string.validation_username_length,
            "username must contain only alphanumeric characters and underscores" to R.string.validation_username_format,
            "password must be at least 8 characters" to R.string.validation_password_min_length,
            "password must contain at least one uppercase letter" to R.string.validation_password_uppercase,
            "password must contain at least one number" to R.string.validation_password_number,
            "password must contain at least one special character" to R.string.validation_password_special,
            "title is required" to R.string.validation_title_required,
            "title must be at most 200 characters" to R.string.validation_title_too_long,
            "comment text is required" to R.string.validation_comment_required,
            "comment must be at most 5000 characters" to R.string.validation_comment_too_long,
            "password is required" to R.string.validation_password_required,
        )
    }
}

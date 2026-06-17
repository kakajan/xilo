package com.example.xilo.util

import com.example.xilo.R

object InputValidator {

    private val emailRegex = Regex("""^[a-zA-Z0-9._%+\-]+@[a-zA-Z0-9.\-]+\.[a-zA-Z]{2,}$""")
    private val usernameRegex = Regex("""^[a-zA-Z0-9_]{3,32}$""")

    fun validateEmail(email: String): Int? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> R.string.validation_email_required
            trimmed.length > 254 -> R.string.validation_email_too_long
            !emailRegex.matches(trimmed) -> R.string.validation_email_invalid
            else -> null
        }
    }

    fun validateUsername(username: String): Int? {
        val trimmed = username.trim()
        return when {
            trimmed.isEmpty() -> R.string.validation_username_required
            trimmed.length !in 3..32 -> R.string.validation_username_length
            !usernameRegex.matches(trimmed) -> R.string.validation_username_format
            else -> null
        }
    }

    fun validatePassword(password: String): Int? {
        if (password.isEmpty()) return R.string.validation_password_required
        if (password.length < 8) return R.string.validation_password_min_length

        var hasUpper = false
        var hasNumber = false
        var hasSpecial = false
        for (ch in password) {
            when {
                ch.isUpperCase() -> hasUpper = true
                ch.isDigit() -> hasNumber = true
                !ch.isLetterOrDigit() -> hasSpecial = true
            }
        }

        if (!hasUpper) return R.string.validation_password_uppercase
        if (!hasNumber) return R.string.validation_password_number
        if (!hasSpecial) return R.string.validation_password_special
        return null
    }
}

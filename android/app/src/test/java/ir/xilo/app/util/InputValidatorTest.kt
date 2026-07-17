package ir.xilo.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ir.xilo.app.R

class InputValidatorTest {

    @Test
    fun validatePassword_acceptsStrongPassword() {
        assertNull(InputValidator.validatePassword("Test1234!"))
    }

    @Test
    fun validatePassword_rejectsShortPassword() {
        assertEquals(
            R.string.validation_password_min_length,
            InputValidator.validatePassword("Test1!"),
        )
    }

    @Test
    fun validatePassword_rejectsMissingUppercase() {
        assertEquals(
            R.string.validation_password_uppercase,
            InputValidator.validatePassword("test1234!"),
        )
    }

    @Test
    fun validateUsername_rejectsPersianCharacters() {
        assertEquals(
            R.string.validation_username_format,
            InputValidator.validateUsername("کاربر"),
        )
    }

    @Test
    fun validateEmail_rejectsInvalidFormat() {
        assertEquals(
            R.string.validation_email_invalid,
            InputValidator.validateEmail("not-an-email"),
        )
    }
}

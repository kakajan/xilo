package ir.xilo.app.data.remote.idempotency

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OperationKeyGeneratorTest {

    private val generator = OperationKeyGenerator()

    @Test
    fun generate_returnsCanonicalUuidV4() {
        repeat(50) {
            val key = generator.generate()

            assertTrue(generator.isValid(key))
            assertEquals(key.lowercase(), key)
        }
    }

    @Test
    fun validator_acceptsCanonicalUuidV4CaseInsensitively() {
        assertTrue(generator.isValid("123E4567-E89B-42D3-A456-426614174000"))
    }

    @Test
    fun validator_rejectsMalformedNonV4AndNonRfc4122Values() {
        assertFalse(generator.isValid(""))
        assertFalse(generator.isValid("not-a-uuid"))
        assertFalse(generator.isValid("123e4567-e89b-12d3-a456-426614174000"))
        assertFalse(generator.isValid("123e4567-e89b-42d3-c456-426614174000"))
        assertFalse(generator.isValid("1-1-4-8-1"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireValid_rejectsInvalidValue() {
        generator.requireValid("invalid")
    }
}

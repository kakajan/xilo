package ir.xilo.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PostCoverPlaceholderTest {
    @Test
    fun resolveCoverLabel_prefersTitle() {
        assertEquals(
            "عنوان",
            resolveCoverLabel("عنوان", "خلاصه", """{"type":"doc"}"""),
        )
    }

    @Test
    fun resolveCoverLabel_fallsBackToExcerpt() {
        assertEquals(
            "خلاصه",
            resolveCoverLabel("  ", "خلاصه", """{"type":"doc"}"""),
        )
    }

    @Test
    fun resolveCoverLabel_fallsBackToPlainContent() {
        assertEquals("سلام", resolveCoverLabel("", null, "سلام"))
    }
}

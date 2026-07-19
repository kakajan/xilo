package ir.xilo.app.ui.components

import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class BidiTextTest {

    @Test
    fun firstStrongPersian_isRtl() {
        assertEquals(
            LayoutDirection.Rtl,
            layoutDirectionForContent("سلام خانواده ❤️"),
        )
    }

    @Test
    fun firstStrongEnglish_isLtr() {
        assertEquals(
            LayoutDirection.Ltr,
            layoutDirectionForContent("Hello خانواده"),
        )
    }

    @Test
    fun englishLineAlone_isLtr() {
        assertEquals(
            LayoutDirection.Ltr,
            layoutDirectionForContent("Hi"),
        )
    }

    @Test
    fun empty_usesDefault() {
        assertEquals(
            LayoutDirection.Ltr,
            layoutDirectionForContent("", emptyDefault = LayoutDirection.Ltr),
        )
        assertEquals(
            LayoutDirection.Rtl,
            layoutDirectionForContent("   ", emptyDefault = LayoutDirection.Rtl),
        )
    }

    @Test
    fun arabic_isRtl() {
        assertEquals(
            LayoutDirection.Rtl,
            layoutDirectionForContent("مرحبا بالعالم"),
        )
    }

    @Test
    fun usernameHandle_prefixesAt() {
        assertEquals("@usher", usernameHandle("usher"))
    }

    @Test
    fun mixedParagraphs_resolveIndependently() {
        val annotated = buildContentAwareAnnotatedString(
            "سلام خانواده ❤️\nHi",
            emptyDefault = LayoutDirection.Ltr,
        )
        assertEquals("سلام خانواده ❤️\nHi", annotated.text)
        val styles = annotated.paragraphStyles
        assertEquals(2, styles.size)
        assertEquals(
            androidx.compose.ui.text.style.TextDirection.Rtl,
            styles[0].item.textDirection,
        )
        assertEquals(
            androidx.compose.ui.text.style.TextAlign.Right,
            styles[0].item.textAlign,
        )
        assertEquals(
            androidx.compose.ui.text.style.TextDirection.Ltr,
            styles[1].item.textDirection,
        )
        assertEquals(
            androidx.compose.ui.text.style.TextAlign.Left,
            styles[1].item.textAlign,
        )
    }
}

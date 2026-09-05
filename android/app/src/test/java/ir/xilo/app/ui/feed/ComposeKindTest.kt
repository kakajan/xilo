package ir.xilo.app.ui.feed

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposeKindTest {

    @Test
    fun apiPostType_text_mapsToMicro() {
        assertEquals(ComposeKind.MICRO, ComposeKind.apiPostType(ComposeKind.TEXT))
    }

    @Test
    fun apiPostType_photo_video_link() {
        assertEquals(ComposeKind.PHOTO, ComposeKind.apiPostType(ComposeKind.PHOTO))
        assertEquals(ComposeKind.VIDEO, ComposeKind.apiPostType(ComposeKind.VIDEO))
        assertEquals(ComposeKind.LINK, ComposeKind.apiPostType(ComposeKind.LINK))
    }

    @Test
    fun apiPostType_audio_mapsToArticle() {
        assertEquals(ComposeKind.ARTICLE, ComposeKind.apiPostType(ComposeKind.AUDIO))
    }

    @Test
    fun apiPostType_legacyTextString_mapsToMicro() {
        assertEquals(ComposeKind.MICRO, ComposeKind.apiPostType("text"))
    }
}

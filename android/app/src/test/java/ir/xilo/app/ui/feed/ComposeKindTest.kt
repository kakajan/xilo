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
    fun apiPostType_audio_and_quote_mapToArticle() {
        assertEquals(ComposeKind.ARTICLE, ComposeKind.apiPostType(ComposeKind.AUDIO))
        assertEquals(ComposeKind.ARTICLE, ComposeKind.apiPostType(ComposeKind.QUOTE))
    }

    @Test
    fun apiPostType_legacyTextString_mapsToMicro() {
        assertEquals(ComposeKind.MICRO, ComposeKind.apiPostType("text"))
    }

    @Test
    fun fromPost_quoted_isQuote() {
        assertEquals(
            ComposeKind.QUOTE,
            ComposeKind.fromPost("article", null, "post-1", null),
        )
        assertEquals(
            ComposeKind.QUOTE,
            ComposeKind.fromPost("micro", null, null, "c-1"),
        )
    }

    @Test
    fun fromPost_articleWithAudio_isAudio() {
        assertEquals(
            ComposeKind.AUDIO,
            ComposeKind.fromPost("article", "https://cdn.example/a.mp3", null, null),
        )
    }

    @Test
    fun fromPost_photoStaysPhoto() {
        assertEquals(
            ComposeKind.PHOTO,
            ComposeKind.fromPost("photo", "https://cdn.example/a.mp3", null, null),
        )
    }
}

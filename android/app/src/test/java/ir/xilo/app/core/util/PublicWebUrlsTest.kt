package ir.xilo.app.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicWebUrlsTest {

    private val origin = "https://aile.ir"

    @Test
    fun post_usesUsernameAndSlug() {
        assertEquals(
            "https://aile.ir/usher/hello-world",
            PublicWebUrls.post(origin, "usher", "hello-world"),
        )
    }

    @Test
    fun post_fallsBackToShortPathWithoutUsername() {
        assertEquals(
            "https://aile.ir/p/hello-world",
            PublicWebUrls.post("$origin/", "  ", "hello-world"),
        )
    }

    @Test
    fun comment_appendsReplyQuery() {
        assertEquals(
            "https://aile.ir/usher/hello-world?reply=c1",
            PublicWebUrls.comment(origin, "usher", "hello-world", "c1"),
        )
    }

    @Test
    fun profile_isAbsoluteHandleUrl() {
        assertEquals(
            "https://aile.ir/usher",
            PublicWebUrls.profile(origin, "usher"),
        )
    }

    @Test
    fun sharePostBody_putsUrlLast() {
        assertEquals(
            "Hello\nhttps://aile.ir/usher/hello-world",
            PublicWebUrls.sharePostBody("Hello", "https://aile.ir/usher/hello-world"),
        )
    }

    @Test
    fun parse_canonicalPostAndReply() {
        val dest = PublicWebUrls.parse(
            "https://aile.ir/usher/hello-world?reply=c1",
            origin,
        )
        assertEquals(PublicWebDestination.Post("hello-world", "c1"), dest)
    }

    @Test
    fun parse_shortPostPath() {
        val dest = PublicWebUrls.parse("https://www.aile.ir/p/hello-world", origin)
        assertEquals(PublicWebDestination.Post("hello-world"), dest)
    }

    @Test
    fun parse_rejectsForeignHost() {
        assertNull(PublicWebUrls.parse("https://evil.example/usher/hello", origin))
    }

    @Test
    fun parse_rejectsRelativePath() {
        assertNull(PublicWebUrls.parse("/p/hello-world", origin))
    }
}

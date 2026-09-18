package ir.xilo.app.data.local.entity

import ir.xilo.app.data.remote.dto.decodePostTags
import ir.xilo.app.data.remote.dto.encodePostTags
import ir.xilo.app.data.remote.dto.tags
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostEntityTagsTest {

    @Test
    fun encodeAndDecodeRoundTrip() {
        val original = listOf("فناوری", "اندروید", "xilo")
        val json = encodePostTags(original)
        val decoded = decodePostTags(json)
        assertEquals(original, decoded)
    }

    @Test
    fun decodeHandlesEmptyOrInvalidJson() {
        assertEquals(emptyList<String>(), decodePostTags(""))
        assertEquals(emptyList<String>(), decodePostTags("[]"))
        assertEquals(emptyList<String>(), decodePostTags("not a json"))
        assertEquals(emptyList<String>(), decodePostTags("{ \"key\": 123 }"))
    }

    @Test
    fun postEntityTagsExtensionProperty() {
        val entity = PostEntity(
            id = "test-1",
            authorId = "author-1",
            authorName = "تستر",
            authorUsername = "tester",
            authorAvatar = null,
            title = "پست تستی",
            slug = "test-post",
            content = "متن تست",
            excerpt = "خلاصه",
            coverImageUrl = null,
            createdAt = 1000L,
            tagsJson = encodePostTags(listOf("صوت", "موزیک")),
        )
        assertEquals(listOf("صوت", "موزیک"), entity.tags)
    }

    @Test
    fun defaultPostEntityHasEmptyTags() {
        val entity = PostEntity(
            id = "test-2",
            authorId = "author-1",
            authorName = "تستر",
            authorUsername = "tester",
            authorAvatar = null,
            title = "پست ۲",
            slug = "test-post-2",
            content = "متن",
            excerpt = "خلاصه",
            coverImageUrl = null,
            createdAt = 1000L,
        )
        assertTrue(entity.tags.isEmpty())
    }
}

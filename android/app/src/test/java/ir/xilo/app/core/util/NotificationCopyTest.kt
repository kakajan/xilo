package ir.xilo.app.core.util

import ir.xilo.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationCopyTest {

    @Test
    fun titleResId_mapsKnownTypes() {
        assertEquals(R.string.notif_title_new_follower, NotificationCopy.titleResId("new_follower"))
        assertEquals(R.string.notif_title_post_comment, NotificationCopy.titleResId("post_comment"))
        assertEquals(R.string.notif_title_comment_reply, NotificationCopy.titleResId("COMMENT_REPLY"))
        assertEquals(R.string.notif_title_post_published, NotificationCopy.titleResId("post_published"))
        assertEquals(R.string.notif_title_new_message, NotificationCopy.titleResId("new_message"))
    }

    @Test
    fun titleResId_unknownType_returnsNull() {
        assertNull(NotificationCopy.titleResId("system_announcement"))
        assertNull(NotificationCopy.titleResId(""))
    }

    @Test
    fun bodyResId_onlyTemplateTypes() {
        assertEquals(R.string.notif_body_new_follower, NotificationCopy.bodyResId("new_follower"))
        assertNull(NotificationCopy.bodyResId("post_comment"))
        assertNull(NotificationCopy.bodyResId("new_message"))
        assertNull(NotificationCopy.bodyResId("post_published"))
    }
}

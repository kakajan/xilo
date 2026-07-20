package ir.xilo.app.data.repository

import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationDeepLinkResolverTest {

    private val userDao = mockk<ir.xilo.app.data.local.dao.UserDao>()
    private val apiService = mockk<ir.xilo.app.data.remote.api.XiloApiService>()
    private val repository = NotificationRepository(apiService, userDao)

    @Test
    fun resolveDeepLinkFromData_chatId() = runTest {
        val result = repository.resolveDeepLinkFromData(
            mapOf("type" to "new_message", "chat_id" to "chat-123"),
        )
        assertEquals(NotificationDeepLink.ChatConversation("chat-123"), result)
    }

    @Test
    fun resolveDeepLinkFromData_postSlug() = runTest {
        val result = repository.resolveDeepLinkFromData(
            mapOf("type" to "comment_reply", "slug" to "hello-world", "comment_id" to "c1"),
        )
        assertEquals(NotificationDeepLink.PostDetail("hello-world", "c1"), result)
    }

    @Test
    fun resolveDeepLinkFromData_rejectsUnsafeSlug() = runTest {
        val result = repository.resolveDeepLinkFromData(
            mapOf("type" to "comment_reply", "slug" to "https://evil.example/pwn"),
        )
        assertEquals(NotificationDeepLink.Inbox, result)
    }

    @Test
    fun resolveDeepLinkFromData_unknownKeysIgnored() = runTest {
        val result = repository.resolveDeepLinkFromData(
            mapOf("url" to "https://evil.example", "type" to "system"),
        )
        assertEquals(NotificationDeepLink.Inbox, result)
    }

    @Test
    fun sanitizePushData_normalizesKeys() {
        val sanitized = repository.sanitizePushData(
            mapOf("Chat_ID" to " abc ", "ignored" to "x"),
        )
        assertEquals(mapOf("chat_id" to "abc"), sanitized)
    }
}

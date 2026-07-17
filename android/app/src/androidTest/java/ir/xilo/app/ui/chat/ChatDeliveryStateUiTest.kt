package ir.xilo.app.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import ir.xilo.app.R
import ir.xilo.app.data.local.entity.MessageDeliveryState
import ir.xilo.app.data.local.entity.MessageEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatDeliveryStateUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun pendingMessage_showsQueuedStateWithoutSentTick() {
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = message(MessageDeliveryState.PENDING),
                    isMe = true
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.chat_message_pending))
            .assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.chat_message_sent)
        ).assertDoesNotExist()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.chat_message_state_pending_accessibility)
            )
        ).assertIsDisplayed()
    }

    @Test
    fun authoritativeMessage_isLabeledServerAcceptedNotDeliveredOrRead() {
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = message(MessageDeliveryState.DELIVERED).copy(isRead = true),
                    isMe = true
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.chat_message_sent)
        ).assertIsDisplayed()
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.chat_message_state_sent_accessibility)
            )
        ).assertIsDisplayed()
    }

    @Test
    fun permanentFailure_exposesLabeledRetryAndDeleteTargets() {
        composeRule.setContent {
            MaterialTheme {
                MessageBubble(
                    message = message(MessageDeliveryState.PERMANENT_FAILURE),
                    isMe = true
                )
            }
        }

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.chat_message_retry_accessibility)
        ).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        composeRule.onNodeWithContentDescription(
            context.getString(R.string.chat_message_delete_accessibility)
        ).assertHasClickAction().assertHeightIsAtLeast(48.dp)
        composeRule.onNode(
            SemanticsMatcher.expectValue(
                SemanticsProperties.StateDescription,
                context.getString(R.string.chat_message_state_failed_accessibility)
            )
        ).assertIsDisplayed()
    }

    private fun message(deliveryState: String) = MessageEntity(
        id = "message-1",
        chatId = "chat-1",
        senderId = "user-1",
        senderName = null,
        senderAvatar = null,
        content = "پیام آزمایشی",
        mediaUrl = null,
        replyToId = null,
        clientOperationKey = "123e4567-e89b-42d3-a456-426614174000",
        clientPayloadHash = "payload-hash",
        deliveryState = deliveryState,
        createdAt = 100
    )
}

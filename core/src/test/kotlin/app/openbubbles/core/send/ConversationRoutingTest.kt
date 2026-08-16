package app.openbubbles.core.send

import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import kotlin.test.Test
import kotlin.test.assertEquals

class ConversationRoutingTest {
    @Test
    fun `preferred sending handle matches normalized address`() {
        val chat = Chat().apply {
            usingHandle = "me@icloud.com"
        }

        val selected = selectSendingHandle(
            chat,
            linkedSetOf("mailto:me@icloud.com", "tel:+15550000000"),
        )

        assertEquals("mailto:me@icloud.com", selected)
    }

    @Test
    fun `conversation preserves protocol routing metadata`() {
        val chat = Chat().apply {
            guid = "iMessage;+;group-guid"
            displayName = "Fallback"
            apnTitle = "Group"
            handles.add(Handle().apply { address = "+15550000001" })
            handles.add(Handle().apply { address = "friend@icloud.com" })
        }

        val conversation = buildSendConversation(
            chat = chat,
            afterGuid = "previous-message",
            sender = "mailto:me@icloud.com",
        )

        assertEquals(
            listOf("tel:+15550000001", "mailto:friend@icloud.com", "mailto:me@icloud.com"),
            conversation.participants,
        )
        assertEquals("Group", conversation.cvName)
        assertEquals("iMessage;+;group-guid", conversation.senderGuid)
        assertEquals("previous-message", conversation.afterGuid)
    }
}

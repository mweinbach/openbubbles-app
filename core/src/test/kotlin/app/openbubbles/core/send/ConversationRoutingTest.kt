package app.openbubbles.core.send

import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
    fun `default sending handle is used when chat has no registered preference`() {
        val selected = selectSendingHandle(
            chat = Chat(),
            handles = linkedSetOf("tel:+15550000000", "mailto:me@icloud.com"),
            defaultHandle = "me@icloud.com",
        )

        assertEquals("mailto:me@icloud.com", selected)
    }

    @Test
    fun `global default wins over the address the chat was received on`() {
        // A chat that started on the email keeps usingHandle = email; once a
        // default is chosen every send must follow it or replies keep
        // splitting threads for the other participants.
        val chat = Chat().apply { usingHandle = "mailto:me@icloud.com" }

        val selected = selectSendingHandle(
            chat = chat,
            handles = linkedSetOf("mailto:me@icloud.com", "tel:+15550000000"),
            defaultHandle = "tel:+15550000000",
        )

        assertEquals("tel:+15550000000", selected)
    }

    @Test
    fun `per-chat override wins over the global default`() {
        val chat = Chat().apply {
            usingHandle = "mailto:me@icloud.com"
            senderOverride = "mailto:me@icloud.com"
        }

        val selected = selectSendingHandle(
            chat = chat,
            handles = linkedSetOf("mailto:me@icloud.com", "tel:+15550000000"),
            defaultHandle = "tel:+15550000000",
        )

        assertEquals("mailto:me@icloud.com", selected)
    }

    @Test
    fun `stale override falls back to the global default`() {
        val chat = Chat().apply { senderOverride = "mailto:gone@icloud.com" }

        val selected = selectSendingHandle(
            chat = chat,
            handles = linkedSetOf("mailto:me@icloud.com", "tel:+15550000000"),
            defaultHandle = "tel:+15550000000",
        )

        assertEquals("tel:+15550000000", selected)
    }

    @Test
    fun `override matches a normalized raw address`() {
        val chat = Chat().apply { senderOverride = "me@icloud.com" }

        val selected = selectSendingHandle(
            chat = chat,
            handles = linkedSetOf("tel:+15550000000", "mailto:me@icloud.com"),
            defaultHandle = "tel:+15550000000",
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

    @Test
    fun `first message does not invent a previous message anchor`() {
        val chat = Chat().apply {
            guid = "local-chat-guid"
            handles.add(Handle().apply { address = "friend@icloud.com" })
        }

        val conversation = buildSendConversation(
            chat = chat,
            afterGuid = null,
            sender = "mailto:me@icloud.com",
        )

        assertEquals("local-chat-guid", conversation.senderGuid)
        assertNull(conversation.afterGuid)
    }
}

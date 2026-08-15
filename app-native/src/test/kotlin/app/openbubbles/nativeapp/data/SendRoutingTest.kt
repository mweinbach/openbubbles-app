package app.openbubbles.nativeapp.data

import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SendRoutingTest {

    @Test
    fun `chat selected handle wins over global ordering`() {
        val chat = Chat().apply { usingHandle = "me@icloud.com" }
        val handles = linkedSetOf("tel:+15551234567", "mailto:me@icloud.com")

        assertEquals("mailto:me@icloud.com", sendingHandle(chat, handles))
    }

    @Test
    fun `falls back to registered handle when preferred handle is stale`() {
        val chat = Chat().apply { usingHandle = "mailto:old@icloud.com" }

        assertEquals("mailto:new@icloud.com", sendingHandle(chat, setOf("mailto:new@icloud.com")))
        assertNull(sendingHandle(chat, emptySet()))
    }

    @Test
    fun `conversation preserves chat identity anchor and rust handle prefixes`() {
        val chat = Chat().apply {
            guid = "stable-group-guid"
            apnTitle = "Friends"
            handles.add(Handle().apply { address = "+15551234567" })
            handles.add(Handle().apply { address = "friend@icloud.com" })
        }

        val conversation = sendConversation(chat, "latest-message-guid")

        assertEquals(
            listOf("tel:+15551234567", "mailto:friend@icloud.com"),
            conversation.participants,
        )
        assertEquals("Friends", conversation.cvName)
        assertEquals("stable-group-guid", conversation.senderGuid)
        assertEquals("latest-message-guid", conversation.afterGuid)
    }

    @Test
    fun `conversation includes the registered sending handle`() {
        val chat = Chat().apply {
            guid = "stable-group-guid"
            handles.add(Handle().apply { address = "friend@icloud.com" })
        }

        val conversation = sendConversation(chat, "latest-message-guid", "mailto:me@icloud.com")

        assertEquals(
            listOf("mailto:friend@icloud.com", "mailto:me@icloud.com"),
            conversation.participants,
        )
    }

    @Test
    fun `conversation falls back to chat guid when history is empty`() {
        val chat = Chat().apply { guid = "new-chat-guid" }

        assertEquals("new-chat-guid", sendConversation(chat, null).afterGuid)
    }

    @Test
    fun `direct read receipt includes peer and sending handle`() {
        val chat = Chat().apply {
            guid = "direct-guid"
            handles.add(Handle().apply { address = "friend@icloud.com" })
        }

        val conversation = readReceiptConversation(
            chat,
            "mailto:me@icloud.com",
            "latest",
            notifyOthers = true,
        )

        assertEquals(
            listOf("mailto:friend@icloud.com", "mailto:me@icloud.com"),
            conversation.participants,
        )
        assertEquals("latest", conversation.afterGuid)
    }

    @Test
    fun `group read receipt targets only own devices`() {
        val chat = Chat().apply {
            guid = "group-guid"
            handles.add(Handle().apply { address = "one@icloud.com" })
            handles.add(Handle().apply { address = "two@icloud.com" })
        }

        val conversation = readReceiptConversation(
            chat,
            "mailto:me@icloud.com",
            "latest",
            notifyOthers = true,
        )

        assertEquals(listOf("mailto:me@icloud.com"), conversation.participants)
        assertEquals("group-guid", conversation.senderGuid)
        assertEquals("latest", conversation.afterGuid)
    }

    @Test
    fun `private direct read receipt targets only own devices`() {
        val chat = Chat().apply {
            guid = "direct-guid"
            handles.add(Handle().apply { address = "friend@icloud.com" })
        }

        val conversation = readReceiptConversation(
            chat,
            "mailto:me@icloud.com",
            "latest",
            notifyOthers = false,
        )

        assertEquals(listOf("mailto:me@icloud.com"), conversation.participants)
    }
}

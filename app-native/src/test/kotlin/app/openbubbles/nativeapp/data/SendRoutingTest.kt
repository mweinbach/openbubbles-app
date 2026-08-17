package app.openbubbles.nativeapp.data

import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import kotlinx.coroutines.CancellationException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `global default wins over registration order when chat has no sender`() {
        val handles = linkedSetOf("tel:+15551234567", "mailto:me@icloud.com")

        assertEquals(
            "mailto:me@icloud.com",
            sendingHandle(Chat(), handles, defaultHandle = "me@icloud.com"),
        )
    }

    @Test
    fun `global default wins over the address the chat was received on`() {
        val chat = Chat().apply { usingHandle = "mailto:me@icloud.com" }
        val handles = linkedSetOf("mailto:me@icloud.com", "tel:+15551234567")

        assertEquals(
            "tel:+15551234567",
            sendingHandle(chat, handles, defaultHandle = "tel:+15551234567"),
        )
    }

    @Test
    fun `per-chat override wins over the global default`() {
        val chat = Chat().apply {
            usingHandle = "mailto:me@icloud.com"
            senderOverride = "mailto:me@icloud.com"
        }
        val handles = linkedSetOf("mailto:me@icloud.com", "tel:+15551234567")

        assertEquals(
            "mailto:me@icloud.com",
            sendingHandle(chat, handles, defaultHandle = "tel:+15551234567"),
        )
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
    fun `conversation has no message anchor when history is empty`() {
        val chat = Chat().apply { guid = "new-chat-guid" }

        assertNull(sendConversation(chat, null).afterGuid)
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

    @Test
    fun `SIM chats never send Apple read receipts`() {
        assertFalse(shouldSendAppleReadReceipt(Chat().apply { isRpSms = true }))
        assertTrue(shouldSendAppleReadReceipt(Chat().apply { isRpSms = false }))
    }

    @Test
    fun `receipt cancellation is a coroutine signal rather than a push failure`() {
        val cancelled = CancellationException("Job was cancelled")

        assertNull(appleReadReceiptFailureMessage(cancelled))
        assertEquals(
            "Conversation was marked read locally, but the Apple receipt failed: offline",
            appleReadReceiptFailureMessage(IllegalStateException("offline")),
        )
    }
}

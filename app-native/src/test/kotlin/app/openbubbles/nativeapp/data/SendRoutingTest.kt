package app.openbubbles.nativeapp.data

import app.openbubbles.db.Chat
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
}

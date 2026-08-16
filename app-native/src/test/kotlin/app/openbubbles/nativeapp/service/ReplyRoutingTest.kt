package app.openbubbles.nativeapp.service

import app.openbubbles.db.Chat
import kotlin.test.Test
import kotlin.test.assertEquals

class ReplyRoutingTest {

    @Test
    fun `SIM notification reply uses Android transport`() {
        val chat = Chat().apply { isRpSms = true }

        assertEquals(NotificationReplyTransport.SMS, notificationReplyTransport(chat))
    }

    @Test
    fun `iMessage notification reply uses Apple transport`() {
        val chat = Chat().apply { isRpSms = false }

        assertEquals(NotificationReplyTransport.IMESSAGE, notificationReplyTransport(chat))
    }
}

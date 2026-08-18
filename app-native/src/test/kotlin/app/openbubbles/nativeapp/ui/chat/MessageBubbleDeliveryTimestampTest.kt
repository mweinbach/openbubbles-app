package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MessageBubbleDeliveryTimestampTest {
    @Test
    fun `read timestamp takes precedence over delivered`() {
        val timestamp = deliveryTimestamp(message(dateDelivered = 100L, dateRead = 200L))
        assertEquals(DeliveryTimestamp("Read", 200L), timestamp)
    }

    @Test
    fun `delivered timestamp is shown when unread`() {
        val timestamp = deliveryTimestamp(message(dateDelivered = 100L, dateRead = null))
        assertEquals(DeliveryTimestamp("Delivered", 100L), timestamp)
    }

    @Test
    fun `sent message without receipt has no delivery timestamp`() {
        assertNull(deliveryTimestamp(message(dateDelivered = null, dateRead = null)))
    }

    private fun message(dateDelivered: Long?, dateRead: Long?) = MessageItem(
        id = 1L,
        text = "Hello",
        isFromMe = true,
        date = 50L,
        dateDelivered = dateDelivered,
        dateRead = dateRead,
        status = MessageStatus.SENT,
        isGroupEvent = false,
        reactionEmoji = null,
    )
}

package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * [buildConversationEntries] returns newest-first entries: index 0 renders at
 * the visual bottom of the reversed LazyColumn, the last index at the top.
 */
class ConversationEntriesTest {

    private val zone = ZoneOffset.UTC

    /** 2024-06-10T00:00:00Z — a fixed Monday so day math is deterministic. */
    private val baseDay = 1_717_977_600_000L

    private fun at(hours: Int, minutes: Int = 0, day: Int = 0): Long =
        baseDay + day * 24L * 3_600_000L + hours * 3_600_000L + minutes * 60_000L

    @Test
    fun `separator renders above its messages, never at the transcript bottom`() {
        val entries = buildConversationEntries(
            listOf(
                message(id = 1, date = at(10, 0)),
                message(id = 2, date = at(10, 5)),
            ),
            zone = zone,
        )
        // Visual bottom (index 0) is the newest message; the single separator
        // sits at the visual top, above the cluster it labels.
        assertEquals(3, entries.size)
        assertEquals("message-2", entries[0].key)
        assertEquals("message-1", entries[1].key)
        val separator = assertIs<ConversationEntry.TimeSeparator>(entries[2])
        assertEquals(at(10, 0), separator.epochMillis)
    }

    @Test
    fun `an hour-plus quiet gap starts a new timestamped cluster`() {
        val entries = buildConversationEntries(
            listOf(
                message(id = 1, date = at(10, 0)),
                message(id = 2, date = at(10, 30)),
                message(id = 3, date = at(12, 0)),
            ),
            zone = zone,
        )
        assertEquals(
            listOf("message-3", "time-${at(12, 0)}", "message-2", "message-1", "time-${at(10, 0)}"),
            entries.map { it.key },
        )
    }

    @Test
    fun `a day change forces a separator even under an hour`() {
        val entries = buildConversationEntries(
            listOf(
                message(id = 1, date = at(23, 40)),
                message(id = 2, date = at(0, 20, day = 1)),
            ),
            zone = zone,
        )
        assertEquals(
            listOf("message-2", "time-${at(0, 20, day = 1)}", "message-1", "time-${at(23, 40)}"),
            entries.map { it.key },
        )
    }

    @Test
    fun `group runs label the first bubble and anchor the avatar on the last`() {
        val entries = buildConversationEntries(
            listOf(
                message(id = 1, date = at(10, 0), sender = "alex@icloud.com"),
                message(id = 2, date = at(10, 1), sender = "alex@icloud.com"),
                message(id = 3, date = at(10, 2), sender = "sam@icloud.com"),
                message(id = 4, date = at(10, 3), fromMe = true),
            ),
            zone = zone,
            showSenderNames = true,
        )
        val byId = entries.filterIsInstance<ConversationEntry.Message>()
            .associateBy { it.message.id }

        // Alex's run: name on the first bubble, avatar on the last.
        assertTrue(byId.getValue(1).showSenderName)
        assertFalse(byId.getValue(1).showAvatar)
        assertFalse(byId.getValue(2).showSenderName)
        assertTrue(byId.getValue(2).showAvatar)
        // Sam's single message carries both.
        assertTrue(byId.getValue(3).showSenderName)
        assertTrue(byId.getValue(3).showAvatar)
        // My messages never do.
        assertFalse(byId.getValue(4).showSenderName)
        assertFalse(byId.getValue(4).showAvatar)
    }

    @Test
    fun `direct chats never set the avatar flag`() {
        val entries = buildConversationEntries(
            listOf(message(id = 1, date = at(10, 0), sender = "alex@icloud.com")),
            zone = zone,
            showSenderNames = false,
        )
        val only = entries.filterIsInstance<ConversationEntry.Message>().single()
        assertFalse(only.showSenderName)
        assertFalse(only.showAvatar)
    }

    @Test
    fun `a separator breaks same-author bubble grouping`() {
        val entries = buildConversationEntries(
            listOf(
                message(id = 1, date = at(10, 0), sender = "alex@icloud.com"),
                message(id = 2, date = at(13, 0), sender = "alex@icloud.com"),
            ),
            zone = zone,
            showSenderNames = true,
        )
        val byId = entries.filterIsInstance<ConversationEntry.Message>()
            .associateBy { it.message.id }
        assertFalse(byId.getValue(1).tightBottom)
        assertFalse(byId.getValue(2).tightTop)
        // Both messages start and end their own runs.
        assertTrue(byId.getValue(1).showSenderName)
        assertTrue(byId.getValue(1).showAvatar)
        assertTrue(byId.getValue(2).showSenderName)
        assertTrue(byId.getValue(2).showAvatar)
    }

    private fun message(
        id: Long,
        date: Long,
        fromMe: Boolean = false,
        sender: String? = if (fromMe) null else "alex@icloud.com",
    ) = MessageItem(
        id = id,
        text = "m$id",
        isFromMe = fromMe,
        date = date,
        status = MessageStatus.SENT,
        isGroupEvent = false,
        reactionEmoji = null,
        senderAddress = sender,
        guid = "g$id",
    )
}

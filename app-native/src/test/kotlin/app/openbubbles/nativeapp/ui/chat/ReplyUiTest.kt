package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReplyUiTest {

    @Test
    fun `plain messages do not resolve a quote`() {
        assertNull(resolveReplyQuote(message(guid = "solo"), emptyMap()))
    }

    @Test
    fun `quote prefers stored preview text`() {
        val reply = message(
            guid = "child",
            text = "later",
            replyToGuid = "root",
            replyPreviewText = "stored original",
        )
        val quote = resolveReplyQuote(reply, mapOf("root" to message(guid = "root", text = "live")))
        assertEquals("stored original", quote?.text)
        assertEquals(true, quote?.fromMe)
    }

    @Test
    fun `quote falls back to the live original and sender name`() {
        val original = message(
            guid = "root",
            text = "original body",
            fromMe = false,
            senderAddress = "tel:+1555",
        )
        val reply = message(guid = "child", replyToGuid = "root")
        val quote = resolveReplyQuote(
            reply,
            mapOf("root" to original),
            senderNames = mapOf("tel:+1555" to "Alex"),
        )
        assertEquals("original body", quote?.text)
        assertEquals(false, quote?.fromMe)
        assertEquals("Alex", quote?.senderName)
    }

    @Test
    fun `missing original still produces a tappable quote`() {
        val quote = resolveReplyQuote(message(guid = "child", replyToGuid = "gone"), emptyMap())
        assertEquals("Message", quote?.text)
        assertEquals(false, quote?.fromMe)
    }

    @Test
    fun `attachment-only originals quote the file name`() {
        val original = message(guid = "root", text = "").copy(
            attachmentMeta = AttachmentMeta(
                guid = "att",
                mime = "image/jpeg",
                name = "trailhead.jpg",
                sizeBytes = 12L,
                isImage = true,
                downloaded = true,
            ),
        )
        val quote = resolveReplyQuote(
            message(guid = "child", text = "nice", replyToGuid = "root"),
            mapOf("root" to original),
        )
        assertEquals("trailhead.jpg", quote?.text)
    }

    @Test
    fun `thread membership is part-aware`() {
        val root = message(guid = "root")
        val match = message(guid = "a", replyToGuid = "root", replyToPart = 3L)
        val otherPart = message(guid = "b", replyToGuid = "root", replyToPart = 4L)
        val otherRoot = message(guid = "c", replyToGuid = "nope", replyToPart = 3L)
        assertTrue(belongsToReplyThread(root, "root", 3L))
        assertTrue(belongsToReplyThread(match, "root", 3L))
        assertFalse(belongsToReplyThread(otherPart, "root", 3L))
        assertFalse(belongsToReplyThread(otherRoot, "root", 3L))
    }

    @Test
    fun `live transcript messages merge into an open thread`() {
        val source = message(id = 2, guid = "child", replyToGuid = "root", replyToPart = 0L)
        val thread = ReplyThreadState(
            rootGuid = "root",
            part = 0L,
            messages = listOf(source),
            loading = false,
        )
        val root = message(id = 1, guid = "root", text = "original")
        val extra = message(id = 3, guid = "later", text = "second", replyToGuid = "root")
        val merged = mergeReplyThread(thread, listOf(root, source, extra))
        assertEquals(listOf("root", "child", "later"), merged.messages.map { it.guid })
    }

    @Test
    fun `empty query results still keep the tapped message`() {
        val source = message(guid = "child", replyToGuid = "root")
        assertEquals(listOf("child"), ensureThreadContains(emptyList(), source).map { it.guid })
    }

    @Test
    fun `reply bubbles do not tighten into the previous same-author run`() {
        val root = message(id = 1, guid = "root", text = "original", fromMe = true)
        val follow = message(id = 2, guid = "follow", text = "and another", fromMe = true)
        val reply = message(
            id = 3,
            guid = "child",
            text = "reply",
            fromMe = true,
            replyToGuid = "root",
        )
        val entries = buildConversationEntries(listOf(root, follow, reply))
            .filterIsInstance<ConversationEntry.Message>()
        val byGuid = entries.associate { it.message.guid to it }
        assertTrue(byGuid.getValue("follow").tightTop)
        assertFalse(byGuid.getValue("follow").tightBottom)
        assertFalse(byGuid.getValue("child").tightTop)
    }

    private fun message(
        id: Long = 1L,
        guid: String,
        text: String = "hello",
        fromMe: Boolean = true,
        senderAddress: String? = null,
        replyToGuid: String? = null,
        replyToPart: Long? = null,
        replyPreviewText: String? = null,
    ) = MessageItem(
        id = id,
        text = text,
        isFromMe = fromMe,
        date = id,
        status = MessageStatus.SENT,
        isGroupEvent = false,
        reactionEmoji = null,
        senderAddress = senderAddress,
        guid = guid,
        replyToGuid = replyToGuid,
        replyToPart = replyToPart,
        replyPreviewText = replyPreviewText,
    )
}

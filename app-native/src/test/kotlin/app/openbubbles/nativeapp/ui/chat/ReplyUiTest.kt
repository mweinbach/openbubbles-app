package app.openbubbles.nativeapp.ui.chat

import androidx.compose.ui.geometry.Rect
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
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
    fun `reply counts are grouped by root and omit ordinary messages`() {
        val messages = listOf(
            message(guid = "root"),
            message(guid = "one", replyToGuid = "root"),
            message(guid = "two", replyToGuid = "root"),
            message(guid = "other", replyToGuid = "another-root"),
            message(guid = "plain"),
        )

        assertEquals(mapOf("root" to 2, "another-root" to 1), replyCountsByRoot(messages))
    }

    @Test
    fun `adjacent replies keep context without repeating the root quote`() {
        val root = message(id = 1, guid = "root")
        val reply = message(id = 2, guid = "reply", replyToGuid = "root")
        val adjacent = buildConversationEntries(listOf(root, reply))
        assertEquals(setOf("reply"), repliesWithInlineContext(adjacent))

        val separated = buildConversationEntries(
            listOf(root, message(id = 2, guid = "middle"), reply.copy(id = 3, date = 3)),
        )
        assertTrue(repliesWithInlineContext(separated).isEmpty())
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
    fun `scroll target resolves the original message entry`() {
        val root = message(id = 1, guid = "root")
        val mid = message(id = 2, guid = "mid")
        val reply = message(id = 3, guid = "child", replyToGuid = "root")
        val entries = buildConversationEntries(listOf(root, mid, reply))
        val target = resolveReplyScrollTarget(entries, "root")
        assertEquals(
            "root",
            (entries[assertNotNull(target)] as ConversationEntry.Message).message.guid,
        )
    }

    @Test
    fun `scroll target is null when the original is outside the window`() {
        val entries = buildConversationEntries(
            listOf(message(id = 3, guid = "child", replyToGuid = "root")),
        )
        assertNull(resolveReplyScrollTarget(entries, "root"))
        assertNull(resolveReplyScrollTarget(entries, null))
    }

    @Test
    fun `marker hooks over an incoming reply's leading top corner`() {
        val reply = Rect(left = 20f, top = 60f, right = 300f, bottom = 120f)
        val geometry = connector(
            // Opposite side, so the leg is free to rise past the quote.
            quote = Rect(left = 200f, top = 0f, right = 400f, bottom = 40f),
            reply = reply,
            replyFromMe = false,
        )

        assertEquals(34f, geometry.corner.x)
        assertEquals(34f, geometry.legEnd.x)
        // The arm points back toward the transcript centre.
        assertEquals(46f, geometry.armStart.x)
        // The flat tail sits just under the leg's crown.
        assertEquals(geometry.corner.y + 2f, geometry.armStart.y)
        // The leg stops just short of the bubble instead of touching it.
        assertEquals(reply.top - 3f, geometry.legEnd.y)
        assertEquals(43f, geometry.corner.y)
    }

    @Test
    fun `marker mirrors onto an outgoing reply's trailing corner`() {
        val geometry = connector(
            quote = Rect(left = 20f, top = 0f, right = 200f, bottom = 40f),
            reply = Rect(left = 100f, top = 60f, right = 380f, bottom = 120f),
            replyFromMe = true,
        )

        assertEquals(366f, geometry.corner.x)
        assertEquals(366f, geometry.legEnd.x)
        assertEquals(354f, geometry.armStart.x)
    }

    @Test
    fun `right-to-left mirrors the marker`() {
        val quote = Rect(left = 200f, top = 0f, right = 400f, bottom = 40f)
        val reply = Rect(left = 20f, top = 60f, right = 300f, bottom = 120f)

        val ltr = connector(quote, reply, replyFromMe = false, isLtr = true)
        val rtl = connector(quote, reply, replyFromMe = false, isLtr = false)

        assertEquals(reply.left + 14f, ltr.corner.x)
        assertEquals(reply.right - 14f, rtl.corner.x)
    }

    @Test
    fun `marker clears a quote sharing the reply's side`() {
        val quote = Rect(left = 20f, top = 0f, right = 200f, bottom = 40f)
        val geometry = connector(
            quote = quote,
            reply = Rect(left = 20f, top = 50f, right = 300f, bottom = 120f),
            replyFromMe = false,
        )

        assertEquals(quote.bottom + 3f, geometry.corner.y)
    }

    @Test
    fun `marker rises past a quote on the opposite side`() {
        val quote = Rect(left = 200f, top = 0f, right = 400f, bottom = 40f)
        val geometry = connector(
            quote = quote,
            reply = Rect(left = 20f, top = 50f, right = 300f, bottom = 120f),
            replyFromMe = false,
        )

        assertTrue(geometry.corner.y < quote.bottom)
    }

    @Test
    fun `marker stays inside a bubble narrower than the inset`() {
        val geometry = connector(
            quote = Rect(left = 200f, top = 0f, right = 400f, bottom = 40f),
            reply = Rect(left = 20f, top = 60f, right = 40f, bottom = 120f),
            replyFromMe = false,
        )

        assertEquals(30f, geometry.corner.x)
    }

    private fun connector(
        quote: Rect,
        reply: Rect,
        replyFromMe: Boolean,
        isLtr: Boolean = true,
    ) = replyConnectorGeometry(
        quote = quote,
        reply = reply,
        replyFromMe = replyFromMe,
        isLtr = isLtr,
        edgeInset = 14f,
        armLength = 12f,
        legLength = 14f,
        clearance = 3f,
        tipDrop = 2f,
    )

    @Test
    fun `one to one chats do not reserve group sender chrome`() {
        val messages = listOf(
            message(id = 1, guid = "a", fromMe = false, senderAddress = "emily@icloud.com"),
            message(id = 2, guid = "b", fromMe = false, senderAddress = "+15551234567"),
        )

        val direct = buildConversationEntries(messages, showSenderNames = false)
            .filterIsInstance<ConversationEntry.Message>()
        assertTrue(direct.none { it.showSenderName || it.showAvatar })

        val group = buildConversationEntries(messages, showSenderNames = true)
            .filterIsInstance<ConversationEntry.Message>()
        assertTrue(group.any { it.showSenderName || it.showAvatar })
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

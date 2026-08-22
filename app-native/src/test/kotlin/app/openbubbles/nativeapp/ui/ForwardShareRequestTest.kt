package app.openbubbles.nativeapp.ui

import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ForwardShareRequestTest {

    @Test
    fun `multiple selected messages retain chronological text order`() {
        val request = forwardShareRequest(
            listOf(message(id = 2L, text = "second"), message(id = 1L, text = "first")),
            attachmentUri = { null },
        )

        assertEquals("first\nsecond", request?.text)
        assertEquals(emptyList(), request?.streams)
        assertEquals("text/plain", request?.mimeType)
    }

    @Test
    fun `downloaded attachments become an internal unsent share draft`() {
        val image = attachment("image", "image/jpeg")
        val video = attachment("video", "video/mp4")
        val request = forwardShareRequest(
            listOf(message(id = 1L, text = "caption", attachments = listOf(image, video))),
            attachmentUri = { guid -> "content://private.attachments/$guid" },
        )

        assertEquals("caption", request?.text)
        assertEquals(
            listOf("content://private.attachments/image", "content://private.attachments/video"),
            request?.streams,
        )
        assertEquals("*/*", request?.mimeType)
    }

    @Test
    fun `undownloaded attachment without message text cannot be forwarded`() {
        assertNull(
            forwardShareRequest(
                listOf(message(id = 1L, attachments = listOf(attachment("missing", "image/png")))),
                attachmentUri = { null },
            ),
        )
    }

    @Test
    fun `attachment only draft preserves one attachment mime type`() {
        val request = forwardShareRequest(
            listOf(message(id = 1L, attachments = listOf(attachment("photo", "image/png")))),
            attachmentUri = { "content://private.attachments/photo" },
        )

        assertEquals("image/png", request?.mimeType)
        assertNull(request?.text)
    }

    @Test
    fun `opening or canceling the picker never marks a message as forwarded`() {
        assertEquals(
            emptyList(),
            forwardingHandoffMessageIds(listOf(11L, 12L), destinationChatId = null),
        )
        assertEquals(
            emptyList(),
            forwardingHandoffMessageIds(listOf(11L, 12L), destinationChatId = 0L),
        )
    }

    @Test
    fun `confirmed destination marks each real source message exactly once`() {
        assertEquals(
            listOf(11L, 12L),
            forwardingHandoffMessageIds(
                messageIds = listOf(11L, 12L, 11L, 0L, -1L),
                destinationChatId = 44L,
            ),
        )
    }

    @Test
    fun `new conversation keeps forwarding sources until destination creation`() {
        val request = forwardShareRequest(
            listOf(message(id = 7L, text = "forward me")),
            attachmentUri = { null },
        ) ?: error("expected forward draft")
        val picker = ShareTargetPickerKey(request, forwardedMessageIds = listOf(7L))
        val creator = NewChatKey(
            body = picker.request.text,
            sharedUris = picker.request.streams,
            forwardedMessageIds = picker.forwardedMessageIds,
        )

        assertEquals(emptyList(), forwardingHandoffMessageIds(creator.forwardedMessageIds, null))
        assertEquals(listOf(7L), forwardingHandoffMessageIds(creator.forwardedMessageIds, 88L))
    }

    private fun message(
        id: Long,
        text: String = "",
        attachments: List<AttachmentMeta> = emptyList(),
    ) = MessageItem(
        id = id,
        text = text,
        isFromMe = false,
        date = id,
        status = MessageStatus.DELIVERED,
        isGroupEvent = false,
        reactionEmoji = null,
        attachmentMeta = attachments.firstOrNull(),
        attachmentMetas = attachments,
        guid = "message-$id",
    )

    private fun attachment(guid: String, mime: String) = AttachmentMeta(
        guid = guid,
        mime = mime,
        name = "$guid.bin",
        sizeBytes = 12L,
        isImage = mime.startsWith("image/"),
        downloaded = true,
    )
}

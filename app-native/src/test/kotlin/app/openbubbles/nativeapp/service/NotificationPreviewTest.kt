package app.openbubbles.nativeapp.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import uniffi.rust_lib_bluebubbles.UIndexedPart
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.UPart

class NotificationPreviewTest {

    @Test
    fun `text and mentions remain readable`() {
        val preview = notificationPreview(
            message(
                UPart.Text("Hello ", ""),
                UPart.Mention("mailto:friend@icloud.com", "Friend"),
            ),
        )

        assertEquals("Hello Friend", preview)
    }

    @Test
    fun `photo only message has legacy notification summary`() {
        assertEquals(
            "1 Photo",
            notificationPreview(message(attachment("image/jpeg", "photo.jpg"))),
        )
    }

    @Test
    fun `multipart attachment counts use legacy labels`() {
        assertEquals(
            "2 Photos & 1 Video",
            notificationPreview(
                message(
                    attachment("image/jpeg", "one.jpg"),
                    attachment("image/png", "two.png"),
                    attachment("video/quicktime", "clip.mov"),
                    UPart.Text("caption", ""),
                ),
            ),
        )
    }

    @Test
    fun `live photo sidecars and mms layout are not counted`() {
        assertEquals(
            "1 Photo",
            notificationPreview(
                message(
                    attachment("image/jpeg", "photo.jpg"),
                    attachment("image/heic", "live.heic", iris = true),
                    attachment("application/smil", "layout.smil"),
                ),
            ),
        )
    }

    @Test
    fun `object only message does not create an empty notification`() {
        assertNull(notificationPreview(message(UPart.Object("{}"))))
    }

    @Test
    fun `history removes matching newest row and preserves oldest`() {
        val rows = listOf("oldest", "middle", "current")

        assertEquals(
            listOf("oldest", "middle"),
            withoutCurrentNotificationRow(rows, "current") { it },
        )
    }

    @Test
    fun `history remains unchanged when newest row differs`() {
        val rows = listOf("oldest", "middle", "newest")

        assertEquals(
            rows,
            withoutCurrentNotificationRow(rows, "different") { it },
        )
    }

    private fun message(vararg parts: UPart) = UMessageInst(
        id = "message-id",
        sender = "mailto:friend@icloud.com",
        conversation = null,
        message = UMessage.Normal(
            parts = parts.map { UIndexedPart(it, null) },
            effect = null,
            replyGuid = null,
            replyPart = null,
            subject = null,
            voice = false,
            isSms = false,
            appJson = null,
            linkJson = null,
        ),
        sentTimestamp = 1_700_000_000_000uL,
        sendDelivered = false,
        verificationFailed = false,
    )

    private fun attachment(
        mime: String,
        name: String,
        iris: Boolean = false,
    ) = UPart.Attachment(
        part = 0uL,
        uti = "public.data",
        mime = mime,
        name = name,
        iris = iris,
        xml = "<plist/>",
    )
}

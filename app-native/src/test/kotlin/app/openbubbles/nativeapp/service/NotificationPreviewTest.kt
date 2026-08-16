package app.openbubbles.nativeapp.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
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
    fun `tapback notification names the reaction and target text`() {
        assertEquals(
            "Liked “the new photo”",
            notificationPreview(reaction("{\"React\":{\"reaction\":\"Like\",\"enable\":true}}")),
        )
    }

    @Test
    fun `removed custom emoji notification remains readable`() {
        assertEquals(
            "Removed 🔥 from “the new photo”",
            notificationPreview(
                reaction("{\"React\":{\"reaction\":{\"Emoji\":\"🔥\"},\"enable\":false}}"),
            ),
        )
    }

    @Test
    fun `reaction with empty push text uses resolved target`() {
        assertEquals(
            "Liked “the stored message”",
            notificationPreview(
                reaction(
                    "{\"React\":{\"reaction\":\"Like\",\"enable\":true}}",
                    toText = "",
                ),
                reactionTargetText = "the stored message",
            ),
        )
    }

    @Test
    fun `reaction with no target never shows empty quotes`() {
        assertEquals(
            "Emphasized a message",
            notificationPreview(
                reaction(
                    "{\"React\":{\"reaction\":\"Emphasize\",\"enable\":true}}",
                    toText = "",
                ),
            ),
        )
    }

    @Test
    fun `conversation notification id is stable per chat`() {
        assertEquals(conversationNotificationId(42L), conversationNotificationId(42L))
        assertNotEquals(conversationNotificationId(42L), conversationNotificationId(43L))
    }

    @Test
    fun `legacy notification stack keeps one notification per chat`() {
        val stableId = conversationNotificationId(7L)
        val entries = listOf(
            ConversationNotificationEntry(101, 7L, 100L, isSummary = false),
            ConversationNotificationEntry(102, 7L, 300L, isSummary = true),
            ConversationNotificationEntry(stableId, 7L, 200L, isSummary = false),
            ConversationNotificationEntry(201, 8L, 100L, isSummary = false),
        )

        assertEquals(
            setOf(101, 102),
            redundantConversationNotificationIds(entries).toSet(),
        )
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
            parts = parts.map { UIndexedPart(it, null, null) },
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

    private fun reaction(
        json: String,
        toText: String = "the new photo",
    ) = UMessageInst(
        id = "reaction-id",
        sender = "mailto:friend@icloud.com",
        conversation = null,
        message = UMessage.React(
            toUuid = "message-id",
            toPart = null,
            reactionJson = json,
            toText = toText,
            parts = emptyList(),
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

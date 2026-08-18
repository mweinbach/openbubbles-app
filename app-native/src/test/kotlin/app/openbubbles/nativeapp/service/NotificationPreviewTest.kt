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
        assertEquals(conversationNotificationId(7L), conversationNotificationId(listOf(9L, 7L, 12L).min()))
    }

    @Test
    fun `conversation identity uses the lowest related chat id`() {
        val identity = conversationIdentity(9L, listOf(12L, 7L, 9L))

        assertEquals(7L, identity.conversationChatId)
        assertEquals("chat-7", identity.conversationId)
        assertEquals(conversationNotificationId(7L), identity.notificationId)
    }

    @Test
    fun `incoming and reply confirmation share the same conversation identity`() {
        val incoming = conversationIdentity(8L, listOf(8L, 11L))
        val reply = conversationIdentity(11L, listOf(8L, 11L))

        assertEquals(incoming.conversationId, reply.conversationId)
        assertEquals(incoming.notificationId, reply.notificationId)
        assertEquals("chat-8", incoming.conversationId)
    }

    @Test
    fun `direct history person key matches the shortcut chat guid`() {
        assertEquals(
            "any;+15551234567",
            messagingHistoryPersonKey(
                isGroup = false,
                handleAddress = "+15551234567",
                chatGuid = "any;+15551234567",
            ),
        )
    }

    @Test
    fun `group history person key uses the handle address`() {
        assertEquals(
            "mailto:alice@icloud.com",
            messagingHistoryPersonKey(
                isGroup = true,
                handleAddress = "mailto:alice@icloud.com",
                chatGuid = "chat-guid",
            ),
        )
        assertNull(
            messagingHistoryPersonKey(
                isGroup = true,
                handleAddress = "  ",
                chatGuid = "chat-guid",
            ),
        )
    }

    @Test
    fun `conversation shortcut spec keeps locus and shortcut ids aligned`() {
        val identity = conversationIdentity(4L, listOf(4L))
        val spec = conversationShortcutSpec(identity, title = "Alice", chatGuid = "chat-guid")

        assertEquals("chat-4", spec.id)
        assertEquals("chat-4", spec.locusId)
        assertEquals("Alice", spec.shortLabel)
        assertEquals("chat-guid", spec.chatGuid)
    }

    @Test
    fun `blank conversation title falls back for shortcut labels`() {
        val spec = conversationShortcutSpec(
            conversationIdentity(1L),
            title = "  ",
            chatGuid = "guid",
        )

        assertEquals("OpenBubbles", spec.shortLabel)
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

    @Test
    fun `cancel matching covers related ids conversation keys and guids`() {
        val keep = PostedNotificationRef(1, chatId = 8L, conversationId = "chat-8")
        val byChatId = PostedNotificationRef(2, chatId = 7L, conversationId = "other")
        val byConversation = PostedNotificationRef(3, chatId = 0L, conversationId = "chat-7")
        val byGuid = PostedNotificationRef(4, chatId = 0L, chatGuid = "guid-7")
        val tagged = PostedNotificationRef(5, tag = "msg", chatId = 9L)

        val matches = matchingConversationNotifications(
            entries = listOf(keep, byChatId, byConversation, byGuid, tagged),
            relatedChatIds = setOf(7L, 9L),
            conversationIds = setOf("chat-7"),
            chatGuids = setOf("guid-7"),
        )

        assertEquals(setOf(2, 3, 4, 5), matches.map { it.id }.toSet())
    }

    @Test
    fun `visible conversation tracks the open chat and its siblings`() {
        VisibleConversation.reset()
        VisibleConversation.enter(listOf(7L, 9L))
        assertEquals(true, VisibleConversation.contains(7L))
        assertEquals(true, VisibleConversation.contains(9L))
        assertEquals(false, VisibleConversation.contains(8L))

        VisibleConversation.leave(listOf(9L))
        assertEquals(false, VisibleConversation.contains(7L))
        assertEquals(false, VisibleConversation.contains(9L))
        VisibleConversation.reset()
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

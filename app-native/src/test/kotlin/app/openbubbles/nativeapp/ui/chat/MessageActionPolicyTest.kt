package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.core.model.InteractivePayload
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.RichLinkPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageActionPolicyTest {

    @Test
    fun `ordinary incoming and outgoing messages can open actions`() {
        assertTrue(canOpenMessageActions(message(fromMe = false)))
        assertTrue(canOpenMessageActions(message(fromMe = true, status = MessageStatus.DELIVERED)))
        assertTrue(canOpenMessageActions(message(status = MessageStatus.FAILED)))
        assertTrue(canOpenMessageActions(message(fromMe = true, status = MessageStatus.SENDING)))
    }

    @Test
    fun `group events unsent and incoming sending cannot open actions`() {
        assertFalse(canOpenMessageActions(message(isGroupEvent = true)))
        assertFalse(canOpenMessageActions(message(unsent = true)))
        assertFalse(canOpenMessageActions(message(fromMe = false, status = MessageStatus.SENDING)))
    }

    @Test
    fun `double tap is an eligible iMessage shortcut only`() {
        assertTrue(canDoubleTapMessageActions(message()))
        assertFalse(canDoubleTapMessageActions(message(isSms = true)))
        assertFalse(canDoubleTapMessageActions(message(status = MessageStatus.SENDING)))
        assertFalse(canDoubleTapMessageActions(message(status = MessageStatus.FAILED)))
        assertFalse(canDoubleTapMessageActions(message(unsent = true)))
        assertFalse(canDoubleTapMessageActions(message(isGroupEvent = true)))
    }

    @Test
    fun `only failed outgoing iMessages offer a retry action`() {
        assertTrue(canRetryOutgoingMessage(message(fromMe = true, status = MessageStatus.FAILED)))
        assertFalse(canRetryOutgoingMessage(message(fromMe = false, status = MessageStatus.FAILED)))
        assertFalse(canRetryOutgoingMessage(message(fromMe = true, status = MessageStatus.SENDING)))
        assertFalse(canRetryOutgoingMessage(message(fromMe = true, status = MessageStatus.DELIVERED)))
        assertFalse(
            canRetryOutgoingMessage(message(fromMe = true, status = MessageStatus.FAILED, isSms = true)),
        )
    }

    @Test
    fun `outgoing sending and failed messages offer cancellation without enabling reactions`() {
        val sending = message(fromMe = true, status = MessageStatus.SENDING)
        val failed = message(fromMe = true, status = MessageStatus.FAILED)

        assertTrue(canOpenMessageActions(sending))
        assertTrue(canCancelOutgoingMessage(sending))
        assertTrue(canCancelOutgoingMessage(failed))
        assertFalse(canDoubleTapMessageActions(sending))
        assertFalse(canDoubleTapMessageActions(failed))
        assertFalse(canCancelOutgoingMessage(message(fromMe = false, status = MessageStatus.SENDING)))
        assertFalse(canCancelOutgoingMessage(message(fromMe = true, status = MessageStatus.DELIVERED)))
    }

    @Test
    fun `synced deletion is explicit and never applies to carrier or unfinished messages`() {
        assertTrue(canDeleteMessageEverywhere(message(fromMe = false, status = MessageStatus.DELIVERED)))
        assertTrue(canDeleteMessageEverywhere(message(fromMe = true, status = MessageStatus.DELIVERED)))
        assertFalse(canDeleteMessageEverywhere(message(isSms = true)))
        assertFalse(canDeleteMessageEverywhere(message(fromMe = true, status = MessageStatus.SENDING)))
        assertFalse(canDeleteMessageEverywhere(message(fromMe = true, status = MessageStatus.FAILED)))
    }

    @Test
    fun `local deletion cannot bypass cancellation for an active outgoing send`() {
        assertFalse(canDeleteMessageLocally(message(fromMe = true, status = MessageStatus.SENDING)))
        assertTrue(canDeleteMessageLocally(message(fromMe = true, status = MessageStatus.FAILED)))
        assertTrue(canDeleteMessageLocally(message(fromMe = true, status = MessageStatus.DELIVERED)))
        assertTrue(canDeleteMessageLocally(message(fromMe = false, status = MessageStatus.DELIVERED)))
    }

    @Test
    fun `picker shows only reactions for the selected part`() {
        val reactions = listOf(
            app.openbubbles.nativeapp.data.MessageReactionUi("👍", "alex", false, targetPart = 0L),
            app.openbubbles.nativeapp.data.MessageReactionUi("❤️", "alex", false, targetPart = 1L),
        )

        assertEquals(listOf("❤️"), reactionsForPart(reactions, 1L).map { it.emoji })
    }

    @Test
    fun `plain text uses the text part`() {
        val incoming = message(text = "hello", locators = mapOf(0L to "0:0:5"))
        assertEquals(0L, defaultMessageActionPart(incoming))
        assertEquals(0L, messageTextPart(incoming))
        assertTrue(messageShowsTextBubble(incoming))
    }

    @Test
    fun `attachment-only messages use the last attachment part`() {
        val photo = attachment(guid = "p1", partIndex = 2)
        val message = message(text = "").copy(attachmentMetas = listOf(photo), attachmentMeta = photo)
        assertEquals(2L, defaultMessageActionPart(message))
        assertFalse(messageShowsTextBubble(message))
    }

    @Test
    fun `text plus attachment keeps the text part for the default mapping`() {
        val photo = attachment(guid = "p1", partIndex = 1)
        val message = message(
            text = "found the trailhead",
            locators = mapOf(0L to "0:0:18", 1L to "1:0:0"),
        ).copy(attachmentMetas = listOf(photo), attachmentMeta = photo)
        assertEquals(0L, defaultMessageActionPart(message))
        assertEquals(0L, messageTextPart(message))
    }

    @Test
    fun `rich-link-only messages still target the text part`() {
        val preview = richLink()
        val message = message(
            text = "https://www.nps.gov/yose/index.htm",
        ).copy(richLink = preview)
        assertEquals(0L, defaultMessageActionPart(message))
        assertFalse(messageShowsTextBubble(message))
    }

    @Test
    fun `interactive balloons keep the text part`() {
        val message = message(text = "Poll").copy(
            interactivePayload = InteractivePayload.Unsupported(
                bundleId = "com.apple.polls",
                appName = "Polls",
                caption = "Saturday?",
                url = null,
            ),
        )
        assertEquals(0L, defaultMessageActionPart(message))
        assertFalse(messageShowsTextBubble(message))
    }

    @Test
    fun `standard tapbacks have spoken labels`() {
        assertEquals(
            listOf("Love", "Like", "Dislike", "Laugh", "Emphasize", "Question"),
            ActionTapbacks.map(::tapbackContentDescription),
        )
    }
}

class MessageBubbleInteractionTest {

    @Test
    fun `double tapping an eligible text bubble opens actions once`() {
        val events = mutableListOf<String>()
        val bound = bindMessagePartInteraction(
            onOpenActions = { events += "actions:msg-1:0" },
            enableDoubleTapActions = true,
        )

        bound.onDoubleClick!!.invoke()

        assertEquals(listOf("actions:msg-1:0"), events)
        assertNull(bound.onClick)
    }

    @Test
    fun `single tap still opens an attachment or link`() {
        val events = mutableListOf<String>()
        val bound = bindMessagePartInteraction(
            onClick = { events += "open:att-1" },
            onOpenActions = { events += "actions:msg-1:2" },
            enableDoubleTapActions = true,
        )

        bound.onClick!!.invoke()

        assertEquals(listOf("open:att-1"), events)
    }

    @Test
    fun `long press still opens actions without a second callback`() {
        val events = mutableListOf<String>()
        val bound = bindMessagePartInteraction(
            onClick = { events += "open" },
            onOpenActions = { events += "actions" },
            enableDoubleTapActions = true,
        )

        bound.onLongClick!!.invoke()

        assertEquals(listOf("actions"), events)
        assertEquals(1, messageActionInvocationCount(listOf(MessageSurfaceOutcome.OpenActions)))
    }

    @Test
    fun `sms does not bind a double-tap reaction shortcut`() {
        val message = message()
        val bound = bindMessagePartInteraction(
            onClick = {},
            onOpenActions = {},
            enableDoubleTapActions = canDoubleTapMessageActions(message.copy(isSms = true)),
        )

        assertNull(bound.onDoubleClick)
        assertTrue(bound.onLongClick != null)
    }
}

private fun message(
    text: String = "hello",
    fromMe: Boolean = false,
    status: MessageStatus = MessageStatus.DELIVERED,
    unsent: Boolean = false,
    isGroupEvent: Boolean = false,
    isSms: Boolean = false,
    locators: Map<Long, String> = emptyMap(),
) = MessageItem(
    id = 1L,
    text = text,
    isFromMe = fromMe,
    date = 1L,
    status = status,
    isGroupEvent = isGroupEvent,
    reactionEmoji = null,
    unsent = unsent,
    guid = "msg-1",
    isSms = isSms,
    replyPartLocators = locators,
)

private fun attachment(guid: String, partIndex: Long) = AttachmentMeta(
    guid = guid,
    mime = "image/jpeg",
    name = "trail.jpg",
    sizeBytes = 12L,
    isImage = true,
    downloaded = true,
    partIndex = partIndex,
)

private fun richLink() = RichLinkPreview(
    url = "https://www.nps.gov/yose/index.htm",
    displayHost = "nps.gov",
    title = "Yosemite National Park",
    summary = "Plan the route.",
    imageBytes = null,
    imageMime = null,
    iconBytes = null,
    iconMime = null,
)

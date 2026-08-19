package app.openbubbles.nativeapp.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class ReactionChipTest {

    @Test
    fun `ltr tail steps toward the bubble body`() {
        assertEquals(1f, reactionTailDirection(isFromMe = false, isLtr = true))
        assertEquals(-1f, reactionTailDirection(isFromMe = true, isLtr = true))
    }

    @Test
    fun `rtl mirrors the tail direction`() {
        assertEquals(-1f, reactionTailDirection(isFromMe = false, isLtr = false))
        assertEquals(1f, reactionTailDirection(isFromMe = true, isLtr = false))
    }
}

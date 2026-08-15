package app.openbubbles.core

import app.openbubbles.core.model.isGroupConversation
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatSemanticsTest {

    @Test
    fun `two other participants is a group`() {
        assertTrue(isGroupConversation(style = null, otherParticipantCount = 2))
    }

    @Test
    fun `one other participant is direct unless style is group`() {
        assertFalse(isGroupConversation(style = null, otherParticipantCount = 1))
        assertTrue(isGroupConversation(style = 43L, otherParticipantCount = 1))
    }
}

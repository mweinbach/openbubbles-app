package app.openbubbles.core

import app.openbubbles.core.model.ChatMute
import app.openbubbles.db.Chat
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChatMuteTest {
    @Test
    fun `temporary mute expires without suppressing later notifications`() {
        val now = 1_700_000_000_000L
        val chat = Chat().apply {
            muteType = "temporary_mute"
            muteArgs = Instant.ofEpochMilli(now + 60_000).toString()
        }

        assertTrue(ChatMute.shouldMute(chat, nowEpochMs = now))
        assertFalse(ChatMute.shouldMute(chat, nowEpochMs = now + 60_001))
    }

    @Test
    fun `legacy individual and keyword rules retain their semantics`() {
        val chat = Chat().apply {
            muteType = "mute_individuals"
            muteArgs = "friend@example.com, tel:+15551212"
        }
        assertTrue(ChatMute.shouldMute(chat, senderAddress = "mailto:friend@example.com"))
        assertFalse(ChatMute.shouldMute(chat, senderAddress = "other@example.com"))

        chat.muteType = "text_detection"
        chat.muteArgs = "urgent, family"
        assertFalse(ChatMute.shouldMute(chat, messageText = "This is URGENT"))
        assertTrue(ChatMute.shouldMute(chat, messageText = "ordinary update"))
    }
}

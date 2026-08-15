package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatBackgroundSelectionTest {
    @Test
    fun `local background overrides and falls back to synced background`() {
        val synced = chat(custom = null, synced = "/synced/image")
        val local = chat(custom = "/local/image", synced = "/synced/image")

        assertEquals("/synced/image", synced.effectiveBackgroundPath())
        assertEquals("/local/image", local.effectiveBackgroundPath())
        assertNull(chat(custom = null, synced = null).effectiveBackgroundPath())
    }

    private fun chat(custom: String?, synced: String?) = ChatListItem(
        id = 1L,
        title = "Chat",
        snippet = null,
        date = 0L,
        unread = 0,
        pinned = false,
        avatarColor = 0L,
        customBackgroundPath = custom,
        transcriptBackgroundPath = synced,
    )
}

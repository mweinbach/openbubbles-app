package app.openbubbles.nativeapp.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CustomReactionTest {
    @Test
    fun `accepts emoji sequences supported by custom tapbacks`() {
        listOf("🔥", "👍🏽", "👨‍👩‍👧‍👦", "🇺🇸", "1️⃣").forEach { emoji ->
            assertEquals(emoji, normalizeCustomReaction(emoji))
        }
    }

    @Test
    fun `rejects text and multiple separate emoji`() {
        assertNull(normalizeCustomReaction("hello"))
        assertNull(normalizeCustomReaction("🔥🎉"))
    }
}

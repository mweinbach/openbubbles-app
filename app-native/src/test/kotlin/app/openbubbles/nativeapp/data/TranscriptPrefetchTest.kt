package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertEquals

class TranscriptPrefetchTest {

    @Test
    fun `empty viewport warms the first page of the list`() {
        assertEquals(
            listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
            visibleTranscriptPrefetchIds((1L..20L).toList(), emptyList()),
        )
    }

    @Test
    fun `visible rows include three neighbors on each side`() {
        val ordered = (1L..20L).toList()
        assertEquals(
            listOf(2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L),
            visibleTranscriptPrefetchIds(ordered, visibleChatIds = listOf(5L, 7L)),
        )
    }

    @Test
    fun `neighbors clamp at the ends of the list`() {
        assertEquals(
            listOf(1L, 2L, 3L, 4L),
            visibleTranscriptPrefetchIds((1L..8L).toList(), visibleChatIds = listOf(1L)),
        )
        assertEquals(
            listOf(5L, 6L, 7L, 8L),
            visibleTranscriptPrefetchIds((1L..8L).toList(), visibleChatIds = listOf(8L)),
        )
    }

    @Test
    fun `unknown visible ids fall back to the first page`() {
        assertEquals(
            listOf(1L, 2L),
            visibleTranscriptPrefetchIds(
                listOf(1L, 2L, 3L),
                visibleChatIds = listOf(99L),
                emptyVisibleLimit = 2,
            ),
        )
    }
}

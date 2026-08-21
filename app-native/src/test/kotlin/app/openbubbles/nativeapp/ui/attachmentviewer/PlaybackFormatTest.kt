package app.openbubbles.nativeapp.ui.attachmentviewer

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class PlaybackFormatTest {
    @Test
    fun `positions format as minutes and roll over to hours`() {
        assertEquals("0:00", formatPlaybackTime(0))
        assertEquals("0:05", formatPlaybackTime(5_000))
        assertEquals("0:59", formatPlaybackTime(59_999))
        assertEquals("1:23", formatPlaybackTime(83_000))
        assertEquals("1:02:03", formatPlaybackTime(3_723_000))
    }

    @Test
    fun `negative positions clamp to zero`() {
        assertEquals("0:00", formatPlaybackTime(-5_000))
    }

    @Test
    fun `seek is disabled when duration is unknown`() {
        assertNull(clampSeekPositionMs(1_000, null))
        assertNull(clampSeekPositionMs(1_000, 0))
        assertNull(clampSeekPositionMs(1_000, -1))
    }

    @Test
    fun `seek targets clamp into the playable range`() {
        assertEquals(0L, clampSeekPositionMs(-5_000, 10_000))
        assertEquals(5_000L, clampSeekPositionMs(5_000, 10_000))
        assertEquals(10_000L, clampSeekPositionMs(25_000, 10_000))
    }

    @Test
    fun `progress fraction is zero for unknown durations and clamps to one`() {
        assertEquals(0f, playbackFraction(5_000, null))
        assertEquals(0f, playbackFraction(5_000, 0))
        assertEquals(0.5f, playbackFraction(5_000, 10_000))
        assertEquals(1f, playbackFraction(20_000, 10_000))
        assertEquals(0f, playbackFraction(-1_000, 10_000))
    }
}

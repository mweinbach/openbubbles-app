package app.openbubbles.nativeapp.ui.chat

import kotlin.test.Test
import kotlin.test.assertEquals

class RecordingTimeTest {
    @Test
    fun `formats sub-second and partial seconds as whole seconds`() {
        assertEquals("0:00", formatRecordingTime(0))
        assertEquals("0:00", formatRecordingTime(999))
        assertEquals("0:01", formatRecordingTime(1_000))
        assertEquals("0:07", formatRecordingTime(7_499))
    }

    @Test
    fun `formats minutes with padded seconds`() {
        assertEquals("1:00", formatRecordingTime(60_000))
        assertEquals("1:14", formatRecordingTime(74_000))
        assertEquals("12:09", formatRecordingTime(729_000))
    }

    @Test
    fun `negative input clamps to zero`() {
        assertEquals("0:00", formatRecordingTime(-5))
    }
}

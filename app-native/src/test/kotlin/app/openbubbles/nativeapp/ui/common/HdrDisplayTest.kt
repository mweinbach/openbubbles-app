package app.openbubbles.nativeapp.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class HdrDisplayTest {

    @Test
    fun `image ratio is preserved when it fits the display`() {
        assertEquals(1.8f, desiredHdrHeadroom(imageRatio = 1.8f, displayRatio = 3f))
    }

    @Test
    fun `headroom is limited by the display capability`() {
        assertEquals(1.6f, desiredHdrHeadroom(imageRatio = 2.4f, displayRatio = 1.6f))
    }

    @Test
    fun `strong HDR uses the authored image headroom instead of an arbitrary ceiling`() {
        assertEquals(6f, desiredHdrHeadroom(imageRatio = 6f, displayRatio = 8f))
        assertEquals(8f, desiredHdrHeadroom(imageRatio = 12f, displayRatio = 8f))
        assertEquals(1.4f, desiredHdrHeadroom(imageRatio = 6f, displayRatio = 8f, limit = 1.4f))
    }

    @Test
    fun `headroom never exceeds the platform supported request range`() {
        assertEquals(10_000f, desiredHdrHeadroom(imageRatio = 20_000f, displayRatio = 30_000f))
    }

    @Test
    fun `invalid image ratios safely fall back to standard dynamic range`() {
        assertEquals(1f, desiredHdrHeadroom(imageRatio = Float.NaN, displayRatio = 3f))
        assertEquals(1f, desiredHdrHeadroom(imageRatio = Float.POSITIVE_INFINITY, displayRatio = 3f))
        assertEquals(1f, desiredHdrHeadroom(imageRatio = Float.NEGATIVE_INFINITY, displayRatio = 3f))
        assertEquals(1f, desiredHdrHeadroom(imageRatio = 0.9f, displayRatio = 3f))
    }

    @Test
    fun `invalid display ratios safely fall back to standard dynamic range`() {
        assertEquals(1f, desiredHdrHeadroom(imageRatio = 2f, displayRatio = Float.NaN))
        assertEquals(1f, desiredHdrHeadroom(imageRatio = 2f, displayRatio = Float.POSITIVE_INFINITY))
        assertEquals(1f, desiredHdrHeadroom(imageRatio = 2f, displayRatio = 0f))
    }

    @Test
    fun `invalid brightness limits safely fall back to standard dynamic range`() {
        assertEquals(1f, desiredHdrHeadroom(imageRatio = 2f, displayRatio = 3f, limit = Float.NaN))
        assertEquals(
            1f,
            desiredHdrHeadroom(imageRatio = 2f, displayRatio = 3f, limit = Float.POSITIVE_INFINITY),
        )
        assertEquals(1f, desiredHdrHeadroom(imageRatio = 2f, displayRatio = 3f, limit = -1f))
    }

    @Test
    fun `unit ratios keep hdr disabled without violating the platform minimum`() {
        assertEquals(1f, desiredHdrHeadroom(imageRatio = 1f, displayRatio = 2f))
        assertEquals(1f, desiredHdrHeadroom(imageRatio = 2f, displayRatio = 1f))
        assertEquals(1f, desiredHdrHeadroom(imageRatio = 2f, displayRatio = 3f, limit = 1f))
    }
}

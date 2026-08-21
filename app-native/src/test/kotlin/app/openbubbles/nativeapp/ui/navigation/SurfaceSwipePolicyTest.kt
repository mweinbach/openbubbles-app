package app.openbubbles.nativeapp.ui.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Header-swipe arbitration. Every case that is not an unambiguous horizontal
 * drag inside the strip must leave the selected surface alone, because some
 * other owner (a bubble reply, a list row, predictive back, a vertical scroll)
 * is the one the user meant.
 */
class SurfaceSwipePolicyTest {
    private val width = 1080f
    private val threshold = 140f
    private val edge = 60f

    private fun resolve(
        dragX: Float,
        dragY: Float = 0f,
        startX: Float = width / 2f,
        enabled: Boolean = true,
        rtl: Boolean = false,
        widthPx: Float = width,
    ): SurfaceStep? = SurfaceSwipePolicy.resolve(
        dragX = dragX,
        dragY = dragY,
        startX = startX,
        widthPx = widthPx,
        thresholdPx = threshold,
        edgeExclusionPx = edge,
        enabled = enabled,
        rtl = rtl,
    )

    @Test
    fun `a full drag toward the end advances one surface`() {
        assertEquals(SurfaceStep.Forward, resolve(dragX = -threshold))
        assertEquals(SurfaceStep.Forward, resolve(dragX = -threshold * 3))
    }

    @Test
    fun `a full drag toward the start reverses one surface`() {
        assertEquals(SurfaceStep.Backward, resolve(dragX = threshold))
    }

    @Test
    fun `right-to-left layouts mirror the direction`() {
        assertEquals(SurfaceStep.Forward, resolve(dragX = threshold, rtl = true))
        assertEquals(SurfaceStep.Backward, resolve(dragX = -threshold, rtl = true))
    }

    @Test
    fun `a drag that stays under the threshold does nothing`() {
        assertNull(resolve(dragX = -(threshold - 1f)))
        assertNull(resolve(dragX = threshold - 1f))
        assertNull(resolve(dragX = 0f))
    }

    @Test
    fun `vertical and diagonal travel does nothing`() {
        assertNull(resolve(dragX = 0f, dragY = -600f))
        assertNull(resolve(dragX = -threshold, dragY = threshold))
        assertNull(resolve(dragX = -threshold * 2, dragY = -threshold * 2))
        // Includes movement accumulated before horizontal touch slop was crossed.
        assertNull(resolve(dragX = -threshold * 2, dragY = threshold * 1.1f))
    }

    @Test
    fun `travel that is clearly more horizontal than vertical still commits`() {
        assertEquals(
            SurfaceStep.Forward,
            resolve(dragX = -threshold * 3, dragY = threshold * 0.5f),
        )
    }

    @Test
    fun `gestures that start on either back edge do nothing`() {
        assertNull(resolve(dragX = threshold, startX = edge - 1f))
        assertNull(resolve(dragX = -threshold, startX = width - edge + 1f))
    }

    @Test
    fun `gestures just inside the excluded edges still commit`() {
        assertEquals(SurfaceStep.Forward, resolve(dragX = -threshold, startX = edge))
        assertEquals(SurfaceStep.Backward, resolve(dragX = threshold, startX = width - edge))
    }

    @Test
    fun `a disabled strip ignores every drag`() {
        assertNull(resolve(dragX = -threshold * 4, enabled = false))
    }

    @Test
    fun `a strip with no measured width ignores every drag`() {
        assertNull(resolve(dragX = -threshold * 4, startX = 0f, widthPx = 0f))
    }

    @Test
    fun `the shipped thresholds keep the gesture deliberate`() {
        // A reply swipe arms at 48dp inside a bubble; the header must ask for
        // more travel than that so the two never read as the same flick.
        assertEquals(56f, SurfaceSwipePolicy.COMMIT_THRESHOLD_DP)
        assertEquals(24f, SurfaceSwipePolicy.EDGE_EXCLUSION_DP)
        assertEquals(2f, SurfaceSwipePolicy.DIRECTION_DOMINANCE)
    }
}

package app.openbubbles.nativeapp.ui.adaptive

import androidx.compose.material3.adaptive.HingeInfo
import androidx.compose.material3.adaptive.Posture
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.ui.geometry.Rect
import androidx.window.core.layout.WindowSizeClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdaptiveLayoutTest {

    @Test
    fun `messaging uses two panes on medium foldable inner width`() {
        assertEquals(1, messagingHorizontalPartitions(411))
        assertEquals(2, messagingHorizontalPartitions(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND))
        assertEquals(2, messagingHorizontalPartitions(700))
        assertEquals(2, messagingHorizontalPartitions(839))
    }

    @Test
    fun `messaging uses three panes from large desktop width`() {
        assertEquals(3, messagingHorizontalPartitions(WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND))
        assertEquals(3, messagingHorizontalPartitions(WindowSizeClass.WIDTH_DP_EXTRA_LARGE_LOWER_BOUND))
    }

    @Test
    fun `tabletop and tall single-pane windows get two vertical partitions`() {
        assertEquals(
            2,
            messagingVerticalPartitions(
                isTabletop = true,
                horizontalPartitions = 1,
                minHeightDp = 700,
            ),
        )
        assertEquals(
            2,
            messagingVerticalPartitions(
                isTabletop = false,
                horizontalPartitions = 1,
                minHeightDp = WindowSizeClass.HEIGHT_DP_EXPANDED_LOWER_BOUND,
            ),
        )
        assertEquals(
            1,
            messagingVerticalPartitions(
                isTabletop = false,
                horizontalPartitions = 2,
                minHeightDp = 900,
            ),
        )
    }

    @Test
    fun `library directive matches two-panes-on-medium for foldable inner`() {
        // WindowSizeClass.minWidthDp is the breakpoint floor, not the raw
        // window width. currentWindowAdaptiveInfoV2() emits 0 / 600 / 840 /
        // 1200 / 1600 — constructing 411 or 700 would match no branch.
        val compact = messagingListDetailDirective(
            WindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass(minWidthDp = 0, minHeightDp = 900),
                windowPosture = Posture(),
            ),
        )
        assertEquals(1, compact.maxHorizontalPartitions)

        val foldableInner = messagingListDetailDirective(
            WindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass(
                    minWidthDp = WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
                    minHeightDp = 900,
                ),
                windowPosture = Posture(),
            ),
        )
        assertEquals(2, foldableInner.maxHorizontalPartitions)

        val large = messagingListDetailDirective(
            WindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass(
                    minWidthDp = WindowSizeClass.WIDTH_DP_LARGE_LOWER_BOUND,
                    minHeightDp = 900,
                ),
                windowPosture = Posture(),
            ),
        )
        assertEquals(3, large.maxHorizontalPartitions)
    }

    @Test
    fun `separating vertical hinge is excluded from the pane directive`() {
        val book = Posture(
            isTabletop = false,
            hingeList = listOf(
                HingeInfo(
                    bounds = Rect(left = 1050f, top = 0f, right = 1090f, bottom = 1800f),
                    isFlat = false,
                    isVertical = true,
                    isSeparating = true,
                    isOccluding = true,
                ),
            ),
        )
        val directive = messagingListDetailDirective(
            WindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass(minWidthDp = 840, minHeightDp = 900),
                windowPosture = book,
            ),
        )
        assertTrue(directive.excludedBounds.isNotEmpty())
        assertEquals(1050f, directive.excludedBounds.first().left)
        assertEquals(1090f, directive.excludedBounds.first().right)
    }

    @Test
    fun `flat seamless fold does not punch a hinge gap`() {
        val flat = Posture(
            isTabletop = false,
            hingeList = listOf(
                HingeInfo(
                    bounds = Rect(left = 1050f, top = 0f, right = 1060f, bottom = 1800f),
                    isFlat = true,
                    isVertical = true,
                    isSeparating = false,
                    isOccluding = false,
                ),
            ),
        )
        val directive = messagingListDetailDirective(
            WindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass(minWidthDp = 840, minHeightDp = 900),
                windowPosture = flat,
            ),
        )
        assertTrue(directive.excludedBounds.isEmpty())
    }

    @Test
    fun `tabletop compact window gets two vertical partitions from the library`() {
        val tabletop = Posture(
            isTabletop = true,
            hingeList = listOf(
                HingeInfo(
                    bounds = Rect(left = 0f, top = 700f, right = 1080f, bottom = 740f),
                    isFlat = false,
                    isVertical = false,
                    isSeparating = true,
                    isOccluding = false,
                ),
            ),
        )
        val directive = messagingListDetailDirective(
            WindowAdaptiveInfo(
                windowSizeClass = WindowSizeClass(minWidthDp = 0, minHeightDp = 900),
                windowPosture = tabletop,
            ),
        )
        assertEquals(1, directive.maxHorizontalPartitions)
        assertEquals(2, directive.maxVerticalPartitions)
    }

    @Test
    fun `settings book posture ends the category rail at the hinge`() {
        val split = settingsTwoPaneSplit(hingeLeftDp = 410f, hingeRightDp = 430f)
        assertEquals(410f, split.listWidthDp)
        assertEquals(20f, split.gutterDp)
    }

    @Test
    fun `settings without a usable hinge keeps the Messages rail width`() {
        assertEquals(300f, settingsTwoPaneSplit(null, null).listWidthDp)
        assertEquals(16f, settingsTwoPaneSplit(null, null).gutterDp)
        assertEquals(300f, settingsTwoPaneSplit(80f, 120f).listWidthDp)
    }

    @Test
    fun `FaceTime tabletop keeps identity above the fold and controls below`() {
        val split = assertNotNull(faceTimeTabletopInsets(1800, 700, 740))
        assertEquals(700, split.contentHeightPx)
        assertEquals(740, split.controlsTopMarginPx)
        assertTrue(split.contentHeightPx < split.controlsTopMarginPx)
    }

    @Test
    fun `FaceTime tabletop is ignored on the empty first fold frame`() {
        assertNull(faceTimeTabletopInsets(1800, 0, 0))
        assertNull(faceTimeTabletopInsets(0, 700, 740))
        assertFalse(isTabletopFold(horizontalHinge = true, halfOpened = false))
        assertTrue(isTabletopFold(horizontalHinge = true, halfOpened = true))
        assertTrue(isBookFold(verticalHinge = true, halfOpened = true))
        assertFalse(isBookFold(verticalHinge = false, halfOpened = true))
    }
}

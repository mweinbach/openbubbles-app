package app.openbubbles.nativeapp.ui.navigation

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SurfaceSwitcherTrackMetricsTest {

    @Test
    fun `four destinations divide the available track after connected gaps`() {
        val metrics = surfaceSwitcherTrackMetrics(
            trackWidth = 360.dp,
            itemCount = 4,
            selectedIndex = 2,
        )

        assertEquals(88.5.dp, metrics.itemWidth)
        assertEquals(181.dp, metrics.indicatorOffset)
        assertTrue(metrics.hasSelection)
    }

    @Test
    fun `last destination lands exactly against the end of the track`() {
        val metrics = surfaceSwitcherTrackMetrics(
            trackWidth = 360.dp,
            itemCount = 4,
            selectedIndex = 3,
        )

        assertEquals(271.5.dp, metrics.indicatorOffset)
        assertEquals(360.dp, metrics.indicatorOffset + metrics.itemWidth)
    }

    @Test
    fun `unowned destinations hide the indicator at the first segment`() {
        val metrics = surfaceSwitcherTrackMetrics(
            trackWidth = 300.dp,
            itemCount = 4,
            selectedIndex = -1,
        )

        assertEquals(0.dp, metrics.indicatorOffset)
        assertFalse(metrics.hasSelection)
    }

    @Test
    fun `a removed destination clamps the hidden indicator to the surviving track`() {
        val metrics = surfaceSwitcherTrackMetrics(
            trackWidth = 304.dp,
            itemCount = 3,
            selectedIndex = 3,
        )

        assertEquals(100.dp, metrics.itemWidth)
        assertEquals(204.dp, metrics.indicatorOffset)
        assertFalse(metrics.hasSelection)
    }

    @Test
    fun `a single destination occupies the entire track`() {
        val metrics = surfaceSwitcherTrackMetrics(
            trackWidth = 240.dp,
            itemCount = 1,
            selectedIndex = 0,
        )

        assertEquals(240.dp, metrics.itemWidth)
        assertEquals(0.dp, metrics.indicatorOffset)
        assertTrue(metrics.hasSelection)
    }

    @Test
    fun `an empty track is rejected before dividing by its item count`() {
        assertFailsWith<IllegalArgumentException> {
            surfaceSwitcherTrackMetrics(
                trackWidth = 240.dp,
                itemCount = 0,
                selectedIndex = -1,
            )
        }
    }
}

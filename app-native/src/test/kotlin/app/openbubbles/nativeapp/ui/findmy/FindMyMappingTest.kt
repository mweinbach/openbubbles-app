package app.openbubbles.nativeapp.ui.findmy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FindMyMappingTest {
    @Test
    fun `unix seconds become milliseconds`() {
        assertEquals(1_700_000_000_000L, FindMyMapping.epochMs(1_700_000_000L))
        assertEquals(1_700_000_000_000L, FindMyMapping.epochMs(1_700_000_000_000L))
        assertNull(FindMyMapping.epochMs(0L))
        assertNull(FindMyMapping.epochMs(null))
    }

    @Test
    fun `battery fraction becomes a percent`() {
        assertEquals(78, FindMyMapping.batteryPercent(0.78))
        assertEquals(42, FindMyMapping.batteryPercent(42.0))
        assertNull(FindMyMapping.batteryPercent(null))
    }

    @Test
    fun `lost mode flags stay on the device model`() {
        val device = FmDeviceUi(
            id = "d",
            name = "Phone",
            lostModeCapable = true,
            lostModeEnabled = true,
        )
        assertTrue(device.lostModeEnabled)
        assertTrue(device.lostModeCapable)
    }
}

package app.openbubbles.nativeapp.service

import android.app.Service
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativePushServiceStartTest {

    @Test
    fun `poll action selects one-shot non-sticky service`() {
        assertTrue(isPollStart(BatterySaver.ACTION_POLL_ONCE))
        assertEquals(Service.START_NOT_STICKY, restartModeFor(pollMode = true))
    }

    @Test
    fun `normal and restarted starts select persistent service`() {
        assertFalse(isPollStart(null))
        assertFalse(isPollStart("app.openbubbles.nativeapp.action.UNKNOWN"))
        assertEquals(Service.START_STICKY, restartModeFor(pollMode = false))
    }
}

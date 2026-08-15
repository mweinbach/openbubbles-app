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

    @Test
    fun `reload action reinitializes an already-started service`() {
        assertTrue(isReloadStart(NativePushService.ACTION_RELOAD))
        assertTrue(
            shouldInitializePush(
                bootStarted = true,
                action = NativePushService.ACTION_RELOAD,
            ),
        )
    }

    @Test
    fun `ordinary duplicate start does not create a second native state`() {
        assertTrue(shouldInitializePush(bootStarted = false, action = null))
        assertFalse(shouldInitializePush(bootStarted = true, action = null))
    }

    @Test
    fun `reconnect backoff is bounded`() {
        assertEquals(2_000L, reconnectDelayMs(0))
        assertEquals(4_000L, reconnectDelayMs(1))
        assertEquals(120_000L, reconnectDelayMs(20))
    }

    @Test
    fun `journal retry delay only applies after failures`() {
        assertEquals(2_000L, journalRetryDelayMs(0))
        assertEquals(10_000L, journalRetryDelayMs(1))
        assertEquals(10_000L, journalRetryDelayMs(5))
    }
}

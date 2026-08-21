package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Connect-triggered CloudKit syncs are throttled: the push state is
 * re-installed on every reconnect and each auto sync costs several CloudKit
 * round trips even when nothing changed.
 */
class AutoSyncThrottleTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `first connect always syncs`() {
        assertTrue(shouldAutoSyncOnConnect(lastAttemptMs = 0L, nowMs = now))
        assertTrue(shouldAutoSyncOnConnect(lastAttemptMs = -1L, nowMs = now))
    }

    @Test
    fun `reconnect inside the window is suppressed`() {
        assertFalse(shouldAutoSyncOnConnect(lastAttemptMs = now - 1L, nowMs = now))
        assertFalse(
            shouldAutoSyncOnConnect(
                lastAttemptMs = now - AUTO_SYNC_MIN_INTERVAL_MS + 1L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `connect after the window syncs again`() {
        assertTrue(
            shouldAutoSyncOnConnect(
                lastAttemptMs = now - AUTO_SYNC_MIN_INTERVAL_MS,
                nowMs = now,
            ),
        )
        assertTrue(
            shouldAutoSyncOnConnect(
                lastAttemptMs = now - AUTO_SYNC_MIN_INTERVAL_MS - 1L,
                nowMs = now,
            ),
        )
    }

    @Test
    fun `a clock that moved backwards never suppresses indefinitely`() {
        assertTrue(shouldAutoSyncOnConnect(lastAttemptMs = now + 60_000L, nowMs = now))
    }
}

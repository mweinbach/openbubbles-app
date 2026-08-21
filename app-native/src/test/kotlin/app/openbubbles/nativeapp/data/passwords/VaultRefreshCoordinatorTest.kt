package app.openbubbles.nativeapp.data.passwords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class VaultRefreshCoordinatorTest {
    @Test
    fun `forced refresh requested during a pass runs immediately afterward`() = runTest {
        val releaseFirst = CompletableDeferred<Unit>()
        val starts = mutableListOf<String>()
        val coordinator = VaultRefreshCoordinator(
            scope = this,
            opportunisticIntervalMs = 60_000,
            nowMs = { 100_000 },
        )

        coordinator.start(forced = false) {
            starts += "opportunistic"
            releaseFirst.await()
        }
        runCurrent()
        coordinator.start(forced = true) { starts += "forced" }
        runCurrent()
        assertEquals(listOf("opportunistic"), starts)

        releaseFirst.complete(Unit)
        advanceUntilIdle()
        assertEquals(listOf("opportunistic", "forced"), starts)
    }

    @Test
    fun `account invalidation rejects stale provider publication and scheduling`() = runTest {
        val coordinator = VaultRefreshCoordinator(
            scope = this,
            opportunisticIntervalMs = 60_000,
            nowMs = { 100_000 },
        )
        val oldGeneration = coordinator.captureGeneration()

        coordinator.cancelAndJoin()

        var writes = 0
        assertNull(coordinator.publishIfCurrent(oldGeneration) { ++writes })
        coordinator.start(forced = true, expectedGeneration = oldGeneration) { ++writes }
        advanceUntilIdle()
        assertEquals(0, writes)
    }
}

package app.openbubbles.nativeapp.data

import app.openbubbles.core.sync.SyncMode
import app.openbubbles.core.sync.SyncSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class HistorySyncCoordinatorTest {

    @Test
    fun `sync remains single flight and publishes completion`() = runTest {
        var syncCalls = 0
        var completionCalls = 0
        val expected = SyncSummary(
            totalChats = 2u,
            totalMessages = 5u,
            durationMs = 10L,
        )
        val coordinator = HistorySyncCoordinator(
            scope = this,
            sync = {
                syncCalls += 1
                expected
            },
        )

        assertTrue(coordinator.start(SyncMode.FULL) { completionCalls += 1 })
        assertFalse(coordinator.start(SyncMode.INCREMENTAL) { completionCalls += 1 })
        assertTrue(coordinator.running.value)

        advanceUntilIdle()

        assertFalse(coordinator.running.value)
        assertEquals(expected, coordinator.lastSummary.value)
        assertEquals(1, syncCalls)
        assertEquals(1, completionCalls)
    }

    @Test
    fun `initial history retries resume committed cursors`() {
        assertEquals(SyncMode.FULL, initialHistorySyncMode(started = false))
        assertEquals(SyncMode.INCREMENTAL, initialHistorySyncMode(started = true))
    }

    @Test
    fun `account cleanup waits for cancelled sync writer and suppresses stale completion`() = runTest {
        val started = CompletableDeferred<Unit>()
        val cancellationObserved = CompletableDeferred<Unit>()
        val releaseWriter = CompletableDeferred<Unit>()
        var completionCalls = 0
        val coordinator = HistorySyncCoordinator(
            scope = backgroundScope,
            sync = {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cancellationObserved.complete(Unit)
                        releaseWriter.await()
                    }
                }
            },
        )

        assertTrue(coordinator.start(SyncMode.FULL) { completionCalls += 1 })
        started.await()

        val cleanup = async { coordinator.cancelAndJoin() }
        cancellationObserved.await()
        assertFalse(cleanup.isCompleted)
        assertEquals(0, completionCalls)

        releaseWriter.complete(Unit)
        cleanup.await()

        assertFalse(coordinator.running.value)
        assertNull(coordinator.lastSummary.value)
        assertEquals(0, completionCalls)
    }
}

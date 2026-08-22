package app.openbubbles.nativeapp.data.photos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

@OptIn(ExperimentalCoroutinesApi::class)
class PhotosWorkRegistryTest {
    @Test
    fun `sign out blocks new sessions until a connected account explicitly reactivates work`() =
        runTest {
            PhotosWorkRegistry.cancelAndJoinAll()
            var runs = 0
            val blocked = PhotosWorkRegistry.register()

            assertNull(blocked.createJob(this) { runs += 1 })
            assertEquals(0, runs)

            PhotosWorkRegistry.activate()
            val active = PhotosWorkRegistry.register()
            val job = checkNotNull(active.createJob(this) { runs += 1 })
            job.start()
            runCurrent()

            assertEquals(1, runs)
            PhotosWorkRegistry.cancelAndJoinAll()
        }

    @Test
    fun `account cleanup joins externally owned background worker before returning`() = runTest {
        PhotosWorkRegistry.cancelAndJoinAll()
        PhotosWorkRegistry.activate()
        val started = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val worker = backgroundScope.launch {
            val owner = checkNotNull(currentCoroutineContext()[Job])
            val session = PhotosWorkRegistry.register()
            assertTrue(session.adopt(owner))
            try {
                started.complete(Unit)
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    yield()
                    cleaned.complete(Unit)
                }
                session.release(owner)
                session.close()
            }
        }
        started.await()

        PhotosWorkRegistry.cancelAndJoinAll()

        assertTrue(worker.isCompleted)
        assertTrue(cleaned.isCompleted, "worker cleanup must finish before account data is deleted")
        assertNull(PhotosWorkRegistry.register().createJob(this) {})
    }
}

package app.openbubbles.nativeapp.data.photos

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

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
}

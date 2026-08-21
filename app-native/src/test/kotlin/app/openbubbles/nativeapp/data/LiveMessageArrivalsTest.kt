package app.openbubbles.nativeapp.data

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LiveMessageArrivalsTest {
    @Test
    fun `replay bridges an existing viewport but a new viewport baselines it`() = runTest {
        val existingViewportCursor = LiveMessageArrivals.latestSequence
        val marker = LiveMessageArrival(
            messageGuid = "recreation-gap",
            chatId = 42,
        )

        LiveMessageArrivals.publish(marker)

        val replayed = LiveMessageArrivals.events.replayCache.last()
        assertEquals(marker.messageGuid, replayed.messageGuid)
        assertTrue(replayed.sequence > existingViewportCursor)

        val newViewportCursor = LiveMessageArrivals.latestSequence
        assertFalse(replayed.sequence > newViewportCursor)
    }
}

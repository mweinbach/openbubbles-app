package app.openbubbles.nativeapp.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UpdateMessagingServiceTest {
    @Test
    fun `new debug token schedules the existing update topic subscription`() {
        var subscriptions = 0

        assertTrue(
            refreshUpdateTopicForNewToken(
                token = "firebase-installation-token",
                debugBuild = true,
                firebaseTelemetryEnabled = false,
                performanceTest = false,
                resubscribe = { subscriptions++ },
            ),
        )
        assertEquals(1, subscriptions)
        assertEquals("update-ledger-openbubbles-stable", UpdatePushContract.TOPIC)
    }

    @Test
    fun `production token refresh honors the existing Firebase initialization policy`() {
        var subscriptions = 0

        assertTrue(
            refreshUpdateTopicForNewToken(
                token = "production-token",
                debugBuild = false,
                firebaseTelemetryEnabled = true,
                performanceTest = false,
                resubscribe = { subscriptions++ },
            ),
        )
        assertEquals(1, subscriptions)
        assertFalse(
            refreshUpdateTopicForNewToken(
                token = "disabled-production-token",
                debugBuild = false,
                firebaseTelemetryEnabled = false,
                performanceTest = false,
                resubscribe = { subscriptions++ },
            ),
        )
        assertEquals(1, subscriptions)
    }

    @Test
    fun `performance builds never schedule Firebase update subscriptions`() {
        var subscriptions = 0

        assertFalse(
            refreshUpdateTopicForNewToken(
                token = "benchmark-token",
                debugBuild = true,
                firebaseTelemetryEnabled = true,
                performanceTest = true,
                resubscribe = { subscriptions++ },
            ),
        )
        assertEquals(0, subscriptions)
    }

    @Test
    fun `missing token never schedules a topic subscription`() {
        var subscriptions = 0

        for (token in listOf("", " ", "\t\n")) {
            assertFalse(
                refreshUpdateTopicForNewToken(
                    token = token,
                    debugBuild = true,
                    firebaseTelemetryEnabled = true,
                    performanceTest = false,
                    resubscribe = { subscriptions++ },
                ),
            )
        }
        assertEquals(0, subscriptions)
    }

    @Test
    fun `every rotated token restores the process scoped update topic`() {
        var subscriptions = 0

        for (token in listOf("first-token", "rotated-token", "restored-token")) {
            assertTrue(
                refreshUpdateTopicForNewToken(
                    token = token,
                    debugBuild = false,
                    firebaseTelemetryEnabled = true,
                    performanceTest = false,
                    resubscribe = { subscriptions++ },
                ),
            )
        }
        assertEquals(3, subscriptions)
    }
}

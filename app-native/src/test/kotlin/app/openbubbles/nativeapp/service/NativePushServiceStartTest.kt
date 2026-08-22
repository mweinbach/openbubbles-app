package app.openbubbles.nativeapp.service

import android.app.Service
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import uniffi.rust_lib_bluebubbles.URegisterState

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
    fun `battery saver never maps an ordinary start to a persistent connection`() {
        assertEquals(PushServiceMode.ON_DEMAND, pushServiceModeFor(action = null, batterySaverEnabled = true))
        assertEquals(
            PushServiceMode.ON_DEMAND,
            pushServiceModeFor(NativePushService.ACTION_RELOAD, batterySaverEnabled = true),
        )
        assertEquals(
            PushServiceMode.ON_DEMAND,
            pushServiceModeFor(NativePushService.ACTION_ON_DEMAND, batterySaverEnabled = true),
        )
        assertEquals(
            PushServiceMode.POLL,
            pushServiceModeFor(BatterySaver.ACTION_POLL_ONCE, batterySaverEnabled = true),
        )
        assertEquals(
            PushServiceMode.PERSISTENT,
            pushServiceModeFor(BatterySaver.ACTION_POLL_ONCE, batterySaverEnabled = false),
        )
        assertEquals(Service.START_NOT_STICKY, restartModeFor(pollMode = false, onDemandMode = true))
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
    fun `poll and bounded sessions promote without duplicating identical starts`() {
        assertTrue(
            shouldInitializePush(
                bootStarted = true,
                action = NativePushService.ACTION_ON_DEMAND,
                currentMode = PushServiceMode.POLL,
                requestedMode = PushServiceMode.ON_DEMAND,
            ),
        )
        assertFalse(
            shouldInitializePush(
                bootStarted = true,
                action = NativePushService.ACTION_ON_DEMAND,
                currentMode = PushServiceMode.ON_DEMAND,
                requestedMode = PushServiceMode.ON_DEMAND,
            ),
        )
        assertTrue(
            shouldInitializePush(
                bootStarted = true,
                action = null,
                currentMode = PushServiceMode.ON_DEMAND,
                requestedMode = PushServiceMode.PERSISTENT,
            ),
        )
    }

    @Test
    fun `stale poll cannot stop a reloaded live service`() {
        assertTrue(shouldStopAfterPoll(pollGeneration = 2, currentGeneration = 2, pollMode = true))
        assertFalse(shouldStopAfterPoll(pollGeneration = 2, currentGeneration = 3, pollMode = false))
        assertFalse(shouldStopAfterPoll(pollGeneration = 2, currentGeneration = 3, pollMode = true))
    }

    @Test
    fun `bounded session expiry cannot stop another generation or persistent mode`() {
        assertTrue(
            shouldStopAfterOnDemand(
                requestedGeneration = 4,
                currentGeneration = 4,
                mode = PushServiceMode.ON_DEMAND,
                batterySaverEnabled = true,
            ),
        )
        assertFalse(
            shouldStopAfterOnDemand(
                requestedGeneration = 4,
                currentGeneration = 5,
                mode = PushServiceMode.ON_DEMAND,
                batterySaverEnabled = true,
            ),
        )
        assertFalse(
            shouldStopAfterOnDemand(
                requestedGeneration = 4,
                currentGeneration = 4,
                mode = PushServiceMode.PERSISTENT,
                batterySaverEnabled = true,
            ),
        )
        assertFalse(
            shouldStopAfterOnDemand(
                requestedGeneration = 4,
                currentGeneration = 4,
                mode = PushServiceMode.ON_DEMAND,
                batterySaverEnabled = false,
            ),
        )
        assertTrue(NativePushService.ON_DEMAND_IDLE_TIMEOUT_MS in 15_000L..300_000L)
        assertTrue(NativePushService.ON_DEMAND_CONNECT_TIMEOUT_MS < NativePushService.ON_DEMAND_IDLE_TIMEOUT_MS)
    }

    @Test
    fun `poll worker completes only for its own finished sync`() = runTest {
        val polls = BatterySaverPollRuns()
        val first = polls.begin()
        val second = polls.begin()

        assertFalse(first.completion.isCompleted)
        assertFalse(second.completion.isCompleted)
        assertFalse(polls.complete(first.requestId + second.requestId + 100, true))
        assertTrue(polls.complete(first.requestId, true))
        assertTrue(first.completion.await())
        assertFalse(second.completion.isCompleted)

        assertTrue(polls.complete(second.requestId, false))
        assertFalse(second.completion.await())
        assertFalse(polls.complete(second.requestId, true))
    }

    @Test
    fun `account cleanup fails outstanding polls without leaking abandoned work`() = runTest {
        val polls = BatterySaverPollRuns()
        val pending = polls.begin()
        val abandoned = polls.begin()

        polls.abandon(abandoned.requestId)
        assertTrue(abandoned.completion.isCancelled)
        polls.cancelAll()
        assertFalse(pending.completion.await())
        assertFalse(polls.complete(pending.requestId, true))
    }

    @Test
    fun `push callbacks require both current service and current account generations`() {
        assertTrue(
            acceptsPushCallback(
                callbackServiceGeneration = 2,
                activeServiceGeneration = 2,
                callbackAccountGeneration = 7,
                activeAccountGeneration = 7,
                accountActive = true,
            ),
        )
        assertFalse(
            acceptsPushCallback(
                callbackServiceGeneration = 1,
                activeServiceGeneration = 2,
                callbackAccountGeneration = 7,
                activeAccountGeneration = 7,
                accountActive = true,
            ),
        )
        assertFalse(
            acceptsPushCallback(
                callbackServiceGeneration = 2,
                activeServiceGeneration = 2,
                callbackAccountGeneration = 6,
                activeAccountGeneration = 7,
                accountActive = true,
            ),
        )
        assertFalse(
            acceptsPushCallback(
                callbackServiceGeneration = 2,
                activeServiceGeneration = 2,
                callbackAccountGeneration = 7,
                activeAccountGeneration = 7,
                accountActive = false,
            ),
        )
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
        assertEquals(30_000L, journalRetryDelayMs(2))
        assertEquals(240_000L, journalRetryDelayMs(5))
        assertEquals(240_000L, journalRetryDelayMs(50))
    }

    @Test
    fun `only proven malformed payloads count toward durable journal poison limit`() {
        assertEquals(
            JournalFailureDisposition.PERMANENT_PAYLOAD,
            journalFailureDisposition(SerializationException("invalid message payload")),
        )
        assertEquals(
            JournalFailureDisposition.PERMANENT_PAYLOAD,
            journalFailureDisposition(PermanentJournalPayloadException(IllegalArgumentException("bad field"))),
        )

        listOf(
            IOException("disk temporarily unavailable"),
            SecurityException("notification permission changed"),
            IllegalArgumentException("unexpected infrastructure value"),
            IndexOutOfBoundsException("recoverable database projection"),
            IllegalStateException("message store unavailable"),
        ).forEach { failure ->
            assertEquals(
                JournalFailureDisposition.TRANSIENT,
                journalFailureDisposition(failure),
                failure.javaClass.simpleName,
            )
        }
    }

    @Test
    fun `terminal registration failure requires account sign in`() {
        assertTrue(
            registrationRequiresSignIn(
                URegisterState.Failed(
                    retryWait = null,
                    error = "Apple ID session expired. Sign in again.",
                ),
            ),
        )
        assertTrue(
            registrationRequiresSignIn(
                URegisterState.Failed(
                    retryWait = null,
                    error = "Apple ID verification required. Complete two-factor authentication.",
                ),
            ),
        )
        assertFalse(
            registrationRequiresSignIn(
                URegisterState.Failed(retryWait = 300uL, error = "temporary"),
            ),
        )
        assertFalse(
            registrationRequiresSignIn(
                URegisterState.Failed(retryWait = null, error = "Closed"),
            ),
        )
        assertFalse(registrationRequiresSignIn(URegisterState.Registering))
    }
}

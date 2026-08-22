package app.openbubbles.desktop

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopJournalRetryTest {

    @Test
    fun `storage database and unknown failures remain transient`() {
        val failures = listOf(
            IOException("disk temporarily full"),
            SecurityException("notification temporarily unavailable"),
            IllegalStateException("database temporarily unavailable"),
            IllegalArgumentException("recoverable application invariant"),
            IndexOutOfBoundsException("recoverable application failure"),
            RuntimeException("unknown failure"),
        )

        failures.forEach { failure ->
            assertEquals(
                DesktopJournalFailureDisposition.TRANSIENT,
                desktopJournalFailureDisposition(failure),
                "${failure.javaClass.simpleName} must not consume the poison-message budget",
            )
        }
    }

    @Test
    fun `explicitly malformed payload consumes the bounded permanent budget`() {
        val failure = PermanentDesktopJournalPayloadException(
            IllegalArgumentException("invalid message encoding"),
        )

        assertEquals(
            DesktopJournalFailureDisposition.PERMANENT_PAYLOAD,
            desktopJournalFailureDisposition(failure),
        )
    }

    @Test
    fun `repeated transient failures back off without becoming permanent`() {
        val state = DesktopJournalRetryState()
        val expectedDelays = listOf(2_000L, 10_000L, 30_000L, 60_000L, 120_000L, 240_000L)

        expectedDelays.forEach { expected ->
            val decision = state.recordFailure(
                id = 7uL,
                persistedAttempts = 0,
                error = IOException("disk temporarily full"),
            )
            assertEquals(DesktopJournalFailureDisposition.TRANSIENT, decision.disposition)
            assertEquals(expected, decision.retryDelayMs)
        }

        repeat(20) {
            val decision = state.recordFailure(7uL, 0, IOException("still unavailable"))
            assertEquals(DesktopJournalFailureDisposition.TRANSIENT, decision.disposition)
            assertEquals(240_000L, decision.retryDelayMs)
        }
    }

    @Test
    fun `retry state stays per message and resets after completion`() {
        val state = DesktopJournalRetryState()
        val error = IOException("temporary storage failure")

        assertEquals(2_000L, state.recordFailure(1uL, 0, error).retryDelayMs)
        assertEquals(10_000L, state.recordFailure(1uL, 0, error).retryDelayMs)
        assertEquals(2_000L, state.recordFailure(2uL, 0, error).retryDelayMs)

        state.complete(1uL)
        assertEquals(2_000L, state.recordFailure(1uL, 0, error).retryDelayMs)
    }

    @Test
    fun `persisted permanent attempts and extreme retry counts remain bounded`() {
        val state = DesktopJournalRetryState()
        val error = IOException("temporary storage failure")

        assertEquals(30_000L, state.recordFailure(4uL, 2, error).retryDelayMs)
        assertEquals(240_000L, state.recordFailure(5uL, Int.MAX_VALUE, error).retryDelayMs)
        assertEquals(2_000L, state.recordFailure(6uL, -1, error).retryDelayMs)
    }
}

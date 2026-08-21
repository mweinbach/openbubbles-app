package app.openbubbles.nativeapp.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicLong

data class LiveMessageArrival(
    val messageGuid: String,
    val chatId: Long,
    val threadRootGuid: String? = null,
    val threadPart: Long? = null,
    val sequence: Long = 0,
)

/**
 * Process-local boundary between live APNs intake and open transcript viewports.
 * A bounded replay window bridges collector recreation. Each viewport saves its
 * last observed sequence and a genuinely new viewport starts at [latestSequence],
 * so replay is limited to an existing viewport's configuration handoff.
 */
object LiveMessageArrivals {
    private const val RecreationReplayCapacity = 512
    private val sequence = AtomicLong(0)
    private val mutableEvents = MutableSharedFlow<LiveMessageArrival>(
        replay = RecreationReplayCapacity,
        extraBufferCapacity = 64,
    )
    val events = mutableEvents.asSharedFlow()
    val latestSequence: Long get() = sequence.get()

    // Sequence before emission so a viewport that opens concurrently either
    // baselines this marker or receives it from replay; there is no gap.
    suspend fun publish(arrival: LiveMessageArrival) {
        mutableEvents.emit(arrival.copy(sequence = sequence.incrementAndGet()))
    }
}

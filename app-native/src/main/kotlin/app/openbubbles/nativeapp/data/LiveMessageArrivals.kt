package app.openbubbles.nativeapp.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Process-local boundary between live APNs intake and open transcript viewports.
 * No replay is intentional: a screen opened after persistence baselines the row
 * instead of announcing it as newly arrived.
 */
object LiveMessageArrivals {
    private val mutableEvents = MutableSharedFlow<String>(
        extraBufferCapacity = 64,
    )
    val events = mutableEvents.asSharedFlow()

    // Intake is already suspendable. Backpressure after the short burst
    // buffer preserves every marker for an attached transcript collector;
    // with no collector, no replay intentionally lets a later screen baseline.
    suspend fun publish(messageGuid: String) = mutableEvents.emit(messageGuid)
}

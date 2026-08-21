package app.openbubbles.nativeapp.data

import kotlinx.coroutines.channels.BufferOverflow
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
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events = mutableEvents.asSharedFlow()

    fun publish(messageGuid: String) {
        mutableEvents.tryEmit(messageGuid)
    }
}

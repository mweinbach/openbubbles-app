package app.openbubbles.core.repo

import io.objectbox.reactive.SubscriptionBuilder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

/**
 * Bridges ObjectBox's reactive layer to Kotlin flows.
 *
 * `Query.subscribe()` returns a [SubscriptionBuilder] that emits the query
 * result list immediately and again on every relevant DB change. The
 * subscription is cancelled (which closes the query) when the flow collector
 * goes away. Emissions are sent through a buffered channel via `launch` so a
 * slow collector back-pressures instead of dropping updates.
 */
internal fun <T : Any> SubscriptionBuilder<T>.asFlow(): Flow<T> = callbackFlow {
    val subscription = observer { data ->
        launch { send(data) }
    }
    awaitClose { subscription.cancel() }
}

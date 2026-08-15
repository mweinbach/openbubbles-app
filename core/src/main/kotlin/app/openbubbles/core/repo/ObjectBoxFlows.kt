package app.openbubbles.core.repo

import io.objectbox.reactive.SubscriptionBuilder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Bridges ObjectBox's reactive layer to Kotlin flows.
 *
 * `Query.subscribe()` returns a [SubscriptionBuilder] that emits the query
 * result list immediately and again on every relevant DB change. The
 * subscription is cancelled when the flow collector goes away. The callback
 * never launches one coroutine per ObjectBox write: these are invalidation
 * signals, so downstream collectors can conflate them and query current data.
 */
internal fun <T : Any> SubscriptionBuilder<T>.asFlow(): Flow<T> = callbackFlow {
    val subscription = observer { data ->
        trySend(data)
    }
    awaitClose { subscription.cancel() }
}

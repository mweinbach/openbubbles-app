package app.openbubbles.core.repo

import app.openbubbles.db.Attachment
import app.openbubbles.db.Chat
import app.openbubbles.db.ContactV2
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import io.objectbox.BoxStore
import io.objectbox.reactive.SubscriptionBuilder
import java.lang.ref.WeakReference
import java.util.EnumSet
import java.util.WeakHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal enum class StoreEntityChange {
    CHAT,
    MESSAGE,
    ATTACHMENT,
    CONTACT,
    HANDLE,
}

/**
 * Holds store-wide reactive refreshes until a logical history page has landed.
 *
 * ObjectBox commits notify entity subscribers from its own ordered background
 * queue. [awaitPublisherBarrier] adds a marker to that same queue, allowing a
 * page writer to wait until every preceding commit notification was observed
 * before releasing one typed refresh. This avoids relying on timing or a
 * debounce interval while still keeping ordinary live-message writes instant.
 */
internal class StoreInvalidationCoordinator(
    store: BoxStore,
) {
    private val storeRef = WeakReference(store)
    private val lock = Any()
    private var coalescingDepth = 0
    private var changeGeneration = 0L
    private val generationByType = StoreEntityChange.entries.associateWithTo(mutableMapOf()) { 0L }
    private val deferredChanges = EnumSet.noneOf(StoreEntityChange::class.java)
    private val flushes = MutableSharedFlow<Set<StoreEntityChange>>(
        // Keep short mixed-entity live-write bursts intact; history pages are
        // collapsed to one set before they reach this buffer.
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        // One observer set per BoxStore. Repositories consume the shared typed
        // flow instead of each transient ChatRepo/ContactSync permanently
        // registering another strong ObjectBox observer.
        store.subscribe(Chat::class.java).onlyChanges().observer { changed(StoreEntityChange.CHAT) }
        store.subscribe(Message::class.java).onlyChanges().observer { changed(StoreEntityChange.MESSAGE) }
        store.subscribe(Attachment::class.java).onlyChanges().observer { changed(StoreEntityChange.ATTACHMENT) }
        store.subscribe(ContactV2::class.java).onlyChanges().observer { changed(StoreEntityChange.CONTACT) }
        store.subscribe(Handle::class.java).onlyChanges().observer { changed(StoreEntityChange.HANDLE) }
    }

    private fun changed(change: StoreEntityChange) {
        val emit = synchronized(lock) {
            changeGeneration += 1
            generationByType[change] = changeGeneration
            if (coalescingDepth == 0) {
                setOf(change)
            } else {
                deferredChanges += change
                null
            }
        }
        if (emit != null) flushes.tryEmit(emit)
    }

    fun changesFor(vararg changes: StoreEntityChange): Flow<Unit> {
        val wanted = changes.toSet()
        return flushes
            .filter { emitted -> emitted.any(wanted::contains) }
            .map { }
    }

    fun generationFor(vararg changes: StoreEntityChange): Long = synchronized(lock) {
        changes.maxOfOrNull { generationByType.getValue(it) } ?: 0L
    }

    suspend fun <T> coalesce(block: suspend () -> T): T {
        synchronized(lock) { coalescingDepth += 1 }
        try {
            return block()
        } finally {
            val needsBarrier = synchronized(lock) { coalescingDepth == 1 }
            try {
                if (needsBarrier) withContext(NonCancellable) { awaitPublisherBarrier() }
            } finally {
                val toFlush = synchronized(lock) {
                    coalescingDepth -= 1
                    if (coalescingDepth == 0 && deferredChanges.isNotEmpty()) {
                        EnumSet.copyOf(deferredChanges).also { deferredChanges.clear() }
                    } else {
                        null
                    }
                }
                if (toFlush != null) flushes.tryEmit(toFlush)
            }
        }
    }

    private suspend fun awaitPublisherBarrier() {
        val store = storeRef.get() ?: return
        val reached = CompletableDeferred<Unit>()
        store.subscribe().single().observer { reached.complete(Unit) }
        reached.await()
    }
}

internal object StoreInvalidationCoordinators {
    private val coordinators = WeakHashMap<BoxStore, StoreInvalidationCoordinator>()

    fun forStore(store: BoxStore): StoreInvalidationCoordinator = synchronized(coordinators) {
        coordinators.getOrPut(store) { StoreInvalidationCoordinator(store) }
    }
}

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

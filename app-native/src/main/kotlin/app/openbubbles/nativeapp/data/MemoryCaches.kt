package app.openbubbles.nativeapp.data

import java.util.concurrent.CopyOnWriteArrayList

/** Keeps process trim handling independent from UI classes during service-only startup. */
object MemoryCaches {
    private val clearers = CopyOnWriteArrayList<() -> Unit>()

    fun register(clearer: () -> Unit) {
        clearers += clearer
    }

    fun clear() {
        clearers.forEach { it() }
    }
}

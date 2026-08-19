package app.openbubbles.nativeapp.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Display info a transcript or list row needs before the async resolver lands. */
data class ContactDisplay(val displayName: String?, val avatarPath: String?)

/**
 * Generation-checked LRU behind [ContactDisplayWarmCache]: an entry written
 * under an older generation vanishes on read, so a contact sync invalidates
 * everything without an eviction sweep.
 */
internal class ContactDisplayLru(
    private val maxEntries: Int,
    private val currentGeneration: () -> Int,
) {
    private data class Entry(val display: ContactDisplay, val generation: Int)

    private val entries = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>?): Boolean =
            size > maxEntries
    }

    fun peek(address: String): ContactDisplay? = synchronized(entries) {
        val entry = entries[address] ?: return null
        if (entry.generation != currentGeneration()) {
            entries.remove(address)
            null
        } else {
            entry.display
        }
    }

    fun put(address: String, display: ContactDisplay) {
        if (address.isBlank()) return
        val entry = Entry(display, currentGeneration())
        synchronized(entries) { entries[address] = entry }
    }
}

/**
 * Synchronous warm cache mapping handle address -> (display name, avatar
 * path): the TranscriptPrefetch idea applied to contact info. Participant
 * rows, group sender labels, and header avatars seed from it at first
 * composition instead of resolving from empty. Entries are seeds, not truth —
 * every consumer still runs its async [UiContacts.contactNames] path and
 * writes the fresh result back. Invalidation piggybacks on
 * [UiContacts.avatarGeneration] (bumped by contact and group-photo sync):
 * stale-generation entries are dropped on read.
 */
object ContactDisplayWarmCache {
    private const val MaxEntries = 128

    private val cache = ContactDisplayLru(MaxEntries) { UiContacts.avatarGeneration.value }

    fun peek(address: String?): ContactDisplay? = address?.let(cache::peek)

    fun put(address: String, display: ContactDisplay) = cache.put(address, display)

    /** Resolves and stores [addresses] on IO; already-warm entries are skipped. */
    suspend fun warm(addresses: Collection<String>) {
        val resolver = UiContacts.contactNames ?: return
        val misses = addresses.filter { it.isNotBlank() && cache.peek(it) == null }.distinct()
        if (misses.isEmpty()) return
        withContext(Dispatchers.IO) {
            misses.forEach { address ->
                val resolved = runCatching { resolver(address) }.getOrNull()
                cache.put(address, ContactDisplay(resolved?.first, resolved?.second))
            }
        }
    }
}

package app.openbubbles.nativeapp.data

/** Last messages kept warm for list-adjacent conversations. */
const val TRANSCRIPT_PREFETCH_LIMIT = 10

/** Extra chats above and below the visible window that stay warm. */
const val TRANSCRIPT_PREFETCH_NEIGHBORS = 3

/** Page loaded as soon as a conversation is opened or a notification is tapped. */
const val TRANSCRIPT_OPEN_LIMIT = 30

/**
 * Chat ids whose transcripts should be warmed for the current list viewport.
 *
 * [visibleChatIds] are rows actually on screen (and every pin when the pin
 * grid is visible). The returned window adds [neighborCount] conversations
 * above and below so a short fling is already cached. Before the list
 * reports layout, the first [emptyVisibleLimit] rows are warmed instead.
 */
fun visibleTranscriptPrefetchIds(
    orderedChatIds: List<Long>,
    visibleChatIds: Collection<Long>,
    neighborCount: Int = TRANSCRIPT_PREFETCH_NEIGHBORS,
    emptyVisibleLimit: Int = TRANSCRIPT_PREFETCH_LIMIT,
): List<Long> {
    if (orderedChatIds.isEmpty()) return emptyList()
    if (visibleChatIds.isEmpty()) return orderedChatIds.take(emptyVisibleLimit)

    val order = HashMap<Long, Int>(orderedChatIds.size)
    orderedChatIds.forEachIndexed { index, id -> order[id] = index }

    var first = Int.MAX_VALUE
    var last = -1
    visibleChatIds.forEach { id ->
        val index = order[id] ?: return@forEach
        if (index < first) first = index
        if (index > last) last = index
    }
    if (last < 0) return orderedChatIds.take(emptyVisibleLimit)

    val from = (first - neighborCount).coerceAtLeast(0)
    val to = (last + neighborCount).coerceAtMost(orderedChatIds.lastIndex)
    return orderedChatIds.subList(from, to + 1)
}

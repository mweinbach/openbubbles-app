package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.nativeapp.data.MessageItem

/**
 * Pure viewport/arrival policy behind the "New messages" jump pill.
 *
 * The transcript is a reversed LazyColumn (index 0 is the visual bottom), and
 * the repository flow re-emits the whole page whenever an attachment lands, a
 * reaction or edit arrives, a contact resolves, or an optimistic row
 * reconciles. None of those are new messages, so arrivals are classified by
 * stable GUID plus chronological position rather than by list size or by the
 * fact that a flow emitted.
 */

/** Bottom-follow threshold: a reader further than this is reading history. */
internal const val FollowBottomThresholdDp = 96

/** Presentation cap for the pending count ("99+" above this). */
internal const val PendingCountDisplayCap = 99

/** Live GUIDs retained after classification so a bounded page cannot replay them. */
private const val ConsumedLiveGuidRetention = 256
private const val PendingGuidRetention = 512
internal const val LiveMarkerRetention = 512

private fun retainedConsumedLiveGuids(
    previous: Set<String>,
    newlyConsumed: Iterable<String>,
): Set<String> = LinkedHashSet<String>().apply {
    addAll(previous)
    addAll(newlyConsumed)
    while (size > ConsumedLiveGuidRetention) {
        iterator().run {
            next()
            remove()
        }
    }
}

/**
 * Baseline plus pending arrivals for one viewport (the main transcript or one
 * focused reply thread). Immutable so the reducer stays testable.
 */
internal data class ArrivalState(
    /** False until the first non-empty snapshot establishes the baseline. */
    val initialized: Boolean = false,
    /** Every stable GUID in the current window; novelty is measured against it. */
    val knownGuids: Set<String> = emptySet(),
    /** Chronological high-water mark (date, id) of the newest row seen. */
    val newestSeenDate: Long = Long.MIN_VALUE,
    val newestSeenId: Long = Long.MIN_VALUE,
    /** Distinct incoming GUIDs that arrived while the reader was away. */
    val pendingGuids: Set<String> = emptySet(),
    /** Recently classified live GUIDs, including rows no longer in the loaded window. */
    val consumedLiveGuids: Set<String> = emptySet(),
) {
    val pendingCount: Int get() = pendingGuids.size

    /** Clears the pill without rewinding the chronological baseline. */
    fun cleared(): ArrivalState =
        if (pendingGuids.isEmpty()) this else copy(pendingGuids = emptySet())

    /** Removes arrivals already viewed in a focused reply thread. */
    fun viewed(guids: Set<String>): ArrivalState =
        if (pendingGuids.none { it in guids }) this else copy(pendingGuids = pendingGuids - guids)
}

/**
 * Bounded intake markers awaiting a matching database row. Overflow switches
 * this viewport to chronological reconciliation instead of dropping arrivals.
 */
internal data class LiveArrivalMarkerState(
    val unmatchedGuids: Set<String> = emptySet(),
    /** Markers beyond [LiveMarkerRetention], reconciled chronologically by count. */
    val overflowCount: Int = 0,
) {
    /** Exact markers stay usable while overflow reconciliation is active. */
    val reducerGuids: Set<String> get() = unmatchedGuids
    val chronologicalFallback: Boolean get() = overflowCount > 0

    fun added(guid: String): LiveArrivalMarkerState {
        if (guid in unmatchedGuids) return this
        if (unmatchedGuids.size >= LiveMarkerRetention) {
            return copy(overflowCount = overflowCount + 1)
        }
        return copy(unmatchedGuids = LinkedHashSet(unmatchedGuids).apply { add(guid) })
    }

    fun consumed(
        guids: Set<String>,
        fallbackGuids: Set<String> = emptySet(),
    ): LiveArrivalMarkerState = copy(
        unmatchedGuids = unmatchedGuids - guids,
        overflowCount = (overflowCount - fallbackGuids.size).coerceAtLeast(0),
    )
}

/** Every protocol chat represented by a contact-grouped conversation. */
internal fun liveArrivalChatIds(chatId: Long?, memberChatIds: List<Long>): Set<Long> =
    buildSet {
        chatId?.let(::add)
        addAll(memberChatIds)
    }

/** Route identity stays stable while the asynchronously loaded chat model catches up. */
internal fun conversationArrivalStateKey(routeChatId: Long?, loadedChatId: Long?): Long? =
    routeChatId ?: loadedChatId

/** What the caller should do after folding one snapshot into [ArrivalState]. */
internal data class ArrivalOutcome(
    val state: ArrivalState,
    /** Distinct incoming rows this snapshot classified as live arrivals. */
    val arrivals: Int,
    /**
     * True when the reader was following the bottom and a live arrival landed:
     * keep the transcript pinned to the newest row instead of showing a pill.
     */
    val pinToNewest: Boolean,
    /** Intake markers whose independently persisted rows were present in this snapshot. */
    val matchedLiveGuids: Set<String> = emptySet(),
    /** Chronologically reconciled rows whose exact marker overflowed the bounded set. */
    val reconciledFallbackGuids: Set<String> = emptySet(),
)

internal data class DeferredLiveArrival(
    val chatId: Long,
    val messageGuid: String,
)

internal data class DeferredLiveArrivalState(
    val arrivals: List<DeferredLiveArrival> = emptyList(),
) {
    fun added(chatId: Long, messageGuid: String): DeferredLiveArrivalState {
        val marker = DeferredLiveArrival(chatId, messageGuid)
        if (marker in arrivals) return this
        return copy(arrivals = (arrivals + marker).takeLast(LiveMarkerRetention))
    }
}

/** Newer-than-baseline test; ids break ties inside the same millisecond. */
private fun isNewerThanBaseline(message: MessageItem, state: ArrivalState): Boolean =
    message.date > state.newestSeenDate ||
        (message.date == state.newestSeenDate && message.id > state.newestSeenId)

/**
 * Folds one message snapshot (ascending by time) into the arrival state.
 *
 * - The first non-empty snapshot only establishes the baseline: opening a
 *   conversation never announces rows that were already there.
 * - A row already in [ArrivalState.knownGuids] can never become an arrival, so
 *   attachment-only, reaction, edit, upload-progress, contact-enrichment, and
 *   duplicate-flow re-emissions leave the count alone.
 * - An older history page inserts rows that are chronologically behind the
 *   baseline, so paging never increments the count or pins the list.
 * - Outgoing rows (including optimistic/staged ones) never increment the count;
 *   explicit sends keep their own scroll path.
 */
internal fun reduceArrivals(
    state: ArrivalState,
    messages: List<MessageItem>,
    followingBottom: Boolean,
    historySyncActive: Boolean = false,
    /** Exact GUIDs observed at live intake; null keeps the pure legacy fallback for host callers. */
    liveArrivalGuids: Set<String>? = null,
    /** Also reconcile untracked overflow rows chronologically for this snapshot. */
    chronologicalFallback: Boolean = false,
): ArrivalOutcome {
    if (messages.isEmpty()) {
        // Repository startup can briefly emit empty after saveable state was
        // restored. Preserve that baseline and its pill until a real snapshot
        // can reconcile it.
        return ArrivalOutcome(
            state,
            arrivals = 0,
            pinToNewest = false,
        )
    }
    val guids = messages.mapTo(LinkedHashSet()) { it.guid }
    val matchedLiveGuids = liveArrivalGuids
        ?.filterTo(LinkedHashSet()) { it in guids }
        .orEmpty()
    val newest = messages.last()
    if (!state.initialized) {
        val initialArrivals = messages.filter {
            !it.isFromMe && it.guid in liveArrivalGuids.orEmpty()
        }
        val pending = LinkedHashSet<String>().apply {
            initialArrivals.mapTo(this) { it.guid }
        }
        return ArrivalOutcome(
            ArrivalState(
                initialized = true,
                knownGuids = guids,
                newestSeenDate = newest.date,
                newestSeenId = newest.id,
                pendingGuids = pending,
                consumedLiveGuids = retainedConsumedLiveGuids(
                    state.consumedLiveGuids,
                    guids.filter { it in liveArrivalGuids.orEmpty() },
                ),
            ),
            arrivals = initialArrivals.size,
            pinToNewest = followingBottom && initialArrivals.isNotEmpty(),
            matchedLiveGuids = matchedLiveGuids,
        )
    }

    val fallbackGuids = LinkedHashSet<String>()
    val arrivals = messages.filter {
        !it.isFromMe &&
            if (liveArrivalGuids != null) {
                // Persistence and the process-local marker are delivered by
                // independent flows. A row that was already baselined must
                // still be recognized when its marker arrives later.
                (it.guid in liveArrivalGuids && it.guid !in state.consumedLiveGuids) ||
                    (chronologicalFallback &&
                        it.guid !in state.knownGuids &&
                        !historySyncActive &&
                        isNewerThanBaseline(it, state)).also { fallback ->
                            if (fallback && it.guid !in liveArrivalGuids) fallbackGuids += it.guid
                        }
            } else {
                it.guid !in state.knownGuids &&
                    !historySyncActive && isNewerThanBaseline(it, state)
            }
    }
    // Queue before pinning. The UI clears this only after the newest row is
    // visibly reached, so a gesture-cancelled animation leaves a usable pill.
    val pending = LinkedHashSet<String>().apply {
        state.pendingGuids.filterTo(this) { it in guids }
        arrivals.mapTo(this) { it.guid }
        while (size > PendingGuidRetention) {
            iterator().run {
                next()
                remove()
            }
        }
    }
    val advanceBaseline = isNewerThanBaseline(newest, state)
    val consumed = retainedConsumedLiveGuids(state.consumedLiveGuids, arrivals.map { it.guid })
    val advanced = state.copy(
        knownGuids = guids,
        newestSeenDate = if (advanceBaseline) newest.date else state.newestSeenDate,
        newestSeenId = if (advanceBaseline) newest.id else state.newestSeenId,
        pendingGuids = pending,
        consumedLiveGuids = consumed,
    )
    return ArrivalOutcome(
        state = advanced,
        arrivals = arrivals.size,
        pinToNewest = followingBottom && arrivals.isNotEmpty(),
        matchedLiveGuids = matchedLiveGuids,
        reconciledFallbackGuids = fallbackGuids,
    )
}

/**
 * The bottom-most laid-out row plus whether a gesture or fling is running.
 * Mirrors `LazyListState.firstVisibleItemIndex` /
 * `firstVisibleItemScrollOffset` / `isScrollInProgress`, which report the
 * visual bottom in a reversed list.
 */
internal data class TranscriptAnchor(
    val firstVisibleIndex: Int,
    val firstVisibleOffsetPx: Int,
    val isScrollInProgress: Boolean = false,
)

/**
 * True when the newest message row is within [thresholdPx] of the visual
 * bottom. [newestMessageIndex] is the LazyColumn index of the newest message,
 * which is 1 rather than 0 while the optional typing row holds the bottom slot,
 * so this never assumes index zero means "newest message".
 */
internal fun isFollowingBottom(
    anchor: TranscriptAnchor,
    newestMessageIndex: Int,
    thresholdPx: Int,
): Boolean = when {
    // Nothing rendered yet (empty or pre-layout): the list is already pinned.
    newestMessageIndex < 0 -> true
    anchor.firstVisibleIndex < newestMessageIndex -> true
    anchor.firstVisibleIndex > newestMessageIndex -> false
    else -> anchor.firstVisibleOffsetPx <= thresholdPx
}

/**
 * Auto-scroll is suppressed while the reader's finger or a fling owns the list;
 * the arrival stays queued for the pill and is reevaluated once it settles.
 */
internal fun shouldAutoScrollToNewest(followingBottom: Boolean, anchor: TranscriptAnchor): Boolean =
    followingBottom && !anchor.isScrollInProgress

/** LazyColumn index of the newest message row, or -1 when nothing is rendered. */
internal fun newestMessageIndex(entries: List<ConversationEntry>, typingRowVisible: Boolean): Int {
    val index = entries.indexOfFirst { it is ConversationEntry.Message }
    if (index < 0) return -1
    return index + if (typingRowVisible) 1 else 0
}

/** Stable LazyColumn key for the newest message, unaffected by a typing-row insertion. */
internal fun newestMessageKey(entries: List<ConversationEntry>): String? =
    entries.firstOrNull { it is ConversationEntry.Message }?.key

/** Reading position to preserve across an older-history insertion. */
internal data class PagingAnchor(val key: String, val index: Int, val offsetPx: Int)

/**
 * Captures the bottom-most laid-out key and its pixel offset before an older
 * page is requested. [visibleKeys] is keyed by LazyColumn index.
 */
internal fun capturePagingAnchor(
    anchor: TranscriptAnchor,
    visibleKeys: Map<Int, String>,
): PagingAnchor? {
    val key = visibleKeys[anchor.firstVisibleIndex] ?: return null
    return PagingAnchor(key, anchor.firstVisibleIndex, anchor.firstVisibleOffsetPx)
}

/**
 * Where to scroll so the captured anchor keeps its exact reading position after
 * older rows are inserted, or null when the key did not move (the reversed list
 * appends history at higher indices, so this is normally a no-op) or left the
 * window entirely.
 */
internal fun pagingAnchorScrollTarget(
    anchor: PagingAnchor,
    keysAfterInsertion: List<String>,
): Pair<Int, Int>? {
    val index = keysAfterInsertion.indexOf(anchor.key)
    if (index < 0 || index == anchor.index) return null
    return index to anchor.offsetPx
}

/** Which transcript the pill belongs to; only the wording differs. */
internal enum class JumpPillScope { Conversation, Thread }

/**
 * Localized-count pill label. Zero keeps the plain product wording ("New
 * messages") for the case where the count is unknown or already cleared.
 */
internal fun jumpPillLabel(count: Int, scope: JumpPillScope): String {
    val plural = if (scope == JumpPillScope.Thread) "new replies" else "new messages"
    val singular = if (scope == JumpPillScope.Thread) "new reply" else "new message"
    return when {
        count <= 0 -> if (scope == JumpPillScope.Thread) "New replies" else "New messages"
        count == 1 -> "1 $singular"
        count > PendingCountDisplayCap -> "$PendingCountDisplayCap+ $plural"
        else -> "$count $plural"
    }
}

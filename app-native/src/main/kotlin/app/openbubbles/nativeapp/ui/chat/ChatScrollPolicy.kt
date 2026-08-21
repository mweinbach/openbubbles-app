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
) {
    val pendingCount: Int get() = pendingGuids.size

    /** Clears the pill without rewinding the chronological baseline. */
    fun cleared(): ArrivalState =
        if (pendingGuids.isEmpty()) this else copy(pendingGuids = emptySet())
}

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
)

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
): ArrivalOutcome {
    if (messages.isEmpty()) {
        // A chat with nothing loaded has no baseline to defend; the next
        // non-empty snapshot re-establishes one silently.
        return ArrivalOutcome(ArrivalState(), arrivals = 0, pinToNewest = false)
    }
    val guids = messages.mapTo(LinkedHashSet()) { it.guid }
    val newest = messages.last()
    if (!state.initialized) {
        return ArrivalOutcome(
            ArrivalState(
                initialized = true,
                knownGuids = guids,
                newestSeenDate = newest.date,
                newestSeenId = newest.id,
            ),
            arrivals = 0,
            pinToNewest = false,
        )
    }

    val arrivals = messages.filter {
        it.guid !in state.knownGuids && !it.isFromMe && isNewerThanBaseline(it, state)
    }
    val pending = if (followingBottom) {
        emptySet()
    } else {
        // Drop rows that left the window so the count always matches something
        // the reader can still jump to.
        LinkedHashSet<String>().apply {
            state.pendingGuids.filterTo(this) { it in guids }
            arrivals.mapTo(this) { it.guid }
        }
    }
    val advanceBaseline = isNewerThanBaseline(newest, state)
    val advanced = state.copy(
        knownGuids = guids,
        newestSeenDate = if (advanceBaseline) newest.date else state.newestSeenDate,
        newestSeenId = if (advanceBaseline) newest.id else state.newestSeenId,
        pendingGuids = pending,
    )
    return ArrivalOutcome(
        state = advanced,
        arrivals = arrivals.size,
        pinToNewest = followingBottom && arrivals.isNotEmpty(),
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
 * The follow threshold in pixels: [FollowBottomThresholdDp] converted by the
 * caller, or one measured newest-row extent when that row is taller.
 */
internal fun followBottomThresholdPx(thresholdDpPx: Int, newestRowExtentPx: Int): Int =
    maxOf(thresholdDpPx, newestRowExtentPx)

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

package app.openbubbles.nativeapp.ui.chat

import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The arrival reducer and reverse-layout geometry behind the "New messages"
 * pill. Every case here is a snapshot the repository flow really emits: page
 * re-emissions, attachment completions, older pages, and optimistic sends all
 * arrive as a whole new list.
 */
class ChatScrollPolicyTest {

    private val start = 1_717_977_600_000L

    // ---- Baseline ------------------------------------------------------------

    @Test
    fun `first non-empty snapshot only establishes the baseline`() {
        val outcome = reduceArrivals(
            ArrivalState(),
            listOf(message(1, start), message(2, start + 1_000)),
            followingBottom = false,
        )
        assertEquals(0, outcome.arrivals)
        assertEquals(0, outcome.state.pendingCount)
        assertFalse(outcome.pinToNewest)
        assertTrue(outcome.state.initialized)
    }

    @Test
    fun `an initial empty snapshot preserves restored baseline and pending arrivals`() {
        val loaded = reduceArrivals(ArrivalState(), listOf(message(1, start)), false).state
        val restored = loaded.copy(pendingGuids = setOf("pending"))
        val empty = reduceArrivals(restored, emptyList(), false)
        assertEquals(restored, empty.state)
    }

    // ---- Passive incoming arrivals -------------------------------------------

    @Test
    fun `incoming while following bottom pins the list and shows no pill`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(1, start)), true).state
        val outcome = reduceArrivals(
            base,
            listOf(message(1, start), message(2, start + 1_000)),
            followingBottom = true,
        )
        assertEquals(1, outcome.arrivals)
        assertTrue(outcome.pinToNewest)
        assertEquals(0, outcome.state.pendingCount)
    }

    @Test
    fun `incoming while away from bottom counts without pinning`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(1, start)), false).state
        val first = reduceArrivals(
            base,
            listOf(message(1, start), message(2, start + 1_000)),
            followingBottom = false,
        )
        assertEquals(1, first.arrivals)
        assertFalse(first.pinToNewest)
        assertEquals(1, first.state.pendingCount)

        val second = reduceArrivals(
            first.state,
            listOf(message(1, start), message(2, start + 1_000), message(3, start + 2_000)),
            followingBottom = false,
        )
        assertEquals(2, second.state.pendingCount)
    }

    @Test
    fun `a re-emitted page never counts the same guid twice`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(1, start)), false).state
        val page = listOf(message(1, start), message(2, start + 1_000))
        val first = reduceArrivals(base, page, followingBottom = false)
        val repeat = reduceArrivals(first.state, page, followingBottom = false)
        assertEquals(0, repeat.arrivals)
        assertEquals(1, repeat.state.pendingCount)
    }

    @Test
    fun `attachment reaction edit and contact updates do not count`() {
        val incoming = message(2, start + 1_000)
        val base = reduceArrivals(
            ArrivalState(),
            listOf(message(1, start), incoming),
            followingBottom = false,
        ).state

        val enriched = listOf(
            message(1, start),
            incoming.copy(
                attachmentMeta = AttachmentMeta(
                    guid = "a2",
                    mime = "image/jpeg",
                    name = "IMG.jpg",
                    sizeBytes = 1_024,
                    isImage = true,
                    downloaded = true,
                ),
                reactionEmoji = "❤️",
                edited = true,
                senderAddress = "Alex Doe",
                uploadProgress = 512L to 1_024L,
            ),
        )
        val outcome = reduceArrivals(base, enriched, followingBottom = false)
        assertEquals(0, outcome.arrivals)
        assertEquals(0, outcome.state.pendingCount)
    }

    @Test
    fun `an older history page never counts or pins`() {
        val base = reduceArrivals(
            ArrivalState(),
            listOf(message(10, start), message(11, start + 1_000)),
            followingBottom = false,
        ).state
        val withHistory = listOf(
            message(1, start - 60_000),
            message(2, start - 30_000),
            message(10, start),
            message(11, start + 1_000),
        )
        val outcome = reduceArrivals(base, withHistory, followingBottom = false)
        assertEquals(0, outcome.arrivals)
        assertEquals(0, outcome.state.pendingCount)
        assertFalse(outcome.pinToNewest)
    }

    @Test
    fun `a live arrival inserted with an older page is still classified by date`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(10, start)), false).state
        val mixed = listOf(
            message(1, start - 60_000),
            message(10, start),
            message(11, start + 5_000),
        )
        val outcome = reduceArrivals(base, mixed, followingBottom = false)
        assertEquals(1, outcome.arrivals)
        assertEquals(setOf("g11"), outcome.state.pendingGuids)
    }

    @Test
    fun `history sync advances the baseline without announcing imported rows`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(10, start)), false).state
        val synced = reduceArrivals(
            base,
            listOf(message(10, start), message(11, start + 5_000)),
            followingBottom = false,
            historySyncActive = true,
        )
        assertEquals(0, synced.arrivals)
        assertEquals(0, synced.state.pendingCount)

        val afterSync = reduceArrivals(
            synced.state,
            listOf(message(10, start), message(11, start + 5_000)),
            followingBottom = false,
        )
        assertEquals(0, afterSync.arrivals)
    }

    @Test
    fun `live intake distinguishes arrivals from concurrent history imports`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(10, start)), false).state
        val imported = message(11, start + 5_000)
        val live = message(12, start - 5_000)

        val outcome = reduceArrivals(
            base,
            listOf(live, message(10, start), imported),
            followingBottom = false,
            historySyncActive = true,
            liveArrivalGuids = setOf(live.guid),
        )

        assertEquals(1, outcome.arrivals)
        assertEquals(setOf(live.guid), outcome.state.pendingGuids)
    }

    @Test
    fun `delayed live intake is accepted behind the timestamp high water mark`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(10, start)), false).state
        val delayed = message(11, start - 60_000)

        val outcome = reduceArrivals(
            base,
            listOf(delayed, message(10, start)),
            followingBottom = false,
            liveArrivalGuids = setOf(delayed.guid),
        )

        assertEquals(1, outcome.arrivals)
        assertEquals(setOf(delayed.guid), outcome.state.pendingGuids)
    }

    @Test
    fun `marker arriving after the database row classifies it exactly once`() {
        val row = message(11, start + 1_000)
        val base = reduceArrivals(ArrivalState(), listOf(message(10, start)), false).state
        val persisted = reduceArrivals(
            base,
            listOf(message(10, start), row),
            followingBottom = false,
            liveArrivalGuids = emptySet(),
        )
        assertEquals(0, persisted.arrivals)

        val marked = reduceArrivals(
            persisted.state,
            listOf(message(10, start), row),
            followingBottom = false,
            liveArrivalGuids = setOf(row.guid),
        )
        assertEquals(1, marked.arrivals)

        val repeated = reduceArrivals(
            marked.state,
            listOf(message(10, start), row),
            followingBottom = false,
            liveArrivalGuids = setOf(row.guid),
        )
        assertEquals(0, repeated.arrivals)
    }

    @Test
    fun `consumed live marker does not replay after its row leaves and reenters the window`() {
        val row = message(11, start + 1_000)
        val base = reduceArrivals(ArrivalState(), listOf(message(10, start)), false).state
        val consumed = reduceArrivals(
            base,
            listOf(message(10, start), row),
            followingBottom = false,
            liveArrivalGuids = setOf(row.guid),
        ).state
        val trimmed = reduceArrivals(
            consumed,
            listOf(message(10, start)),
            followingBottom = false,
            liveArrivalGuids = setOf(row.guid),
        ).state
        val restored = reduceArrivals(
            trimmed,
            listOf(message(10, start), row),
            followingBottom = false,
            liveArrivalGuids = setOf(row.guid),
        )

        assertEquals(0, restored.arrivals)
        assertEquals(setOf(row.guid), restored.state.consumedLiveGuids)
    }

    @Test
    fun `pill announcement count remains stable through exit`() {
        assertEquals(3, retainedPillAnnouncementCount(previous = 0, visible = true, count = 3))
        assertEquals(3, retainedPillAnnouncementCount(previous = 3, visible = false, count = 0))
        assertEquals(1, displayedPillAnnouncementCount(previous = 3, visible = true, count = 1))
        assertEquals(3, displayedPillAnnouncementCount(previous = 3, visible = false, count = 0))
    }

    @Test
    fun `outgoing rows never increment the count`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(1, start)), false).state
        val outcome = reduceArrivals(
            base,
            listOf(message(1, start), message(2, start + 1_000, fromMe = true)),
            followingBottom = false,
        )
        assertEquals(0, outcome.arrivals)
        assertEquals(0, outcome.state.pendingCount)
        assertFalse(outcome.pinToNewest)
    }

    @Test
    fun `an optimistic row reconciling to its transport guid is not an arrival`() {
        val staged = message(2, start + 1_000, fromMe = true).copy(
            guid = "temp-2",
            status = MessageStatus.SENDING,
        )
        val base = reduceArrivals(
            ArrivalState(),
            listOf(message(1, start), staged),
            followingBottom = false,
        ).state
        val reconciled = listOf(
            message(1, start),
            staged.copy(guid = "real-2", status = MessageStatus.SENT),
        )
        val outcome = reduceArrivals(base, reconciled, followingBottom = false)
        assertEquals(0, outcome.arrivals)
        assertEquals(0, outcome.state.pendingCount)
    }

    @Test
    fun `pending drops rows that left the loaded window`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(1, start)), false).state
        val pending = reduceArrivals(
            base,
            listOf(message(1, start), message(2, start + 1_000)),
            followingBottom = false,
        ).state
        assertEquals(1, pending.pendingCount)
        val trimmed = reduceArrivals(pending, listOf(message(1, start)), followingBottom = false)
        assertEquals(0, trimmed.state.pendingCount)
    }

    @Test
    fun `reaching the bottom clears the pill without rewinding the baseline`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(1, start)), false).state
        val pending = reduceArrivals(
            base,
            listOf(message(1, start), message(2, start + 1_000)),
            followingBottom = false,
        ).state
        val cleared = pending.cleared()
        assertEquals(0, cleared.pendingCount)
        // The cleared baseline still knows message 2, so a re-emission stays quiet.
        val outcome = reduceArrivals(
            cleared,
            listOf(message(1, start), message(2, start + 1_000)),
            followingBottom = false,
        )
        assertEquals(0, outcome.arrivals)
    }

    @Test
    fun `settling back at the bottom drops queued arrivals`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(1, start)), false).state
        val queued = reduceArrivals(
            base,
            listOf(message(1, start), message(2, start + 1_000)),
            followingBottom = false,
        ).state
        assertEquals(1, queued.pendingCount)
        val settled = reduceArrivals(
            queued,
            listOf(message(1, start), message(2, start + 1_000), message(3, start + 2_000)),
            followingBottom = true,
        )
        assertEquals(0, settled.state.pendingCount)
        assertTrue(settled.pinToNewest)
    }

    // ---- Reverse-layout geometry ---------------------------------------------

    @Test
    fun `newest message index accounts for the optional typing row`() {
        val entries = buildConversationEntries(
            listOf(message(1, start), message(2, start + 1_000)),
        )
        assertEquals(0, newestMessageIndex(entries, typingRowVisible = false))
        assertEquals(1, newestMessageIndex(entries, typingRowVisible = true))
        assertEquals(-1, newestMessageIndex(emptyList(), typingRowVisible = true))
    }

    @Test
    fun `following bottom holds within the threshold and fails beyond it`() {
        val threshold = 240
        assertTrue(
            isFollowingBottom(TranscriptAnchor(0, 0), newestMessageIndex = 0, thresholdPx = threshold),
        )
        assertTrue(
            isFollowingBottom(TranscriptAnchor(0, 240), newestMessageIndex = 0, thresholdPx = threshold),
        )
        assertFalse(
            isFollowingBottom(TranscriptAnchor(0, 241), newestMessageIndex = 0, thresholdPx = threshold),
        )
        assertFalse(
            isFollowingBottom(TranscriptAnchor(7, 0), newestMessageIndex = 0, thresholdPx = threshold),
        )
    }

    @Test
    fun `the typing row at the bottom still counts as following the newest message`() {
        // Typing row is index 0, newest message is index 1: the reader is below
        // the newest message, which is as pinned as it gets.
        assertTrue(
            isFollowingBottom(TranscriptAnchor(0, 0), newestMessageIndex = 1, thresholdPx = 240),
        )
        assertFalse(
            isFollowingBottom(TranscriptAnchor(2, 0), newestMessageIndex = 1, thresholdPx = 240),
        )
    }

    @Test
    fun `an empty transcript is treated as pinned`() {
        assertTrue(
            isFollowingBottom(TranscriptAnchor(0, 0), newestMessageIndex = -1, thresholdPx = 240),
        )
    }

    @Test
    fun `a tall newest row does not expand the declared follow boundary`() {
        assertTrue(isFollowingBottom(TranscriptAnchor(0, 240), 0, thresholdPx = 240))
        assertFalse(isFollowingBottom(TranscriptAnchor(0, 241), 0, thresholdPx = 240))
    }

    @Test
    fun `an active gesture suppresses auto-scroll`() {
        val dragging = TranscriptAnchor(0, 0, isScrollInProgress = true)
        assertFalse(shouldAutoScrollToNewest(followingBottom = true, anchor = dragging))
        val settled = TranscriptAnchor(0, 0, isScrollInProgress = false)
        assertTrue(shouldAutoScrollToNewest(followingBottom = true, anchor = settled))
        assertFalse(shouldAutoScrollToNewest(followingBottom = false, anchor = settled))
    }

    // ---- Paging anchor -------------------------------------------------------

    @Test
    fun `an older page insertion keeps the captured key at the same index`() {
        val before = buildConversationEntries(
            listOf(message(10, start), message(11, start + 1_000)),
        )
        val anchor = capturePagingAnchor(
            anchor = TranscriptAnchor(firstVisibleIndex = 1, firstVisibleOffsetPx = 42),
            visibleKeys = before.mapIndexed { index, entry -> index to entry.key }.toMap(),
        )
        checkNotNull(anchor)
        assertEquals("message-10", anchor.key)

        val after = buildConversationEntries(
            listOf(message(1, start - 60_000), message(10, start), message(11, start + 1_000)),
        )
        // Reversed layout appends history at higher indices, so nothing moved.
        assertNull(pagingAnchorScrollTarget(anchor, after.map { it.key }))
    }

    @Test
    fun `a moved anchor is restored at its captured offset`() {
        val anchor = PagingAnchor(key = "message-10", index = 1, offsetPx = 42)
        val moved = listOf("typing-indicator", "message-11", "message-10")
        assertEquals(2 to 42, pagingAnchorScrollTarget(anchor, moved))
    }

    @Test
    fun `an anchor that left the window is not restored`() {
        val anchor = PagingAnchor(key = "message-10", index = 1, offsetPx = 42)
        assertNull(pagingAnchorScrollTarget(anchor, listOf("message-11", "message-12")))
    }

    // ---- Labels --------------------------------------------------------------

    @Test
    fun `pill labels carry the count and stay bounded for presentation`() {
        assertEquals("New messages", jumpPillLabel(0, JumpPillScope.Conversation))
        assertEquals("1 new message", jumpPillLabel(1, JumpPillScope.Conversation))
        assertEquals("3 new messages", jumpPillLabel(3, JumpPillScope.Conversation))
        assertEquals("99 new messages", jumpPillLabel(99, JumpPillScope.Conversation))
        assertEquals("99+ new messages", jumpPillLabel(250, JumpPillScope.Conversation))
        assertEquals("New replies", jumpPillLabel(0, JumpPillScope.Thread))
        assertEquals("1 new reply", jumpPillLabel(1, JumpPillScope.Thread))
        assertEquals("4 new replies", jumpPillLabel(4, JumpPillScope.Thread))
    }

    @Test
    fun `the underlying distinct count survives the display cap`() {
        var state = reduceArrivals(ArrivalState(), listOf(message(0, start)), false).state
        val window = mutableListOf(message(0, start))
        repeat(120) { index ->
            window += message(index + 1L, start + (index + 1) * 1_000L)
            state = reduceArrivals(state, window.toList(), followingBottom = false).state
        }
        assertEquals(120, state.pendingCount)
        assertEquals("99+ new messages", jumpPillLabel(state.pendingCount, JumpPillScope.Conversation))
    }

    @Test
    fun `pending identity remains bounded independently of its display cap`() {
        var state = reduceArrivals(ArrivalState(), listOf(message(0, start)), false).state
        val window = mutableListOf(message(0, start))
        repeat(600) { index ->
            window += message(index + 1L, start + (index + 1) * 1_000L)
            state = reduceArrivals(state, window.toList(), followingBottom = false).state
        }
        assertEquals(512, state.pendingCount)
        assertEquals("99+ new messages", jumpPillLabel(state.pendingCount, JumpPillScope.Conversation))
    }

    @Test
    fun `live marker overflow falls back instead of silently evicting`() {
        var markers = LiveArrivalMarkerState()
        repeat(LiveMarkerRetention) { markers = markers.added("marker-$it") }
        assertEquals(LiveMarkerRetention, markers.reducerGuids.size)

        markers = markers.added("overflow")

        assertTrue(markers.chronologicalFallback)
        assertEquals(LiveMarkerRetention, markers.reducerGuids.size)

        markers = markers.consumed(emptySet()).added("overflow-2")
        assertTrue(markers.chronologicalFallback)

        markers = markers.consumed(setOf("marker-0")).added("next")
        assertTrue(markers.chronologicalFallback)
        assertEquals(LiveMarkerRetention, markers.reducerGuids.size)

        markers = markers.consumed(
            emptySet(),
            fallbackGuids = setOf("overflow", "overflow-2"),
        )
        assertFalse(markers.chronologicalFallback)
    }

    @Test
    fun `overflow fallback remains armed until a persisted row is reconciled`() {
        val base = reduceArrivals(ArrivalState(), listOf(message(1, start)), false).state
        var markers = LiveArrivalMarkerState()
        repeat(LiveMarkerRetention) { markers = markers.added("marker-$it") }
        markers = markers.added("overflow")

        val beforeRows = reduceArrivals(
            base,
            listOf(message(1, start)),
            followingBottom = false,
            liveArrivalGuids = markers.reducerGuids,
            chronologicalFallback = markers.chronologicalFallback,
        )
        markers = markers.consumed(
            beforeRows.matchedLiveGuids,
            fallbackGuids = beforeRows.reconciledFallbackGuids,
        )
        assertTrue(markers.chronologicalFallback)

        val persisted = message(2, start + 1_000).copy(guid = "marker-0")
        val afterRows = reduceArrivals(
            base,
            listOf(message(1, start), persisted),
            followingBottom = false,
            liveArrivalGuids = markers.reducerGuids,
            chronologicalFallback = markers.chronologicalFallback,
        )
        markers = markers.consumed(
            afterRows.matchedLiveGuids,
            fallbackGuids = afterRows.reconciledFallbackGuids,
        )
        assertEquals(1, afterRows.arrivals)
        assertTrue(markers.chronologicalFallback)

        val overflowPersisted = message(3, start + 2_000).copy(guid = "overflow")
        val overflowRows = reduceArrivals(
            afterRows.state,
            listOf(message(1, start), persisted, overflowPersisted),
            followingBottom = false,
            liveArrivalGuids = markers.reducerGuids,
            chronologicalFallback = markers.chronologicalFallback,
        )
        markers = markers.consumed(
            overflowRows.matchedLiveGuids,
            fallbackGuids = overflowRows.reconciledFallbackGuids,
        )
        assertEquals(setOf("overflow"), overflowRows.reconciledFallbackGuids)
        assertFalse(markers.chronologicalFallback)
    }

    @Test
    fun `partial overflow reconciliation retains every outstanding marker`() {
        var markers = LiveArrivalMarkerState()
        repeat(LiveMarkerRetention) { markers = markers.added("marker-$it") }
        markers = markers.added("overflow-1").added("overflow-2")

        markers = markers.consumed(
            guids = setOf("marker-0"),
            fallbackGuids = setOf("overflow-1"),
        )

        assertEquals(LiveMarkerRetention - 1, markers.reducerGuids.size)
        assertEquals(1, markers.overflowCount)
        assertTrue(markers.chronologicalFallback)
    }

    @Test
    fun `matched markers are removed while unmatched markers remain`() {
        val markers = LiveArrivalMarkerState()
            .added("matched")
            .added("waiting")
            .consumed(setOf("matched"))

        assertEquals(setOf("waiting"), markers.reducerGuids)
    }

    @Test
    fun `grouped conversation observes every member chat arrival`() {
        assertEquals(
            setOf(7L, 9L, 11L),
            liveArrivalChatIds(chatId = 7L, memberChatIds = listOf(7L, 9L, 11L)),
        )
        assertEquals(
            setOf(7L),
            liveArrivalChatIds(chatId = 7L, memberChatIds = emptyList()),
        )
    }

    @Test
    fun `route chat id remains the arrival state key while the model loads`() {
        assertEquals(7L, conversationArrivalStateKey(routeChatId = 7L, loadedChatId = null))
        assertEquals(7L, conversationArrivalStateKey(routeChatId = 7L, loadedChatId = 9L))
        assertEquals(9L, conversationArrivalStateKey(routeChatId = null, loadedChatId = 9L))
    }

    private fun message(
        id: Long,
        date: Long,
        fromMe: Boolean = false,
    ) = MessageItem(
        id = id,
        text = "m$id",
        isFromMe = fromMe,
        date = date,
        status = if (fromMe) MessageStatus.SENT else MessageStatus.DELIVERED,
        isGroupEvent = false,
        reactionEmoji = null,
        senderAddress = if (fromMe) null else "alex@icloud.com",
        guid = "g$id",
    )
}

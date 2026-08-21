package app.openbubbles.nativeapp.ui.chat

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver

/** Keeps a viewport's pending/consumed arrival identity across activity recreation. */
internal val ArrivalStateSaver = Saver<ArrivalState, Bundle>(
    save = { state ->
        Bundle().apply {
            putBoolean("initialized", state.initialized)
            putLong("newestDate", state.newestSeenDate)
            putLong("newestId", state.newestSeenId)
            putStringArrayList("pending", ArrayList(state.pendingGuids.toList().takeLast(SavedGuidLimit)))
            putStringArrayList("consumed", ArrayList(state.consumedLiveGuids.toList().takeLast(SavedGuidLimit)))
        }
    },
    restore = { saved ->
        ArrivalState(
            initialized = saved.getBoolean("initialized"),
            // The chronological high-water mark replaces the potentially huge
            // paged GUID window across recreation.
            knownGuids = emptySet(),
            newestSeenDate = saved.getLong("newestDate", Long.MIN_VALUE),
            newestSeenId = saved.getLong("newestId", Long.MIN_VALUE),
            pendingGuids = saved.getStringArrayList("pending").orEmpty().toSet(),
            consumedLiveGuids = saved.getStringArrayList("consumed").orEmpty().toSet(),
        )
    },
)

internal val LiveArrivalMarkerStateSaver = Saver<LiveArrivalMarkerState, Bundle>(
    save = { state ->
        Bundle().apply {
            putStringArrayList(
                "unmatched",
                ArrayList(state.unmatchedGuids.toList().takeLast(LiveMarkerRetention)),
            )
            putInt("overflowCount", state.overflowCount)
        }
    },
    restore = { saved ->
        LiveArrivalMarkerState(
            unmatchedGuids = saved.getStringArrayList("unmatched").orEmpty().toSet(),
            overflowCount = saved.getInt("overflowCount"),
        )
    },
)

internal val DeferredLiveArrivalStateSaver = Saver<DeferredLiveArrivalState, Bundle>(
    save = { state ->
        Bundle().apply {
            putLongArray("chatIds", state.arrivals.map { it.chatId }.toLongArray())
            putStringArrayList("guids", ArrayList(state.arrivals.map { it.messageGuid }))
        }
    },
    restore = { saved ->
        val chatIds = saved.getLongArray("chatIds") ?: longArrayOf()
        val guids = saved.getStringArrayList("guids").orEmpty()
        DeferredLiveArrivalState(
            arrivals = chatIds.indices.mapNotNull { index ->
                guids.getOrNull(index)?.let { guid -> DeferredLiveArrival(chatIds[index], guid) }
            },
        )
    },
)

private const val SavedGuidLimit = 512

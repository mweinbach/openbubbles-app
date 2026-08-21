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
            putBoolean("fallback", state.chronologicalFallback)
        }
    },
    restore = { saved ->
        LiveArrivalMarkerState(
            unmatchedGuids = saved.getStringArrayList("unmatched").orEmpty().toSet(),
            chronologicalFallback = saved.getBoolean("fallback"),
        )
    },
)

private const val SavedGuidLimit = 512

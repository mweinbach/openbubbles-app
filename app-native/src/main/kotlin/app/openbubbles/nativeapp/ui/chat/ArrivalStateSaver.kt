package app.openbubbles.nativeapp.ui.chat

import android.os.Bundle
import androidx.compose.runtime.saveable.Saver

/** Keeps a viewport's pending/consumed arrival identity across activity recreation. */
internal val ArrivalStateSaver = Saver<ArrivalState, Bundle>(
    save = { state ->
        Bundle().apply {
            putBoolean("initialized", state.initialized)
            putStringArrayList("known", ArrayList(state.knownGuids))
            putLong("newestDate", state.newestSeenDate)
            putLong("newestId", state.newestSeenId)
            putStringArrayList("pending", ArrayList(state.pendingGuids))
            putStringArrayList("consumed", ArrayList(state.consumedLiveGuids))
        }
    },
    restore = { saved ->
        ArrivalState(
            initialized = saved.getBoolean("initialized"),
            knownGuids = saved.getStringArrayList("known").orEmpty().toSet(),
            newestSeenDate = saved.getLong("newestDate", Long.MIN_VALUE),
            newestSeenId = saved.getLong("newestId", Long.MIN_VALUE),
            pendingGuids = saved.getStringArrayList("pending").orEmpty().toSet(),
            consumedLiveGuids = saved.getStringArrayList("consumed").orEmpty().toSet(),
        )
    },
)

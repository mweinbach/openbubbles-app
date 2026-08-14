package app.openbubbles.nativeapp.service

import android.content.Context
import android.util.Log
import uniffi.rust_lib_bluebubbles.UPushMessage

/**
 * Routes FaceTime pushes. The ported call UI (FaceTimeActivity) and
 * notification pipeline are ready, but batch 1 of the UniFFI surface
 * collapsed the FaceTime payload to a Debug string — driving the call UI
 * reliably needs the typed UFtMessage surface (the batch that was queued
 * when the subagent rate-limit hit). Until then: log + no-op so pushes are
 * visibly routed but nothing half-works.
 */
object FaceTimeDispatch {
    private const val TAG = "FaceTimeDispatch"

    fun onPushMessage(context: Context, msg: UPushMessage): Boolean {
        return when (msg) {
            is UPushMessage.FaceTime -> {
                Log.i(TAG, "FaceTime push received (${msg.debug.take(120)}) — typed surface pending")
                true // consumed: not a chat message
            }
            else -> false
        }
    }
}

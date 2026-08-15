package app.openbubbles.nativeapp.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sun.jna.NativeLibrary

/** Debug-only ADB entry point for an account-free Apple NAC round trip. */
class NacRoundTripReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val pending = goAsync()
        Thread({
            val result = runCatching {
                NativeLibrary.getInstance("rust_lib_bluebubbles")
                    .getFunction("openbubbles_debug_nac_round_trip")
                    .invokeInt(emptyArray())
            }.getOrElse { error ->
                Log.e(TAG, "round trip invocation failed", error)
                -4
            }
            Log.i(TAG, "validation-length=$result")
            pending.resultCode = result
            pending.finish()
        }, "nac-round-trip").start()
    }

    companion object {
        private const val ACTION = "app.openbubbles.DEBUG_NAC_ROUND_TRIP"
        private const val TAG = "NacRoundTrip"
    }
}

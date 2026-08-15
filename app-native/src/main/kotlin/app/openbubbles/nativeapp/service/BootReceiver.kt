package app.openbubbles.nativeapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the push service after boot / package update — the Rust loop
 * holds the APNs connection, so the process must come back with the phone.
 * Only starts when registered IDS users exist; hardware provisioning alone
 * is not a signed-in state. Otherwise the login flow starts the service.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (!uniffi.rust_lib_bluebubbles.hasSavedUsers(context.filesDir.absolutePath)) return
        if (BatterySaver.isEnabled(context)) {
            BatterySaver.schedule(context)
        } else if (!NativePushService.start(context)) {
            BatterySaver.schedulePushRestart(context)
        }
    }
}

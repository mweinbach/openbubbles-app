package app.openbubbles.nativeapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Restarts the push service after boot / package update — the Rust loop
 * holds the APNs connection, so the process must come back with the phone.
 * Only starts when hardware provisioning exists (a signed-in account implies
 * it); otherwise the login flow starts the service.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        if (!uniffi.rust_lib_bluebubbles.hasHardwareConfig(context.filesDir.absolutePath)) return
        if (BatterySaver.isEnabled(context)) {
            BatterySaver.schedule(context)
        } else {
            NativePushService.start(context)
        }
    }
}

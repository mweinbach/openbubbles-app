package app.openbubbles.nativeapp.data

import app.openbubbles.nativeapp.service.SimpleFilePackager
import com.bluebubbles.messaging.services.rustpush.AndroidNativeKeystore
import java.util.concurrent.atomic.AtomicBoolean
import uniffi.rust_lib_bluebubbles.HandleWifiNetworksCallback
import uniffi.rust_lib_bluebubbles.start
import uniffi.rust_lib_bluebubbles.setupKeystore
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized

/**
 * Process-wide, one-shot Rust runtime boot: uniffi init, state directories
 * (`start`), and the keystore backing the Rust `GLOBAL`
 * (`rustpush::keystore::keystore()`). Must run before ANY Rust call that
 * touches the keystore — provisioning (`new_ngm_identity`) and login both
 * do, and both can run from the activity (onboarding) before the push
 * service ever starts. Idempotent: first caller wins, later calls no-op.
 */
object RustBoot {

    private val started = AtomicBoolean(false)

    fun ensureStarted(context: android.content.Context, dir: String) {
        if (!started.compareAndSet(false, true)) return
        uniffiEnsureInitialized()
        start(dir, SimpleFilePackager(), BootWifiCallback())
        setupKeystore(dir, AndroidNativeKeystore(context.applicationContext))
    }

    private class BootWifiCallback : HandleWifiNetworksCallback {
        override fun handleWifiNetworks(networks: Map<String, String>, userApprove: Boolean) {
            // Wi-Fi suggestions UI is out of scope.
        }
    }
}

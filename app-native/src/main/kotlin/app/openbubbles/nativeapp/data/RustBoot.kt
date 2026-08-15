package app.openbubbles.nativeapp.data

import android.util.Log
import app.openbubbles.nativeapp.service.SimpleFilePackager
import com.bluebubbles.messaging.services.rustpush.AndroidNativeKeystore
import java.io.File
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

    private val startLock = java.lang.Object()

    @Volatile
    private var started = false

    private var starting = false

    fun ensureStarted(context: android.content.Context, dir: String) {
        synchronized(startLock) {
            while (starting) startLock.wait()
            if (started) return
            starting = true
        }
        try {
            purgeLegacyAuthLogs(context, File(dir))
            uniffiEnsureInitialized()
            start(dir, SimpleFilePackager(), BootWifiCallback())
            setupKeystore(dir, AndroidNativeKeystore(context.applicationContext))
        } catch (error: Throwable) {
            synchronized(startLock) {
                starting = false
                startLock.notifyAll()
            }
            throw error
        }
        synchronized(startLock) {
            started = true
            starting = false
            startLock.notifyAll()
        }
    }

    /**
     * Earlier debug builds wrote Apple's decrypted account-service payload to
     * the private Rust log. Current Rust logging is sanitized, so remove only
     * those legacy Rust log files once, before the logger opens them.
     */
    private fun purgeLegacyAuthLogs(context: android.content.Context, filesRoot: File) {
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_LEGACY_AUTH_LOGS_REMOVED, false)) return
        val removed = deleteLegacyRustLogs(filesRoot)
        if (legacyRustLogs(filesRoot).isEmpty()) {
            prefs.edit().putBoolean(KEY_LEGACY_AUTH_LOGS_REMOVED, true).apply()
        }
        if (removed > 0) Log.i(TAG, "removed $removed legacy Rust auth log file(s)")
    }

    private class BootWifiCallback : HandleWifiNetworksCallback {
        override fun handleWifiNetworks(networks: Map<String, String>, userApprove: Boolean) {
            // Wi-Fi suggestions UI is out of scope.
        }
    }

    private const val PREFS = "native_setup"
    private const val KEY_LEGACY_AUTH_LOGS_REMOVED = "legacy_auth_logs_removed_v1"
    private const val TAG = "RustBoot"
}

private val LEGACY_RUST_LOG_NAME = Regex("""rs_r(?:CURRENT|\d+)\.log""")

internal fun legacyRustLogs(filesRoot: File): List<File> =
    File(filesRoot, "logs").listFiles()
        ?.filter { it.isFile && LEGACY_RUST_LOG_NAME.matches(it.name) }
        .orEmpty()

internal fun deleteLegacyRustLogs(filesRoot: File): Int =
    legacyRustLogs(filesRoot).count { it.delete() }

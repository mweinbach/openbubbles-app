package app.openbubbles.nativeapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.DeviceContacts
import app.openbubbles.nativeapp.data.OfficialEngineProbe
import app.openbubbles.nativeapp.ui.OpenBubblesApp
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.shared.Hello
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.isLocked
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized

class NativeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContext = applicationContext

        // Notification deep link: only a fresh launch carries a new tap
        // (config-change recreations would re-fire the original intent and
        // re-trigger navigation; process death still works via the launch
        // intent extras).
        if (savedInstanceState == null) readPendingChatGuid(intent)

        // Boot the Rust runtime (state dirs + keystore) before any UI can
        // provision or sign in — onboarding reaches Rust before the push
        // service ever starts.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { app.openbubbles.nativeapp.data.RustBoot.ensureStarted(this@NativeMainActivity, filesDir.absolutePath) }
                .onFailure { android.util.Log.e("RustBoot", "boot failed", it) }
        }

        // Permission priming moved into the onboarding flow; returning users
        // (or taps into the signed-in app) just get a contact re-sync.
        if (DeviceContacts.hasPermission(this)) {
            syncContacts()
        }

        // Smoke test: load librust_lib_bluebubbles.so (built by cargokit from
        // the same Rust crate the Flutter app uses) and call through UniFFI.
        // Shown as a small status footer on the chat list.
        val rustStatus = try {
            uniffiEnsureInitialized()
            "uniffi ok — isLocked=${isLocked()}"
        } catch (t: Throwable) {
            "rust load failed: ${t.message}"
        }

        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                android.util.Log.i("OfficialEngineProbe", OfficialEngineProbe.probe().summary())
            }
        }

        setContent {
            OpenBubblesTheme {
                OpenBubblesApp(
                    debugLines = listOf(Hello.greeting(), rustStatus),
                    startChatGuid = pendingChatGuid,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Notification tap while the activity is alive (launchMode=singleTask
        // routes it here instead of stacking a second instance).
        readPendingChatGuid(intent)
    }

    private fun readPendingChatGuid(intent: Intent?) {
        pendingChatGuid = intent?.getStringExtra(EXTRA_CHAT_GUID)?.takeIf { it.isNotBlank() }
    }

    private fun syncContacts() {
        lifecycleScope.launch {
            val raw = DeviceContacts.read(this@NativeMainActivity)
            CoreGraph.syncContacts(raw)
        }
    }

    companion object {
        /** Deep-link extra carrying the tapped notification's chat guid. */
        const val EXTRA_CHAT_GUID = "chat_guid"

        /** Application context for the composition root (set in onCreate). */
        @Volatile
        var appContext: android.content.Context? = null

        /**
         * Chat guid requested by a notification tap, consumed once by
         * [OpenBubblesApp] (which resolves it, navigates, and nulls it).
         * Compose state, so an onNewIntent tap recomposes the scaffold.
         */
        var pendingChatGuid: String? by mutableStateOf<String?>(null)
    }
}

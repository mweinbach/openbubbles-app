package app.openbubbles.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.openbubbles.nativeapp.ui.OpenBubblesApp
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.shared.Hello
import uniffi.rust_lib_bluebubbles.isLocked
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized

class NativeMainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContext = applicationContext

        // Smoke test: load librust_lib_bluebubbles.so (built by cargokit from
        // the same Rust crate the Flutter app uses) and call through UniFFI.
        // Shown as a small status footer on the chat list.
        val rustStatus = try {
            uniffiEnsureInitialized()
            "uniffi ok — isLocked=${isLocked()}"
        } catch (t: Throwable) {
            "rust load failed: ${t.message}"
        }

        setContent {
            OpenBubblesTheme {
                OpenBubblesApp(debugLines = listOf(Hello.greeting(), rustStatus))
            }
        }
    }

    companion object {
        /** Application context for the composition root (set in onCreate). */
        @Volatile
        var appContext: android.content.Context? = null
    }
}

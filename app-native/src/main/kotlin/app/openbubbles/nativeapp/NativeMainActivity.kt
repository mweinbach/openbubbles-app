package app.openbubbles.nativeapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.DeviceContacts
import app.openbubbles.nativeapp.ui.OpenBubblesApp
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.shared.Hello
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.isLocked
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized

class NativeMainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            if (grants[Manifest.permission.READ_CONTACTS] == true) {
                syncContacts()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appContext = applicationContext

        val wanted = buildList {
            if (Build.VERSION.SDK_INT >= 33 &&
                notGranted(Manifest.permission.POST_NOTIFICATIONS)
            ) add(Manifest.permission.POST_NOTIFICATIONS)
            if (notGranted(Manifest.permission.READ_CONTACTS)) add(Manifest.permission.READ_CONTACTS)
        }
        if (wanted.isNotEmpty()) {
            permissionLauncher.launch(wanted.toTypedArray())
        } else if (DeviceContacts.hasPermission(this)) {
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

        setContent {
            OpenBubblesTheme {
                OpenBubblesApp(debugLines = listOf(Hello.greeting(), rustStatus))
            }
        }
    }

    private fun notGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED

    private fun syncContacts() {
        lifecycleScope.launch {
            val raw = DeviceContacts.read(this@NativeMainActivity)
            CoreGraph.syncContacts(raw)
        }
    }

    companion object {
        /** Application context for the composition root (set in onCreate). */
        @Volatile
        var appContext: android.content.Context? = null
    }
}

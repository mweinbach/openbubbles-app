package app.openbubbles.nativeapp.update

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Foreground target of the "Update ready" notification. Notifications must
 * launch activities (Android 12+ discards activity starts relayed through a
 * receiver or service — the "notification trampoline" restriction), and the
 * PackageInstaller confirmation for each self-update can only be shown
 * while this app is visible. The activity therefore stays in the foreground
 * while the session is committed and finishes when the pipeline reports a
 * terminal result; `noHistory` in the manifest tears it down whenever
 * something else (the system confirmation, home) covers it.
 */
class UpdateInstallActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OpenBubblesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(
                            16.dp,
                            Alignment.CenterVertically,
                        ),
                    ) {
                        LoadingIndicator()
                        Text(
                            "Installing update…",
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
        // Success restarts the process and failure posts its own status
        // notification; either way this screen is done.
        lifecycleScope.launch {
            UpdateCoordinator.installFinishedEvents.first()
            finish()
        }
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                UpdateCoordinator.installNow(this@UpdateInstallActivity)
            }
            when (result) {
                UpdateCoordinator.InstallNowResult.NothingPending -> finish()
                UpdateCoordinator.InstallNowResult.NeedsUnknownSourcesPermission -> {
                    Toast.makeText(
                        this@UpdateInstallActivity,
                        "Allow \"Install unknown apps\" for OpenGarden, then tap the update again",
                        Toast.LENGTH_LONG,
                    ).show()
                    runCatching {
                        startActivity(ApkInstaller.unknownSourcesIntent(this@UpdateInstallActivity))
                    }
                    finish()
                }
                is UpdateCoordinator.InstallNowResult.Failed -> {
                    Toast.makeText(this@UpdateInstallActivity, result.message, Toast.LENGTH_LONG).show()
                    finish()
                }
                UpdateCoordinator.InstallNowResult.Installing -> Unit // wait for the terminal event
            }
        }
    }
}

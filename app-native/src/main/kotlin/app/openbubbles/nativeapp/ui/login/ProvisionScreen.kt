package app.openbubbles.nativeapp.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.UHwExtra
import uniffi.rust_lib_bluebubbles.hasHardwareConfig
import uniffi.rust_lib_bluebubbles.provisionFromRelay
import uniffi.rust_lib_bluebubbles.provisionFromValidationData
import java.util.UUID

/**
 * One-time hardware provisioning (writes hw_info.plist) — the native
 * counterpart of the Flutter app's hw_inp setup page. Required before the
 * Apple ID login can connect: Apple's activation demands Mac-derived
 * validation data, either extracted from a real Mac (paste) or from a
 * hosted relay slot.
 */
@Composable
fun ProvisionScreen(
    confDir: String,
    onProvisioned: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var mode by remember { mutableStateOf<ProvisionMode?>(ProvisionMode.Paste) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var validationData by remember { mutableStateOf("") }
    var relayCode by remember { mutableStateOf("") }
    var relayHost by remember { mutableStateOf(DEFAULT_RELAY_HOST) }
    var relayToken by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Device setup", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Apple requires Mac-derived validation data once, before this " +
                "device can register with iMessage. Provide it from a real Mac " +
                "or a relay slot.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val selected = mode
        if (selected == ProvisionMode.Paste) {
            OutlinedTextField(
                value = validationData,
                onValueChange = { validationData = it },
                label = { Text("Validation data (hex or base64)") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Button(
                enabled = !busy && validationData.isNotBlank(),
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        val result: Result<Unit> = withContext(Dispatchers.IO) {
                            val bytes = decodeBlob(validationData)
                            if (bytes == null) {
                                Result.failure(IllegalArgumentException("not valid hex or base64"))
                            } else {
                                runCatching {
                                    provisionFromValidationData(
                                        dir = confDir,
                                        data = bytes,
                                        extra = defaultHwExtra(),
                                    )
                                }
                            }
                        }
                        busy = false
                        result.fold(
                            onSuccess = { onProvisioned() },
                            onFailure = { failure -> error = failure.message },
                        )
                    }
                },
            ) { Text("Use validation data") }
            TextButton(onClick = { mode = ProvisionMode.Relay }) { Text("Use a relay slot instead") }
        } else {
            OutlinedTextField(
                value = relayCode,
                onValueChange = { relayCode = it },
                label = { Text("Relay code") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = relayHost,
                onValueChange = { relayHost = it },
                label = { Text("Relay host") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                ),
            )
            OutlinedTextField(
                value = relayToken,
                onValueChange = { relayToken = it },
                label = { Text("Access token (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                enabled = !busy && relayCode.isNotBlank() && relayHost.isNotBlank(),
                onClick = {
                    busy = true; error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            runCatching {
                                provisionFromRelay(
                                    dir = confDir,
                                    code = relayCode,
                                    host = relayHost,
                                    token = relayToken.ifBlank { null },
                                )
                            }
                        }
                        busy = false
                        result.fold(
                            onSuccess = { onProvisioned() },
                            onFailure = { failure -> error = failure.message },
                        )
                    }
                },
            ) { Text("Connect relay") }
            TextButton(onClick = { mode = ProvisionMode.Paste }) { Text("Paste validation data instead") }
        }

        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onBack) { Text("Back") }
    }
}

private enum class ProvisionMode { Paste, Relay }

private const val DEFAULT_RELAY_HOST = "https://hw.openbubbles.app"

/** Same defaults the Flutter app's hardware-input page used. */
private fun defaultHwExtra() = UHwExtra(
    version = "13.6.4",
    protocolVersion = 1660u,
    deviceId = UUID.randomUUID().toString(),
    icloudUa = "com.apple.iCloudHelper/282 CFNetwork/1408.0.4 Darwin/22.5.0",
    aoskitVersion = "com.apple.AOSKit/282 (com.apple.accountsd/113)",
)

private fun decodeBlob(text: String): ByteArray? {
    val cleaned = text.trim().replace("\\s".toRegex(), "")
    hexToBytes(cleaned)?.let { return it }
    return runCatching { android.util.Base64.decode(cleaned, android.util.Base64.DEFAULT) }
        .getOrNull()
}

private fun hexToBytes(hex: String): ByteArray? {
    if (hex.length % 2 != 0 || hex.isEmpty()) return null
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val b = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
        out[i] = b.toByte()
    }
    return out
}

/** Provisioned gate: true when hw_info.plist exists for [confDir]. */
suspend fun isProvisioned(confDir: String): Boolean =
    withContext(Dispatchers.IO) { runCatching { hasHardwareConfig(confDir) }.getOrDefault(false) }

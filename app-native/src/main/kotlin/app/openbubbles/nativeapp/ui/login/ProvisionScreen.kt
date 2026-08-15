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
import uniffi.rust_lib_bluebubbles.hasHardwareConfig
import uniffi.rust_lib_bluebubbles.provisionFromRelay
import java.util.Base64

/**
 * One-time hardware provisioning (writes hw_info.plist) — the native
 * counterpart of the Flutter app's hw_inp setup page. The public build uses
 * a hosted relay slot because the repository only contains the nonfunctional
 * OpenAbsinthe placeholder for processing raw Mac validation data.
 */
@Composable
fun ProvisionScreen(
    confDir: String,
    onProvisioned: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }

    var relayCode by remember { mutableStateOf("") }
    var relayHost by remember { mutableStateOf(DEFAULT_RELAY_HOST) }
    var relayToken by remember { mutableStateOf("") }

    /** QR result: relay URLs/codes prefill; raw `OABS` payloads are rejected. */
    fun handleScan(bytes: ByteArray?, text: String?) {
        when (val input = classifyProvisioningInput(bytes, text, relayHost)) {
            ProvisioningInput.Invalid -> {
                error = "Couldn't read a relay activation code from that QR code."
            }
            ProvisioningInput.UnsupportedRaw -> {
                error = "Raw Mac pairing data cannot finish sign-in in this build. Use a relay activation code instead."
            }
            is ProvisioningInput.Relay -> {
                relayCode = input.code
                relayHost = input.host
                error = null
            }
        }
    }

    if (scanning) {
        QrScannerSheet(
            onResult = { bytes, text ->
                scanning = false
                handleScan(bytes, text)
            },
            onClose = { scanning = false },
        )
        return
    }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Device setup", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Apple requires Mac-derived validation data before this device " +
                "can register with iMessage. Enter a relay " +
                "activation code (Settings → Share Activation Code in your " +
                "other OpenBubbles install); the relay completes validation " +
                "server-side without sending your Apple ID password.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

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
        TextButton(onClick = { scanning = true }) { Text("Scan relay QR / URL instead") }

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

private const val DEFAULT_RELAY_HOST = "https://hw.openbubbles.app"

internal sealed interface ProvisioningInput {
    data class Relay(val code: String, val host: String) : ProvisioningInput
    data object UnsupportedRaw : ProvisioningInput
    data object Invalid : ProvisioningInput
}

internal fun classifyProvisioningInput(
    bytes: ByteArray?,
    text: String?,
    currentHost: String = DEFAULT_RELAY_HOST,
): ProvisioningInput {
    val payload = bytes ?: text?.toByteArray(Charsets.UTF_8)
    if (payload != null && payload.size > 5 &&
        String(payload.copyOfRange(0, 4), Charsets.US_ASCII) == "OABS"
    ) {
        return ProvisioningInput.UnsupportedRaw
    }

    val value = text?.trim().orEmpty()
    if (value.isEmpty()) return ProvisioningInput.Invalid
    if (value.startsWith("http://") || value.startsWith("https://")) {
        val url = runCatching { java.net.URI(value) }.getOrNull()
            ?: return ProvisioningInput.Invalid
        val hostName = url.host ?: return ProvisioningInput.Invalid
        val code = url.path.trim('/').split('/').lastOrNull { it.isNotBlank() }
            ?: return ProvisioningInput.Invalid
        val host = "${url.scheme}://$hostName${if (url.port != -1) ":${url.port}" else ""}"
        return ProvisioningInput.Relay(code, host)
    }
    if ((decodeBlob(value)?.size ?: 0) >= 256) return ProvisioningInput.UnsupportedRaw
    return ProvisioningInput.Relay(value, currentHost)
}

private fun decodeBlob(text: String): ByteArray? {
    val cleaned = text.trim().replace("\\s".toRegex(), "")
    hexToBytes(cleaned)?.let { return it }
    return sequenceOf(Base64.getDecoder(), Base64.getMimeDecoder(), Base64.getUrlDecoder())
        .mapNotNull { decoder -> runCatching { decoder.decode(cleaned) }.getOrNull() }
        .firstOrNull()
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

/** Provisioned gate: true only for relay-backed hardware state supported here. */
suspend fun isProvisioned(confDir: String): Boolean =
    withContext(Dispatchers.IO) { runCatching { hasHardwareConfig(confDir) }.getOrDefault(false) }

package app.openbubbles.nativeapp.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.UHwExtra
import uniffi.rust_lib_bluebubbles.hasHardwareConfig
import uniffi.rust_lib_bluebubbles.provisionFromEncoded
import uniffi.rust_lib_bluebubbles.provisionFromValidationData
import java.util.Base64
import java.util.UUID

/**
 * One-time self-hosted hardware provisioning. A Mac exports either a complete
 * `OABS` hardware payload or raw validation data; the resulting Mac config is
 * stored locally so OpenAbsinthe can generate future registration data on this
 * Android device without an OpenBubbles-hosted relay.
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
    var activationPayload by remember { mutableStateOf("") }

    fun provision(input: ProvisioningInput) {
        if (input is ProvisioningInput.Invalid) {
            error = "Use the OABS QR code or activation payload exported by your Mac."
            return
        }
        busy = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    when (input) {
                        is ProvisioningInput.Encoded -> provisionFromEncoded(
                            dir = confDir,
                            encoded = input.payload,
                        )
                        is ProvisioningInput.ValidationData -> provisionFromValidationData(
                            dir = confDir,
                            data = input.payload,
                            extra = defaultHwExtra(),
                        )
                        ProvisioningInput.Invalid -> error("invalid activation payload")
                    }
                }
            }
            busy = false
            result.fold(
                onSuccess = { onProvisioned() },
                onFailure = { failure ->
                    error = failure.message ?: "Local hardware provisioning failed."
                },
            )
        }
    }

    if (scanning) {
        QrScannerSheet(
            onResult = { bytes, text ->
                scanning = false
                provision(classifyProvisioningInput(bytes, text))
            },
            onClose = { scanning = false },
        )
        return
    }

    // Shown both standalone (full-screen route) and embedded in onboarding;
    // the safeDrawing/IME padding is a no-op where a parent already consumed
    // the insets, and essential where it hasn't.
    Column(
        modifier = modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Self-hosted device setup", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Scan the hardware QR code generated on your Mac, or paste its " +
                    "exported activation payload. After this one-time transfer, " +
                    "Apple validation runs locally on this Android device; the " +
                    "OpenBubbles hosted relay is not used.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "This build includes the version-pinned on-device validation engine. " +
                    "If its compatibility check fails, setup stops instead of " +
                    "sending hardware data to a hosted relay.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Button(
                enabled = !busy,
                onClick = { scanning = true },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scan Mac hardware QR code")
            }
            OutlinedTextField(
                value = activationPayload,
                onValueChange = { activationPayload = it },
                label = { Text("Mac activation payload") },
                supportingText = { Text("OABS base64 or 517-byte validation data") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
            )
            Button(
                enabled = !busy && activationPayload.isNotBlank(),
                onClick = {
                    provision(classifyProvisioningInput(bytes = null, text = activationPayload))
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use local Mac hardware")
            }

            // Indeterminate provisioning is exactly the wavy indicator's job.
            if (busy) LinearWavyProgressIndicator(Modifier.fillMaxWidth())
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
}

internal sealed interface ProvisioningInput {
    data class Encoded(val payload: ByteArray) : ProvisioningInput
    data class ValidationData(val payload: ByteArray) : ProvisioningInput
    data object Invalid : ProvisioningInput
}

internal fun classifyProvisioningInput(
    bytes: ByteArray?,
    text: String?,
): ProvisioningInput {
    parseProvisioningBytes(bytes)?.let { return it }

    val value = text?.trim().orEmpty()
    if (value.isEmpty()) return ProvisioningInput.Invalid
    return parseProvisioningBytes(decodeBlob(value)) ?: ProvisioningInput.Invalid
}

private fun parseProvisioningBytes(payload: ByteArray?): ProvisioningInput? {
    if (payload == null) return null
    if (payload.size > OABS_HEADER_SIZE &&
        String(payload.copyOfRange(0, 4), Charsets.US_ASCII) == OABS_MAGIC
    ) {
        return ProvisioningInput.Encoded(payload.copyOfRange(OABS_HEADER_SIZE, payload.size))
    }
    if (payload.size == VALIDATION_DATA_SIZE && payload.firstOrNull() == 0x02.toByte()) {
        return ProvisioningInput.ValidationData(payload)
    }
    return null
}

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
    return sequenceOf(Base64.getDecoder(), Base64.getMimeDecoder(), Base64.getUrlDecoder())
        .mapNotNull { decoder -> runCatching { decoder.decode(cleaned) }.getOrNull() }
        .firstOrNull()
}

private fun hexToBytes(hex: String): ByteArray? {
    if (hex.length % 2 != 0 || hex.isEmpty()) return null
    val out = ByteArray(hex.length / 2)
    for (i in out.indices) {
        val byte = hex.substring(i * 2, i * 2 + 2).toIntOrNull(16) ?: return null
        out[i] = byte.toByte()
    }
    return out
}

private const val OABS_MAGIC = "OABS"
private const val OABS_HEADER_SIZE = 5
private const val VALIDATION_DATA_SIZE = 517

/** Provisioned gate: true when a local hardware config exists for [confDir]. */
suspend fun isProvisioned(confDir: String): Boolean =
    withContext(Dispatchers.IO) { runCatching { hasHardwareConfig(confDir) }.getOrDefault(false) }

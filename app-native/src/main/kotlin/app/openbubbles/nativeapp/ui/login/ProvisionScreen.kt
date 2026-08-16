package app.openbubbles.nativeapp.ui.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
    showBackAction: Boolean = true,
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
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
        ) {
            // Standalone routes have no parent header; the onboarding connect
            // step supplies its own, so this title only shows when needed.
            if (showBackAction) {
                Text("Set up Mac hardware", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(4.dp))
            }
            Text(
                "Scan the QR code from your Mac, or paste its activation payload. " +
                    "This is a one-time transfer — after this, Apple validation runs " +
                    "on this device and the hosted relay is not used.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            Button(
                enabled = !busy,
                onClick = { scanning = true },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Scan QR code")
            }
            Spacer(Modifier.height(20.dp))

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(6.dp))
            Text(
                "Or paste the payload",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = activationPayload,
                onValueChange = { activationPayload = it },
                label = { Text("Mac activation payload") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                enabled = !busy && activationPayload.isNotBlank(),
                onClick = {
                    provision(classifyProvisioningInput(bytes = null, text = activationPayload))
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use payload")
            }

            // Indeterminate provisioning is exactly the wavy indicator's job.
            if (busy) {
                Spacer(Modifier.height(16.dp))
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (showBackAction) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBack) { Text("Back") }
            }
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

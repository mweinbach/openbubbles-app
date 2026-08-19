package app.openbubbles.nativeapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

internal fun escrowRecoveryFailure(message: String?): String {
    val detail = message.orEmpty()
    return if (
        detail.contains("unimplemented escrow format 1", ignoreCase = true) ||
        detail.contains("legacy escrow", ignoreCase = true)
    ) {
        "Apple only returned a legacy recovery record that OpenGarden cannot use. " +
            "Use nearby-device approval instead; encrypted iCloud data was not reset."
    } else {
        detail.ifEmpty { "Unable to fetch trusted devices" }
    }
}

@Composable
internal fun ICloudPasswordsSetupMethodDialog(
    onNearbyApproval: () -> Unit,
    onDevicePasscode: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set up iCloud Passwords") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Join your Apple account's end-to-end encrypted trust circle without resetting existing data.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SettingsActionItem(
                    title = "Approve from a nearby device",
                    supporting = "Use a signed-in iPhone, iPad, or Mac. Keep Bluetooth on and the devices close together.",
                    onClick = onNearbyApproval,
                    index = 0,
                    count = 2,
                    multiline = true,
                    icon = Icons.Filled.Bluetooth,
                )
                SettingsActionItem(
                    title = "Use a trusted device passcode",
                    supporting = "Recover from a current iCloud escrow record using that device's passcode.",
                    onClick = onDevicePasscode,
                    index = 1,
                    count = 2,
                    multiline = true,
                    icon = Icons.Filled.Restore,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
internal fun NearbyICloudApprovalDialog(
    starting: Boolean,
    completing: Boolean,
    sessionActive: Boolean,
    approvalCode: String,
    error: String?,
    onApprovalCodeChange: (String) -> Unit,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val approvalCodeValid = approvalCode.length == 6 && approvalCode.all(Char::isDigit)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Approve from a nearby device") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    starting -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                            Text(
                                "Starting secure approval…",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                    sessionActive -> {
                        Text(
                            "On a signed-in Apple device, approve the OpenGarden sign-in request. " +
                                "Keep both devices nearby with Bluetooth on, then enter the six-digit code it shows.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "Do not leave this screen until Apple confirms the approval.",
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        OutlinedTextField(
                            value = approvalCode,
                            onValueChange = { value ->
                                onApprovalCodeChange(value.filter(Char::isDigit).take(6))
                            },
                            label = { Text("Approval code") },
                            supportingText = { Text("Six digits shown on the trusted Apple device") },
                            enabled = !completing,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    else -> {
                        Text(
                            "Nearby approval is not active. Check Bluetooth and try again.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                error?.let {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = if (sessionActive) onComplete else onStart,
                enabled = !starting && !completing && (!sessionActive || approvalCodeValid),
            ) {
                if (starting || completing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                } else if (sessionActive) {
                    Text("Approve and sync")
                } else {
                    Text("Try again")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !starting && !completing,
            ) { Text("Cancel") }
        },
    )
}

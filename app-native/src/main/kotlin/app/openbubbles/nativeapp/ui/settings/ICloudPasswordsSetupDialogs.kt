package app.openbubbles.nativeapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.data.ICloudKeychainEnrollment

internal fun escrowRecoveryFailure(message: String?): String =
    ICloudKeychainEnrollment.escrowRecoveryFailure(message)

/**
 * Method chooser for joining the trust circle. Nearby-device approval is
 * withheld while [ICloudKeychainEnrollment.NEARBY_APPROVAL_ENABLED] is off,
 * which leaves the trusted-device passcode as the only offered method — the
 * dialog is then pure explanation before that single action.
 */
@Composable
internal fun ICloudPasswordsSetupMethodDialog(
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
                    title = "Use a trusted device passcode",
                    supporting = "Recover from a current iCloud escrow record using that device's passcode.",
                    onClick = onDevicePasscode,
                    index = 0,
                    count = 1,
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

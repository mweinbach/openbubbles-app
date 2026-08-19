package app.openbubbles.nativeapp.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.openbubbles.core.contacts.ConflictDecision
import app.openbubbles.core.contacts.ContactConflict
import app.openbubbles.nativeapp.data.contacts.ContactDeviceSync
import app.openbubbles.nativeapp.data.contacts.DeviceContactWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Opt-in and conflict review for the "save iCloud contacts to phone"
 * mirror. Kept out of SettingsScreen so the in-flight settings revamp only
 * has one row of wiring to carry.
 */
@Composable
internal fun ContactsToPhoneDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember { mutableStateOf(false) }
    var conflicts by remember { mutableStateOf(emptyList<ContactConflict>()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            enabled = ContactDeviceSync.isEnabled(context)
            conflicts = ContactDeviceSync.pendingConflicts(context)
        }
    }

    // Flip the switch immediately; prefs, WorkManager, and the provider
    // pass are binder work that would stall the tap frame if run inline.
    fun enableAndSync() {
        enabled = true
        scope.launch(Dispatchers.IO) {
            ContactDeviceSync.setEnabled(context, true)
            runCatching { ContactDeviceSync.syncNow(context) }
            conflicts = ContactDeviceSync.pendingConflicts(context)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) enableAndSync()
    }

    fun onToggle(wanted: Boolean) {
        when {
            !wanted -> {
                enabled = false
                scope.launch(Dispatchers.IO) { ContactDeviceSync.setEnabled(context, false) }
            }
            DeviceContactWriter.hasPermission(context) -> enableAndSync()
            else -> permissionLauncher.launch(Manifest.permission.WRITE_CONTACTS)
        }
    }

    fun decide(conflict: ContactConflict, decision: ConflictDecision) {
        conflicts = conflicts.filterNot { it.icloudId == conflict.icloudId }
        scope.launch(Dispatchers.IO) {
            ContactDeviceSync.recordDecision(context, conflict.icloudId, decision)
            if (decision == ConflictDecision.USE_ICLOUD) {
                runCatching { ContactDeviceSync.syncNow(context) }
            }
            conflicts = ContactDeviceSync.pendingConflicts(context)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Contacts on this phone") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Saved contacts appear in every app on this phone and merge " +
                        "with matching contacts from other accounts.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SettingsGroup {
                    SettingsToggleItem(
                        title = "Save iCloud contacts to phone",
                        supporting = "Keep them updated in the background; iCloud stays the source of truth",
                        checked = enabled,
                        onCheckedChange = ::onToggle,
                        index = 0,
                        count = 1,
                    )
                }
                if (enabled && conflicts.isNotEmpty()) {
                    Text(
                        text = "This phone disagrees with iCloud",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    conflicts.forEach { conflict ->
                        ConflictRow(
                            conflict = conflict,
                            onUseICloud = { decide(conflict, ConflictDecision.USE_ICLOUD) },
                            onKeepPhone = { decide(conflict, ConflictDecision.KEEP_PHONE) },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun ConflictRow(
    conflict: ContactConflict,
    onUseICloud: () -> Unit,
    onKeepPhone: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = conflict.icloudName ?: conflict.deviceName ?: "Unnamed contact",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "iCloud: ${describe(conflict.icloudName, conflict.icloudNumbers)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Phone: ${describe(conflict.deviceName, conflict.deviceNumbers)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = onKeepPhone) { Text("Keep phone version") }
            TextButton(onClick = onUseICloud) { Text("Use iCloud info") }
        }
    }
}

private fun describe(name: String?, numbers: List<String>): String =
    listOfNotNull(
        name?.takeIf(String::isNotBlank),
        numbers.joinToString(", ").takeIf(String::isNotBlank),
    ).joinToString(" · ").ifBlank { "No details" }

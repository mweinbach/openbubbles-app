package app.openbubbles.nativeapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.common.formatRelativePast
import app.openbubbles.nativeapp.update.UpdateCoordinator

/**
 * The update center: current version, last-check bookkeeping, and a manual
 * check on top; the downloaded update with its full release notes and the
 * install/skip actions below; a footer stating the automatic cadence so the
 * polling is visible instead of implicit. Everything is hoisted — the sheet
 * owns no update logic.
 */
@Composable
internal fun UpdateSheet(
    currentVersionName: String?,
    pendingUpdate: UpdateCoordinator.PendingUpdate?,
    skippedVersionName: String?,
    lastCheckMs: Long,
    checking: Boolean,
    status: String?,
    error: String?,
    onCheckNow: () -> Unit,
    onInstall: () -> Unit,
    onSkip: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        UpdateSheetContent(
            currentVersionName = currentVersionName,
            pendingUpdate = pendingUpdate,
            skippedVersionName = skippedVersionName,
            lastCheckMs = lastCheckMs,
            checking = checking,
            status = status,
            error = error,
            onCheckNow = onCheckNow,
            onInstall = onInstall,
            onSkip = onSkip,
        )
    }
}

/** Sheet body, split from the modal shell so previews can render it directly. */
@Composable
internal fun UpdateSheetContent(
    currentVersionName: String?,
    pendingUpdate: UpdateCoordinator.PendingUpdate?,
    skippedVersionName: String?,
    lastCheckMs: Long,
    checking: Boolean,
    status: String?,
    error: String?,
    onCheckNow: () -> Unit,
    onInstall: () -> Unit,
    onSkip: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .navigationBarsPadding()
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "App updates",
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))

        SettingsGroup {
            SettingsInfoItem(
                title = "Current version",
                supporting = currentVersionName ?: "unknown",
                index = 0,
                count = 3,
                icon = Icons.Filled.Info,
            )
            SettingsInfoItem(
                title = "Last checked",
                supporting = if (lastCheckMs > 0L) {
                    formatRelativePast(lastCheckMs)
                } else {
                    "Not yet"
                },
                index = 1,
                count = 3,
                icon = Icons.Filled.Schedule,
            )
            SettingsActionItem(
                title = if (checking) "Checking for updates…" else "Check now",
                supporting = "GitHub Releases feed",
                onClick = onCheckNow,
                index = 2,
                count = 3,
                enabled = !checking,
                busy = checking,
                icon = Icons.Filled.Refresh,
            )
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        } else if (!status.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(16.dp))

        val pending = pendingUpdate
        if (pending != null) {
            // The one accent on this sheet: the ready-to-install card.
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Version ${pending.versionName}",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Downloaded and verified — ready to install",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(
                            onClick = onInstall,
                            shapes = ButtonDefaults.shapes(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text("Install now")
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { onSkip(pending.versionCode) }) {
                            Text("Skip this version")
                        }
                    }
                }
            }
            if (!pending.notes.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "What's new",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                UpdateNotes(markdown = pending.notes)
            }
        } else {
            SettingsGroup {
                SettingsInfoItem(
                    title = if (skippedVersionName != null) {
                        "Version $skippedVersionName skipped"
                    } else {
                        "You're up to date"
                    },
                    supporting = if (skippedVersionName != null) {
                        "The next release will be offered here"
                    } else {
                        "You'll get a notification when a new release is ready"
                    },
                    index = 0,
                    count = 1,
                    icon = if (skippedVersionName != null) {
                        Icons.Filled.SkipNext
                    } else {
                        Icons.Filled.CheckCircle
                    },
                    tone = SettingsRowTone.Active,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            text = "OpenGarden checks for updates about twice a day in the background " +
                "and when you open the app.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

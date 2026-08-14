package app.openbubbles.nativeapp.ui.settings

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.NotifPrefs
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.URegisterState
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One-shot connection snapshot for the Connection section. */
private data class ConnectionInfo(
    val regstate: String,
    val handles: List<String>,
)

private fun describeRegstate(state: URegisterState): String = when (state) {
    is URegisterState.Registered ->
        if (state.nextS > 0) "Registered — next check-in in ${state.nextS}s" else "Registered"
    URegisterState.Registering -> "Registering…"
    is URegisterState.Failed -> "Failed: ${state.error}"
}

/**
 * Settings (M2 "lite"): connection health (registration state + handles via
 * the live Rust push state), an appearance placeholder, attachment storage
 * maintenance, and an about row. Sign out is a stub pending the M3 teardown
 * flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenFindMy: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()

    // Connection details come from the live Rust state (blocking calls; IO).
    val connection by produceState<ConnectionInfo?>(initialValue = null, pushState) {
        val live = pushState ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                ConnectionInfo(describeRegstate(live.getRegstate()), live.getHandles())
            }.getOrNull()
        }
    }

    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        cacheBytes = withContext(Dispatchers.IO) { AppGraph.attachmentsCacheBytes() }
    }

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
    }

    // ------------------------------------------------------------------
    // Backup / restore
    // ------------------------------------------------------------------

    // Thread-safe stage text: core progress fires on the IO dispatcher, so a
    // StateFlow (not snapshot state) carries the updates into composition.
    val backupStageFlow = remember { MutableStateFlow<String?>(null) }
    val backupStage by backupStageFlow.asStateFlow().collectAsStateWithLifecycle()
    var backupError by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var restarting by remember { mutableStateOf(false) }
    val backupBusy = backupStage != null || restarting

    fun backupFileName(): String = "openbubbles-backup-" +
        LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".zip"

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        backupError = null
        backupStageFlow.value = "Starting…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    CoreGraph.backupTo(out) { stage -> backupStageFlow.value = stage }
                } ?: Result.failure(IllegalStateException("cannot open destination file"))
            }
            backupStageFlow.value = null
            result.onFailure { backupError = it.message ?: "export failed" }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) pendingRestoreUri = uri // confirmed before anything runs
    }

    fun runRestore(uri: Uri) {
        backupError = null
        backupStageFlow.value = "Restoring…"
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    CoreGraph.restoreFrom(input)
                } ?: Result.failure(IllegalStateException("cannot open backup file"))
            }
            backupStageFlow.value = null
            result.onSuccess {
                // CoreGraph's lazy singletons (and the open store) cannot be
                // rebuilt in place, so the process restarts to load the
                // restored data — the row below is the user-facing notice.
                restarting = true
                delay(2_500)
                Runtime.getRuntime().exit(0)
            }.onFailure {
                backupError = it.message ?: "restore failed"
            }
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard(title = "Connection") {
                if (pushState == null) {
                    SettingRow(
                        title = "Not connected",
                        supporting = "Sign in from the banner on the chat list",
                    )
                } else {
                    SettingRow(
                        title = "Registration",
                        supporting = connection?.regstate ?: "Checking…",
                    )
                    SettingRow(
                        title = "Handles",
                        supporting = connection?.handles?.joinToString("\n") ?: "Checking…",
                        multiline = true,
                    )
                    var signingOut by remember { androidx.compose.runtime.mutableStateOf(false) }
                    TextButton(
                        enabled = !signingOut,
                        onClick = {
                            signingOut = true
                            scope.launch {
                                app.openbubbles.nativeapp.data.CoreGraph.signOut(context)
                                signingOut = false
                                onBack()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Logout,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(if (signingOut) "Signing out…" else "Sign out")
                    }
                }
            }

            SectionCard(title = "iCloud Sync") {
                val syncManager = app.openbubbles.nativeapp.data.CloudSyncWiring.manager
                val syncProgress by syncManager?.progress?.collectAsStateWithLifecycle()
                    ?: remember { androidx.compose.runtime.mutableStateOf(null) }
                var syncing by remember { androidx.compose.runtime.mutableStateOf(false) }
                var syncResult by remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

                if (syncManager == null) {
                    SettingRow(
                        title = "History sync",
                        supporting = "Connect to enable syncing messages from iCloud",
                    )
                } else {
                    val progress = syncProgress
                    SettingRow(
                        title = "History sync",
                        supporting = when {
                            progress != null && syncing ->
                                "${progress.phase}: ${progress.chatsDone} chats, ${progress.messagesDone} messages"
                            syncResult != null -> syncResult!!
                            else -> "Chats back up to iCloud; new devices sync history on sign-in"
                        },
                    )
                    if (syncing) {
                        TextButton(onClick = { syncManager.cancel() }) { Text("Stop") }
                    } else {
                        TextButton(
                            onClick = {
                                syncing = true
                                syncResult = null
                                scope.launch {
                                    val summary = syncManager.sync(
                                        app.openbubbles.core.sync.SyncMode.FULL,
                                    )
                                    syncing = false
                                    syncResult = if (summary.error != null) {
                                        "Sync failed: ${summary.error}"
                                    } else {
                                        "Synced ${summary.totalChats} chats, ${summary.totalMessages} messages " +
                                            "(${summary.chatTombstones + summary.messageTombstones} removed) " +
                                            "in ${summary.durationMs / 1000}s"
                                    }
                                }
                            },
                        ) { Text("Sync all history now") }
                    }
                }
            }

            SectionCard(title = "Power") {
                val ctx = context
                var batterySaver by remember {
                    androidx.compose.runtime.mutableStateOf(
                        app.openbubbles.nativeapp.service.BatterySaver.isEnabled(ctx))
                }
                SettingRow(
                    title = "Battery saver",
                    supporting = if (batterySaver) {
                        "Checking iCloud every 15 min — messages may be delayed"
                    } else {
                        "Live connection — instant messages, uses more battery"
                    },
                )
                androidx.compose.material3.Switch(
                    checked = batterySaver,
                    onCheckedChange = { enabled ->
                        batterySaver = app.openbubbles.nativeapp.service.BatterySaver
                            .setEnabled(ctx, enabled)
                    },
                )
            }

            SectionCard(title = "Notifications") {
                val notifPrefs = remember { NotifPrefs(context) }
                var hidePreviews by remember { mutableStateOf(notifPrefs.hidePreviews) }
                var replyEnabled by remember { mutableStateOf(notifPrefs.replyEnabled) }
                SwitchSettingRow(
                    title = "Hide message previews",
                    supporting = "Show \"iMessage\" instead of message content on notifications",
                    checked = hidePreviews,
                    onCheckedChange = { enabled ->
                        hidePreviews = enabled
                        notifPrefs.hidePreviews = enabled
                    },
                )
                SwitchSettingRow(
                    title = "Quick reply",
                    supporting = "Show the Reply action on message notifications",
                    checked = replyEnabled,
                    onCheckedChange = { enabled ->
                        replyEnabled = enabled
                        notifPrefs.replyEnabled = enabled
                    },
                )
            }

            SectionCard(title = "Find My") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenFindMy),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column {
                        Text(text = "Find My", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "Devices, friends and items",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionCard(title = "Appearance") {
                SettingRow(
                    title = "Theme",
                    supporting = "Coming soon — M3 theming milestone",
                )
            }

            SectionCard(title = "Storage") {
                SettingRow(
                    title = "Attachments on disk",
                    supporting = cacheBytes
                        ?.let { formatBytes(it).ifEmpty { "Empty" } }
                        ?: "Calculating…",
                )
                Button(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { AppGraph.clearAttachmentCache() }
                            cacheBytes = withContext(Dispatchers.IO) { AppGraph.attachmentsCacheBytes() }
                        }
                    },
                    enabled = (cacheBytes ?: 0L) > 0L,
                ) {
                    Text("Clear attachment cache")
                }
            }

            SectionCard(title = "Backup") {
                SettingRow(
                    title = "Local backup",
                    supporting = "Export the database and attachments to a zip file, or restore from one.",
                )
                Button(
                    onClick = { exportLauncher.launch(backupFileName()) },
                    enabled = !backupBusy,
                ) {
                    Text("Export backup")
                }
                Button(
                    onClick = {
                        restoreLauncher.launch(arrayOf(
                            "application/zip",
                            "application/x-zip-compressed",
                            "application/octet-stream",
                        ))
                    },
                    enabled = !backupBusy,
                ) {
                    Text("Restore backup")
                }
                if (backupStage != null) {
                    SettingRow(title = "Working…", supporting = backupStage ?: "")
                }
                if (restarting) {
                    SettingRow(
                        title = "Restore complete",
                        supporting = "Restarting to load the restored data…",
                    )
                }
                backupError?.let {
                    SettingRow(title = "Backup error", supporting = it, multiline = true)
                }
            }

            SectionCard(title = "About") {
                SettingRow(
                    title = "OpenBubbles native",
                    supporting = "Version ${versionName ?: "unknown"}",
                )
            }
        }
    }

    // Restore confirmation: replacing data is destructive, so the picked file
    // waits here until the user explicitly confirms.
    pendingRestoreUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestoreUri = null },
            title = { Text("Restore backup?") },
            text = {
                Text("This replaces current data — messages and attachments on this device will be replaced with the backup's contents. The app restarts afterwards.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingRestoreUri = null
                        runRestore(uri)
                    },
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestoreUri = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun SettingRow(
    title: String,
    supporting: String,
    multiline: Boolean = false,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = supporting,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (multiline) 6 else 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** [SettingRow] with a trailing Material3 switch (notification toggles). */
@Composable
private fun SwitchSettingRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// --------------------------------------------------------------------- previews

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    OpenBubblesTheme {
        SettingsScreen(onBack = {})
    }
}

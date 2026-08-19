package app.openbubbles.nativeapp.ui.settings

import android.content.Intent
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.CloudSyncWiring
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.service.NativePushService
import app.openbubbles.nativeapp.update.UpdateCoordinator
import app.openbubbles.nativeapp.update.UpdateDecision
import app.openbubbles.nativeapp.update.UpdateSettings
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.common.formatRelativePast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.URegisterState
import java.io.File

/** One-shot connection snapshot for the iMessage stats group. */
private data class ConnectionInfo(
    val regstate: URegisterState,
    val handles: List<String>,
)

private fun describeRegstate(state: URegisterState): String = when (state) {
    is URegisterState.Registered -> when {
        state.nextS >= 86_400 -> "Registered · renews in ${state.nextS / 86_400} days"
        state.nextS >= 3_600 -> "Registered · renews in ${state.nextS / 3_600} hours"
        state.nextS > 0 -> "Registered · renews soon"
        else -> "Registered"
    }
    URegisterState.Registering -> "Registering…"
    is URegisterState.Failed -> "Failed: ${state.error}"
}

/**
 * Logging, Troubleshoot, iMessage-stats, and About groups plus the update
 * sheet. Clique membership and the attachment-cache size come in from the
 * screen, which shares them with the iCloud and Storage sections.
 */
@Composable
internal fun rememberDiagnosticsAboutSection(
    inClique: Boolean?,
    cacheBytes: Long?,
): SettingsSectionSlice {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()
    val registrationState by PushStateHolder.registrationStateFlow.collectAsStateWithLifecycle()
    val syncing by CloudSyncWiring.syncing.collectAsStateWithLifecycle()
    val syncSummary by CloudSyncWiring.lastSummary.collectAsStateWithLifecycle()
    var logRevision by remember { mutableIntStateOf(0) }
    var logCount by remember { mutableIntStateOf(0) }
    var logBytes by remember { mutableStateOf(0L) }
    LaunchedEffect(logRevision) {
        val files = withContext(Dispatchers.IO) { diagnosticLogFiles(context) }
        logCount = files.size
        logBytes = files.sumOf(File::length)
    }

    // Connection details come from the live Rust state (blocking calls; IO).
    val connection by produceState<ConnectionInfo?>(initialValue = null, pushState) {
        val live = pushState ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                ConnectionInfo(live.getRegstate(), live.getHandles())
            }.getOrNull()
        }
    }

    val versionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull()
    }

    // ------------------------------------------------------------------
    // Self-update (GitHub Releases feed + PackageInstaller)
    // ------------------------------------------------------------------
    var updateBusy by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var updateRefresh by remember { mutableIntStateOf(0) }
    val pendingUpdate = remember(updateRefresh) {
        UpdateCoordinator.pendingUpdate(context)
    }
    var showUpdateSheet by rememberSaveable { mutableStateOf(false) }
    val lastCheckMs = remember(updateRefresh) { UpdateSettings.lastCheckMs(context) }
    // A skipped version keeps its downloaded record (only the deferral is
    // set), so its display name is still available for the "skipped" state.
    val skippedVersionName = remember(updateRefresh) {
        val deferred = UpdateSettings.deferredVersionCode(context)
        if (deferred > 0L && deferred == UpdateSettings.pendingVersionCode(context)) {
            UpdateSettings.pendingVersionName(context)
        } else {
            null
        }
    }

    fun runUpdateCheck() {
        if (updateBusy) return
        updateBusy = true
        updateStatus = null
        updateError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { UpdateCoordinator.checkNow(context) }
            updateBusy = false
            when (result) {
                is UpdateCoordinator.CheckResult.Done -> when (val decision = result.decision) {
                    UpdateDecision.UpToDate ->
                        updateStatus = "You're up to date${versionName?.let { " (version $it)" } ?: ""}"
                    UpdateDecision.RollbackBlocked ->
                        updateStatus = "You're up to date (ignored an older feed)"
                    is UpdateDecision.Deferred ->
                        updateStatus = "Version ${decision.versionCode} is skipped; you're up to date"
                    is UpdateDecision.Available, is UpdateDecision.Mandatory ->
                        if (result.downloaded) {
                            updateStatus = "Update downloaded — ready to install"
                        } else {
                            updateError = "Update found but the download did not complete"
                        }
                }
                is UpdateCoordinator.CheckResult.Failed -> updateError = result.message
            }
            updateRefresh++
        }
    }

    fun runInstallPending() {
        // installNow streams the whole APK into the PackageInstaller
        // session; keep that off the main thread.
        scope.launch {
            val installResult = withContext(Dispatchers.IO) { UpdateCoordinator.installNow(context) }
            when (installResult) {
                UpdateCoordinator.InstallNowResult.NothingPending -> updateRefresh++
                UpdateCoordinator.InstallNowResult.NeedsUnknownSourcesPermission -> {
                    updateStatus = "Allow \"Install unknown apps\" for OpenGarden, then tap Install again"
                    runCatching {
                        context.startActivity(
                            app.openbubbles.nativeapp.update.ApkInstaller.unknownSourcesIntent(context)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }
                UpdateCoordinator.InstallNowResult.Installing ->
                    updateStatus = "Installing… the app will restart when done"
                is UpdateCoordinator.InstallNowResult.Failed -> updateError = installResult.message
            }
            updateRefresh++
        }
    }

    return SettingsSectionSlice(
        groups = { filter, showTitles ->
            if (filter == null || filter == SettingsSection.Diagnostics) {
                SettingsGroup(title = if (showTitles) "Logging" else null) {
                    SettingsInfoItem(
                        title = "Stored logs",
                        supporting = "$logCount file(s) · ${formatBytes(logBytes).ifBlank { "Empty" }}",
                        index = 0,
                        count = 3,
                        icon = Icons.Filled.Folder,
                    )
                    SettingsActionItem(
                        title = "Share recent logs",
                        supporting = "Combine current app and Rust logs into a text export",
                        onClick = { if (shareDiagnosticLogs(context)) logRevision++ },
                        index = 1,
                        count = 3,
                        enabled = logCount > 0,
                        icon = Icons.Filled.Upload,
                    )
                    SettingsActionItem(
                        title = "Clear logs",
                        supporting = "Delete locally stored diagnostic logs",
                        onClick = {
                            diagnosticLogFiles(context).forEach(File::delete)
                            logRevision++
                        },
                        index = 2,
                        count = 3,
                        enabled = logCount > 0,
                        destructive = true,
                        icon = Icons.Filled.DeleteSweep,
                    )
                }
                SettingsGroup(title = if (showTitles) "Troubleshoot" else null) {
                    SettingsActionItem(
                        title = "Restart push service",
                        supporting = "Reload the persisted Apple push connection",
                        onClick = { NativePushService.reloadAfterLogin(context) },
                        index = 0,
                        count = 2,
                        icon = Icons.Filled.RestartAlt,
                    )
                    SettingsActionItem(
                        title = "Reset CloudKit sync",
                        supporting = "Clear saved cursors and start a fresh Messages in iCloud pass",
                        onClick = {
                            scope.launch(Dispatchers.IO) { CloudSyncWiring.resetHistorySync(context) }
                        },
                        index = 1,
                        count = 2,
                        enabled = pushState != null,
                        destructive = true,
                        icon = Icons.Filled.CloudSync,
                    )
                }
                SettingsGroup(title = if (showTitles) "iMessage stats" else null) {
                    val stats = listOf(
                        "Registration" to ((registrationState ?: connection?.regstate)
                            ?.let(::describeRegstate) ?: "Not connected"),
                        // The Account section stays actionable-only, so the
                        // full handle list is shown here.
                        "Registered handles" to when {
                            connection != null ->
                                connection!!.handles.joinToString("\n").ifBlank { "None" }
                            pushState != null -> "Checking…"
                            else -> "Not connected"
                        },
                        "Secure iCloud clique" to when (inClique) {
                            true -> "Member"
                            false -> "Not joined"
                            null -> "Unknown"
                        },
                        "Last CloudKit sync" to when {
                            syncing -> "Running"
                            syncSummary?.error != null -> "Failed: ${syncSummary!!.error}"
                            syncSummary != null -> "${syncSummary!!.totalChats} chats · ${syncSummary!!.totalMessages} messages · ${syncSummary!!.totalAttachments} attachments"
                            else -> "No completed sync this session"
                        },
                        "Attachment cache" to (cacheBytes?.let(::formatBytes) ?: "Calculating…"),
                    )
                    stats.forEachIndexed { index, (title, supporting) ->
                        SettingsInfoItem(
                            title = title,
                            supporting = supporting,
                            index = index,
                            count = stats.size,
                            multiline = true,
                            icon = when (index) {
                                0 -> Icons.Filled.CheckCircle
                                1 -> Icons.Filled.AlternateEmail
                                2 -> Icons.Filled.Key
                                3 -> Icons.Filled.CloudSync
                                else -> Icons.Filled.Storage
                            },
                        )
                    }
                }
            }
            if (filter == null || filter == SettingsSection.About) {
                SettingsGroup(title = if (showTitles) "About" else null) {
                    SettingsInfoItem(
                        title = "OpenGarden",
                        supporting = "Version ${versionName ?: "unknown"}",
                        index = 0,
                        count = 2,
                        icon = Icons.Filled.Info,
                    )
                    // One entry point to the update center (sheet): the row
                    // carries the state, the sheet carries the detail.
                    val pending = pendingUpdate
                    SettingsActionItem(
                        title = "App updates",
                        supporting = when {
                            pending != null -> "Version ${pending.versionName} is ready to install"
                            updateBusy -> "Checking for updates…"
                            updateError != null -> "Couldn't check for updates — tap to retry"
                            lastCheckMs <= 0L -> "Automatic checks twice a day"
                            else -> "Up to date · checked ${formatRelativePast(lastCheckMs)}"
                        },
                        onClick = { showUpdateSheet = true },
                        index = 1,
                        count = 2,
                        multiline = true,
                        icon = Icons.Filled.SystemUpdate,
                        iconTone = when {
                            pending != null -> SettingsRowTone.Active
                            updateError != null -> SettingsRowTone.Error
                            else -> SettingsRowTone.Neutral
                        },
                    )
                }
            }
        },
        dialogs = {
            if (showUpdateSheet) {
                UpdateSheet(
                    currentVersionName = versionName,
                    pendingUpdate = pendingUpdate,
                    skippedVersionName = skippedVersionName,
                    lastCheckMs = lastCheckMs,
                    checking = updateBusy,
                    status = updateStatus,
                    error = updateError,
                    onCheckNow = ::runUpdateCheck,
                    onInstall = ::runInstallPending,
                    onSkip = { code ->
                        UpdateSettings.deferVersionCode(context, code)
                        updateRefresh++
                    },
                    onDismiss = { showUpdateSheet = false },
                )
            }
        },
    )
}

private fun diagnosticLogFiles(context: android.content.Context): List<File> =
    File(context.filesDir, "logs").listFiles()
        ?.filter { it.isFile }
        ?.sortedByDescending(File::lastModified)
        .orEmpty()

private fun shareDiagnosticLogs(context: android.content.Context): Boolean = runCatching {
    val files = diagnosticLogFiles(context).take(8)
    if (files.isEmpty()) return@runCatching false
    val directory = File(context.cacheDir, "diagnostics").apply { mkdirs() }
    val export = File(directory, "openbubbles-logs.txt")
    export.bufferedWriter().use { writer ->
        files.forEach { file ->
            writer.appendLine("===== ${file.name} =====")
            file.bufferedReader().useLines { lines -> lines.take(2_000).forEach(writer::appendLine) }
            writer.appendLine()
        }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", export)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share OpenGarden logs"))
    true
}.getOrDefault(false)

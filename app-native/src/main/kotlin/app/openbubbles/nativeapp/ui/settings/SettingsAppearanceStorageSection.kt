package app.openbubbles.nativeapp.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.AppearancePrefs
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.ThemeMode
import app.openbubbles.nativeapp.ui.common.formatBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Appearance and Storage-and-backup groups plus the theme picker and the
 * restore confirmation. The attachment-cache size stays hoisted at the screen
 * (Diagnostics shows it too); clearing the cache reports the recomputed size
 * back through [onCacheBytes].
 */
@Composable
internal fun rememberAppearanceStorageSection(
    cacheBytes: Long?,
    onCacheBytes: (Long?) -> Unit,
): SettingsSectionSlice {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeMode by AppearancePrefs.themeModeFlow.collectAsStateWithLifecycle()
    var showThemeModeDialog by rememberSaveable { mutableStateOf(false) }

    // Thread-safe stage text: core progress fires on the IO dispatcher, so a
    // StateFlow (not snapshot state) carries the updates into composition.
    val backupStageFlow = remember { MutableStateFlow<String?>(null) }
    // Collected directly: MutableStateFlow already is a StateFlow, and calling
    // asStateFlow() here built a new wrapper on every recomposition, which
    // restarted the collector (FlowOperatorInvokedInComposition).
    val backupStage by backupStageFlow.collectAsStateWithLifecycle()
    var backupError by remember { mutableStateOf<String?>(null) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    val backupBusy = backupStage != null

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
        // Runs on CoreGraph's process scope: once the restore passes its
        // swap boundary the root UI swaps in the shutdown overlay (dismissing
        // this screen), which would cancel a composition-scoped coroutine
        // mid-swap. Post-swap outcome + restart are handled there.
        CoreGraph.runRestore(
            context = context,
            uri = uri,
            onStage = { stage -> backupStageFlow.value = stage },
            onError = { message -> backupError = message },
        )
    }

    return SettingsSectionSlice(
        groups = { filter, showTitles ->
            if (filter == null || filter == SettingsSection.Appearance) SettingsGroup(
                title = if (showTitles) "Appearance" else null,
            ) {
                val persistedDynamicColor by AppearancePrefs.dynamicColorFlow
                    .collectAsStateWithLifecycle()
                var dynamicColor by remember(persistedDynamicColor) {
                    mutableStateOf(persistedDynamicColor)
                }
                SettingsActionItem(
                    title = "Theme",
                    supporting = themeMode.title,
                    onClick = { showThemeModeDialog = true },
                    index = 0,
                    count = 2,
                    icon = Icons.Filled.DarkMode,
                )
                SettingsToggleItem(
                    title = "Dynamic color",
                    supporting = "Colors follow your wallpaper (Material You)",
                    checked = dynamicColor,
                    onCheckedChange = { enabled ->
                        // The pref write re-themes the whole app; emitting it
                        // off the tap frame keeps the switch animation smooth.
                        dynamicColor = enabled
                        scope.launch(Dispatchers.Default) {
                            AppearancePrefs.dynamicColor = enabled
                        }
                    },
                    index = 1,
                    count = 2,
                    icon = Icons.Filled.Palette,
                )
            }

            if (filter == null || filter == SettingsSection.Storage) {
            SettingsGroup(
                title = if (showTitles) "Storage & backup" else null,
            ) {
                val cacheLabel = cacheBytes
                    ?.let { formatBytes(it).ifEmpty { "Empty" } }
                    ?: "Calculating…"
                val backupErrorText = backupError
                val backupStageText = backupStage
                val storageRows = buildList {
                    add("attachments")
                    add("clear")
                    add("export")
                    add("restore")
                    if (backupStageText != null) add("working")
                    if (backupErrorText != null) add("error")
                }
                val storageCount = storageRows.size
                storageRows.forEachIndexed { storageIndex, row ->
                    when (row) {
                        "attachments" -> SettingsInfoItem(
                            title = "Attachments on disk",
                            supporting = cacheLabel,
                            index = storageIndex,
                            count = storageCount,
                            icon = Icons.Filled.Folder,
                        )
                        "clear" -> SettingsActionItem(
                            title = "Clear attachment cache",
                            supporting = "Removes downloaded files; they can be fetched again",
                            onClick = {
                                scope.launch {
                                    withContext(Dispatchers.IO) { AppGraph.clearAttachmentCache() }
                                    onCacheBytes(withContext(Dispatchers.IO) { AppGraph.attachmentsCacheBytes() })
                                }
                            },
                            index = storageIndex,
                            count = storageCount,
                            enabled = (cacheBytes ?: 0L) > 0L,
                            icon = Icons.Filled.DeleteSweep,
                        )
                        "export" -> SettingsActionItem(
                            title = "Export backup",
                            supporting = "Save a zip of this device's messages and attachments",
                            onClick = { exportLauncher.launch(backupFileName()) },
                            index = storageIndex,
                            count = storageCount,
                            enabled = !backupBusy,
                            busy = backupBusy && backupStageText != null,
                            icon = Icons.Filled.Upload,
                        )
                        "restore" -> SettingsActionItem(
                            title = "Restore backup",
                            supporting = "Replace this device's data from a zip; the app restarts afterwards",
                            onClick = {
                                restoreLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                            index = storageIndex,
                            count = storageCount,
                            enabled = !backupBusy,
                            icon = Icons.Filled.Restore,
                        )
                        "working" -> SettingsInfoItem(
                            title = "Working…",
                            supporting = backupStageText,
                            index = storageIndex,
                            count = storageCount,
                            icon = Icons.Filled.HourglassTop,
                        )
                        else -> SettingsInfoItem(
                            title = "Backup error",
                            supporting = backupErrorText,
                            index = storageIndex,
                            count = storageCount,
                            multiline = true,
                            titleColor = MaterialTheme.colorScheme.error,
                            icon = Icons.Filled.ErrorOutline,
                            tone = SettingsRowTone.Error,
                        )
                    }
                }
            }

            }
        },
        dialogs = {
            if (showThemeModeDialog) {
                AlertDialog(
                    onDismissRequest = { showThemeModeDialog = false },
                    title = { Text("Theme") },
                    text = {
                        SettingsGroup {
                            ThemeMode.entries.forEachIndexed { index, mode ->
                                SettingsChoiceItem(
                                    title = mode.title,
                                    supporting = mode.description,
                                    selected = themeMode == mode,
                                    onClick = {
                                        // The pref write re-themes the whole app; off
                                        // the tap frame so the selection stays smooth.
                                        scope.launch(Dispatchers.Default) {
                                            AppearancePrefs.themeMode = mode
                                        }
                                    },
                                    index = index,
                                    count = ThemeMode.entries.size,
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showThemeModeDialog = false }) {
                            Text("Done")
                        }
                    },
                )
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
        },
    )
}

package app.openbubbles.nativeapp.ui.settings

import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.AppearancePrefs
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.ThemeMode
import app.openbubbles.nativeapp.data.CloudSyncWiring
import app.openbubbles.nativeapp.service.NativePushService
import app.openbubbles.nativeapp.update.UpdateCoordinator
import app.openbubbles.nativeapp.update.UpdateDecision
import app.openbubbles.nativeapp.update.UpdateSettings
import app.openbubbles.nativeapp.ui.adaptive.settingsTwoPaneSplit
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.common.formatRelativePast
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.URegisterState
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** One-shot connection snapshot for the Account section. */
private data class ConnectionInfo(
    val regstate: URegisterState,
    val handles: List<String>,
)

/**
 * One extracted settings area: `groups` emits its preference groups into the
 * settings column (honoring the two-pane section filter), `dialogs` mounts
 * its dialogs unconditionally at screen level. Both close over state owned
 * by the section's `remember*Section` composable, which the screen calls
 * unconditionally so state and running work survive rail-section switches
 * and pane-layout changes.
 */
internal class SettingsSectionSlice(
    val groups: @Composable (filter: SettingsSection?, showTitles: Boolean) -> Unit,
    val dialogs: @Composable () -> Unit,
)

internal enum class SettingsSection(
    val title: String,
    val supporting: String,
    val icon: ImageVector,
) {
    Account("Account", "Recovery, profile, sign out", Icons.Filled.AccountCircle),
    ICloud("iCloud", "History, Keychain, contacts", Icons.Filled.Cloud),
    Notifications("Notifications", "Previews, replies, reactions", Icons.Filled.Notifications),
    Messaging("Messaging", "Sending address, archived chats, SMS", Icons.AutoMirrored.Filled.Chat),
    Power("Power", "Battery saver", Icons.Filled.PowerSettingsNew),
    Appearance("Appearance", "Theme and color", Icons.Filled.Palette),
    Storage("Storage & backup", "Attachments and local backup", Icons.Filled.Storage),
    Diagnostics("Diagnostics", "Logs, troubleshoot, iMessage stats", Icons.Filled.ManageHistory),
    About("About", "App version", Icons.Filled.Info),
}

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
 * Settings: titled groups of segmented rows (one container per row, 2dp
 * gaps, tonal icon chips up front, chevrons on actions, switches on
 * toggles) so every setting is scannable and its affordance is visible.
 * Compact is a single column; medium+ is a category rail plus a narrow
 * detail column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenFindMy: () -> Unit = {},
    onOpenArchived: () -> Unit = {},
    onOpenRecentlyDeleted: () -> Unit = {},
    onOpenPasswords: () -> Unit = {},
    onOpenSharedAlbums: () -> Unit = {},
    onOpenSignIn: () -> Unit = {},
    archivedCount: Int = 0,
    recentlyDeletedCount: Int = 0,
    showBackButton: Boolean = true,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()
    val registrationState by PushStateHolder.registrationStateFlow.collectAsStateWithLifecycle()
    val themeMode by AppearancePrefs.themeModeFlow.collectAsStateWithLifecycle()
    var showThemeModeDialog by rememberSaveable { mutableStateOf(false) }
    var logRevision by remember { mutableIntStateOf(0) }
    var logCount by remember { mutableIntStateOf(0) }
    var logBytes by remember { mutableStateOf(0L) }

    val accountSection = rememberAccountSection(
        onOpenSignIn = onOpenSignIn,
        onBack = onBack,
    )

    LaunchedEffect(logRevision) {
        val files = withContext(Dispatchers.IO) { diagnosticLogFiles(context) }
        logCount = files.size
        logBytes = files.sumOf(File::length)
    }

    val syncing by CloudSyncWiring.syncing.collectAsStateWithLifecycle()
    val syncSummary by CloudSyncWiring.lastSummary.collectAsStateWithLifecycle()

    var cliqueRefresh by remember { mutableIntStateOf(0) }
    var inClique by remember(pushState) { mutableStateOf<Boolean?>(null) }
    var cliqueError by remember(pushState) { mutableStateOf<String?>(null) }
    LaunchedEffect(pushState, cliqueRefresh) {
        val live = pushState
        if (live == null) {
            inClique = null
            cliqueError = null
        } else {
            val result = withContext(Dispatchers.IO) { runCatching { live.isInClique() } }
            result.onSuccess {
                inClique = it
                cliqueError = null
            }.onFailure {
                inClique = false
                cliqueError = it.message ?: "Unable to check iCloud Keychain"
            }
        }
    }

    val icloudSection = rememberICloudSection(
        inClique = inClique,
        cliqueError = cliqueError,
        onCliqueJoined = {
            inClique = true
            cliqueRefresh += 1
        },
        onOpenSignIn = onOpenSignIn,
        onOpenPasswords = onOpenPasswords,
        onOpenSharedAlbums = onOpenSharedAlbums,
    )


    // Connection details come from the live Rust state (blocking calls; IO).
    val connection by produceState<ConnectionInfo?>(initialValue = null, pushState) {
        val live = pushState ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                ConnectionInfo(live.getRegstate(), live.getHandles())
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
                    updateStatus = "Allow \"Install unknown apps\" for OpenBubbles, then tap Install again"
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

    val messagingSection = rememberMessagingSection(
        archivedCount = archivedCount,
        recentlyDeletedCount = recentlyDeletedCount,
        onOpenArchived = onOpenArchived,
        onOpenRecentlyDeleted = onOpenRecentlyDeleted,
    )

    // ------------------------------------------------------------------
    // Backup / restore
    // ------------------------------------------------------------------

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

    // Compact: collapsing headline. Medium+ (foldable inner, tablet): the
    // Messages pattern — title sits next to back so the list gets the height.
    val windowSizeClass = windowAdaptiveInfo.windowSizeClass
    val isMediumWidth = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
    )
    val density = LocalDensity.current
    val hinge = windowAdaptiveInfo.windowPosture.separatingVerticalHingeBounds.firstOrNull()
    val twoPane = settingsTwoPaneSplit(
        hingeLeftDp = hinge?.let { with(density) { it.left.toDp().value } },
        hingeRightDp = hinge?.let { with(density) { it.right.toDp().value } },
        defaultListWidthDp = SettingsListPaneWidth.value,
    )
    val scrollBehavior = if (isMediumWidth) {
        TopAppBarDefaults.pinnedScrollBehavior()
    } else {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    val barColors = TopAppBarDefaults.topAppBarColors(
        containerColor = MaterialTheme.colorScheme.surface,
        scrolledContainerColor = MaterialTheme.colorScheme.surface,
    )
    val navigationIcon: @Composable () -> Unit = {
        if (showBackButton) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
        }
    }
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isMediumWidth) {
                TopAppBar(
                    title = { Text("Settings") },
                    navigationIcon = navigationIcon,
                    colors = barColors,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                MediumFlexibleTopAppBar(
                    title = { Text("Settings") },
                    scrollBehavior = scrollBehavior,
                    colors = barColors,
                    navigationIcon = navigationIcon,
                )
            }
        },
    ) { padding ->
        var selectedSectionName by rememberSaveable {
            mutableStateOf(SettingsSection.Account.name)
        }
        val selectedSection = SettingsSection.valueOf(selectedSectionName)

        @Composable
        fun SectionColumn(
            filter: SettingsSection?,
            showTitles: Boolean,
            modifier: Modifier = Modifier,
        ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing),
        ) {
            accountSection.groups(filter, showTitles)

            icloudSection.groups(filter, showTitles)

            messagingSection.groups(filter, showTitles)

            if (filter == null) SettingsGroup(
                title = if (showTitles) "Location" else null,
            ) {
                SettingsActionItem(
                    title = "Find My",
                    supporting = "Devices, friends and items",
                    onClick = onOpenFindMy,
                    index = 0,
                    count = 1,
                    icon = Icons.Filled.LocationOn,
                )
            }

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
                                    cacheBytes = withContext(Dispatchers.IO) { AppGraph.attachmentsCacheBytes() }
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
                        // The full handle list lives here (it moved out of the
                        // Account section, which keeps only actionable rows).
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
                        title = "OpenBubbles",
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
        }
        }

        if (isMediumWidth) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(twoPane.gutterDp.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(twoPane.listWidthDp.dp)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                ) {
                    SettingsGroup {
                        val sections = SettingsSection.entries
                        sections.forEachIndexed { index, section ->
                            SettingsCategoryItem(
                                title = section.title,
                                supporting = section.supporting,
                                selected = section == selectedSection,
                                onClick = { selectedSectionName = section.name },
                                index = index,
                                count = sections.size + 1,
                                icon = section.icon,
                            )
                        }
                        SettingsCategoryItem(
                            title = "Find My",
                            supporting = "Devices, friends and items",
                            selected = false,
                            onClick = onOpenFindMy,
                            index = sections.size,
                            count = sections.size + 1,
                            icon = Icons.Filled.LocationOn,
                            showChevron = true,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .widthIn(max = SettingsDetailMaxWidth)
                        .fillMaxHeight()
                        .weight(1f, fill = false),
                ) {
                    Text(
                        text = selectedSection.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
                    )
                    SectionColumn(
                        filter = selectedSection,
                        showTitles = false,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                SectionColumn(
                    filter = null,
                    showTitles = true,
                    modifier = Modifier
                        .widthIn(max = SettingsSingleColumnMaxWidth)
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

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

    accountSection.dialogs()

    icloudSection.dialogs()

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
    context.startActivity(Intent.createChooser(intent, "Share OpenBubbles logs"))
    true
}.getOrDefault(false)

// --------------------------------------------------------------------- previews

@Preview(name = "phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "foldable", device = Devices.FOLDABLE, showBackground = true)
@Preview(name = "tablet", device = Devices.TABLET, showBackground = true)
@Preview(name = "dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SettingsScreenPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        SettingsScreen(onBack = {})
    }
}

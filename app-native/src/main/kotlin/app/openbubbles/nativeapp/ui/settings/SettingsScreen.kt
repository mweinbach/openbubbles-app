package app.openbubbles.nativeapp.ui.settings

import android.content.res.Configuration
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
// material3 1.5.0-alpha26: MenuAnchorType was replaced by
// ExposedDropdownMenuAnchorType, and ExposedDropdownMenu became an extension
// function on ExposedDropdownMenuBoxScope, so it needs an explicit import.
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.AppearancePrefs
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.HistorySyncPreferences
import app.openbubbles.nativeapp.data.HistorySyncWindow
import app.openbubbles.nativeapp.data.ICloudContactSync
import app.openbubbles.nativeapp.data.ICloudContactSyncStatus
import app.openbubbles.nativeapp.data.MessagingPrefs
import app.openbubbles.nativeapp.data.NotifPrefs
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.CloudSyncWiring
import app.openbubbles.nativeapp.data.unlockICloudKeychain
import app.openbubbles.nativeapp.sms.SmsRole
import app.openbubbles.nativeapp.update.UpdateCoordinator
import app.openbubbles.nativeapp.update.UpdateDecision
import app.openbubbles.nativeapp.update.UpdateSettings
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.URegisterState
import uniffi.rust_lib_bluebubbles.UViableBottle
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One-shot connection snapshot for the Connection section. */
private data class ConnectionInfo(
    val regstate: String,
    val handles: List<String>,
)

private const val NATIVE_SETUP_PREFS = "native_setup"
private const val KEY_KEYCHAIN_RECOVERY_CODE = "keychain_recovery_code"

private enum class SettingsSection(val title: String, val supporting: String) {
    Account("Account", "Registration, handles, sign out"),
    ICloud("iCloud", "History, Keychain, contacts"),
    Notifications("Notifications", "Previews, replies, reactions"),
    Messaging("Messaging", "Sending address, read receipts, SMS"),
    Power("Power", "Battery saver"),
    Appearance("Appearance", "Theme and color"),
    Storage("Storage & backup", "Attachments and local backup"),
    About("About", "App version"),
}

private fun describeRegstate(state: URegisterState): String = when (state) {
    is URegisterState.Registered ->
        if (state.nextS > 0) "Registered — next check-in in ${state.nextS}s" else "Registered"
    URegisterState.Registering -> "Registering…"
    is URegisterState.Failed -> "Failed: ${state.error}"
}

/**
 * Settings: Messages-style preference cards (one surface, hairline
 * dividers, section labels, chevrons on actions). Compact is a single
 * column; medium+ is a category rail plus a narrow detail column.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenFindMy: () -> Unit = {},
    showBackButton: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()
    val registeredHandles by PushStateHolder.myHandlesFlow.collectAsStateWithLifecycle()
    val pushError by PushStateHolder.lastErrorFlow.collectAsStateWithLifecycle()
    var showSignOutConfirmation by rememberSaveable { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf<String?>(null) }
    val messagingPrefs = remember(context) { MessagingPrefs(context) }
    val historySyncPreferences = remember(context) { HistorySyncPreferences(context) }
    var defaultSendingHandle by remember {
        mutableStateOf(messagingPrefs.defaultSendingHandle)
    }
    var showDefaultSendingHandleDialog by remember { mutableStateOf(false) }
    var historySyncWindow by remember { mutableStateOf(historySyncPreferences.window) }
    var showHistorySyncLimitDialog by remember { mutableStateOf(false) }
    val availableSendingHandles = remember(registeredHandles) {
        registeredHandles.sortedWith(
            compareBy<String>(
                { if (it.startsWith("tel:")) 0 else 1 },
                { sendingHandleLabel(it).lowercase() },
            ),
        )
    }

    val syncManager = CloudSyncWiring.manager
    val syncProgress by syncManager?.progress?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val syncing by CloudSyncWiring.syncing.collectAsStateWithLifecycle()
    val syncSummary by CloudSyncWiring.lastSummary.collectAsStateWithLifecycle()
    var contactSyncing by remember { mutableStateOf(false) }
    var contactStatus by remember {
        mutableStateOf(ICloudContactSync.status(context))
    }

    var cliqueRefresh by remember { mutableStateOf(0) }
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

    var showCliqueJoin by remember { mutableStateOf(false) }
    var loadingBottles by remember { mutableStateOf(false) }
    var joiningClique by remember { mutableStateOf(false) }
    var bottles by remember { mutableStateOf<List<UViableBottle>>(emptyList()) }
    var selectedBottle by remember { mutableStateOf<UViableBottle?>(null) }
    var deviceMenuExpanded by remember { mutableStateOf(false) }
    var trustedDevicePasscode by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }
    var newRecoveryCode by remember { mutableStateOf<String?>(null) }
    var revealSavedRecoveryCode by remember { mutableStateOf(false) }

    fun syncAllHistory() {
        CloudSyncWiring.startHistorySync(
            context,
            app.openbubbles.core.sync.SyncMode.FULL,
        )
    }

    fun syncICloudContacts() {
        val live = pushState ?: return
        if (contactSyncing) return
        contactSyncing = true
        scope.launch {
            contactStatus = ICloudContactSync.sync(context, live, force = true)
            contactSyncing = false
        }
    }

    fun openCliqueJoin() {
        val live = pushState ?: return
        showCliqueJoin = true
        loadingBottles = true
        joiningClique = false
        bottles = emptyList()
        selectedBottle = null
        deviceMenuExpanded = false
        trustedDevicePasscode = ""
        joinError = null
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { live.getViableBottles() } }
            loadingBottles = false
            result.onSuccess { found ->
                bottles = found
                selectedBottle = found.singleOrNull()
                if (found.isEmpty()) {
                    joinError = "No recoverable trusted devices were found. Encrypted iCloud data was not reset."
                }
            }.onFailure {
                joinError = it.message ?: "Unable to fetch trusted devices"
            }
        }
    }

    fun joinSelectedBottle() {
        val live = pushState ?: return
        val bottle = selectedBottle ?: return
        if (joiningClique || trustedDevicePasscode.isEmpty()) return
        joiningClique = true
        joinError = null
        scope.launch {
            if (!unlockICloudKeychain(context)) {
                joiningClique = false
                joinError = "iCloud Keychain unlock was cancelled or unavailable"
                return@launch
            }
            val recoveryCode = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    live.joinCliqueWithBottle(
                        bottle.escrowData,
                        trustedDevicePasscode,
                        recoveryCode,
                    )
                    check(live.isInClique()) { "Apple did not confirm iCloud Keychain membership" }
                }
            }
            joiningClique = false
            result.onSuccess {
                context.getSharedPreferences(NATIVE_SETUP_PREFS, android.content.Context.MODE_PRIVATE)
                    .edit().putString(KEY_KEYCHAIN_RECOVERY_CODE, recoveryCode).apply()
                trustedDevicePasscode = ""
                showCliqueJoin = false
                newRecoveryCode = recoveryCode
                inClique = true
                cliqueRefresh += 1
                syncAllHistory()
            }.onFailure {
                joinError = it.message ?: "Unable to join iCloud Keychain"
            }
        }
    }

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
    // Self-update (GitHub Releases feed + PackageInstaller)
    // ------------------------------------------------------------------
    var updateBusy by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateError by remember { mutableStateOf<String?>(null) }
    var updateRefresh by remember { mutableStateOf(0) }
    val pendingUpdate = remember(updateRefresh) {
        UpdateCoordinator.pendingUpdate(context)
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
        when (val installResult = UpdateCoordinator.installNow(context)) {
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

    var isDefaultSmsApp by remember { mutableStateOf(SmsRole.isHeld(context)) }
    val smsRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isDefaultSmsApp = SmsRole.isHeld(context)
    }

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
            if (result.isSuccess || CoreGraph.restoreRequiresRestart()) {
                // CoreGraph's lazy singletons (and the open store) cannot be
                // rebuilt in place, so the process restarts to load the
                // restored data. A failure after pre-swap shutdown also needs
                // a restart because the live store is already closed.
                result.exceptionOrNull()?.let {
                    backupError = it.message ?: "restore failed after shutdown"
                }
                restarting = true
                delay(2_500)
                Runtime.getRuntime().exit(0)
            } else {
                backupError = result.exceptionOrNull()?.message ?: "restore failed"
            }
        }
    }

    // Compact: collapsing headline. Medium+ (foldable inner, tablet): the
    // Messages pattern — title sits next to back so the list gets the height.
    val windowSizeClass = currentWindowAdaptiveInfoV2().windowSizeClass
    val isMediumWidth = windowSizeClass.isWidthAtLeastBreakpoint(
        WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND,
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
            if (filter == null || filter == SettingsSection.Account) SettingsGroup(
                title = if (showTitles) "Account" else null,
            ) {
                if (pushState == null) {
                    SettingsInfoItem(
                        title = "Not connected",
                        supporting = pushError ?: "Sign in from the banner on the chat list",
                        index = 0,
                        count = 1,
                        multiline = pushError != null,
                    )
                } else {
                    val error = pushError
                    val accountRows = buildList {
                        add("registration")
                        add("handles")
                        if (error != null) add("error")
                        add("signout")
                    }
                    val count = accountRows.size
                    accountRows.forEachIndexed { index, row ->
                        when (row) {
                            "registration" -> SettingsInfoItem(
                                title = "Registration",
                                supporting = connection?.regstate ?: "Checking…",
                                index = index,
                                count = count,
                            )
                            "handles" -> SettingsInfoItem(
                                title = "Handles",
                                supporting = connection?.handles?.joinToString("\n") ?: "Checking…",
                                index = index,
                                count = count,
                                multiline = true,
                            )
                            "error" -> SettingsInfoItem(
                                title = "Last push problem",
                                supporting = error.orEmpty(),
                                index = index,
                                count = count,
                                multiline = true,
                                titleColor = MaterialTheme.colorScheme.error,
                            )
                            else -> SettingsActionItem(
                                title = if (signingOut) "Signing out…" else "Sign out",
                                supporting = "Disconnect this Apple ID on this device",
                                onClick = { showSignOutConfirmation = true },
                                index = index,
                                count = count,
                                destructive = true,
                                enabled = !signingOut,
                                busy = signingOut,
                            )
                        }
                    }
                }
            }

            if (filter == null || filter == SettingsSection.ICloud) SettingsGroup(
                title = if (showTitles) "iCloud" else null,
            ) {
                val savedRecoveryCode = if (inClique == true) {
                    context.getSharedPreferences(NATIVE_SETUP_PREFS, android.content.Context.MODE_PRIVATE)
                        .getString(KEY_KEYCHAIN_RECOVERY_CODE, null)
                } else {
                    null
                }
                val historySupporting = when {
                    syncManager == null -> "Connect to enable syncing messages from iCloud"
                    cliqueError != null -> cliqueError!!
                    inClique == null -> "Checking Secure iCloud Keychain…"
                    inClique == false ->
                        "Join Secure iCloud Keychain before Messages in iCloud history can be decrypted"
                    syncProgress != null && syncing ->
                        "${historySyncWindow.title} · ${syncProgress!!.phase}: " +
                            "${syncProgress!!.chatsDone} chats, " +
                            "${syncProgress!!.messagesDone} messages, ${syncProgress!!.attachmentsDone} attachments"
                    syncSummary?.error != null -> "Sync failed: ${syncSummary!!.error}"
                    syncSummary?.cancelled == true -> "History sync stopped; progress was saved"
                    syncSummary != null ->
                        "${historySyncWindow.title} · Synced ${syncSummary!!.totalChats} chats, " +
                            "${syncSummary!!.totalMessages} messages, " +
                            "${syncSummary!!.totalAttachments} attachments " +
                            "(${syncSummary!!.chatTombstones + syncSummary!!.messageTombstones + syncSummary!!.attachmentTombstones} removed) " +
                            "in ${syncSummary!!.durationMs / 1000}s"
                    else ->
                        "${historySyncWindow.description}. Attachment files download only when opened."
                }
                val manager = syncManager
                val recoveryCode = savedRecoveryCode
                val icloudRows = buildList {
                    add("history")
                    add("limit")
                    if (manager != null && inClique == false) add("join")
                    if (manager != null && syncing) add("stop")
                    if (manager != null && !syncing && inClique == true) add("sync")
                    if (recoveryCode != null) add("recovery")
                    add("contacts")
                }
                val count = icloudRows.size
                icloudRows.forEachIndexed { index, row ->
                    when (row) {
                        "history" -> SettingsInfoItem(
                            title = "History sync",
                            supporting = historySupporting,
                            index = index,
                            count = count,
                            multiline = true,
                        )
                        "limit" -> SettingsActionItem(
                            title = "History download limit",
                            supporting = historySyncWindow.title +
                                ". Applies to new downloads; messages already on this device stay here.",
                            onClick = { showHistorySyncLimitDialog = true },
                            index = index,
                            count = count,
                            enabled = !syncing,
                            multiline = true,
                        )
                        "join" -> SettingsActionItem(
                            title = "Join iCloud Keychain",
                            supporting = "Unlock Messages in iCloud from a trusted Apple device",
                            onClick = ::openCliqueJoin,
                            index = index,
                            count = count,
                            enabled = !loadingBottles && !joiningClique,
                            busy = loadingBottles || joiningClique,
                        )
                        "stop" -> SettingsActionItem(
                            title = "Stop sync",
                            supporting = "Cancel the history download in progress",
                            onClick = CloudSyncWiring::cancelHistorySync,
                            index = index,
                            count = count,
                        )
                        "sync" -> SettingsActionItem(
                            title = "Sync selected history now",
                            supporting = "Apply ${historySyncWindow.title.lowercase()} to this full history download",
                            onClick = ::syncAllHistory,
                            index = index,
                            count = count,
                        )
                        "recovery" -> SettingsActionItem(
                            title = "Device Keychain code",
                            supporting = if (revealSavedRecoveryCode) {
                                recoveryCode.orEmpty()
                            } else {
                                "Saved on this device — tap to reveal"
                            },
                            onClick = { revealSavedRecoveryCode = !revealSavedRecoveryCode },
                            index = index,
                            count = count,
                        )
                        else -> SettingsActionItem(
                            title = if (contactSyncing) "Syncing iCloud contacts…" else "iCloud contacts",
                            supporting = contactSyncStatusText(contactStatus, pushState != null),
                            onClick = ::syncICloudContacts,
                            index = index,
                            count = count,
                            enabled = pushState != null && !contactSyncing,
                            busy = contactSyncing,
                        )
                    }
                }
            }

            if (filter == null || filter == SettingsSection.Power) SettingsGroup(
                title = if (showTitles) "Power" else null,
            ) {
                val ctx = context
                var batterySaver by remember {
                    androidx.compose.runtime.mutableStateOf(
                        app.openbubbles.nativeapp.service.BatterySaver.isEnabled(ctx))
                }
                SettingsToggleItem(
                    title = "Battery saver",
                    // Subtitle must not change with state — the switch carries
                    // that. State-dependent copy reflows the row on toggle.
                    supporting = "Check iCloud every 15 min instead of a live connection — messages may be delayed",
                    checked = batterySaver,
                    onCheckedChange = { enabled ->
                        // Flip the switch now; stopService + WorkManager +
                        // startForegroundService are binder work that stalls
                        // the tap frame if run inline.
                        batterySaver = enabled
                        scope.launch(Dispatchers.IO) {
                            app.openbubbles.nativeapp.service.BatterySaver.setEnabled(ctx, enabled)
                        }
                    },
                    index = 0,
                    count = 1,
                )
            }

            if (filter == null || filter == SettingsSection.Notifications) SettingsGroup(
                title = if (showTitles) "Notifications" else null,
            ) {
                val notifPrefs = remember { NotifPrefs(context) }
                var hidePreviews by remember { mutableStateOf(notifPrefs.hidePreviews) }
                var replyEnabled by remember { mutableStateOf(notifPrefs.replyEnabled) }
                var notifyReactions by remember { mutableStateOf(notifPrefs.notifyReactions) }
                SettingsToggleItem(
                    title = "Hide message previews",
                    supporting = "Show \"iMessage\" instead of message content on notifications",
                    checked = hidePreviews,
                    onCheckedChange = { enabled ->
                        hidePreviews = enabled
                        notifPrefs.hidePreviews = enabled
                    },
                    index = 0,
                    count = 3,
                )
                SettingsToggleItem(
                    title = "Quick reply",
                    supporting = "Show the Reply action on message notifications",
                    checked = replyEnabled,
                    onCheckedChange = { enabled ->
                        replyEnabled = enabled
                        notifPrefs.replyEnabled = enabled
                    },
                    index = 1,
                    count = 3,
                )
                SettingsToggleItem(
                    title = "Reaction notifications",
                    supporting = "Notify when someone reacts to a message",
                    checked = notifyReactions,
                    onCheckedChange = { enabled ->
                        notifyReactions = enabled
                        notifPrefs.notifyReactions = enabled
                    },
                    index = 2,
                    count = 3,
                )
            }

            if (filter == null || filter == SettingsSection.Messaging) {
            SettingsGroup(
                title = if (showTitles) "Messaging" else null,
            ) {
                var sendReadReceipts by remember {
                    mutableStateOf(messagingPrefs.sendReadReceipts)
                }
                SettingsActionItem(
                    title = "Default sending address",
                    supporting = defaultSendingHandle?.let(::sendingHandleLabel) ?: "Automatic",
                    onClick = { showDefaultSendingHandleDialog = true },
                    index = 0,
                    count = 2,
                    enabled = availableSendingHandles.isNotEmpty() || defaultSendingHandle != null,
                )
                SettingsToggleItem(
                    title = "Send read receipts",
                    supporting = "Tell people in direct iMessage chats when you read their messages",
                    checked = sendReadReceipts,
                    onCheckedChange = { enabled ->
                        sendReadReceipts = enabled
                        messagingPrefs.sendReadReceipts = enabled
                    },
                    index = 1,
                    count = 2,
                )
            }

            SettingsGroup(
                title = if (showTitles) "SMS & MMS" else null,
            ) {
                val smsCount = if (isDefaultSmsApp) 1 else 2
                SettingsInfoItem(
                    title = if (isDefaultSmsApp) "Default SMS app" else "Finish SMS setup",
                    supporting = if (isDefaultSmsApp) {
                        "OpenBubbles can receive carrier SMS, MMS, photos, and group messages"
                    } else {
                        "Set OpenBubbles as the default SMS app for reliable incoming MMS and media"
                    },
                    index = 0,
                    count = smsCount,
                    multiline = true,
                )
                if (!isDefaultSmsApp) {
                    SettingsActionItem(
                        title = "Set as default SMS app",
                        supporting = "Needed for incoming MMS and media",
                        onClick = {
                            SmsRole.requestIntent(context)?.let(smsRoleLauncher::launch)
                        },
                        index = 1,
                        count = smsCount,
                    )
                }
            }

            }
            if (filter == null) SettingsGroup(
                title = if (showTitles) "Location" else null,
            ) {
                SettingsActionItem(
                    title = "Find My",
                    supporting = "Devices, friends and items",
                    onClick = onOpenFindMy,
                    index = 0,
                    count = 1,
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
                SettingsInfoItem(
                    title = "Theme",
                    supporting = "System default",
                    index = 0,
                    count = 2,
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
                )
            }

            if (filter == null || filter == SettingsSection.Storage) {
            SettingsGroup(
                title = if (showTitles) "Storage" else null,
            ) {
                val cacheLabel = cacheBytes
                    ?.let { formatBytes(it).ifEmpty { "Empty" } }
                    ?: "Calculating…"
                SettingsInfoItem(
                    title = "Attachments on disk",
                    supporting = cacheLabel,
                    index = 0,
                    count = 2,
                )
                SettingsActionItem(
                    title = "Clear attachment cache",
                    supporting = "Removes downloaded files; they can be fetched again",
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { AppGraph.clearAttachmentCache() }
                            cacheBytes = withContext(Dispatchers.IO) { AppGraph.attachmentsCacheBytes() }
                        }
                    },
                    index = 1,
                    count = 2,
                    enabled = (cacheBytes ?: 0L) > 0L,
                )
            }

            SettingsGroup(
                title = if (showTitles) "Backup" else null,
            ) {
                val backupErrorText = backupError
                val backupStageText = backupStage
                val backupRows = buildList {
                    add("info")
                    add("export")
                    add("restore")
                    if (backupStageText != null) add("working")
                    if (restarting) add("restarting")
                    if (backupErrorText != null) add("error")
                }
                val backupCount = backupRows.size
                backupRows.forEachIndexed { backupIndex, row ->
                    when (row) {
                        "info" -> SettingsInfoItem(
                            title = "Local backup",
                            supporting = "Export the database and attachments to a zip file, or restore from one",
                            index = backupIndex,
                            count = backupCount,
                        )
                        "export" -> SettingsActionItem(
                            title = "Export backup",
                            supporting = "Save a zip of this device's messages and attachments",
                            onClick = { exportLauncher.launch(backupFileName()) },
                            index = backupIndex,
                            count = backupCount,
                            enabled = !backupBusy,
                            busy = backupBusy && backupStageText != null && !restarting,
                        )
                        "restore" -> SettingsActionItem(
                            title = "Restore backup",
                            supporting = "Replace this device's data from a zip",
                            onClick = {
                                restoreLauncher.launch(
                                    arrayOf(
                                        "application/zip",
                                        "application/x-zip-compressed",
                                        "application/octet-stream",
                                    ),
                                )
                            },
                            index = backupIndex,
                            count = backupCount,
                            enabled = !backupBusy,
                        )
                        "working" -> SettingsInfoItem(
                            title = "Working…",
                            supporting = backupStageText,
                            index = backupIndex,
                            count = backupCount,
                        )
                        "restarting" -> SettingsInfoItem(
                            title = "Restore complete",
                            supporting = "Restarting to load the restored data…",
                            index = backupIndex,
                            count = backupCount,
                        )
                        else -> SettingsInfoItem(
                            title = "Backup error",
                            supporting = backupErrorText,
                            index = backupIndex,
                            count = backupCount,
                            multiline = true,
                            titleColor = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            }
            if (filter == null || filter == SettingsSection.About) {
                val hasStatusRow = updateStatus != null || updateError != null
                val aboutCount = 1 + (if (pendingUpdate != null) 2 else 1) +
                    (if (hasStatusRow) 1 else 0)
                SettingsGroup(title = if (showTitles) "About" else null) {
                    SettingsInfoItem(
                        title = "OpenBubbles",
                        supporting = "Version ${versionName ?: "unknown"}",
                        index = 0,
                        count = aboutCount,
                    )
                    if (pendingUpdate != null) {
                        SettingsActionItem(
                            title = "Install update ${pendingUpdate.versionName}",
                            supporting = pendingUpdate.notes?.take(200)?.ifBlank { null },
                            onClick = { runInstallPending() },
                            index = 1,
                            count = aboutCount,
                            multiline = true,
                        )
                        SettingsActionItem(
                            title = "Skip this version",
                            supporting = "Hide this update until the next release",
                            onClick = {
                                UpdateSettings.deferVersionCode(context, pendingUpdate.versionCode)
                                updateRefresh++
                            },
                            index = 2,
                            count = aboutCount,
                        )
                    } else {
                        SettingsActionItem(
                            title = "Check for updates",
                            supporting = "Internal releases are served from GitHub",
                            onClick = { runUpdateCheck() },
                            index = 1,
                            count = aboutCount,
                            busy = updateBusy,
                            enabled = !updateBusy,
                        )
                    }
                    if (hasStatusRow) {
                        SettingsInfoItem(
                            title = if (updateError != null) "Update problem" else "Updates",
                            supporting = updateError ?: updateStatus ?: "",
                            index = aboutCount - 1,
                            count = aboutCount,
                            multiline = true,
                            titleColor = if (updateError != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                Color.Unspecified
                            },
                        )
                    }
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
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(
                    modifier = Modifier
                        .width(SettingsListPaneWidth)
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
                            )
                        }
                        SettingsCategoryItem(
                            title = "Find My",
                            supporting = "Devices, friends and items",
                            selected = false,
                            onClick = onOpenFindMy,
                            index = sections.size,
                            count = sections.size + 1,
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

    if (showSignOutConfirmation) {
        AlertDialog(
            onDismissRequest = {
                if (!signingOut) showSignOutConfirmation = false
            },
            title = { Text("Sign out of Apple ID?") },
            text = {
                Text(
                    "This disconnects iMessage on this device. Local message history and hardware setup stay on the device, but you'll need to sign in again to reconnect.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        signingOut = true
                        signOutError = null
                        scope.launch {
                            val result = CoreGraph.signOut(context)
                            signingOut = false
                            showSignOutConfirmation = false
                            if (result.isSuccess) {
                                onBack()
                            } else {
                                signOutError = result.exceptionOrNull()?.message ?: "Sign-out cleanup failed"
                            }
                        }
                    },
                    enabled = !signingOut,
                ) {
                    if (signingOut) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Sign out", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSignOutConfirmation = false },
                    enabled = !signingOut,
                ) { Text("Cancel") }
            },
        )
    }

    signOutError?.let { error ->
        AlertDialog(
            onDismissRequest = { signOutError = null },
            title = { Text("Sign-out incomplete") },
            text = { Text(error) },
            confirmButton = {
                TextButton(onClick = { signOutError = null }) { Text("OK") }
            },
        )
    }

    if (showDefaultSendingHandleDialog) {
        val optionCount = availableSendingHandles.size + 1
        AlertDialog(
            onDismissRequest = { showDefaultSendingHandleDialog = false },
            title = { Text("Default sending address") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Used for new conversations and replies when a chat does not already have a saved sender.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsGroup {
                        SettingsChoiceItem(
                            title = "Automatic",
                            supporting = "Use the conversation address, then the first registered address",
                            selected = defaultSendingHandle == null,
                            onClick = {
                                defaultSendingHandle = null
                                messagingPrefs.defaultSendingHandle = null
                                showDefaultSendingHandleDialog = false
                            },
                            index = 0,
                            count = optionCount,
                        )
                        availableSendingHandles.forEachIndexed { index, handle ->
                            SettingsChoiceItem(
                                title = sendingHandleLabel(handle),
                                supporting = sendingHandleType(handle),
                                selected = defaultSendingHandle == handle,
                                onClick = {
                                    defaultSendingHandle = handle
                                    messagingPrefs.defaultSendingHandle = handle
                                    showDefaultSendingHandleDialog = false
                                },
                                index = index + 1,
                                count = optionCount,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDefaultSendingHandleDialog = false }) {
                    Text("Done")
                }
            },
        )
    }

    if (showHistorySyncLimitDialog) {
        AlertDialog(
            onDismissRequest = { showHistorySyncLimitDialog = false },
            title = { Text("History download limit") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "The selected time window limits new message-history downloads. " +
                            "Chat records still sync so new messages route correctly.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsGroup {
                        HistorySyncWindow.entries.forEachIndexed { index, option ->
                            SettingsChoiceItem(
                                title = option.title,
                                supporting = option.description,
                                selected = historySyncWindow == option,
                                onClick = {
                                    historySyncWindow = option
                                    historySyncPreferences.window = option
                                },
                                index = index,
                                count = HistorySyncWindow.entries.size,
                            )
                        }
                    }
                    Text(
                        "Attachment metadata follows the selected messages, but attachment files " +
                            "download only when opened. Reducing the window does not delete local history.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistorySyncLimitDialog = false }) {
                    Text("Done")
                }
            },
        )
    }

    if (showCliqueJoin) {
        val requiredLength = selectedBottle?.numericLength?.toInt()?.takeIf { it > 0 }
        val passcodeValid = trustedDevicePasscode.isNotEmpty() &&
            (requiredLength == null || trustedDevicePasscode.length == requiredLength)
        AlertDialog(
            onDismissRequest = {
                if (!joiningClique) {
                    deviceMenuExpanded = false
                    showCliqueJoin = false
                }
            },
            title = { Text("Join iCloud Keychain") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Choose a trusted Apple device and enter its passcode. " +
                            "This unlocks Messages in iCloud without resetting encrypted data.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (loadingBottles) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    } else if (bottles.isNotEmpty()) {
                        ExposedDropdownMenuBox(
                            expanded = deviceMenuExpanded,
                            onExpandedChange = {
                                if (!joiningClique) deviceMenuExpanded = !deviceMenuExpanded
                            },
                        ) {
                            OutlinedTextField(
                                value = selectedBottle?.displayName().orEmpty(),
                                onValueChange = {},
                                label = { Text("Trusted Apple device") },
                                placeholder = { Text("Select a device") },
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(
                                        expanded = deviceMenuExpanded,
                                    )
                                },
                                readOnly = true,
                                enabled = !joiningClique,
                                singleLine = true,
                                modifier = Modifier
                                    .menuAnchor(
                                        type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                        enabled = !joiningClique,
                                    )
                                    .fillMaxWidth(),
                            )
                            ExposedDropdownMenu(
                                expanded = deviceMenuExpanded,
                                onDismissRequest = { deviceMenuExpanded = false },
                                modifier = Modifier.heightIn(max = 320.dp),
                            ) {
                                bottles.forEach { bottle ->
                                    val selected = selectedBottle == bottle
                                    DropdownMenuItem(
                                        text = { Text(bottle.displayName()) },
                                        trailingIcon = if (selected) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Filled.Check,
                                                    contentDescription = "Selected",
                                                )
                                            }
                                        } else {
                                            null
                                        },
                                        onClick = {
                                            selectedBottle = bottle
                                            deviceMenuExpanded = false
                                            trustedDevicePasscode = ""
                                            joinError = null
                                        },
                                    )
                                }
                            }
                        }
                        selectedBottle?.let {
                            OutlinedTextField(
                                value = trustedDevicePasscode,
                                onValueChange = { value ->
                                    trustedDevicePasscode = if (requiredLength != null) {
                                        value.filter(Char::isDigit).take(requiredLength)
                                    } else {
                                        value
                                    }
                                },
                                label = {
                                    Text(
                                        requiredLength?.let { "Trusted device passcode ($it digits)" }
                                            ?: "Trusted device password",
                                    )
                                },
                                enabled = !joiningClique,
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = if (requiredLength != null) {
                                        KeyboardType.NumberPassword
                                    } else {
                                        KeyboardType.Password
                                    },
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    joinError?.let { error ->
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = ::joinSelectedBottle,
                    enabled = passcodeValid && !joiningClique,
                ) {
                    if (joiningClique) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    } else {
                        Text("Join and sync")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCliqueJoin = false },
                    enabled = !joiningClique,
                ) { Text("Cancel") }
            },
        )
    }

    newRecoveryCode?.let { code ->
        AlertDialog(
            onDismissRequest = { newRecoveryCode = null },
            title = { Text("iCloud Keychain joined") },
            text = {
                Text(
                    "This device generated and saved a local iCloud Keychain recovery code: $code. " +
                        "The join used your trusted Apple device's existing escrow record. " +
                        "History sync is now running.",
                )
            },
            confirmButton = {
                TextButton(onClick = { newRecoveryCode = null }) { Text("Done") }
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

private fun sendingHandleLabel(handle: String): String = handle.substringAfter(':', handle)

private fun sendingHandleType(handle: String): String = when {
    handle.startsWith("tel:") -> "Phone number"
    handle.startsWith("mailto:") -> "Email address"
    else -> "Registered address"
}

private fun UViableBottle.displayName(): String = buildString {
    append(deviceName.ifBlank { "Trusted device" })
    if (modelClass.isNotBlank()) append(" · $modelClass")
}

private fun contactSyncStatusText(status: ICloudContactSyncStatus, connected: Boolean): String = when {
    !connected -> "Connect your Apple account to sync contact names from iCloud"
    status.error != null -> "Last sync failed: ${status.error}"
    status.lastSuccessMs == 0L -> "Sync names, phone numbers, emails, and photos directly from iCloud"
    else -> {
        val whenSynced = Instant.ofEpochMilli(status.lastSuccessMs)
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("MMM d, h:mm a"))
        "Last synced $whenSynced · ${status.imported} updated, ${status.removed} removed"
    }
}

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

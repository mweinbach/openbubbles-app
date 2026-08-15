package app.openbubbles.nativeapp.ui.settings

import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
// material3 1.5.0-alpha26: MenuAnchorType was replaced by
// ExposedDropdownMenuAnchorType, and ExposedDropdownMenu became an extension
// function on ExposedDropdownMenuBoxScope, so it needs an explicit import.
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.ICloudContactSync
import app.openbubbles.nativeapp.data.ICloudContactSyncStatus
import app.openbubbles.nativeapp.data.MessagingPrefs
import app.openbubbles.nativeapp.data.NotifPrefs
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.CloudSyncWiring
import app.openbubbles.nativeapp.data.unlockICloudKeychain
import app.openbubbles.nativeapp.sms.SmsRole
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
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

private fun describeRegstate(state: URegisterState): String = when (state) {
    is URegisterState.Registered ->
        if (state.nextS > 0) "Registered — next check-in in ${state.nextS}s" else "Registered"
    URegisterState.Registering -> "Registering…"
    is URegisterState.Failed -> "Failed: ${state.error}"
}

/**
 * Settings: iOS-style inset grouped cards — connection health (registration
 * state + handles via the live Rust push state), iCloud sync, power,
 * notifications, attachment storage maintenance, backup/restore, and an
 * about row.
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
    val pushError by PushStateHolder.lastErrorFlow.collectAsStateWithLifecycle()

    val syncManager = CloudSyncWiring.manager
    val syncProgress by syncManager?.progress?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    var syncing by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<String?>(null) }
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
        val manager = syncManager ?: return
        if (syncing) return
        syncing = true
        syncResult = null
        scope.launch {
            val summary = manager.sync(app.openbubbles.core.sync.SyncMode.FULL)
            syncing = false
            syncResult = if (summary.error != null) {
                "Sync failed: ${summary.error}"
            } else {
                CoreGraph.relinkContacts()
                CloudSyncWiring.markHistorySyncComplete(context)
                "Synced ${summary.totalChats} chats, ${summary.totalMessages} messages, " +
                    "${summary.totalAttachments} attachments " +
                    "(${summary.chatTombstones + summary.messageTombstones + summary.attachmentTombstones} removed) " +
                    "in ${summary.durationMs / 1000}s"
            }
        }
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

    // Top-level destination with a headline worth collapsing, so it gets the
    // flexible bar. Transient detail screens (Chat Info, New Message) keep the
    // small bar, which is what Material specifies for dense/pushed layouts.
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Settings") },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SectionCard(title = "Connection") {
                if (pushState == null) {
                    SettingRow(
                        title = "Not connected",
                        supporting = pushError ?: "Sign in from the banner on the chat list",
                        icon = Icons.Filled.CloudDone,
                        multiline = pushError != null,
                    )
                } else {
                    SettingRow(
                        title = "Registration",
                        supporting = connection?.regstate ?: "Checking…",
                        icon = Icons.Filled.CloudDone,
                    )
                    SettingRow(
                        title = "Handles",
                        supporting = connection?.handles?.joinToString("\n") ?: "Checking…",
                        icon = Icons.Filled.AlternateEmail,
                        multiline = true,
                    )
                    pushError?.let { error ->
                        SettingRow(
                            title = "Last push problem",
                            supporting = error,
                            icon = Icons.Filled.Warning,
                            multiline = true,
                        )
                    }
                    var signingOut by remember { androidx.compose.runtime.mutableStateOf(false) }
                    SettingActionRow(
                        title = if (signingOut) "Signing out…" else "Sign out",
                        supporting = "Disconnect this Apple ID on this device",
                        icon = Icons.AutoMirrored.Filled.Logout,
                        destructive = true,
                        enabled = !signingOut,
                        onClick = {
                            signingOut = true
                            scope.launch {
                                CoreGraph.signOut(context)
                                signingOut = false
                                onBack()
                            }
                        },
                    )
                }
            }

            SectionCard(title = "iCloud Sync") {
                if (syncManager == null) {
                    SettingRow(
                        title = "History sync",
                        supporting = "Connect to enable syncing messages from iCloud",
                        icon = Icons.Filled.CloudSync,
                    )
                } else {
                    val progress = syncProgress
                    SettingRow(
                        title = "History sync",
                        supporting = when {
                            cliqueError != null -> cliqueError!!
                            inClique == null -> "Checking Secure iCloud Keychain…"
                            inClique == false ->
                                "Join Secure iCloud Keychain before Messages in iCloud history can be decrypted"
                            progress != null && syncing ->
                                "${progress.phase}: ${progress.chatsDone} chats, " +
                                    "${progress.messagesDone} messages, ${progress.attachmentsDone} attachments"
                            syncResult != null -> syncResult!!
                            else -> "Downloads your Messages in iCloud history to this device"
                        },
                        icon = Icons.Filled.CloudSync,
                        multiline = true,
                    )
                    if (inClique == false) {
                        FilledTonalButton(
                            onClick = ::openCliqueJoin,
                            enabled = !loadingBottles && !joiningClique,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Join iCloud Keychain") }
                    } else if (syncing) {
                        TextButton(onClick = { syncManager.cancel() }) { Text("Stop") }
                    } else if (inClique == true) {
                        FilledTonalButton(
                            onClick = ::syncAllHistory,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Sync all history now") }
                    }

                    if (inClique == true) {
                        val savedRecoveryCode = context
                            .getSharedPreferences(NATIVE_SETUP_PREFS, android.content.Context.MODE_PRIVATE)
                            .getString(KEY_KEYCHAIN_RECOVERY_CODE, null)
                        if (savedRecoveryCode != null) {
                            SettingActionRow(
                                title = "Device Keychain code",
                                supporting = if (revealSavedRecoveryCode) {
                                    savedRecoveryCode
                                } else {
                                    "Saved on this device — tap to reveal"
                                },
                                icon = Icons.Filled.CloudDone,
                                onClick = { revealSavedRecoveryCode = !revealSavedRecoveryCode },
                            )
                        }
                    }
                }

                SettingActionRow(
                    title = if (contactSyncing) "Syncing iCloud contacts…" else "iCloud contacts",
                    supporting = contactSyncStatusText(contactStatus, pushState != null),
                    icon = Icons.Filled.Contacts,
                    enabled = pushState != null && !contactSyncing,
                    onClick = ::syncICloudContacts,
                )
            }

            SectionCard(title = "Power") {
                val ctx = context
                var batterySaver by remember {
                    androidx.compose.runtime.mutableStateOf(
                        app.openbubbles.nativeapp.service.BatterySaver.isEnabled(ctx))
                }
                SwitchSettingRow(
                    title = "Battery saver",
                    supporting = if (batterySaver) {
                        "Checking iCloud every 15 min — messages may be delayed"
                    } else {
                        "Live connection — instant messages, uses more battery"
                    },
                    icon = Icons.Filled.BatterySaver,
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
                var notifyReactions by remember { mutableStateOf(notifPrefs.notifyReactions) }
                SwitchSettingRow(
                    title = "Hide message previews",
                    supporting = "Show \"iMessage\" instead of message content on notifications",
                    icon = Icons.Filled.NotificationsActive,
                    checked = hidePreviews,
                    onCheckedChange = { enabled ->
                        hidePreviews = enabled
                        notifPrefs.hidePreviews = enabled
                    },
                )
                SwitchSettingRow(
                    title = "Quick reply",
                    supporting = "Show the Reply action on message notifications",
                    icon = Icons.Filled.NotificationsActive,
                    checked = replyEnabled,
                    onCheckedChange = { enabled ->
                        replyEnabled = enabled
                        notifPrefs.replyEnabled = enabled
                    },
                )
                SwitchSettingRow(
                    title = "Reaction notifications",
                    supporting = "Notify when someone reacts to a message",
                    icon = Icons.Filled.NotificationsActive,
                    checked = notifyReactions,
                    onCheckedChange = { enabled ->
                        notifyReactions = enabled
                        notifPrefs.notifyReactions = enabled
                    },
                )
            }

            SectionCard(title = "Messaging") {
                val messagingPrefs = remember { MessagingPrefs(context) }
                var sendReadReceipts by remember {
                    mutableStateOf(messagingPrefs.sendReadReceipts)
                }
                SwitchSettingRow(
                    title = "Send read receipts",
                    supporting = if (sendReadReceipts) {
                        "Tell people in direct iMessage chats when you read their messages"
                    } else {
                        "Read state syncs privately to your Apple devices"
                    },
                    icon = Icons.Filled.Check,
                    checked = sendReadReceipts,
                    onCheckedChange = { enabled ->
                        sendReadReceipts = enabled
                        messagingPrefs.sendReadReceipts = enabled
                    },
                )
            }

            SectionCard(title = "SMS & MMS") {
                SettingRow(
                    title = if (isDefaultSmsApp) "Default SMS app" else "Finish SMS setup",
                    supporting = if (isDefaultSmsApp) {
                        "OpenBubbles can receive carrier SMS, MMS, photos, and group messages"
                    } else {
                        "Set OpenBubbles as the default SMS app for reliable incoming MMS and media"
                    },
                    icon = Icons.Filled.Sms,
                    multiline = true,
                )
                if (!isDefaultSmsApp) {
                    FilledTonalButton(
                        onClick = {
                            SmsRole.requestIntent(context)?.let(smsRoleLauncher::launch)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Set as default SMS app") }
                }
            }

            SectionCard(title = "Location") {
                SettingActionRow(
                    title = "Find My",
                    supporting = "Devices, friends and items",
                    icon = Icons.Filled.LocationOn,
                    onClick = onOpenFindMy,
                )
            }

            SectionCard(title = "Appearance") {
                SettingRow(
                    title = "Theme",
                    supporting = "Follows your system's light or dark mode",
                    icon = Icons.Filled.Palette,
                )
            }

            SectionCard(title = "Storage") {
                SettingRow(
                    title = "Attachments on disk",
                    supporting = cacheBytes
                        ?.let { formatBytes(it).ifEmpty { "Empty" } }
                        ?: "Calculating…",
                    icon = Icons.Filled.Folder,
                )
                FilledTonalButton(
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) { AppGraph.clearAttachmentCache() }
                            cacheBytes = withContext(Dispatchers.IO) { AppGraph.attachmentsCacheBytes() }
                        }
                    },
                    enabled = (cacheBytes ?: 0L) > 0L,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Clear attachment cache")
                }
            }

            SectionCard(title = "Backup") {
                SettingRow(
                    title = "Local backup",
                    supporting = "Export the database and attachments to a zip file, or restore from one.",
                    icon = Icons.Filled.Archive,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Button(
                        onClick = { exportLauncher.launch(backupFileName()) },
                        enabled = !backupBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Export")
                    }
                    OutlinedButton(
                        onClick = {
                            restoreLauncher.launch(arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                            ))
                        },
                        enabled = !backupBusy,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Restore")
                    }
                }
                if (backupStage != null) {
                    SettingRow(title = "Working…", supporting = backupStage ?: "", icon = Icons.Filled.Archive)
                }
                if (restarting) {
                    SettingRow(
                        title = "Restore complete",
                        supporting = "Restarting to load the restored data…",
                        icon = Icons.Filled.Archive,
                    )
                }
                backupError?.let {
                    SettingRow(
                        title = "Backup error",
                        supporting = it,
                        icon = Icons.Filled.Archive,
                        multiline = true,
                    )
                }
            }

            SectionCard(title = "About") {
                SettingRow(
                    title = "OpenBubbles native",
                    supporting = "Version ${versionName ?: "unknown"}",
                    icon = Icons.Filled.Info,
                )
            }
        }
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

/** iOS-style inset grouped card: small header above a rounded surface. */
@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
        )
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                content()
            }
        }
    }
}

/** Tinted rounded-square leading icon (iOS settings row flavor). */
@Composable
private fun SettingsIcon(
    icon: ImageVector,
    destructive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (destructive) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = if (destructive) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        },
        modifier = modifier.size(36.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
        }
    }
}

/** Read-only row: leading icon, title, supporting text, optional trailing. */
@Composable
private fun SettingRow(
    title: String,
    supporting: String,
    icon: ImageVector? = null,
    multiline: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon?.let { SettingsIcon(it) }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (multiline) 6 else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        trailing?.invoke()
    }
}

/** Clickable row with a chevron (or spinner while busy). */
@Composable
private fun SettingActionRow(
    title: String,
    supporting: String,
    icon: ImageVector,
    onClick: () -> Unit,
    destructive: Boolean = false,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SettingsIcon(icon, destructive)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = supporting,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!enabled) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(18.dp),
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** [SettingRow] with a trailing Material3 switch (toggles). */
@Composable
private fun SwitchSettingRow(
    title: String,
    supporting: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        icon?.let { SettingsIcon(it) }
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

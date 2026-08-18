package app.openbubbles.nativeapp.ui.settings

import android.content.res.Configuration
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
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
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.adaptive.WindowAdaptiveInfo
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfoV2
import androidx.compose.material3.adaptive.separatingVerticalHingeBounds
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowSizeClass
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AppGraph
import app.openbubbles.nativeapp.data.AppearancePrefs
import app.openbubbles.nativeapp.data.AutoDownloadLimit
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.HistorySyncPreferences
import app.openbubbles.nativeapp.data.HistorySyncWindow
import app.openbubbles.nativeapp.data.ICloudContactSync
import app.openbubbles.nativeapp.data.ICloudContactSyncStatus
import app.openbubbles.nativeapp.data.MessagingPrefs
import app.openbubbles.nativeapp.data.NotifPrefs
import app.openbubbles.nativeapp.data.ProfilePrefs
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.CloudSyncWiring
import app.openbubbles.nativeapp.service.NativePushService
import app.openbubbles.nativeapp.data.unlockICloudKeychain
import app.openbubbles.nativeapp.facetime.fullScreenCallSettingsIntent
import app.openbubbles.nativeapp.facetime.shouldOfferFullScreenCallSettings
import app.openbubbles.nativeapp.sms.SmsRole
import app.openbubbles.nativeapp.update.UpdateCoordinator
import app.openbubbles.nativeapp.update.UpdateDecision
import app.openbubbles.nativeapp.update.UpdateSettings
import app.openbubbles.nativeapp.ui.adaptive.settingsTwoPaneSplit
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.common.formatRelativePast
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.URegisterState
import uniffi.rust_lib_bluebubbles.UViableBottle
import java.security.SecureRandom
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** One-shot connection snapshot for the Account section. */
private data class ConnectionInfo(
    val regstate: URegisterState,
    val handles: List<String>,
)

private const val NATIVE_SETUP_PREFS = "native_setup"
private const val KEY_KEYCHAIN_RECOVERY_CODE = "keychain_recovery_code"
private const val SHARED_FOCUS_GUID = "0f58d6c8-0d40-4b40-9d48-e4ac18e38155"

private enum class SettingsSection(
    val title: String,
    val supporting: String,
    val icon: ImageVector,
) {
    Account("Account", "Registration, handles, sign out", Icons.Filled.AccountCircle),
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
    is URegisterState.Registered ->
        if (state.nextS > 0) "Registered — next check-in in ${state.nextS}s" else "Registered"
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
    onOpenPasswords: () -> Unit = {},
    onOpenSharedAlbums: () -> Unit = {},
    archivedCount: Int = 0,
    showBackButton: Boolean = true,
    windowAdaptiveInfo: WindowAdaptiveInfo = currentWindowAdaptiveInfoV2(),
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
    val profilePrefs = remember(context) { ProfilePrefs(context) }
    val historySyncPreferences = remember(context) { HistorySyncPreferences(context) }
    var defaultSendingHandle by remember {
        mutableStateOf(messagingPrefs.defaultSendingHandle)
    }
    var showDefaultSendingHandleDialog by remember { mutableStateOf(false) }
    var historySyncWindow by remember { mutableStateOf(historySyncPreferences.window) }
    var showHistorySyncLimitDialog by remember { mutableStateOf(false) }
    var autoDownloadLimit by remember {
        mutableStateOf(AutoDownloadLimit.fromPersistedValue(messagingPrefs.autoDownloadMaxBytes))
    }
    var showAutoDownloadDialog by remember { mutableStateOf(false) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var firstName by remember { mutableStateOf(profilePrefs.firstName) }
    var lastName by remember { mutableStateOf(profilePrefs.lastName) }
    var displayName by remember { mutableStateOf(profilePrefs.displayName) }
    var avatarPath by remember { mutableStateOf(profilePrefs.avatarPath) }
    var nameAndPhotoSharing by remember { mutableStateOf(profilePrefs.nameAndPhotoSharing) }
    var shareAutomatically by remember { mutableStateOf(profilePrefs.shareAutomatically) }
    var profileSaving by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }
    var logRevision by remember { mutableIntStateOf(0) }
    var logCount by remember { mutableIntStateOf(0) }
    var logBytes by remember { mutableStateOf(0L) }
    val profilePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            avatarPath = withContext(Dispatchers.IO) {
                val destination = File(context.filesDir, "profile/avatar.img")
                destination.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destination.outputStream().use(input::copyTo)
                } ?: error("Could not read profile photo")
                destination.absolutePath
            }
        }
    }

    fun saveProfile() {
        if (profileSaving) return
        profileSaving = true
        profileError = null
        scope.launch {
            runCatching {
                profilePrefs.firstName = firstName.trim()
                profilePrefs.lastName = lastName.trim()
                profilePrefs.displayName = displayName.trim()
                profilePrefs.avatarPath = avatarPath
                if (nameAndPhotoSharing) {
                    val state = pushState ?: error("Connect to iMessage before publishing your profile")
                    val image = withContext(Dispatchers.IO) {
                        avatarPath?.let(::File)?.takeIf(File::isFile)?.readBytes()
                    }
                    val resolvedDisplayName = displayName.trim().ifBlank {
                        listOf(firstName.trim(), lastName.trim()).filter(String::isNotBlank).joinToString(" ")
                    }
                    val json = withContext(Dispatchers.IO) {
                        state.setProfile(
                            resolvedDisplayName,
                            firstName.trim(),
                            lastName.trim(),
                            image,
                            null,
                            profilePrefs.shareProfileJson,
                        )
                    }
                    profilePrefs.shareProfileJson = json
                    profilePrefs.clearSharedContacts()
                }
            }.onSuccess {
                showProfileDialog = false
            }.onFailure { profileError = it.message ?: "Could not update profile" }
            profileSaving = false
        }
    }

    LaunchedEffect(logRevision) {
        val files = withContext(Dispatchers.IO) { diagnosticLogFiles(context) }
        logCount = files.size
        logBytes = files.sumOf(File::length)
    }
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
                    .edit { putString(KEY_KEYCHAIN_RECOVERY_CODE, recoveryCode) }
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

    var isDefaultSmsApp by remember { mutableStateOf(SmsRole.isHeld(context)) }
    var offerFullScreenCalls by remember {
        mutableStateOf(shouldOfferFullScreenCallSettings(context))
    }
    val smsRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isDefaultSmsApp = SmsRole.isHeld(context)
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDefaultSmsApp = SmsRole.isHeld(context)
                offerFullScreenCalls = shouldOfferFullScreenCallSettings(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                        icon = Icons.Filled.CloudOff,
                        tone = if (pushError != null) {
                            SettingsRowTone.Error
                        } else {
                            SettingsRowTone.Neutral
                        },
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
                            "registration" -> {
                                // Icon + chip tone carry the state at a glance;
                                // the supporting text keeps the detail.
                                val reg = connection?.regstate
                                val (regIcon, regTone) = when (reg) {
                                    is URegisterState.Registered ->
                                        Icons.Filled.CheckCircle to SettingsRowTone.Active
                                    is URegisterState.Failed ->
                                        Icons.Filled.ErrorOutline to SettingsRowTone.Error
                                    else ->
                                        Icons.Filled.Sync to SettingsRowTone.Neutral
                                }
                                SettingsInfoItem(
                                    title = "Registration",
                                    supporting = reg?.let(::describeRegstate) ?: "Checking…",
                                    index = index,
                                    count = count,
                                    icon = regIcon,
                                    tone = regTone,
                                )
                            }
                            "handles" -> SettingsInfoItem(
                                title = "Handles",
                                supporting = connection?.handles?.joinToString("\n") ?: "Checking…",
                                index = index,
                                count = count,
                                multiline = true,
                                icon = Icons.Filled.AlternateEmail,
                            )
                            "error" -> SettingsInfoItem(
                                title = "Last push problem",
                                supporting = error.orEmpty(),
                                index = index,
                                count = count,
                                multiline = true,
                                titleColor = MaterialTheme.colorScheme.error,
                                icon = Icons.Filled.ErrorOutline,
                                tone = SettingsRowTone.Error,
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
                                icon = Icons.AutoMirrored.Filled.Logout,
                            )
                        }
                    }
                }
            }

            if (filter == null || filter == SettingsSection.Account) SettingsGroup(
                title = if (showTitles) "Name and photo" else null,
            ) {
                SettingsActionItem(
                    title = "My profile",
                    supporting = displayName.ifBlank {
                        listOf(firstName, lastName).filter(String::isNotBlank).joinToString(" ")
                    }.ifBlank { "Set your shared name and photo" },
                    onClick = { showProfileDialog = true },
                    index = 0,
                    count = 3,
                    icon = Icons.Filled.AccountCircle,
                )
                SettingsToggleItem(
                    title = "Name and Photo Sharing",
                    supporting = "Allow iMessage contacts to receive your saved profile",
                    checked = nameAndPhotoSharing,
                    onCheckedChange = { enabled ->
                        nameAndPhotoSharing = enabled
                        profilePrefs.nameAndPhotoSharing = enabled
                        if (enabled && profilePrefs.shareProfileJson == null) showProfileDialog = true
                    },
                    index = 1,
                    count = 3,
                    icon = Icons.Filled.Contacts,
                )
                SettingsToggleItem(
                    title = "Share Automatically",
                    supporting = "Send your profile once when you first message a direct contact",
                    checked = shareAutomatically,
                    onCheckedChange = { enabled ->
                        shareAutomatically = enabled
                        profilePrefs.shareAutomatically = enabled
                    },
                    index = 2,
                    count = 3,
                    icon = Icons.AutoMirrored.Filled.Send,
                )
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
                    add("passwords")
                    add("albums")
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
                        "passwords" -> SettingsActionItem(
                            title = "Passwords",
                            supporting = "Logins, passkeys, verification codes, Wi-Fi, and shared groups",
                            onClick = onOpenPasswords,
                            index = index,
                            count = count,
                            multiline = true,
                            icon = Icons.Filled.Password,
                        )
                        "albums" -> SettingsActionItem(
                            title = "Shared Albums",
                            supporting = "Invitations, album assets, and iCloud Shared Streams sync",
                            onClick = onOpenSharedAlbums,
                            index = index,
                            count = count,
                            multiline = true,
                            icon = Icons.Filled.PhotoAlbum,
                        )
                        "history" -> SettingsInfoItem(
                            title = "History sync",
                            supporting = historySupporting,
                            index = index,
                            count = count,
                            multiline = true,
                            icon = Icons.Filled.CloudSync,
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
                            icon = Icons.Filled.ManageHistory,
                        )
                        "join" -> SettingsActionItem(
                            title = "Join iCloud Keychain",
                            supporting = "Unlock Messages in iCloud from a trusted Apple device",
                            onClick = ::openCliqueJoin,
                            index = index,
                            count = count,
                            enabled = !loadingBottles && !joiningClique,
                            busy = loadingBottles || joiningClique,
                            icon = Icons.Filled.Key,
                        )
                        "stop" -> SettingsActionItem(
                            title = "Stop sync",
                            supporting = "Cancel the history download in progress",
                            onClick = CloudSyncWiring::cancelHistorySync,
                            index = index,
                            count = count,
                            icon = Icons.Filled.Stop,
                        )
                        "sync" -> SettingsActionItem(
                            title = "Sync selected history now",
                            supporting = "Apply ${historySyncWindow.title.lowercase()} to this full history download",
                            onClick = ::syncAllHistory,
                            index = index,
                            count = count,
                            icon = Icons.Filled.CloudDownload,
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
                            icon = Icons.Filled.Password,
                        )
                        else -> SettingsActionItem(
                            title = if (contactSyncing) "Syncing iCloud contacts…" else "iCloud contacts",
                            supporting = contactSyncStatusText(contactStatus, pushState != null),
                            onClick = ::syncICloudContacts,
                            index = index,
                            count = count,
                            enabled = pushState != null && !contactSyncing,
                            busy = contactSyncing,
                            icon = Icons.Filled.Contacts,
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
                    icon = Icons.Filled.BatterySaver,
                )
            }

            if (filter == null || filter == SettingsSection.Notifications) SettingsGroup(
                title = if (showTitles) "Notifications" else null,
            ) {
                val notifPrefs = remember { NotifPrefs(context) }
                var hidePreviews by remember { mutableStateOf(notifPrefs.hidePreviews) }
                var replyEnabled by remember { mutableStateOf(notifPrefs.replyEnabled) }
                var notifyReactions by remember { mutableStateOf(notifPrefs.notifyReactions) }
                val notifCount = if (offerFullScreenCalls) 4 else 3
                SettingsToggleItem(
                    title = "Hide message previews",
                    supporting = "Show \"iMessage\" instead of message content on notifications",
                    checked = hidePreviews,
                    onCheckedChange = { enabled ->
                        hidePreviews = enabled
                        notifPrefs.hidePreviews = enabled
                    },
                    index = 0,
                    count = notifCount,
                    icon = Icons.Filled.VisibilityOff,
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
                    count = notifCount,
                    icon = Icons.AutoMirrored.Filled.Reply,
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
                    count = notifCount,
                    icon = Icons.Filled.EmojiEmotions,
                )
                if (offerFullScreenCalls) {
                    SettingsActionItem(
                        title = "Full-screen FaceTime alerts",
                        supporting = "Android is blocking incoming calls from taking over the lock screen. Tap to allow them.",
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    fullScreenCallSettingsIntent(context.packageName),
                                )
                            }
                        },
                        index = 3,
                        count = notifCount,
                        multiline = true,
                        icon = Icons.Filled.Videocam,
                        iconTone = SettingsRowTone.Error,
                    )
                }
            }

            if (filter == null || filter == SettingsSection.Messaging) {
            SettingsGroup(
                title = if (showTitles) "Messaging" else null,
            ) {
                var sendReadReceipts by remember {
                    mutableStateOf(messagingPrefs.sendReadReceipts)
                }
                var showDeliveryTimestamps by remember {
                    mutableStateOf(messagingPrefs.showDeliveryTimestamps)
                }
                var shareFocusStatus by remember {
                    mutableStateOf(messagingPrefs.shareFocusStatus)
                }
                var sendSubjectLines by remember {
                    mutableStateOf(messagingPrefs.sendSubjectLines)
                }
                SettingsActionItem(
                    title = "Default sending address",
                    supporting = defaultSendingHandle?.let(::sendingHandleLabel) ?: "Automatic",
                    onClick = { showDefaultSendingHandleDialog = true },
                    index = 0,
                    count = 8,
                    enabled = availableSendingHandles.isNotEmpty() || defaultSendingHandle != null,
                    icon = Icons.AutoMirrored.Filled.Send,
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
                    count = 8,
                    icon = Icons.Filled.DoneAll,
                )
                SettingsToggleItem(
                    title = "Delivery timestamps",
                    supporting = "Show delivered and read times below outgoing messages",
                    checked = showDeliveryTimestamps,
                    onCheckedChange = { enabled ->
                        showDeliveryTimestamps = enabled
                        messagingPrefs.showDeliveryTimestamps = enabled
                    },
                    index = 2,
                    count = 8,
                    icon = Icons.Filled.ManageHistory,
                )
                SettingsToggleItem(
                    title = "Share Focus",
                    supporting = "Publish a silenced Focus status to iMessage contacts",
                    checked = shareFocusStatus,
                    onCheckedChange = { enabled ->
                        shareFocusStatus = enabled
                        messagingPrefs.shareFocusStatus = enabled
                        scope.launch(Dispatchers.IO) {
                            runCatching {
                                pushState?.publishStatus(if (enabled) SHARED_FOCUS_GUID else null)
                            }.onFailure {
                                withContext(Dispatchers.Main) {
                                    shareFocusStatus = !enabled
                                    messagingPrefs.shareFocusStatus = !enabled
                                }
                            }
                        }
                    },
                    index = 3,
                    count = 8,
                    icon = Icons.Filled.Notifications,
                )
                SettingsToggleItem(
                    title = "Show subject field",
                    supporting = "Add an optional subject line above the message composer",
                    checked = sendSubjectLines,
                    onCheckedChange = { enabled ->
                        sendSubjectLines = enabled
                        messagingPrefs.sendSubjectLines = enabled
                    },
                    index = 4,
                    count = 8,
                    icon = Icons.Filled.AlternateEmail,
                )
                SettingsActionItem(
                    title = "Auto-download media",
                    supporting = autoDownloadLimit.title,
                    onClick = { showAutoDownloadDialog = true },
                    index = 5,
                    count = 8,
                    icon = Icons.Filled.DownloadForOffline,
                )
                SettingsActionItem(
                    title = "Archived conversations",
                    supporting = if (archivedCount == 0) {
                        "None"
                    } else {
                        "$archivedCount archived"
                    },
                    onClick = onOpenArchived,
                    index = 6,
                    count = 8,
                    icon = Icons.Filled.Archive,
                )
                // One row for the SMS role: the chip tone says whether it is
                // active, the tap opens the system role picker either way.
                SettingsActionItem(
                    title = "SMS & MMS",
                    supporting = if (isDefaultSmsApp) {
                        "On — incoming and outgoing SMS stay in this app and in Android's message store"
                    } else {
                        "Off — set OpenBubbles as the default SMS app so carrier SMS, MMS, and media arrive here"
                    },
                    onClick = {
                        SmsRole.requestIntent(context)?.let(smsRoleLauncher::launch)
                    },
                    index = 7,
                    count = 8,
                    multiline = true,
                    icon = Icons.Filled.Sms,
                    iconTone = if (isDefaultSmsApp) {
                        SettingsRowTone.Active
                    } else {
                        SettingsRowTone.Neutral
                    },
                )
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
                SettingsInfoItem(
                    title = "Theme",
                    supporting = "System default",
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
                    if (restarting) add("restarting")
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
                            busy = backupBusy && backupStageText != null && !restarting,
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
                        "restarting" -> SettingsInfoItem(
                            title = "Restore complete",
                            supporting = "Restarting to load the restored data…",
                            index = storageIndex,
                            count = storageCount,
                            icon = Icons.Filled.RestartAlt,
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
                        "Registration" to (connection?.regstate?.let(::describeRegstate) ?: "Not connected"),
                        "Registered handles" to registeredHandles.size.toString(),
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

    if (showProfileDialog) {
        AlertDialog(
            onDismissRequest = { if (!profileSaving) showProfileDialog = false },
            title = { Text("Name and Photo Sharing") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("First name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Last name") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = displayName,
                        onValueChange = { displayName = it },
                        label = { Text("Display name") },
                        singleLine = true,
                    )
                    TextButton(onClick = { profilePhotoPicker.launch("image/*") }) {
                        Text(if (avatarPath == null) "Choose photo" else "Change photo")
                    }
                    profileError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = ::saveProfile,
                    enabled = !profileSaving && (displayName.isNotBlank() || firstName.isNotBlank() || lastName.isNotBlank()),
                ) { Text(if (profileSaving) "Saving…" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { showProfileDialog = false }, enabled = !profileSaving) {
                    Text("Cancel")
                }
            },
        )
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
                        "Used for every conversation, including ones that started on " +
                            "another address. Long-press a conversation to give it its " +
                            "own send-from address instead.",
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

    if (showAutoDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showAutoDownloadDialog = false },
            title = { Text("Auto-download media") },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        "Incoming photos, videos, and voice memos up to the selected " +
                            "size download on their own. Anything larger shows a " +
                            "download button instead.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SettingsGroup {
                        AutoDownloadLimit.entries.forEachIndexed { index, option ->
                            SettingsChoiceItem(
                                title = option.title,
                                supporting = option.description,
                                selected = autoDownloadLimit == option,
                                onClick = {
                                    autoDownloadLimit = option
                                    messagingPrefs.autoDownloadMaxBytes = option.persistedValue
                                },
                                index = index,
                                count = AutoDownloadLimit.entries.size,
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAutoDownloadDialog = false }) {
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
                        "Attachment metadata follows the selected messages; files auto-download " +
                            "up to your Auto-download media size and the rest download when opened. " +
                            "Reducing the window does not delete local history.",
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

package app.openbubbles.nativeapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Laptop
import androidx.compose.material.icons.filled.ManageHistory
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
// material3 1.5.0-alpha26: MenuAnchorType was replaced by
// ExposedDropdownMenuAnchorType, and ExposedDropdownMenu became an extension
// function on ExposedDropdownMenuBoxScope, so it needs an explicit import.
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.CircleProximityAdvertiser
import app.openbubbles.nativeapp.data.CloudSyncWiring
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.HistorySyncPreferences
import app.openbubbles.nativeapp.data.HistorySyncWindow
import app.openbubbles.nativeapp.data.ICloudContactSync
import app.openbubbles.nativeapp.data.ICloudContactSyncStatus
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.unlockICloudKeychain
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.UViableBottle
import java.security.SecureRandom
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val NATIVE_SETUP_PREFS = "native_setup"
private const val KEY_KEYCHAIN_RECOVERY_CODE = "keychain_recovery_code"

/**
 * One derived mode for the Messages-in-iCloud group. The status row's look
 * and the single contextual action row both follow from it, so the section
 * can never show conflicting mode-dependent rows at once.
 */
internal enum class ICloudSyncMode {
    /** A history download is running; the only sensible action is stopping it. */
    Syncing,

    /** Signed out or the push connection is down; nothing to act on here. */
    NotConnected,

    /** The keychain client is missing or broken on this device; offer repair. */
    KeychainUnavailable,

    /** Connected but not in the Secure iCloud Keychain yet; offer joining. */
    NotJoined,

    /** Keychain member with no sync running; offer a manual sync. */
    Ready,

    /** Membership check still in flight; no action until it resolves. */
    Checking,
}

internal fun icloudSyncMode(
    connected: Boolean,
    managerAvailable: Boolean,
    inClique: Boolean?,
    cliqueError: String?,
    syncing: Boolean,
): ICloudSyncMode = when {
    managerAvailable && syncing -> ICloudSyncMode.Syncing
    !connected -> ICloudSyncMode.NotConnected
    // Joining is pointless while the keychain client itself is broken
    // (isInClique throws "no iCloud Keychain on this state"): offer repair.
    !managerAvailable || cliqueError != null -> ICloudSyncMode.KeychainUnavailable
    inClique == false -> ICloudSyncMode.NotJoined
    inClique == true -> ICloudSyncMode.Ready
    else -> ICloudSyncMode.Checking
}

/**
 * Messages-in-iCloud and iCloud-services groups plus the Secure iCloud
 * Keychain join, repair, and history-limit dialogs. Membership state stays
 * hoisted at the screen (Diagnostics reads it too); a successful join
 * reports back through [onCliqueJoined].
 */
@Composable
internal fun rememberICloudSection(
    inClique: Boolean?,
    cliqueError: String?,
    onCliqueJoined: () -> Unit,
    onOpenSignIn: () -> Unit,
    onOpenPasswords: () -> Unit,
    onOpenSharedAlbums: () -> Unit,
): SettingsSectionSlice {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()
    var showRepairConfirmation by rememberSaveable { mutableStateOf(false) }
    var repairing by remember { mutableStateOf(false) }
    var repairError by remember { mutableStateOf<String?>(null) }
    // The emulated Mac's identity, so the user can match this device
    // against the entry Apple shows in their trusted-device / keychain list.
    var deviceInfo by remember { mutableStateOf<uniffi.rust_lib_bluebubbles.UDeviceInfo?>(null) }
    LaunchedEffect(pushState) {
        deviceInfo = pushState?.let { state ->
            withContext(Dispatchers.IO) { runCatching { state.deviceInfo() }.getOrNull() }
        }
    }
    val historySyncPreferences = remember(context) { HistorySyncPreferences(context) }
    var historySyncWindow by remember { mutableStateOf(historySyncPreferences.window) }
    var showHistorySyncLimitDialog by remember { mutableStateOf(false) }
    var showContactsToPhoneDialog by remember { mutableStateOf(false) }

    val syncManager = CloudSyncWiring.manager
    val syncProgress by syncManager?.progress?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    val syncing by CloudSyncWiring.syncing.collectAsStateWithLifecycle()
    val syncSummary by CloudSyncWiring.lastSummary.collectAsStateWithLifecycle()
    var contactSyncing by remember { mutableStateOf(false) }
    var contactStatus by remember {
        mutableStateOf(ICloudContactSync.status(context))
    }

    var showCliqueSetupMethods by remember { mutableStateOf(false) }
    var showCliqueJoin by remember { mutableStateOf(false) }
    var showNearbyJoin by remember { mutableStateOf(false) }
    var loadingBottles by remember { mutableStateOf(false) }
    var joiningClique by remember { mutableStateOf(false) }
    var bottles by remember { mutableStateOf<List<UViableBottle>>(emptyList()) }
    var selectedBottle by remember { mutableStateOf<UViableBottle?>(null) }
    var deviceMenuExpanded by remember { mutableStateOf(false) }
    var trustedDevicePasscode by remember { mutableStateOf("") }
    var joinError by remember { mutableStateOf<String?>(null) }
    var startingNearbyJoin by remember { mutableStateOf(false) }
    var completingNearbyJoin by remember { mutableStateOf(false) }
    var nearbySessionId by remember { mutableStateOf<String?>(null) }
    var nearbyApprovalCode by remember { mutableStateOf("") }
    var nearbyError by remember { mutableStateOf<String?>(null) }
    var nearbyStartRequest by remember { mutableIntStateOf(0) }
    var newRecoveryCode by remember { mutableStateOf<String?>(null) }
    var joinedWithNearbyApproval by remember { mutableStateOf(false) }
    var revealSavedRecoveryCode by remember { mutableStateOf(false) }
    val proximityAdvertiser = remember(context) { CircleProximityAdvertiser(context) }
    val latestPushState by rememberUpdatedState(pushState)
    val latestNearbySessionId by rememberUpdatedState(nearbySessionId)
    val latestNearbyVisible by rememberUpdatedState(showNearbyJoin)

    val bluetoothPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            startingNearbyJoin = true
            nearbyStartRequest += 1
        } else {
            startingNearbyJoin = false
            nearbyError = "Nearby-device approval needs Bluetooth permission. Allow it and try again."
        }
    }

    LaunchedEffect(nearbyStartRequest) {
        if (nearbyStartRequest == 0) return@LaunchedEffect
        val live = pushState
        if (live == null) {
            startingNearbyJoin = false
            nearbyError = "Apple services are not connected"
            return@LaunchedEffect
        }
        nearbyError = null
        val sessionResult = withContext(Dispatchers.IO) {
            runCatching {
                runCatching { live.cancelCliquePairing() }
                live.startCliquePairing()
            }
        }
        val sessionId = sessionResult.getOrElse { error ->
            startingNearbyJoin = false
            nearbyError = error.message ?: "Unable to start nearby-device approval"
            return@LaunchedEffect
        }
        nearbySessionId = sessionId
        val advertiseResult = proximityAdvertiser.start(sessionId) { error ->
            scope.launch {
                if (nearbySessionId == sessionId) {
                    nearbySessionId = null
                    startingNearbyJoin = false
                    nearbyError = error
                    withContext(Dispatchers.IO) { runCatching { live.cancelCliquePairing() } }
                }
            }
        }
        val advertiseError = advertiseResult.exceptionOrNull()
        if (advertiseError != null) {
            proximityAdvertiser.stop()
            nearbySessionId = null
            withContext(Dispatchers.IO) { runCatching { live.cancelCliquePairing() } }
            nearbyError = advertiseError.message ?: "Unable to advertise the nearby approval request"
        }
        startingNearbyJoin = false
    }

    DisposableEffect(proximityAdvertiser) {
        onDispose {
            proximityAdvertiser.stop()
            if (latestNearbyVisible || latestNearbySessionId != null) {
                latestPushState?.let { live ->
                    CoroutineScope(Dispatchers.IO).launch {
                        runCatching { live.cancelCliquePairing() }
                        delay(1_000)
                        runCatching { live.cancelCliquePairing() }
                    }
                }
            }
        }
    }

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
        if (pushState == null) return
        showCliqueSetupMethods = true
        joinError = null
        nearbyError = null
    }

    fun openBottleRecovery() {
        val live = pushState ?: return
        showCliqueSetupMethods = false
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
                    joinError = "No current recovery record was found. Nothing was reset; use nearby-device approval instead."
                }
            }.onFailure {
                joinError = escrowRecoveryFailure(it.message)
            }
        }
    }

    fun requestNearbyPairing() {
        if (startingNearbyJoin || completingNearbyJoin) return
        showCliqueSetupMethods = false
        showNearbyJoin = true
        nearbyApprovalCode = ""
        nearbyError = null
        val missingPermissions = proximityAdvertiser.missingPermissions()
        if (missingPermissions.isEmpty()) {
            startingNearbyJoin = true
            nearbyStartRequest += 1
        } else {
            bluetoothPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    fun cancelNearbyPairing() {
        if (startingNearbyJoin || completingNearbyJoin) return
        val live = pushState
        val hadSession = nearbySessionId != null
        proximityAdvertiser.stop()
        nearbySessionId = null
        nearbyApprovalCode = ""
        nearbyError = null
        showNearbyJoin = false
        if (hadSession && live != null) {
            scope.launch {
                withContext(Dispatchers.IO) { runCatching { live.cancelCliquePairing() } }
            }
        }
    }

    fun completeNearbyPairing() {
        val live = pushState ?: return
        if (nearbySessionId == null || nearbyApprovalCode.length != 6 || completingNearbyJoin) return
        completingNearbyJoin = true
        nearbyError = null
        scope.launch {
            if (!unlockICloudKeychain(context)) {
                completingNearbyJoin = false
                nearbyError = "iCloud Keychain unlock was cancelled or unavailable"
                return@launch
            }
            val recoveryCode = SecureRandom().nextInt(1_000_000).toString().padStart(6, '0')
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    live.completeCliquePairing(nearbyApprovalCode, recoveryCode)
                    check(live.isInClique()) { "Apple did not confirm iCloud Keychain membership" }
                }
            }
            completingNearbyJoin = false
            proximityAdvertiser.stop()
            nearbySessionId = null
            result.onSuccess {
                context.getSharedPreferences(NATIVE_SETUP_PREFS, android.content.Context.MODE_PRIVATE)
                    .edit { putString(KEY_KEYCHAIN_RECOVERY_CODE, recoveryCode) }
                nearbyApprovalCode = ""
                showNearbyJoin = false
                joinedWithNearbyApproval = true
                newRecoveryCode = recoveryCode
                onCliqueJoined()
                syncAllHistory()
            }.onFailure {
                nearbyApprovalCode = ""
                nearbyError = it.message ?: "Unable to join iCloud Keychain from the nearby device"
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
                joinedWithNearbyApproval = false
                newRecoveryCode = recoveryCode
                onCliqueJoined()
                syncAllHistory()
            }.onFailure {
                joinError = it.message ?: "Unable to join iCloud Keychain"
            }
        }
    }

    return SettingsSectionSlice(
        groups = { filter, showTitles ->
            if (filter == null || filter == SettingsSection.ICloud) {
                val syncMode = icloudSyncMode(
                    connected = pushState != null,
                    managerAvailable = syncManager != null,
                    inClique = inClique,
                    cliqueError = cliqueError,
                    syncing = syncing,
                )
                val statusSupporting = when {
                    syncManager == null -> "Connect to enable syncing messages from iCloud"
                    cliqueError != null -> cliqueError
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
                val (statusIcon, statusTone) = when (syncMode) {
                    ICloudSyncMode.Syncing -> Icons.Filled.CloudSync to SettingsRowTone.Active
                    ICloudSyncMode.NotConnected -> Icons.Filled.CloudOff to SettingsRowTone.Neutral
                    ICloudSyncMode.KeychainUnavailable ->
                        Icons.Filled.ErrorOutline to SettingsRowTone.Error
                    ICloudSyncMode.NotJoined -> Icons.Filled.Key to SettingsRowTone.Neutral
                    ICloudSyncMode.Ready -> if (syncSummary?.error != null) {
                        Icons.Filled.ErrorOutline to SettingsRowTone.Error
                    } else {
                        Icons.Filled.CheckCircle to SettingsRowTone.Active
                    }
                    ICloudSyncMode.Checking -> Icons.Filled.Sync to SettingsRowTone.Neutral
                }
                SettingsGroup(
                    title = if (showTitles) "Messages in iCloud" else null,
                ) {
                    val rows = buildList<SettingsRowContent> {
                        add { index, count ->
                            SettingsInfoItem(
                                title = "Status",
                                supporting = statusSupporting,
                                index = index,
                                count = count,
                                multiline = true,
                                icon = statusIcon,
                                tone = statusTone,
                            )
                        }
                        // Exactly one contextual action follows the status.
                        when (syncMode) {
                            ICloudSyncMode.Syncing -> add { index, count ->
                                SettingsActionItem(
                                    title = "Stop sync",
                                    supporting = "Cancel the history download in progress",
                                    onClick = CloudSyncWiring::cancelHistorySync,
                                    index = index,
                                    count = count,
                                    icon = Icons.Filled.Stop,
                                )
                            }
                            ICloudSyncMode.KeychainUnavailable -> add { index, count ->
                                SettingsActionItem(
                                    title = "Repair iCloud sync",
                                    supporting = if (cliqueError != null) {
                                        "iCloud Keychain unavailable on this device. Reset its iCloud state and sign in again to rebuild it."
                                    } else {
                                        "Reset this device's iCloud state, then sign in again to rebuild it"
                                    },
                                    onClick = { showRepairConfirmation = true },
                                    index = index,
                                    count = count,
                                    enabled = !repairing,
                                    busy = repairing,
                                    multiline = true,
                                    icon = Icons.Filled.Healing,
                                )
                            }
                            ICloudSyncMode.NotJoined -> add { index, count ->
                                SettingsActionItem(
                                    title = "Set up iCloud Passwords",
                                    supporting = "Approve nearby or recover with a trusted device passcode",
                                    onClick = ::openCliqueJoin,
                                    index = index,
                                    count = count,
                                    enabled = !loadingBottles && !joiningClique && !startingNearbyJoin && !completingNearbyJoin,
                                    busy = loadingBottles || joiningClique || startingNearbyJoin || completingNearbyJoin,
                                    icon = Icons.Filled.Key,
                                )
                            }
                            ICloudSyncMode.Ready -> add { index, count ->
                                SettingsActionItem(
                                    title = "Sync selected history now",
                                    supporting = "Apply ${historySyncWindow.title.lowercase()} to this full history download",
                                    onClick = ::syncAllHistory,
                                    index = index,
                                    count = count,
                                    icon = Icons.Filled.CloudDownload,
                                )
                            }
                            ICloudSyncMode.NotConnected, ICloudSyncMode.Checking -> Unit
                        }
                        add { index, count ->
                            SettingsActionItem(
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
                        }
                    }
                    rows.forEachIndexed { index, row -> row(index, rows.size) }
                }
                SettingsGroup(
                    title = if (showTitles) "iCloud services" else null,
                ) {
                    val savedRecoveryCode = if (inClique == true) {
                        context.getSharedPreferences(NATIVE_SETUP_PREFS, android.content.Context.MODE_PRIVATE)
                            .getString(KEY_KEYCHAIN_RECOVERY_CODE, null)
                    } else {
                        null
                    }
                    val rows = buildList<SettingsRowContent> {
                        add { index, count ->
                            SettingsActionItem(
                                title = "Passwords",
                                supporting = "Logins, passkeys, verification codes, Wi-Fi, and shared groups",
                                onClick = onOpenPasswords,
                                index = index,
                                count = count,
                                multiline = true,
                                icon = Icons.Filled.Password,
                            )
                        }
                        add { index, count ->
                            SettingsActionItem(
                                title = "Shared Albums",
                                supporting = "Invitations, album assets, and iCloud Shared Streams sync",
                                onClick = onOpenSharedAlbums,
                                index = index,
                                count = count,
                                multiline = true,
                                icon = Icons.Filled.PhotoAlbum,
                            )
                        }
                        add { index, count ->
                            SettingsActionItem(
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
                        add { index, count ->
                            SettingsActionItem(
                                title = "Save contacts to phone",
                                supporting = "Keep iCloud contacts in this phone's contact list and review differences",
                                onClick = { showContactsToPhoneDialog = true },
                                index = index,
                                count = count,
                                multiline = true,
                                icon = Icons.Filled.ContactPhone,
                            )
                        }
                        if (deviceInfo != null) {
                            add { index, count ->
                                val info = deviceInfo
                                SettingsActionItem(
                                    title = "This device in iCloud",
                                    supporting = if (info != null) {
                                        "${info.name} · Serial ${info.serial}\n" +
                                            "Tap to copy the serial to match it in your Apple device list"
                                    } else {
                                        "Loading…"
                                    },
                                    onClick = {
                                        info?.let {
                                            val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
                                            clipboard?.setPrimaryClip(
                                                android.content.ClipData.newPlainText("Device serial", it.serial),
                                            )
                                            android.widget.Toast.makeText(
                                                context,
                                                "Serial ${it.serial} copied",
                                                android.widget.Toast.LENGTH_SHORT,
                                            ).show()
                                        }
                                    },
                                    index = index,
                                    count = count,
                                    enabled = info != null,
                                    multiline = true,
                                    icon = Icons.Filled.Laptop,
                                )
                            }
                        }
                        if (savedRecoveryCode != null) {
                            add { index, count ->
                                SettingsActionItem(
                                    title = "Device Keychain code",
                                    supporting = if (revealSavedRecoveryCode) {
                                        savedRecoveryCode
                                    } else {
                                        "Saved on this device — tap to reveal"
                                    },
                                    onClick = { revealSavedRecoveryCode = !revealSavedRecoveryCode },
                                    index = index,
                                    count = count,
                                    icon = Icons.Filled.Password,
                                )
                            }
                        }
                    }
                    rows.forEachIndexed { index, row -> row(index, rows.size) }
                }
            }
        },
        dialogs = {
            if (showContactsToPhoneDialog) {
                ContactsToPhoneDialog(onDismiss = { showContactsToPhoneDialog = false })
            }

            if (showRepairConfirmation) {
                AlertDialog(
                    onDismissRequest = {
                        if (!repairing) showRepairConfirmation = false
                    },
                    title = { Text("Repair iCloud sync?") },
                    text = {
                        Text(
                            "This resets the iCloud state on this device (keychain, sync, passwords) and " +
                                "rebuilds it from your existing Apple ID session — no password needed. " +
                                "iMessage registration and local messages are kept. " +
                                "Afterwards use Join iCloud Keychain.",
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                repairing = true
                                repairError = null
                                scope.launch {
                                    val result = CoreGraph.repairICloudServices(context)
                                    repairing = false
                                    showRepairConfirmation = false
                                    if (result.isSuccess) {
                                        onOpenSignIn()
                                    } else {
                                        repairError = result.exceptionOrNull()?.message ?: "Repair failed"
                                    }
                                }
                            },
                            enabled = !repairing,
                        ) {
                            if (repairing) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            } else {
                                Text("Repair", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { showRepairConfirmation = false },
                            enabled = !repairing,
                        ) { Text("Cancel") }
                    },
                )
            }

            repairError?.let { error ->
                AlertDialog(
                    onDismissRequest = { repairError = null },
                    title = { Text("Repair incomplete") },
                    text = { Text(error) },
                    confirmButton = {
                        TextButton(onClick = { repairError = null }) { Text("OK") }
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

            if (showCliqueSetupMethods) {
                ICloudPasswordsSetupMethodDialog(
                    onNearbyApproval = ::requestNearbyPairing,
                    onDevicePasscode = ::openBottleRecovery,
                    onDismiss = { showCliqueSetupMethods = false },
                )
            }

            if (showNearbyJoin) {
                NearbyICloudApprovalDialog(
                    starting = startingNearbyJoin,
                    completing = completingNearbyJoin,
                    sessionActive = nearbySessionId != null,
                    approvalCode = nearbyApprovalCode,
                    error = nearbyError,
                    onApprovalCodeChange = { nearbyApprovalCode = it },
                    onStart = ::requestNearbyPairing,
                    onComplete = ::completeNearbyPairing,
                    onDismiss = ::cancelNearbyPairing,
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
                    title = { Text("Recover with a device passcode") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                "Choose a current trusted Apple device and enter its passcode. " +
                                    "If no usable recovery record appears, go back and use nearby-device approval.",
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
                    title = { Text("iCloud Passwords is ready") },
                    text = {
                        Text(
                            "This device generated and saved a local iCloud Keychain recovery code: $code. " +
                                if (joinedWithNearbyApproval) {
                                    "A nearby trusted Apple device approved this device. " +
                                        "OpenGarden saved its local recovery code for future recovery. "
                                } else {
                                    "The join used your trusted Apple device's existing escrow record. "
                                } +
                                "History sync is now running.",
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = { newRecoveryCode = null }) { Text("Done") }
                    },
                )
            }
        },
    )
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

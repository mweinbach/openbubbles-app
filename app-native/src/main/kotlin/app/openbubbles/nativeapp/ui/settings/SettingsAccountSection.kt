package app.openbubbles.nativeapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.ProfilePrefs
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.service.NativePushService
import app.openbubbles.nativeapp.ui.AccountConnectionAction
import app.openbubbles.nativeapp.ui.AccountConnectionTone
import app.openbubbles.nativeapp.ui.AccountConnectionUiState
import app.openbubbles.nativeapp.ui.accountConnectionUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Account and Name-and-photo groups plus the profile and sign-out dialogs. */
@Composable
internal fun rememberAccountSection(
    onOpenSignIn: () -> Unit,
    onBack: () -> Unit,
): SettingsSectionSlice {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()
    val pushError by PushStateHolder.lastErrorFlow.collectAsStateWithLifecycle()
    val registrationState by PushStateHolder.registrationStateFlow.collectAsStateWithLifecycle()
    val accountConnection = accountConnectionUiState(
        hasLiveState = pushState != null,
        registration = registrationState,
        lastError = pushError,
    )
    var showSignOutConfirmation by rememberSaveable { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf<String?>(null) }
    val profilePrefs = remember(context) { ProfilePrefs(context) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var firstName by remember { mutableStateOf(profilePrefs.firstName) }
    var lastName by remember { mutableStateOf(profilePrefs.lastName) }
    var displayName by remember { mutableStateOf(profilePrefs.displayName) }
    var avatarPath by remember { mutableStateOf(profilePrefs.avatarPath) }
    var nameAndPhotoSharing by remember { mutableStateOf(profilePrefs.nameAndPhotoSharing) }
    var shareAutomatically by remember { mutableStateOf(profilePrefs.shareAutomatically) }
    var profileSaving by remember { mutableStateOf(false) }
    var profileError by remember { mutableStateOf<String?>(null) }
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

    return SettingsSectionSlice(
        groups = { filter, showTitles ->
            if (filter == null || filter == SettingsSection.Account) SettingsGroup(
                title = if (showTitles) "Account" else null,
            ) {
                if (pushState == null) {
                    val recovery = checkNotNull(accountConnection)
                    AccountRecoverySettingsItem(
                        recovery = recovery,
                        index = 0,
                        count = 1,
                        onAction = { action ->
                            when (action) {
                                AccountConnectionAction.SignIn -> onOpenSignIn()
                                AccountConnectionAction.Retry ->
                                    NativePushService.reloadAfterLogin(context)
                            }
                        },
                    )
                } else {
                    // Actionable rows only; registration and handle details
                    // belong to Diagnostics → iMessage stats.
                    val error = pushError
                    val rows = buildList<SettingsRowContent> {
                        if (accountConnection != null) {
                            add { index, count ->
                                AccountRecoverySettingsItem(
                                    recovery = checkNotNull(accountConnection),
                                    index = index,
                                    count = count,
                                    onAction = { action ->
                                        when (action) {
                                            AccountConnectionAction.SignIn -> onOpenSignIn()
                                            AccountConnectionAction.Retry ->
                                                NativePushService.reloadAfterLogin(context)
                                        }
                                    },
                                )
                            }
                        }
                        if (error != null && accountConnection == null) {
                            add { index, count ->
                                SettingsInfoItem(
                                    title = "Last push problem",
                                    supporting = error.orEmpty(),
                                    index = index,
                                    count = count,
                                    multiline = true,
                                    titleColor = MaterialTheme.colorScheme.error,
                                    icon = Icons.Filled.ErrorOutline,
                                    tone = SettingsRowTone.Error,
                                )
                            }
                        }
                        add { index, count ->
                            SettingsActionItem(
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
                    rows.forEachIndexed { index, row -> row(index, rows.size) }
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
        },
        dialogs = {
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
        },
    )
}

@Composable
private fun AccountRecoverySettingsItem(
    recovery: AccountConnectionUiState,
    index: Int,
    count: Int,
    onAction: (AccountConnectionAction) -> Unit,
) {
    val rowTone = when (recovery.tone) {
        AccountConnectionTone.Neutral -> SettingsRowTone.Neutral
        AccountConnectionTone.Attention -> SettingsRowTone.Active
        AccountConnectionTone.Error -> SettingsRowTone.Error
    }
    val icon = when {
        recovery.busy -> Icons.Filled.Sync
        recovery.tone == AccountConnectionTone.Error -> Icons.Filled.CloudOff
        else -> Icons.Filled.AccountCircle
    }
    val action = recovery.action
    if (action != null) {
        SettingsActionItem(
            title = recovery.title,
            supporting = recovery.supporting,
            onClick = { onAction(action) },
            index = index,
            count = count,
            multiline = true,
            icon = icon,
            iconTone = rowTone,
        )
    } else {
        SettingsInfoItem(
            title = recovery.title,
            supporting = recovery.supporting,
            index = index,
            count = count,
            multiline = true,
            icon = icon,
            tone = rowTone,
        )
    }
}

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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import app.openbubbles.nativeapp.data.deleteOwnedFile
import app.openbubbles.nativeapp.data.profileImagesDirectory
import app.openbubbles.nativeapp.data.promoteProfileImage
import app.openbubbles.nativeapp.data.stageProfileImage
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
    var showSignOutConfirmation by rememberSaveable { mutableStateOf(false) }
    var signingOut by remember { mutableStateOf(false) }
    var signOutError by remember { mutableStateOf<String?>(null) }
    val profilePrefs = remember(context) { ProfilePrefs(context) }
    var showProfileDialog by rememberSaveable { mutableStateOf(false) }
    var firstName by remember { mutableStateOf(profilePrefs.firstName) }
    var lastName by remember { mutableStateOf(profilePrefs.lastName) }
    var displayName by remember { mutableStateOf(profilePrefs.displayName) }
    var avatarPath by remember { mutableStateOf(profilePrefs.avatarPath) }
    val pendingAvatarState = remember { mutableStateOf<File?>(null) }
    var pendingAvatar by pendingAvatarState
    var nameAndPhotoSharing by remember { mutableStateOf(profilePrefs.nameAndPhotoSharing) }
    var shareAutomatically by remember { mutableStateOf(profilePrefs.shareAutomatically) }
    var profileSaving by remember { mutableStateOf(false) }
    var profilePhotoPreparing by remember { mutableStateOf(false) }
    var profilePhotoGeneration by remember { mutableLongStateOf(0L) }
    var activeProfilePickerGeneration by remember { mutableStateOf<Long?>(null) }
    var profileError by remember { mutableStateOf<String?>(null) }
    fun discardPendingAvatar() {
        profilePhotoGeneration++
        activeProfilePickerGeneration = null
        profilePhotoPreparing = false
        deleteOwnedFile(pendingAvatar, profileImagesDirectory(context))
        pendingAvatar = null
        firstName = profilePrefs.firstName
        lastName = profilePrefs.lastName
        displayName = profilePrefs.displayName
        avatarPath = profilePrefs.avatarPath
    }
    val profilePhotoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val pickerGeneration = activeProfilePickerGeneration
            ?: return@rememberLauncherForActivityResult
        if (uri == null) {
            if (profilePhotoGeneration == pickerGeneration) {
                activeProfilePickerGeneration = null
                profilePhotoPreparing = false
            }
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            profileError = null
            runCatching {
                withContext(Dispatchers.IO) { stageProfileImage(context, uri) }
            }.onSuccess { staged ->
                if (profilePhotoGeneration == pickerGeneration && !profileSaving && showProfileDialog) {
                    deleteOwnedFile(pendingAvatar, profileImagesDirectory(context))
                    pendingAvatar = staged
                    avatarPath = staged.absolutePath
                } else {
                    deleteOwnedFile(staged, profileImagesDirectory(context))
                }
            }.onFailure { failure ->
                if (profilePhotoGeneration == pickerGeneration) {
                    profileError = failure.message ?: "Could not prepare profile photo"
                }
            }
            if (profilePhotoGeneration == pickerGeneration) {
                activeProfilePickerGeneration = null
                profilePhotoPreparing = false
            }
        }
    }

    DisposableEffect(profilePrefs) {
        onDispose {
            deleteOwnedFile(pendingAvatarState.value, profileImagesDirectory(context))
        }
    }

    fun saveProfile() {
        if (profileSaving || profilePhotoPreparing) return
        profileSaving = true
        profileError = null
        scope.launch {
            runCatching {
                val selectedAvatar = pendingAvatar
                val resolvedFirstName = firstName.trim()
                val resolvedLastName = lastName.trim()
                val resolvedDisplayName = displayName.trim()
                if (nameAndPhotoSharing) {
                    val state = pushState ?: error("Connect to iMessage before publishing your profile")
                    val image = withContext(Dispatchers.IO) {
                        (selectedAvatar ?: avatarPath?.let(::File))?.takeIf(File::isFile)?.readBytes()
                    }
                    val publishedName = resolvedDisplayName.ifBlank {
                        listOf(resolvedFirstName, resolvedLastName).filter(String::isNotBlank).joinToString(" ")
                    }
                    val json = withContext(Dispatchers.IO) {
                        state.setProfile(
                            publishedName,
                            resolvedFirstName,
                            resolvedLastName,
                            image,
                            null,
                            profilePrefs.shareProfileJson,
                        )
                    }
                    profilePrefs.shareProfileJson = json
                    profilePrefs.clearSharedContacts()
                }
                val committedAvatar = withContext(Dispatchers.IO) {
                    selectedAvatar?.let { promoteProfileImage(context, it).absolutePath }
                        ?: profilePrefs.avatarPath
                }
                profilePrefs.firstName = resolvedFirstName
                profilePrefs.lastName = resolvedLastName
                profilePrefs.displayName = resolvedDisplayName
                profilePrefs.avatarPath = committedAvatar
                avatarPath = committedAvatar
                pendingAvatar = null
                profilePhotoGeneration++
            }.onSuccess {
                showProfileDialog = false
            }.onFailure { profileError = it.message ?: "Could not update profile" }
            profileSaving = false
        }
    }

    return SettingsSectionSlice(
        groups = { filter, showTitles ->
            val accountConnection = accountConnectionUiState(
                hasLiveState = pushState != null,
                registration = registrationState,
                lastError = pushError,
            )
            if (filter == null || filter == SettingsSection.Account) SettingsGroup(
                title = if (showTitles) "Account" else null,
            ) {
                if (pushState == null) {
                    val recovery = accountConnection ?: accountConnectionUiState(
                        hasLiveState = false,
                        registration = registrationState,
                        lastError = pushError,
                    ) ?: AccountConnectionUiState(
                        title = "Sign in to message",
                        supporting = "Use your Apple ID to send and receive iMessages.",
                        action = AccountConnectionAction.SignIn,
                        actionLabel = "Sign in",
                    )
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
                                    recovery = accountConnection,
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
                    onClick = {
                        discardPendingAvatar()
                        showProfileDialog = true
                    },
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
                    onDismissRequest = {
                        if (!profileSaving) {
                            discardPendingAvatar()
                            showProfileDialog = false
                        }
                    },
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
                            TextButton(
                                onClick = {
                                    profilePhotoGeneration++
                                    activeProfilePickerGeneration = profilePhotoGeneration
                                    profilePhotoPreparing = true
                                    runCatching { profilePhotoPicker.launch("image/*") }
                                        .onFailure { failure ->
                                            activeProfilePickerGeneration = null
                                            profilePhotoPreparing = false
                                            profileError = failure.message ?: "Could not open photo picker"
                                        }
                                },
                                enabled = !profileSaving && !profilePhotoPreparing,
                            ) {
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
                            enabled = !profileSaving && !profilePhotoPreparing &&
                                (displayName.isNotBlank() || firstName.isNotBlank() || lastName.isNotBlank()),
                        ) { Text(if (profileSaving) "Saving…" else "Save") }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            discardPendingAvatar()
                            showProfileDialog = false
                        }, enabled = !profileSaving) {
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
                                        signOutError = "Sign-out cleanup did not finish. Try again before using another Apple ID."
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

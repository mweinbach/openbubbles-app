package app.openbubbles.nativeapp.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Password
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** What the keychain step is currently asking the user for. */
internal enum class KeychainStepStage {
    /** Waiting for Apple services to finish connecting after sign-in. */
    Connecting,

    /** Explaining what unlocking does, before any Apple call. */
    Intro,

    /** Fetching the account's trusted-device recovery records. */
    LoadingDevices,

    /** Device chosen; collecting that device's passcode. */
    Passcode,

    /** This device is already a trust-circle member. */
    Joined,
}

internal fun keychainStepStage(
    connected: Boolean,
    inClique: Boolean?,
    loadingDevices: Boolean,
    hasDevices: Boolean,
): KeychainStepStage = when {
    inClique == true -> KeychainStepStage.Joined
    // A null answer means the membership probe has not come back yet, so the
    // step must not offer to join a circle this device may already be in.
    !connected || inClique == null -> KeychainStepStage.Connecting
    loadingDevices -> KeychainStepStage.LoadingDevices
    hasDevices -> KeychainStepStage.Passcode
    else -> KeychainStepStage.Intro
}

/**
 * Step 4 — unlock encrypted iCloud data.
 *
 * Messages in iCloud, iCloud Passwords, and personal Photos are all sealed
 * behind the Apple account's end-to-end encrypted trust circle, so this is
 * where the user proves the device with an existing Apple device's passcode.
 * Skipping is always allowed: everything except history download still works,
 * and Settings offers the same join later.
 */
@Composable
internal fun KeychainStep(
    onContinue: (joined: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val owner: KeychainStepViewModel = viewModel()
    val state by owner.uiState.collectAsStateWithLifecycle()
    DisposableEffect(owner) {
        onDispose(owner::clearSensitiveState)
    }

    KeychainStepContent(
        stage = keychainStepStage(
            connected = state.connected,
            inClique = state.inClique,
            loadingDevices = state.loadingDevices,
            hasDevices = state.devices.isNotEmpty(),
        ),
        devices = state.devices,
        selectedDevice = state.selectedDevice,
        onSelectDevice = { owner.selectDevice(it.id) },
        passcode = state.passcode,
        onPasscodeChange = owner::updatePasscode,
        joining = state.joining,
        error = state.error,
        onFindDevices = owner::loadDevices,
        onJoin = owner::join,
        onContinue = { joined ->
            owner.clearSensitiveState()
            onContinue(joined)
        },
        onBack = {
            owner.clearSensitiveState()
            onBack()
        },
        modifier = modifier,
    )
}

/**
 * The step's rendering, with every fact passed in. Splitting it out keeps the
 * Apple-facing calls above and lets each stage be rendered (and screenshot
 * tested) without a live account.
 */
@Composable
internal fun KeychainStepContent(
    stage: KeychainStepStage,
    devices: List<KeychainDeviceUi>,
    selectedDevice: KeychainDeviceUi?,
    onSelectDevice: (KeychainDeviceUi) -> Unit,
    passcode: String,
    onPasscodeChange: (String) -> Unit,
    joining: Boolean,
    error: String?,
    onFindDevices: () -> Unit,
    onJoin: () -> Unit,
    onContinue: (joined: Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deviceMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize()) {
        OnboardingTopBar(onBack = onBack, activeSegment = 3, showBack = false)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = OnboardingPadding),
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (stage == KeychainStepStage.Joined) {
                    "Your iCloud data is unlocked"
                } else {
                    "Unlock your iCloud data"
                },
                style = MaterialTheme.typography.headlineMedium,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = if (stage == KeychainStepStage.Joined) {
                    "This phone is now a trusted member of your Apple account's " +
                        "encrypted circle, so your history and passwords can be read here."
                } else {
                    "Apple keeps your message history, passwords, and photos " +
                        "encrypted so only your own devices can read them. Confirm " +
                        "this phone with an Apple device you already own to add it " +
                        "to that circle."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))

            when (stage) {
                KeychainStepStage.Joined -> {
                    UnlockedFeature(
                        icon = Icons.Filled.CloudDone,
                        title = "Message history",
                        detail = "Past conversations can be downloaded from iCloud.",
                    )
                    Spacer(Modifier.height(12.dp))
                    UnlockedFeature(
                        icon = Icons.Filled.Password,
                        title = "Passwords",
                        detail = "Logins, passkeys, and verification codes are readable.",
                    )
                    Spacer(Modifier.height(12.dp))
                    UnlockedFeature(
                        icon = Icons.Filled.Photo,
                        title = "Photos",
                        detail = "Your personal iCloud library becomes browsable.",
                    )
                }

                KeychainStepStage.Connecting -> StatusCard(
                    text = "Connecting to Apple…",
                    busy = true,
                )

                KeychainStepStage.LoadingDevices -> StatusCard(
                    text = "Looking for your other Apple devices…",
                    busy = true,
                )

                KeychainStepStage.Intro -> {
                    InfoCard(
                        "You'll need the screen passcode of an iPhone, iPad, or Mac " +
                            "signed in to this same Apple Account. Nothing on those " +
                            "devices changes and nothing is reset.",
                    )
                }

                KeychainStepStage.Passcode -> {
                    ExposedDropdownMenuBox(
                        expanded = deviceMenuExpanded,
                        onExpandedChange = { if (!joining) deviceMenuExpanded = !deviceMenuExpanded },
                    ) {
                        OutlinedTextField(
                            value = selectedDevice?.displayName.orEmpty(),
                            onValueChange = {},
                            label = { Text("Your Apple device") },
                            placeholder = { Text("Select a device") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = deviceMenuExpanded)
                            },
                            readOnly = true,
                            enabled = !joining,
                            singleLine = true,
                            modifier = Modifier
                                .menuAnchor(
                                    type = ExposedDropdownMenuAnchorType.PrimaryNotEditable,
                                    enabled = !joining,
                                )
                                .fillMaxWidth(),
                        )
                        ExposedDropdownMenu(
                            expanded = deviceMenuExpanded,
                            onDismissRequest = { deviceMenuExpanded = false },
                        ) {
                            devices.forEach { device ->
                                DropdownMenuItem(
                                    text = { Text(device.displayName) },
                                    trailingIcon = if (selectedDevice == device) {
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
                                        deviceMenuExpanded = false
                                        onSelectDevice(device)
                                    },
                                )
                            }
                        }
                    }
                    val requiredLength = selectedDevice?.numericLength
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passcode,
                        onValueChange = { value ->
                            onPasscodeChange(
                                value,
                            )
                        },
                        label = {
                            Text(
                                requiredLength?.let { "Device passcode ($it digits)" }
                                    ?: "Device password",
                            )
                        },
                        supportingText = { Text("The passcode you type to unlock that device") },
                        enabled = !joining,
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

            error?.let { message ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            val primaryLabel = when (stage) {
                KeychainStepStage.Joined -> "Continue"
                KeychainStepStage.Passcode -> "Unlock"
                else -> "Confirm with an Apple device"
            }
            Button(
                onClick = when (stage) {
                    KeychainStepStage.Joined -> { { onContinue(true) } }
                    KeychainStepStage.Passcode -> onJoin
                    else -> onFindDevices
                },
                enabled = when (stage) {
                    KeychainStepStage.Joined, KeychainStepStage.Intro -> true
                    KeychainStepStage.Passcode -> !joining &&
                        selectedDevice != null &&
                        isKeychainPasscodeComplete(passcode, selectedDevice.numericLength)
                    else -> false
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                if (joining) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp))
                } else {
                    Text(text = primaryLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
            if (stage != KeychainStepStage.Joined) {
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = { onContinue(false) },
                    enabled = !joining,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Skip for now") }
                Text(
                    text = "Skipping means no history download yet. You can unlock " +
                        "any time from Settings → iCloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StatusCard(text: String, busy: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (busy) {
                Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            }
            Text(text = text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun InfoCard(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Composable
private fun UnlockedFeature(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

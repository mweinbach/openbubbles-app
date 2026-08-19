package app.openbubbles.nativeapp.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import app.openbubbles.nativeapp.data.MessagingPrefs
import app.openbubbles.nativeapp.data.SecurityPrefs

@Composable
internal fun rememberPrivacySection(): SettingsSectionSlice {
    val context = LocalContext.current
    val securityPrefs = remember(context) { SecurityPrefs(context) }
    val messagingPrefs = remember(context) { MessagingPrefs(context) }
    var appLock by remember { mutableStateOf(securityPrefs.appLockEnabled) }
    var filterUnknown by remember { mutableStateOf(messagingPrefs.filterUnknownSenders) }

    return SettingsSectionSlice(
        groups = { filter, showTitles ->
            if (filter == null || filter == SettingsSection.Privacy) SettingsGroup(
                title = if (showTitles) "Privacy" else null,
            ) {
                SettingsToggleItem(
                    title = "App lock",
                    supporting = "Require this device's screen lock when opening the app",
                    checked = appLock,
                    onCheckedChange = { enabled ->
                        appLock = enabled
                        securityPrefs.appLockEnabled = enabled
                    },
                    index = 0,
                    count = 2,
                    icon = Icons.Filled.Lock,
                )
                SettingsToggleItem(
                    title = "Filter unknown senders",
                    supporting = "Hide direct chats that are not in your contacts",
                    checked = filterUnknown,
                    onCheckedChange = { enabled ->
                        filterUnknown = enabled
                        messagingPrefs.filterUnknownSenders = enabled
                    },
                    index = 1,
                    count = 2,
                    icon = Icons.Filled.PersonOff,
                )
            }
        },
        dialogs = {},
    )
}

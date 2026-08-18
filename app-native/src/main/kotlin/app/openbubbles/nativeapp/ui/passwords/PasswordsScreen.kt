package app.openbubbles.nativeapp.ui.passwords

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.settings.SettingsActionItem
import app.openbubbles.nativeapp.ui.settings.SettingsGroup
import app.openbubbles.nativeapp.ui.settings.SettingsGroupSpacing
import app.openbubbles.nativeapp.ui.settings.SettingsInfoItem
import app.openbubbles.nativeapp.ui.settings.SettingsRowTone
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordsScreen(
    uiState: PasswordsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenICloudSettings: () -> Unit,
    onCategory: (VaultCategory) -> Unit,
    onQuery: (String) -> Unit,
    onSelect: (VaultItemUi?) -> Unit,
    onReveal: () -> Unit,
    onCopy: (String) -> Unit,
    onCreatePassword: (String, String, String, String?) -> Unit,
    onCreateGroup: (String) -> Unit,
    onAcceptInvite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreatePassword by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Passwords") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.inClique == true) {
                        IconButton(onClick = { showCreatePassword = true }, enabled = !uiState.busy) {
                            Icon(Icons.Filled.Add, contentDescription = "Create password")
                        }
                    }
                    IconButton(onClick = onRefresh, enabled = !uiState.busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            uiState.loading -> CircularProgressIndicator(modifier = Modifier.padding(padding).padding(32.dp))
            uiState.inClique != true -> CliqueRequired(
                error = uiState.error,
                onOpenICloudSettings = onOpenICloudSettings,
                modifier = Modifier.padding(padding),
            )
            else -> VaultContent(
                uiState = uiState,
                onCategory = onCategory,
                onQuery = onQuery,
                onSelect = onSelect,
                onCreateGroup = { showCreateGroup = true },
                onAcceptInvite = onAcceptInvite,
                modifier = Modifier.padding(padding),
            )
        }
    }

    uiState.selected?.let { item ->
        CredentialDialog(
            item = item,
            secret = uiState.revealedSecret,
            busy = uiState.busy,
            onReveal = onReveal,
            onCopy = onCopy,
            onDismiss = { onSelect(null) },
        )
    }
    if (showCreatePassword) {
        CreatePasswordDialog(
            groups = uiState.groups,
            busy = uiState.busy,
            onDismiss = { showCreatePassword = false },
            onCreate = { site, username, password, group ->
                onCreatePassword(site, username, password, group)
                showCreatePassword = false
            },
        )
    }
    if (showCreateGroup) {
        TextEntryDialog(
            title = "Create password group",
            label = "Group name",
            busy = uiState.busy,
            onDismiss = { showCreateGroup = false },
            onSubmit = {
                onCreateGroup(it)
                showCreateGroup = false
            },
        )
    }
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = {},
            confirmButton = { TextButton(onClick = onRefresh) { Text("Retry") } },
            dismissButton = { TextButton(onClick = onBack) { Text("Close") } },
            title = { Text("iCloud Passwords") },
            text = { Text(error) },
        )
    }
}

@Composable
private fun CliqueRequired(error: String?, onOpenICloudSettings: () -> Unit, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            SettingsGroup(title = "iCloud Keychain") {
                SettingsActionItem(
                    title = "Join iCloud Keychain",
                    supporting = error ?: "Use the existing trusted-device recovery flow in iCloud settings.",
                    onClick = onOpenICloudSettings,
                    index = 0,
                    count = 1,
                    icon = Icons.Filled.CloudOff,
                    iconTone = SettingsRowTone.Error,
                    multiline = true,
                )
            }
        }
    }
}

@Composable
private fun VaultContent(
    uiState: PasswordsUiState,
    onCategory: (VaultCategory) -> Unit,
    onQuery: (String) -> Unit,
    onSelect: (VaultItemUi) -> Unit,
    onCreateGroup: () -> Unit,
    onAcceptInvite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = filterVaultItems(uiState.items, uiState.category, uiState.query)
    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing),
    ) {
        item {
            SettingsGroup(title = "Vault") {
                val categories = VaultCategory.entries
                categories.forEachIndexed { index, category ->
                    SettingsActionItem(
                        title = category.title,
                        supporting = category.supporting(uiState),
                        onClick = { onCategory(category) },
                        index = index,
                        count = categories.size,
                        icon = category.icon,
                        iconTone = if (category == uiState.category) SettingsRowTone.Active else null,
                    )
                }
            }
        }
        if (uiState.category != VaultCategory.Groups) {
            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = onQuery,
                    label = { Text("Search") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 600.dp),
                )
            }
            item {
                SettingsGroup(title = uiState.category.title) {
                    if (filtered.isEmpty()) {
                        SettingsInfoItem(
                            title = "Nothing saved",
                            supporting = "No matching items are available.",
                            index = 0,
                            count = 1,
                            icon = uiState.category.icon,
                        )
                    } else {
                        filtered.forEachIndexed { index, item ->
                            SettingsActionItem(
                                title = item.title,
                                supporting = item.username,
                                onClick = { onSelect(item) },
                                index = index,
                                count = filtered.size,
                                icon = item.category.icon,
                            )
                        }
                    }
                }
            }
        } else {
            if (uiState.invites.isNotEmpty()) {
                item {
                    SettingsGroup(title = "Invitations") {
                        uiState.invites.forEachIndexed { index, invite ->
                            SettingsActionItem(
                                title = invite.groupName,
                                supporting = "Invited by ${invite.inviter}",
                                onClick = { onAcceptInvite(invite.id) },
                                index = index,
                                count = uiState.invites.size,
                                icon = Icons.Filled.Badge,
                                iconTone = SettingsRowTone.Active,
                            )
                        }
                    }
                }
            }
            item {
                SettingsGroup(title = "Available groups") {
                    val count = uiState.groups.size + 1
                    SettingsActionItem(
                        title = "Create group",
                        onClick = onCreateGroup,
                        index = 0,
                        count = count,
                        icon = Icons.Filled.Add,
                    )
                    uiState.groups.forEachIndexed { index, group ->
                        SettingsInfoItem(
                            title = group.name,
                            supporting = "${group.memberCount} members${if (group.owner) " • Owner" else ""}",
                            index = index + 1,
                            count = count,
                            icon = Icons.Filled.Badge,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CredentialDialog(
    item: VaultItemUi,
    secret: String?,
    busy: Boolean,
    onReveal: () -> Unit,
    onCopy: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.username?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (item.category == VaultCategory.Passkeys) {
                    Text("Passkey private keys stay protected and cannot be revealed.")
                } else if (secret == null) {
                    Text("Authenticate to reveal this ${item.category.title.lowercase()} value.")
                } else {
                    Text(secret, style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = { onCopy(secret) }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Text("Copy")
                    }
                }
            }
        },
        confirmButton = {
            if (item.category != VaultCategory.Passkeys && secret == null) {
                TextButton(onClick = onReveal, enabled = !busy) { Text("Reveal") }
            } else {
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        dismissButton = { if (secret == null) TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreatePasswordDialog(
    groups: List<VaultGroupUi>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String?) -> Unit,
) {
    var site by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var groupId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(site, { site = it }, label = { Text("Website") }, singleLine = true)
                OutlinedTextField(username, { username = it }, label = { Text("Username") }, singleLine = true)
                OutlinedTextField(
                    password,
                    { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                if (groups.isNotEmpty()) {
                    Text("Group", style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = {
                        val index = groups.indexOfFirst { it.id == groupId }
                        groupId = groups.getOrNull(index + 1)?.id
                    }) {
                        Text(groups.firstOrNull { it.id == groupId }?.name ?: "Personal")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(site, username, password, groupId) },
                enabled = !busy && site.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TextEntryDialog(
    title: String,
    label: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onSubmit(value) }, enabled = !busy && value.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val VaultCategory.title: String get() = when (this) {
    VaultCategory.Passwords -> "Web passwords"
    VaultCategory.Passkeys -> "Passkeys"
    VaultCategory.Codes -> "Codes"
    VaultCategory.Wifi -> "Wi-Fi"
    VaultCategory.Groups -> "Groups"
}

private val VaultCategory.icon get() = when (this) {
    VaultCategory.Passwords -> Icons.Filled.Public
    VaultCategory.Passkeys -> Icons.Filled.Key
    VaultCategory.Codes -> Icons.Filled.Security
    VaultCategory.Wifi -> Icons.Filled.Wifi
    VaultCategory.Groups -> Icons.Filled.Badge
}

private fun VaultCategory.supporting(state: PasswordsUiState): String = when (this) {
    VaultCategory.Passwords -> "${state.items.count { it.category == this }} saved logins"
    VaultCategory.Passkeys -> "${state.items.count { it.category == this }} cloud sign-ins"
    VaultCategory.Codes -> "${state.items.count { it.category == this }} one-time codes"
    VaultCategory.Wifi -> "${state.items.count { it.category == this }} saved networks"
    VaultCategory.Groups -> "${state.groups.size} groups • ${state.invites.size} invites"
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PasswordsPreview() {
    OpenBubblesTheme {
        PasswordsScreen(
            uiState = PasswordsUiState(
                loading = false,
                inClique = true,
                items = listOf(VaultItemUi("1", VaultCategory.Passwords, "example.com", "person@example.com")),
            ),
            onBack = {}, onRefresh = {}, onOpenICloudSettings = {}, onCategory = {}, onQuery = {},
            onSelect = {}, onReveal = {}, onCopy = {}, onCreatePassword = { _, _, _, _ -> },
            onCreateGroup = {}, onAcceptInvite = {},
        )
    }
}

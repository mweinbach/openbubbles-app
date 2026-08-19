package app.openbubbles.nativeapp.ui.passwords

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.settings.SettingsActionItem
import app.openbubbles.nativeapp.ui.settings.SettingsGroup
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
    onSelect: (VaultItemUi) -> Unit,
    onOpenGroup: (VaultGroupUi) -> Unit,
    onPrepareCreatePassword: () -> Unit,
    onCreatePassword: (String, String, String, String?) -> Unit,
    onCreateGroup: (String) -> Unit,
    onAcceptInvite: (String) -> Unit,
    onDeclineInvite: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showCreatePassword by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var inviteActions by remember { mutableStateOf<VaultInviteUi?>(null) }
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
                        IconButton(
                            onClick = {
                                onPrepareCreatePassword()
                                showCreatePassword = true
                            },
                            enabled = !uiState.busy,
                        ) {
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
                onGroupClick = onOpenGroup,
                onInviteClick = { inviteActions = it },
                modifier = Modifier.padding(padding),
            )
        }
    }

    inviteActions?.let { invite ->
        InviteActionsDialog(
            invite = invite,
            busy = uiState.busy,
            onAccept = { onAcceptInvite(invite.id); inviteActions = null },
            onDecline = { onDeclineInvite(invite.id); inviteActions = null },
            onDismiss = { inviteActions = null },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VaultContent(
    uiState: PasswordsUiState,
    onCategory: (VaultCategory) -> Unit,
    onQuery: (String) -> Unit,
    onSelect: (VaultItemUi) -> Unit,
    onCreateGroup: () -> Unit,
    onGroupClick: (VaultGroupUi) -> Unit,
    onInviteClick: (VaultInviteUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filtered = filterVaultItems(uiState.items, uiState.category, uiState.query)
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter,
    ) {
        LazyColumn(
            modifier = Modifier
                .widthIn(max = 840.dp)
                .fillMaxSize()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "categories") {
                Column(modifier = Modifier.padding(bottom = 20.dp)) {
                    Text(
                        text = "Categories",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                    )
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val columnCount = when {
                            maxWidth >= 720.dp -> 4
                            maxWidth >= 520.dp -> 3
                            else -> 2
                        }
                        val gap = 12.dp
                        val cardWidth = (maxWidth - gap * (columnCount - 1)) / columnCount
                        FlowRow(
                            maxItemsInEachRow = columnCount,
                            horizontalArrangement = Arrangement.spacedBy(gap),
                            verticalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            VaultCategory.entries.forEach { category ->
                                VaultCategoryCard(
                                    category = category,
                                    count = uiState.categoryCounts[category],
                                    selected = category == uiState.category,
                                    loading = category == uiState.category && uiState.categoryLoading,
                                    onClick = { onCategory(category) },
                                    modifier = Modifier.width(cardWidth),
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.category != VaultCategory.Groups) {
                item(key = "search") {
                    TextField(
                        value = uiState.query,
                        onValueChange = onQuery,
                        placeholder = { Text("Search ${uiState.category.title}") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null)
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 20.dp),
                    )
                }
                item(key = "section-title") {
                    VaultSectionHeader(
                        category = uiState.category,
                        count = uiState.categoryCounts[uiState.category],
                    )
                }
                when {
                    uiState.categoryLoading -> item(key = "loading") {
                        VaultLoadingRow(uiState.category)
                    }
                    filtered.isEmpty() -> item(key = "empty") {
                        SettingsInfoItem(
                            title = "Nothing saved",
                            supporting = if (uiState.query.isBlank()) {
                                "No ${uiState.category.title.lowercase()} are available."
                            } else {
                                "No matching items are available."
                            },
                            index = 0,
                            count = 1,
                            icon = uiState.category.icon,
                        )
                    }
                    else -> itemsIndexed(
                        items = filtered,
                        key = { _, item -> "${item.category}:${item.id}" },
                    ) { index, item ->
                        VaultRow(
                            title = item.title,
                            supporting = item.username,
                            onClick = { onSelect(item) },
                            index = index,
                            count = filtered.size,
                        )
                    }
                }
            } else {
                item(key = "groups-title") {
                    VaultSectionHeader(
                        category = VaultCategory.Groups,
                        count = uiState.categoryCounts[VaultCategory.Groups],
                    )
                }
                if (uiState.categoryLoading) {
                    item(key = "groups-loading") { VaultLoadingRow(VaultCategory.Groups) }
                } else {
                    if (uiState.invites.isNotEmpty()) {
                        item(key = "invites-title") {
                            VaultListLabel("Invitations")
                        }
                        itemsIndexed(
                            items = uiState.invites,
                            key = { _, invite -> "invite:${invite.id}" },
                        ) { index, invite ->
                            SettingsActionItem(
                                title = invite.groupName,
                                supporting = "Invited by ${invite.inviter}",
                                onClick = { onInviteClick(invite) },
                                index = index,
                                count = uiState.invites.size,
                                icon = Icons.Filled.Badge,
                                iconTone = SettingsRowTone.Active,
                            )
                        }
                    }
                    item(key = "available-groups-title") {
                        VaultListLabel("Available groups", topPadding = 20.dp)
                    }
                    val groupRowCount = uiState.groups.size + 1
                    item(key = "create-group") {
                        SettingsActionItem(
                            title = "Create group",
                            onClick = onCreateGroup,
                            index = 0,
                            count = groupRowCount,
                            icon = Icons.Filled.Add,
                        )
                    }
                    itemsIndexed(
                        items = uiState.groups,
                        key = { _, group -> "group:${group.id}" },
                    ) { index, group ->
                        VaultRow(
                            title = group.name,
                            supporting = "${group.memberCount} members${if (group.owner) " • Owner" else ""}",
                            onClick = { onGroupClick(group) },
                            index = index + 1,
                            count = groupRowCount,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VaultCategoryCard(
    category: VaultCategory,
    count: Int?,
    selected: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = contentColor,
        modifier = modifier
            .heightIn(min = 108.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(22.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = contentColor,
                    )
                } else {
                    Text(
                        text = count?.toString() ?: "—",
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = contentColor,
                    )
                }
            }
            Text(
                text = category.title,
                style = if (selected) {
                    MaterialTheme.typography.titleMediumEmphasized
                } else {
                    MaterialTheme.typography.titleMedium
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun VaultSectionHeader(category: VaultCategory, count: Int?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text = category.title,
            style = MaterialTheme.typography.titleLargeEmphasized,
        )
        Spacer(modifier = Modifier.weight(1f))
        count?.let {
            Text(
                text = "$it items",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VaultLoadingRow(category: VaultCategory) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
            Text("Loading ${category.title.lowercase()}…")
        }
    }
}

@Composable
private fun VaultListLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 8.dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = topPadding, bottom = 8.dp),
    )
}

/**
 * List row echoing the iOS Passwords list: a monogram identity tile, an
 * emphasized title, and supporting detail below it. Opens the entry's page.
 */
@Composable
private fun VaultRow(
    title: String,
    supporting: String?,
    onClick: () -> Unit,
    index: Int,
    count: Int,
) {
    ListItem(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        leadingContent = { VaultMonogram(title) },
        supportingContent = supporting?.let { text ->
            {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun InviteActionsDialog(
    invite: VaultInviteUi,
    busy: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(invite.groupName) },
        text = { Text("Invited by ${invite.inviter}") },
        confirmButton = { TextButton(onClick = onAccept, enabled = !busy) { Text("Accept") } },
        dismissButton = {
            TextButton(
                onClick = onDecline,
                enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text("Decline") }
        },
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
                    TextButton(
                        onClick = {
                            val index = groups.indexOfFirst { it.id == groupId }
                            groupId = groups.getOrNull(index + 1)?.id
                        },
                        enabled = !busy,
                    ) {
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
internal fun TextEntryDialog(
    title: String,
    label: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    initial: String = "",
    confirmLabel: String = "Create",
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(value, { value = it }, label = { Text(label) }, singleLine = true) },
        confirmButton = {
            TextButton(onClick = { onSubmit(value) }, enabled = !busy && value.isNotBlank()) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val VaultCategory.title: String get() = when (this) {
    VaultCategory.Passwords -> "Passwords"
    VaultCategory.Passkeys -> "Passkeys"
    VaultCategory.Codes -> "Codes"
    VaultCategory.Wifi -> "Wi-Fi"
    VaultCategory.Groups -> "Groups"
}

internal val VaultCategory.singular: String get() = when (this) {
    VaultCategory.Passwords -> "password"
    VaultCategory.Passkeys -> "passkey"
    VaultCategory.Codes -> "code"
    VaultCategory.Wifi -> "Wi-Fi password"
    VaultCategory.Groups -> "group"
}

private val VaultCategory.icon get() = when (this) {
    VaultCategory.Passwords -> Icons.Filled.Password
    VaultCategory.Passkeys -> Icons.Filled.Key
    VaultCategory.Codes -> Icons.Filled.Security
    VaultCategory.Wifi -> Icons.Filled.Wifi
    VaultCategory.Groups -> Icons.Filled.Badge
}

@Preview(name = "phone", device = Devices.PHONE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Preview(name = "foldable", device = Devices.FOLDABLE, showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PasswordsPreview() {
    OpenBubblesTheme {
        PasswordsScreen(
            uiState = PasswordsUiState(
                loading = false,
                inClique = true,
                items = listOf(VaultItemUi("1", VaultCategory.Passwords, "example.com", "person@example.com")),
                categoryCounts = mapOf(VaultCategory.Passwords to 1),
            ),
            onBack = {}, onRefresh = {}, onOpenICloudSettings = {}, onCategory = {}, onQuery = {},
            onSelect = {}, onOpenGroup = {}, onPrepareCreatePassword = {},
            onCreatePassword = { _, _, _, _ -> },
            onCreateGroup = {}, onAcceptInvite = {}, onDeclineInvite = {},
        )
    }
}

package app.openbubbles.nativeapp.ui.passwords

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.settings.SettingsActionItem
import app.openbubbles.nativeapp.ui.settings.SettingsGroup
import app.openbubbles.nativeapp.ui.settings.SettingsInfoItem
import app.openbubbles.nativeapp.ui.settings.SettingsRowTone
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews

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
    /** Clears a failure the user has read; the cached vault stays usable. */
    onDismissError: () -> Unit = {},
    /**
     * Peer-surface switcher pinned under the app bar. It only routes: revealing
     * or copying a secret still goes through this surface's own authentication.
     */
    surfaceSwitcher: @Composable (gestureEnabled: Boolean) -> Unit = {},
) {
    var showCreatePassword by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var inviteActions by remember { mutableStateOf<VaultInviteUi?>(null) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
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
                surfaceSwitcher(
                    !showCreatePassword && !showCreateGroup && inviteActions == null,
                )
            }
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
        CreatePasswordSheet(
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
        TextEntrySheet(
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
        // Dismissible: a failure here is worth reading, not worth trapping the
        // user on a screen whose cached vault is still perfectly usable.
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = { TextButton(onClick = onRefresh) { Text("Retry") } },
            dismissButton = { TextButton(onClick = onDismissError) { Text("Dismiss") } },
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
                        // Explicit rows of weighted cards rather than a flow of
                        // fixed widths: computing the width from the constraint
                        // rounded a hair over it, and every row wrapped to one
                        // card with half the screen left empty.
                        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                            VaultCategory.entries.chunked(columnCount).forEach { row ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                ) {
                                    row.forEach { category ->
                                        VaultCategoryCard(
                                            category = category,
                                            count = uiState.categoryCounts[category],
                                            selected = category == uiState.category,
                                            loading = category == uiState.category &&
                                                uiState.categoryLoading,
                                            onClick = { onCategory(category) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                    // A short final row keeps its cards the same
                                    // size as the rows above instead of stretching.
                                    repeat(columnCount - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
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
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        },
                        trailingIcon = {
                            if (uiState.query.isNotEmpty()) {
                                IconButton(onClick = { onQuery("") }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search")
                                }
                            }
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
                            title = if (uiState.query.isBlank()) "Nothing saved" else "No matches",
                            supporting = if (uiState.query.isBlank()) {
                                "No ${uiState.category.title.lowercase()} are available."
                            } else {
                                "Nothing in ${uiState.category.title.lowercase()} matches " +
                                    "“${uiState.query.trim()}”."
                            },
                            index = 0,
                            count = 1,
                            icon = uiState.category.icon,
                        )
                    }
                    else -> vaultSections(filtered).forEach { section ->
                        if (section.letter.isNotEmpty()) {
                            item(key = "letter-${uiState.category}-${section.letter}") {
                                VaultListLabel(section.letter, topPadding = 16.dp)
                            }
                        }
                        itemsIndexed(
                            items = section.items,
                            key = { _, item -> "${item.category}:${item.id}" },
                        ) { index, item ->
                            VaultRow(
                                title = item.title,
                                supporting = item.username,
                                onClick = { onSelect(item) },
                                index = index,
                                count = section.items.size,
                            )
                        }
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

/**
 * New-password form.
 *
 * A sheet rather than a dialog: this is data entry with four fields and a
 * keyboard, which a dialog cramps. The group choice is a real menu instead of a
 * button that cycled through groups one tap at a time, and the suggestion button
 * fills a generated password so the manager does the work it exists for.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreatePasswordSheet(
    groups: List<VaultGroupUi>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, String, String, String?) -> Unit,
) {
    var site by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var reveal by remember { mutableStateOf(false) }
    var groupId by remember { mutableStateOf<String?>(null) }
    var groupMenu by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("New password", style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = site,
                onValueChange = { site = it },
                label = { Text("Website") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                singleLine = true,
                visualTransformation = if (reveal) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { reveal = !reveal }) {
                        Icon(
                            imageVector = if (reveal) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = if (reveal) "Hide password" else "Show password",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        password = VaultPasswordGenerator.generate()
                        reveal = true
                    },
                ) {
                    Icon(Icons.Filled.Autorenew, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Suggest strong password")
                }
            }
            if (groups.isNotEmpty()) {
                Box {
                    OutlinedButton(onClick = { groupMenu = true }, enabled = !busy) {
                        Icon(Icons.Filled.Badge, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(groups.firstOrNull { it.id == groupId }?.name ?: "Personal")
                    }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Personal") },
                            onClick = {
                                groupId = null
                                groupMenu = false
                            },
                        )
                        groups.forEach { group ->
                            DropdownMenuItem(
                                text = { Text(group.name) },
                                onClick = {
                                    groupId = group.id
                                    groupMenu = false
                                },
                            )
                        }
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onCreate(site, username, password, groupId) },
                    enabled = !busy && site.isNotBlank() && username.isNotBlank() && password.isNotEmpty(),
                    modifier = Modifier.weight(1f),
                ) { Text("Create") }
            }
        }
    }
}

/**
 * One-field form used for creating a group, renaming it, and inviting a member.
 * A sheet for the same reason as the password form: it is typing, not a decision.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TextEntrySheet(
    title: String,
    label: String,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
    initial: String = "",
    confirmLabel: String = "Create",
) {
    var value by remember { mutableStateOf(initial) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.headlineSmall)
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onSubmit(value) },
                    enabled = !busy && value.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) { Text(confirmLabel) }
            }
        }
    }
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

@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@LightDarkPreviews
@Composable
private fun PasswordsPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        PasswordsScreen(
            uiState = PasswordsUiState(
                loading = false,
                inClique = true,
                items = listOf(VaultItemUi("1", VaultCategory.Passwords, "example.com", "person@example.com")),
                loadedCategories = setOf(VaultCategory.Passwords),
                categoryCounts = mapOf(VaultCategory.Passwords to 1),
            ),
            onBack = {}, onRefresh = {}, onOpenICloudSettings = {}, onCategory = {}, onQuery = {},
            onSelect = {}, onOpenGroup = {}, onPrepareCreatePassword = {},
            onCreatePassword = { _, _, _, _ -> },
            onCreateGroup = {}, onAcceptInvite = {}, onDeclineInvite = {},
        )
    }
}

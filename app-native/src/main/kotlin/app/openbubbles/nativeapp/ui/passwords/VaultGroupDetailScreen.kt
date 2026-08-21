package app.openbubbles.nativeapp.ui.passwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.settings.SettingsActionItem
import app.openbubbles.nativeapp.ui.settings.SettingsDetailMaxWidth
import app.openbubbles.nativeapp.ui.settings.SettingsGroup
import app.openbubbles.nativeapp.ui.settings.SettingsGroupSpacing
import app.openbubbles.nativeapp.ui.settings.SettingsInfoItem
import app.openbubbles.nativeapp.ui.settings.SettingsRowTone
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews

/**
 * One shared-password group as its own page, in the same iOS-Passwords-like
 * layout as the item detail: monogram header, then grouped cards — the member
 * roster, owner management actions, and the destructive delete/leave action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultGroupDetailScreen(
    uiState: VaultGroupDetailUiState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onInviteMember: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onDeleteOrLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRename by remember { mutableStateOf(false) }
    var showInvite by remember { mutableStateOf(false) }
    var removeTarget by remember { mutableStateOf<VaultGroupMemberUi?>(null) }
    var confirmDeleteOrLeave by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.closed) {
        if (uiState.closed) onBack()
    }
    val group = uiState.group
    val owner = group?.owner == true
    val destructiveLabel = if (owner) "Delete Group" else "Leave Group"
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = SettingsDetailMaxWidth)
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                VaultMonogram(
                    title = uiState.name,
                    size = 72.dp,
                    textStyle = MaterialTheme.typography.headlineMediumEmphasized,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = uiState.name,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                group?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${it.memberCount} members${if (it.owner) " • Owner" else ""}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(24.dp))

                when {
                    uiState.loading -> Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 20.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        Text("Loading group…")
                    }
                    group != null -> {
                        SettingsGroup(title = "Members") {
                            group.members.forEachIndexed { index, member ->
                                GroupMemberRow(
                                    member = member,
                                    canRemove = owner && !member.currentUser,
                                    busy = uiState.busy,
                                    onRemove = { removeTarget = member },
                                    index = index,
                                    count = group.members.size,
                                )
                            }
                            if (group.members.isEmpty()) {
                                SettingsInfoItem(
                                    title = "No members yet",
                                    supporting = "People you invite appear here.",
                                    index = 0,
                                    count = 1,
                                )
                            }
                        }
                        if (owner) {
                            Spacer(Modifier.height(SettingsGroupSpacing))
                            SettingsGroup {
                                SettingsActionItem(
                                    title = "Add Member",
                                    supporting = "Invite by email or phone number",
                                    onClick = { showInvite = true },
                                    index = 0,
                                    count = 2,
                                    icon = Icons.Filled.PersonAdd,
                                    enabled = !uiState.busy,
                                )
                                SettingsActionItem(
                                    title = "Rename Group",
                                    onClick = { showRename = true },
                                    index = 1,
                                    count = 2,
                                    icon = Icons.Filled.Edit,
                                    enabled = !uiState.busy,
                                )
                            }
                        }
                        Spacer(Modifier.height(SettingsGroupSpacing))
                        SettingsGroup {
                            SettingsActionItem(
                                title = destructiveLabel,
                                onClick = { confirmDeleteOrLeave = true },
                                index = 0,
                                count = 1,
                                icon = Icons.Filled.Delete,
                                destructive = true,
                                busy = uiState.busy,
                            )
                        }
                    }
                    else -> SettingsGroup {
                        SettingsInfoItem(
                            title = "Group unavailable",
                            supporting = "This group no longer exists or has not synced yet.",
                            index = 0,
                            count = 1,
                            icon = Icons.Filled.ErrorOutline,
                        )
                    }
                }

                uiState.error?.let { error ->
                    Spacer(Modifier.height(SettingsGroupSpacing))
                    SettingsGroup {
                        SettingsInfoItem(
                            title = "iCloud Passwords",
                            supporting = error,
                            index = 0,
                            count = 1,
                            icon = Icons.Filled.ErrorOutline,
                            tone = SettingsRowTone.Error,
                            multiline = true,
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showRename) {
        TextEntrySheet(
            title = "Rename group",
            label = "Group name",
            busy = uiState.busy,
            initial = uiState.name,
            confirmLabel = "Rename",
            onDismiss = { showRename = false },
            onSubmit = {
                onRename(it)
                showRename = false
            },
        )
    }
    if (showInvite) {
        TextEntrySheet(
            title = "Invite to ${uiState.name}",
            label = "Email or phone number",
            busy = uiState.busy,
            confirmLabel = "Invite",
            onDismiss = { showInvite = false },
            onSubmit = {
                onInviteMember(it)
                showInvite = false
            },
        )
    }
    removeTarget?.let { member ->
        AlertDialog(
            onDismissRequest = { removeTarget = null },
            title = { Text("Remove ${member.displayName}?") },
            text = { Text("They will lose access to passwords shared in \"${uiState.name}\".") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveMember(member.handle)
                        removeTarget = null
                    },
                    enabled = !uiState.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removeTarget = null }) { Text("Cancel") } },
        )
    }
    if (confirmDeleteOrLeave) {
        AlertDialog(
            onDismissRequest = { confirmDeleteOrLeave = false },
            title = { Text("$destructiveLabel?") },
            text = {
                Text(
                    if (owner) {
                        "Delete \"${uiState.name}\"? Everyone loses access to its shared passwords."
                    } else {
                        "Leave \"${uiState.name}\"? You lose access to its shared passwords."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeleteOrLeave = false
                        onDeleteOrLeave()
                    },
                    enabled = !uiState.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(destructiveLabel) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteOrLeave = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GroupMemberRow(
    member: VaultGroupMemberUi,
    canRemove: Boolean,
    busy: Boolean,
    onRemove: () -> Unit,
    index: Int,
    count: Int,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        leadingContent = { VaultMonogram(member.displayName) },
        supportingContent = {
            Text(
                text = member.supportingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = if (canRemove) {
            {
                IconButton(onClick = onRemove, enabled = !busy) {
                    Icon(
                        imageVector = Icons.Filled.PersonRemove,
                        contentDescription = "Remove ${member.displayName}",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        } else {
            null
        },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = member.displayName,
            style = MaterialTheme.typography.bodyLargeEmphasized,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal val VaultGroupMemberUi.displayName: String
    get() = name?.takeIf { it.isNotBlank() } ?: handle.removePrefix("mailto:").removePrefix("tel:")

internal val VaultGroupMemberUi.supportingText: String
    get() {
        val address = handle.removePrefix("mailto:").removePrefix("tel:")
        val state = when {
            currentUser -> "You"
            joined -> "Member"
            else -> "Invited"
        }
        return if (displayName == address) state else "$address • $state"
    }

@LightDarkPreviews
@Composable
private fun VaultGroupDetailPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        VaultGroupDetailScreen(
            uiState = VaultGroupDetailUiState(
                groupId = "family",
                name = "Family",
                loading = false,
                group = VaultGroupUi(
                    id = "family",
                    name = "Family",
                    owner = true,
                    memberCount = 2,
                    members = listOf(
                        VaultGroupMemberUi("Alice", "mailto:alice@example.com", true, true),
                        VaultGroupMemberUi(null, "mailto:bob@example.com", false, false),
                    ),
                ),
            ),
            onBack = {}, onRename = {}, onInviteMember = {}, onRemoveMember = {}, onDeleteOrLeave = {},
        )
    }
}

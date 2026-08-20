package app.openbubbles.nativeapp.ui.sharedalbums

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.settings.SettingsActionItem
import app.openbubbles.nativeapp.ui.settings.SettingsGroup
import app.openbubbles.nativeapp.ui.settings.SettingsGroupSpacing
import app.openbubbles.nativeapp.ui.settings.SettingsInfoItem
import app.openbubbles.nativeapp.ui.settings.SettingsRowTone
import app.openbubbles.nativeapp.ui.settings.SettingsToggleItem
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAlbumsScreen(
    uiState: SharedAlbumsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSyncNow: () -> Unit,
    onSelect: (SharedAlbumUi?) -> Unit,
    onAccept: (String) -> Unit,
    onAcceptToken: (String) -> Unit,
    onSetSync: (SharedAlbumUi, Boolean) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showTokenDialog by remember { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("Shared Albums") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.refreshing || (uiState.busy && !uiState.assetsLoading)) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .semantics {
                                    contentDescription = if (uiState.refreshing) {
                                        "Refreshing shared albums"
                                    } else {
                                        "Updating shared albums"
                                    }
                                },
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = { showTokenDialog = true }, enabled = !uiState.busy) {
                        Icon(Icons.Filled.AddLink, contentDescription = "Enter invitation code")
                    }
                    IconButton(onClick = onSyncNow, enabled = !uiState.refreshing && !uiState.busy) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "Sync now")
                    }
                    IconButton(onClick = onRefresh, enabled = !uiState.refreshing && !uiState.busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (uiState.loading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(padding)
                    .padding(32.dp)
                    .semantics { contentDescription = "Loading shared albums" },
            )
        } else {
            SharedAlbumsContent(
                uiState = uiState,
                onSelect = onSelect,
                onAccept = onAccept,
                onSetSync = onSetSync,
                modifier = Modifier.padding(padding),
            )
        }
    }
    uiState.selected?.let { album ->
        AlbumDialog(
            album = album,
            assets = uiState.assets,
            assetsLoading = uiState.assetsLoading,
            busy = uiState.busy,
            onSetSync = onSetSync,
            onDismiss = { onSelect(null) },
        )
    }
    if (showTokenDialog) {
        var token by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Shared Album invitation") },
            text = { OutlinedTextField(token, { token = it }, label = { Text("Email link code") }, singleLine = true) },
            confirmButton = {
                TextButton(
                    onClick = { onAcceptToken(token); showTokenDialog = false },
                    enabled = token.isNotBlank() && !uiState.busy,
                ) { Text("Accept") }
            },
            dismissButton = { TextButton(onClick = { showTokenDialog = false }) { Text("Cancel") } },
        )
    }
    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text("Shared Albums") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = onClearError) { Text("Dismiss") } },
        )
    }
}

@Composable
private fun SharedAlbumsContent(
    uiState: SharedAlbumsUiState,
    onSelect: (SharedAlbumUi) -> Unit,
    onAccept: (String) -> Unit,
    onSetSync: (SharedAlbumUi, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val invitations = uiState.albums.filter { it.invitation }
    val albums = uiState.albums.filterNot { it.invitation }
    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing),
    ) {
        if (invitations.isNotEmpty()) {
            item {
                SettingsGroup(title = "Invitations") {
                    invitations.forEachIndexed { index, album ->
                        SettingsActionItem(
                            title = album.name,
                            supporting = album.owner,
                            onClick = { onAccept(album.id) },
                            enabled = !uiState.busy,
                            index = index,
                            count = invitations.size,
                            icon = Icons.Filled.PhotoAlbum,
                            iconTone = SettingsRowTone.Active,
                        )
                    }
                }
            }
        }
        item {
            SettingsGroup(title = "Albums") {
                if (albums.isEmpty()) {
                    SettingsInfoItem(
                        title = "No Shared Albums",
                        supporting = "Albums shared with this Apple ID appear here.",
                        index = 0,
                        count = 1,
                        icon = Icons.Filled.PhotoAlbum,
                    )
                } else {
                    albums.forEachIndexed { index, album ->
                        SettingsActionItem(
                            title = album.name,
                            supporting = album.syncStatus ?: "${album.assetCount} assets",
                            onClick = { onSelect(album) },
                            enabled = !uiState.busy,
                            index = index,
                            count = albums.size,
                            icon = if (album.syncing) Icons.Filled.CloudSync else Icons.Filled.PhotoAlbum,
                            iconTone = if (album.syncing) SettingsRowTone.Active else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumDialog(
    album: SharedAlbumUi,
    assets: List<SharedAlbumAssetUi>,
    assetsLoading: Boolean,
    busy: Boolean,
    onSetSync: (SharedAlbumUi, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(album.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                album.owner?.let { Text("Shared by $it") }
                album.location?.let { Text(it) }
                Text("${album.assetCount} assets")
                if (assetsLoading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .semantics { contentDescription = "Loading album assets" },
                            strokeWidth = 2.dp,
                        )
                        Text("Loading assets…")
                    }
                } else if (assets.isNotEmpty()) {
                    Text(assets.take(8).joinToString("\n") { it.filename })
                    if (assets.size > 8) Text("…and ${assets.size - 8} more")
                }
                SettingsToggleItem(
                    title = "Sync on this device",
                    checked = album.syncing,
                    onCheckedChange = { onSetSync(album, it) },
                    supporting = "Downloads album assets to the app's Pictures directory.",
                    enabled = !busy,
                    index = 0,
                    count = 1,
                    icon = Icons.Filled.Folder,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Done") } },
    )
}

@LightDarkPreviews
@Composable
private fun SharedAlbumsPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        SharedAlbumsScreen(
            uiState = SharedAlbumsUiState(
                loading = false,
                albums = listOf(SharedAlbumUi("1", "Family", "Alex", null, 24, false, true, "Synced")),
            ),
            onBack = {}, onRefresh = {}, onSyncNow = {}, onSelect = {}, onAccept = {},
            onAcceptToken = {}, onSetSync = { _, _ -> }, onClearError = {},
        )
    }
}

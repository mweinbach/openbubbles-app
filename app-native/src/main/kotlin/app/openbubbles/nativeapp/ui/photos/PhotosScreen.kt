package app.openbubbles.nativeapp.ui.photos

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.core.photos.PhotosAccess
import app.openbubbles.core.photos.PhotosAvailability
import app.openbubbles.core.photos.PhotosSnapshot
import app.openbubbles.nativeapp.ui.settings.SettingsActionItem
import app.openbubbles.nativeapp.ui.settings.SettingsGroup
import app.openbubbles.nativeapp.ui.settings.SettingsGroupSpacing
import app.openbubbles.nativeapp.ui.settings.SettingsInfoItem
import app.openbubbles.nativeapp.ui.settings.SettingsRowTone
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    uiState: PhotosUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = { Text("iCloud Photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = onRefresh,
                        enabled = !uiState.refreshing && !uiState.loading && !uiState.loadingMore,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Run Photos probe again")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        if (uiState.loading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            PhotosContent(
                uiState = uiState,
                onLoadMore = onLoadMore,
                modifier = Modifier.padding(padding),
            )
        }
    }

    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("iCloud Photos") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = onRefresh) { Text("Retry") } },
            dismissButton = { TextButton(onClick = onBack) { Text("Close") } },
        )
    }
}

@Composable
private fun PhotosContent(
    uiState: PhotosUiState,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snapshot = uiState.snapshot ?: return
    LazyColumn(
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(SettingsGroupSpacing),
    ) {
        item {
            SettingsGroup(title = "Read-only access") {
                SettingsInfoItem(
                    title = accessTitle(snapshot.access),
                    supporting = snapshot.access.detail +
                        ". Experimental metadata probe only; no media is downloaded or saved.",
                    index = 0,
                    count = 1,
                    multiline = true,
                    icon = accessIcon(snapshot.access.availability),
                    tone = accessTone(snapshot.access.availability),
                )
            }
        }
        item {
            SettingsGroup(title = "Newest metadata") {
                if (snapshot.assets.isEmpty()) {
                    SettingsInfoItem(
                        title = "No photo records loaded",
                        supporting = when (snapshot.access.availability) {
                            PhotosAvailability.Ready -> "The metadata query returned no paired photo records."
                            PhotosAvailability.Indexing -> "Try again after Apple finishes indexing this library."
                            PhotosAvailability.Unavailable -> "Sign in to an Apple ID with iCloud Photos enabled."
                        },
                        index = 0,
                        count = 1,
                        multiline = true,
                        icon = Icons.Filled.PhotoLibrary,
                    )
                } else {
                    snapshot.assets.forEachIndexed { index, asset ->
                        SettingsInfoItem(
                            title = asset.filename ?: "Photo ${index + 1}",
                            supporting = assetSupporting(asset),
                            index = index,
                            count = snapshot.assets.size,
                            multiline = true,
                            icon = when (asset.mediaKind) {
                                PhotoMediaKind.Video -> Icons.Filled.VideoLibrary
                                PhotoMediaKind.Image, PhotoMediaKind.Unknown -> Icons.Filled.PhotoLibrary
                            },
                        )
                    }
                }
            }
        }
        if (snapshot.nextCursor != null) {
            item {
                SettingsGroup(title = null) {
                    SettingsActionItem(
                        title = "Load more metadata",
                        supporting = "Continue the bounded newest-first probe",
                        onClick = onLoadMore,
                        enabled = !uiState.loadingMore && !uiState.refreshing,
                        busy = uiState.loadingMore,
                        index = 0,
                        count = 1,
                        icon = Icons.Filled.ExpandMore,
                    )
                }
            }
        }
    }
}

private fun accessTitle(access: PhotosAccess): String = when (access.availability) {
    PhotosAvailability.Ready -> "Personal library metadata available"
    PhotosAvailability.Indexing -> "Photo library indexing"
    PhotosAvailability.Unavailable -> "Personal library unavailable"
}

private fun accessIcon(availability: PhotosAvailability) = when (availability) {
    PhotosAvailability.Ready -> Icons.Filled.CloudQueue
    PhotosAvailability.Indexing -> Icons.Filled.CloudSync
    PhotosAvailability.Unavailable -> Icons.Filled.CloudOff
}

private fun accessTone(availability: PhotosAvailability) = when (availability) {
    PhotosAvailability.Ready -> SettingsRowTone.Active
    PhotosAvailability.Indexing -> SettingsRowTone.Neutral
    PhotosAvailability.Unavailable -> SettingsRowTone.Error
}

private fun assetSupporting(asset: PhotoSummary): String = buildList {
    if (asset.width != null && asset.height != null) add("${asset.width} × ${asset.height}")
    asset.originalSize?.let { add(formatBytes(it)) }
    asset.capturedAtMs?.let { add(DateFormat.getDateTimeInstance().format(Date(it))) }
    if (asset.livePhoto) add("Live Photo")
    if (asset.favorite) add("Favorite")
    if (asset.hidden) add("Hidden")
}.joinToString(" · ").ifEmpty { "Photo metadata" }

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
    else -> "$bytes B"
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun PhotosPreview() {
    OpenBubblesTheme {
        PhotosScreen(
            uiState = PhotosUiState(
                loading = false,
                snapshot = PhotosSnapshot(
                    access = PhotosAccess(
                        PhotosAvailability.Ready,
                        "Personal iCloud Photos metadata is available",
                    ),
                    assets = listOf(
                        PhotoSummary(
                            id = "master-1",
                            assetId = "asset-1",
                            filename = "IMG_1042.HEIC",
                            mediaKind = PhotoMediaKind.Image,
                            livePhoto = true,
                            width = 4032,
                            height = 3024,
                            originalSize = 4_200_000,
                            capturedAtMs = 1_700_000_000_000,
                            addedAtMs = 1_700_000_000_000,
                            favorite = true,
                            hidden = false,
                        ),
                    ),
                ),
            ),
            onBack = {},
            onRefresh = {},
            onLoadMore = {},
        )
    }
}

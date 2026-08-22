package app.openbubbles.nativeapp.ui.sharedalbums

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoAlbum
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openbubbles.core.attachment.AttachmentMedia
import app.openbubbles.nativeapp.ui.attachmentviewer.AttachmentVideoPlayer
import app.openbubbles.nativeapp.ui.attachmentviewer.openAttachmentExternally
import app.openbubbles.nativeapp.ui.common.rememberDecodedImageResult
import app.openbubbles.nativeapp.ui.common.rememberVideoPoster
import app.openbubbles.nativeapp.ui.settings.SettingsGroup
import app.openbubbles.nativeapp.ui.settings.SettingsToggleItem
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews
import java.io.File

private const val SharedAlbumMinimumZoom = 1f
private const val SharedAlbumMaximumZoom = 5f
private const val SharedAlbumDoubleTapZoom = 2.5f

/**
 * A real gallery for media already downloaded by the user's explicit sync
 * choice. Entering this destination never fetches assets or starts a sync.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedAlbumGalleryScreen(
    uiState: SharedAlbumsUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onSyncNow: () -> Unit,
    onSetSync: (SharedAlbumUi, Boolean) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val album = uiState.selected
    var viewedAssetId by rememberSaveable(album?.id) { mutableStateOf<String?>(null) }
    val viewedIndex = uiState.assets.indexOfFirst { it.id == viewedAssetId }
    if (album != null && viewedIndex >= 0) {
        SharedAlbumViewer(
            albumName = album.name,
            assets = uiState.assets,
            initialIndex = viewedIndex,
            onBack = { viewedAssetId = null },
            modifier = modifier,
        )
        return
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(
                title = {
                    Text(
                        text = album?.name ?: "Shared Album",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Shared Albums")
                    }
                },
                actions = {
                    if (uiState.refreshing || uiState.busy && !uiState.assetsLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .semantics { contentDescription = "Updating shared album" },
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(
                        onClick = onSyncNow,
                        enabled = album?.syncing == true && !uiState.busy && !uiState.refreshing,
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = "Sync album now")
                    }
                    IconButton(onClick = onRefresh, enabled = !uiState.busy && !uiState.refreshing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh album")
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        when {
            album != null -> SharedAlbumGalleryContent(
                album = album,
                uiState = uiState,
                onSelectAsset = { viewedAssetId = it.id },
                onSyncNow = onSyncNow,
                onSetSync = onSetSync,
                modifier = Modifier.padding(padding),
            )
            uiState.loading -> GalleryMessage(
                title = "Loading album",
                detail = "Reading your existing Shared Albums.",
                loading = true,
                modifier = Modifier.padding(padding),
            )
            else -> GalleryMessage(
                title = "Album unavailable",
                detail = "This album is no longer available to this Apple account.",
                actionLabel = "Refresh albums",
                onAction = onRefresh,
                modifier = Modifier.padding(padding),
            )
        }
    }

    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = onClearError,
            title = { Text(album?.name ?: "Shared Album") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = onClearError) { Text("Dismiss") } },
        )
    }
}

@Composable
private fun SharedAlbumGalleryContent(
    album: SharedAlbumUi,
    uiState: SharedAlbumsUiState,
    onSelectAsset: (SharedAlbumAssetUi) -> Unit,
    onSyncNow: () -> Unit,
    onSetSync: (SharedAlbumUi, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 116.dp),
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item(key = "album-summary", span = { GridItemSpan(maxLineSpan) }) {
            SharedAlbumSummary(
                album = album,
                availableAssets = uiState.assets.size,
                busy = uiState.busy,
                onSetSync = onSetSync,
                modifier = Modifier.padding(bottom = 13.dp),
            )
        }

        if (uiState.assetsLoading) {
            item(key = "album-loading", span = { GridItemSpan(maxLineSpan) }) {
                GalleryMessage(
                    title = "Finding your photos",
                    detail = "Checking photos and videos already saved on this device.",
                    loading = true,
                )
            }
        } else if (uiState.assets.isEmpty()) {
            item(key = "album-empty", span = { GridItemSpan(maxLineSpan) }) {
                GalleryMessage(
                    title = if (album.syncing) "No photos on this device yet" else "Photos are not synced",
                    detail = if (album.syncing) {
                        "Sync this album to download its photos and videos, or check back when syncing finishes."
                    } else {
                        "Turn on device sync to download this album's photos and videos. Opening this album never starts a download."
                    },
                    actionLabel = "Sync now".takeIf { album.syncing && !uiState.busy },
                    onAction = onSyncNow.takeIf { album.syncing && !uiState.busy },
                )
            }
        } else {
            items(items = uiState.assets, key = { it.id }) { asset ->
                SharedAlbumAssetTile(asset = asset, onClick = { onSelectAsset(asset) })
            }
        }
    }
}

@Composable
private fun SharedAlbumSummary(
    album: SharedAlbumUi,
    availableAssets: Int,
    busy: Boolean,
    onSetSync: (SharedAlbumUi, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        album.owner?.let { owner ->
            Text(
                text = "Shared by $owner",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = if (album.assetCount > 0) {
                "$availableAssets of ${album.assetCount} photos and videos available on this device"
            } else {
                "$availableAssets photos and videos available on this device"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        album.syncStatus?.let { status ->
            Text(
                text = status,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (status.startsWith("Downloading", ignoreCase = true) ||
                status.equals("Syncing", ignoreCase = true)
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = status },
                )
            }
        }
        SettingsGroup(title = "On this device") {
            SettingsToggleItem(
                title = "Sync this album",
                checked = album.syncing,
                onCheckedChange = { onSetSync(album, it) },
                supporting = "Downloads shared photos and videos only when you choose to sync.",
                enabled = !busy,
                index = 0,
                count = 1,
                icon = Icons.Filled.Folder,
            )
        }
    }
}

@Composable
private fun SharedAlbumAssetTile(
    asset: SharedAlbumAssetUi,
    onClick: () -> Unit,
) {
    val file = remember(asset.localPath) { asset.localPath?.let(::File) }
    val isVideo = AttachmentMedia.isVideo(null, null, asset.filename)
    val imageResult = if (isVideo) null else rememberDecodedImageResult(file, maxDimensionPx = 512)
    val image = if (isVideo) rememberVideoPoster(file, maxDimensionPx = 512) else imageResult?.image

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClickLabel = "View ${asset.filename}", onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image.image,
                contentDescription = asset.filename,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (imageResult?.isLoading == true) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = if (isVideo) Icons.Filled.PlayCircle else Icons.Filled.BrokenImage,
                contentDescription = asset.filename,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }

        if (isVideo && image != null) {
            Icon(
                imageVector = Icons.Filled.PlayCircle,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(24.dp),
            )
        }
    }
}

@Composable
private fun GalleryMessage(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 260.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.semantics { contentDescription = title },
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PhotoAlbum,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (actionLabel != null && onAction != null) {
                Button(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun SharedAlbumViewer(
    albumName: String,
    assets: List<SharedAlbumAssetUi>,
    initialIndex: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onBack)
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (assets.size - 1).coerceAtLeast(0)),
        pageCount = { assets.size },
    )
    var chromeVisible by rememberSaveable { mutableStateOf(true) }
    val current = assets.getOrNull(pagerState.settledPage)

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { assets[it].id },
        ) { page ->
            SharedAlbumViewerPage(
                asset = assets[page],
                controlsVisible = chromeVisible,
                playbackEnabled = page == pagerState.settledPage && !pagerState.isScrollInProgress,
                onToggleChrome = { chromeVisible = !chromeVisible },
            )
        }

        if (chromeVisible && current != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close photo",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.filename,
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$albumName · ${pagerState.settledPage + 1} of ${assets.size}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SharedAlbumViewerPage(
    asset: SharedAlbumAssetUi,
    controlsVisible: Boolean,
    playbackEnabled: Boolean,
    onToggleChrome: () -> Unit,
) {
    val context = LocalContext.current
    val file = remember(asset.localPath) { asset.localPath?.let(::File) }
    val isVideo = AttachmentMedia.isVideo(null, null, asset.filename)
    val windowSize = LocalWindowInfo.current.containerSize
    val maxDimension = ((maxOf(windowSize.width, windowSize.height) * 5) / 4)
        .coerceIn(2048, 4096)
    val imageResult = if (isVideo) null else rememberDecodedImageResult(file, maxDimension)
    var scale by remember(asset.id) { mutableFloatStateOf(SharedAlbumMinimumZoom) }
    var offset by remember(asset.id) { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(asset.id) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = {
                        if (!isVideo) {
                            scale = if (scale > SharedAlbumMinimumZoom) {
                                SharedAlbumMinimumZoom
                            } else {
                                SharedAlbumDoubleTapZoom
                            }
                            offset = Offset.Zero
                        }
                    },
                )
            }
            .then(
                if (isVideo) {
                    Modifier
                } else {
                    Modifier.pointerInput(asset.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(SharedAlbumMinimumZoom, SharedAlbumMaximumZoom)
                            offset = if (scale == SharedAlbumMinimumZoom) Offset.Zero else offset + pan
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isVideo && file != null) {
            AttachmentVideoPlayer(
                file = file,
                controlsVisible = controlsVisible,
                playbackEnabled = playbackEnabled,
                onOpenExternally = {
                    val mime = AttachmentMedia.suggestedMime(null, null, asset.filename)
                    if (!openAttachmentExternally(context, file, mime)) {
                        Toast.makeText(context, "Unable to open this video", Toast.LENGTH_SHORT).show()
                    }
                },
            )
        } else if (imageResult?.image != null) {
            Image(
                bitmap = imageResult.image.image,
                contentDescription = asset.filename,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer(
                        scaleX = scale,
                        scaleY = scale,
                        translationX = offset.x,
                        translationY = offset.y,
                    ),
            )
        } else if (imageResult?.isLoading == true) {
            CircularProgressIndicator(color = Color.White)
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
                Text(
                    text = "This ${if (isVideo) "video" else "photo"} is no longer available.",
                    color = Color.White,
                )
            }
        }
    }
}

@LightDarkPreviews
@Composable
private fun SharedAlbumGalleryPreview() {
    val album = SharedAlbumUi(
        id = "family",
        name = "Family",
        owner = "Alex",
        location = "https://apple.example/sharedstreams/",
        assetCount = 12,
        invitation = false,
        syncing = true,
        syncStatus = "Synced",
    )
    OpenBubblesTheme(dynamicColor = false) {
        SharedAlbumGalleryScreen(
            uiState = SharedAlbumsUiState(
                loading = false,
                albums = listOf(album),
                selected = album,
                assets = listOf(
                    SharedAlbumAssetUi("family/IMG_0001.jpg", "IMG_0001.jpg"),
                    SharedAlbumAssetUi("family/IMG_0002.mov", "IMG_0002.mov"),
                    SharedAlbumAssetUi("family/IMG_0003.heic", "IMG_0003.heic"),
                ),
            ),
            onBack = {},
            onRefresh = {},
            onSyncNow = {},
            onSetSync = { _, _ -> },
            onClearError = {},
        )
    }
}

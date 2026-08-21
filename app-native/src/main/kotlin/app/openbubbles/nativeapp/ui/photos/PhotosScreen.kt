package app.openbubbles.nativeapp.ui.photos

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FilterListOff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.attachment.AttachmentMedia
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.core.photos.PhotoTransfer
import app.openbubbles.core.photos.PhotoTransferState
import app.openbubbles.core.photos.PhotosAccess
import app.openbubbles.core.photos.PhotosAvailability
import androidx.core.content.ContextCompat
import app.openbubbles.core.photos.PhotosSnapshot
import app.openbubbles.nativeapp.data.photos.PhotoFolderSource
import app.openbubbles.nativeapp.data.photos.PhotoLibraryExport
import app.openbubbles.nativeapp.ui.attachmentviewer.AttachmentVideoPlayer
import app.openbubbles.nativeapp.ui.attachmentviewer.openAttachmentExternally
import app.openbubbles.nativeapp.ui.attachmentviewer.requiresLegacyMediaWritePermission
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.common.rememberVideoPoster
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val PhotosReadyTag = "benchmark_photos_ready"
private const val PhotosScrollableTag = "benchmark_photos_scrollable"
private const val PhotosIdleTag = "openbubbles_photos_grid"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotosScreen(
    uiState: PhotosUiState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onPreviewVisible: (PhotoSummary) -> Unit,
    onPreviewHidden: (PhotoSummary) -> Unit,
    onRetryPreview: (PhotoSummary) -> Unit,
    onSelect: (PhotoSummary) -> Unit,
    onCloseSelected: () -> Unit,
    onRetryOriginal: (PhotoSummary) -> Unit,
    onChooseUploads: () -> Unit,
    onAddFolder: () -> Unit,
    onScanFolder: (PhotoFolderSource) -> Unit,
    onRemoveFolder: (PhotoFolderSource) -> Unit,
    onUpload: (PhotoTransfer) -> Unit,
    onUploadAll: () -> Unit,
    modifier: Modifier = Modifier,
    /** Clock injected so previews and screenshots render fixed section titles. */
    nowMillis: Long? = null,
    /**
     * Peer-surface switcher pinned under the app bar. Opening this surface
     * through it stays metadata-first: the switcher only routes, it never asks
     * for a refresh, a preview, or an upload.
     */
    surfaceSwitcher: @Composable (gestureEnabled: Boolean) -> Unit = {},
) {
    var showTransfers by remember { mutableStateOf(false) }
    var grouping by rememberSaveable { mutableStateOf(PhotoGrouping.Day) }
    var filter by rememberSaveable { mutableStateOf(PhotoFilter.All) }
    var showFilterMenu by remember { mutableStateOf(false) }
    val snapshot = uiState.snapshot
    // The default must remain stable while transfer progress recomposes the
    // screen; section labels need at most a fresh value when the screen opens.
    val timelineNowMillis = remember(nowMillis) { nowMillis ?: System.currentTimeMillis() }
    // The timeline is the screen's model of the library: filtered, newest first,
    // grouped by when each photo was taken rather than when iCloud indexed it.
    val timeline = remember(snapshot?.assets, grouping, filter, timelineNowMillis) {
        photoTimeline(
            assets = snapshot?.assets.orEmpty(),
            grouping = grouping,
            filter = filter,
            nowMillis = timelineNowMillis,
        )
    }
    val visible = remember(timeline) { timeline.flatMap(PhotoSection::assets) }
    Scaffold(
        modifier = modifier.fillMaxSize().testTag(PhotosReadyTag),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("Photos")
                            if (snapshot != null) {
                                Text(
                                    text = libraryStatus(uiState, visible),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    imageVector = if (filter == PhotoFilter.All) {
                                        Icons.Filled.FilterList
                                    } else {
                                        Icons.Filled.FilterListOff
                                    },
                                    contentDescription = "Filter library",
                                )
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false },
                            ) {
                                PhotoFilter.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.label) },
                                        trailingIcon = {
                                            if (option == filter) {
                                                Icon(Icons.Filled.Check, contentDescription = "Selected")
                                            }
                                        },
                                        onClick = {
                                            filter = option
                                            showFilterMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        // Density has buttons as well as a pinch, so the grid is
                        // resizable with a keyboard, a switch, and one hand.
                        IconButton(
                            onClick = { grouping.denser()?.let { grouping = it } },
                            enabled = grouping.denser() != null,
                        ) {
                            Icon(Icons.Filled.ZoomIn, contentDescription = "Larger photos")
                        }
                        IconButton(
                            onClick = { grouping.wider()?.let { grouping = it } },
                            enabled = grouping.wider() != null,
                        ) {
                            Icon(Icons.Filled.ZoomOut, contentDescription = "Smaller photos")
                        }
                        IconButton(
                            onClick = onRefresh,
                            enabled = !uiState.refreshing && !uiState.loading && !uiState.loadingMore,
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh Photos")
                        }
                    },
                )
                // The viewer and the uploads sheet cover this strip; while either
                // owns the screen the switcher swipe must not fire underneath it.
                surfaceSwitcher(uiState.selectedAssetId == null && !showTransfers)
            }
        },
        floatingActionButton = {
            // Adding to the library is an explicit action, so it gets the one
            // prominent control on the screen rather than a crowded app bar.
            if (uiState.selectedAssetId == null) {
                FloatingActionButton(onClick = { showTransfers = true }) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = "Uploads and folders")
                }
            }
        },
    ) { padding ->
        when {
            uiState.loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(36.dp))
            }
            snapshot != null -> PhotosGrid(
                uiState = uiState,
                snapshot = snapshot,
                timeline = timeline,
                grouping = grouping,
                filter = filter,
                onGrouping = { grouping = it },
                onLoadMore = onLoadMore,
                onPreviewVisible = onPreviewVisible,
                onPreviewHidden = onPreviewHidden,
                onRetryPreview = onRetryPreview,
                onSelect = onSelect,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showTransfers) {
        UploadsSheet(
            uiState = uiState,
            onDismiss = { showTransfers = false },
            onChooseUploads = onChooseUploads,
            onAddFolder = onAddFolder,
            onScanFolder = onScanFolder,
            onRemoveFolder = onRemoveFolder,
            onUpload = onUpload,
            onUploadAll = onUploadAll,
        )
    }

    val selectedIndex = visible.indexOfFirst { it.id == uiState.selectedAssetId }
    if (selectedIndex >= 0) {
        PhotoViewer(
            assets = visible,
            initialIndex = selectedIndex,
            uiState = uiState,
            onPageSettled = onSelect,
            onBack = onCloseSelected,
            onRetryOriginal = onRetryOriginal,
        )
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

/**
 * The library as a dated timeline.
 *
 * Sections come from [photoTimeline], so the grid never decides what "October"
 * means. Only tiles the grid actually composes ask for a preview, which is what
 * keeps opening the library metadata-first.
 */
@Composable
private fun PhotosGrid(
    uiState: PhotosUiState,
    snapshot: PhotosSnapshot,
    timeline: List<PhotoSection>,
    grouping: PhotoGrouping,
    filter: PhotoFilter,
    onGrouping: (PhotoGrouping) -> Unit,
    onLoadMore: () -> Unit,
    onPreviewVisible: (PhotoSummary) -> Unit,
    onPreviewHidden: (PhotoSummary) -> Unit,
    onRetryPreview: (PhotoSummary) -> Unit,
    onSelect: (PhotoSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()
    val gridIsScrollable by remember {
        derivedStateOf { gridState.canScrollForward || gridState.canScrollBackward }
    }
    // Paging follows the viewport instead of an index guess, so a re-flow to a
    // denser grid cannot skip the fetch or fire it twice.
    LaunchedEffect(gridState, snapshot.nextCursor, filter) {
        if (snapshot.nextCursor == null || !shouldAutoPagePhotos(filter)) return@LaunchedEffect
        snapshotFlow {
            val info = gridState.layoutInfo
            photoGridNearEnd(
                lastVisibleIndex = info.visibleItemsInfo.lastOrNull()?.index,
                totalItemsCount = info.totalItemsCount,
                approximateColumns = grouping.columns,
            )
        }
            .distinctUntilChanged()
            .collect { nearEnd -> if (nearEnd) onLoadMore() }
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(grouping.minimumTileWidthDp.dp),
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .pinchGrouping(grouping, onGrouping)
            .testTag(if (gridIsScrollable) PhotosScrollableTag else PhotosIdleTag),
        state = gridState,
        // The last row can scroll fully above the bottom-end upload FAB.
        contentPadding = PaddingValues(start = 2.dp, top = 2.dp, end = 16.dp, bottom = 96.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (snapshot.access.availability != PhotosAvailability.Ready || uiState.showingCachedMetadata) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AccessNotice(snapshot.access, uiState.showingCachedMetadata)
            }
        }
        if (timeline.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emptyLibraryMessage(snapshot, filter),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        timeline.forEach { section ->
            item(key = "header-${section.key}", span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(section)
            }
            items(section.assets, key = { asset -> asset.id }) { asset ->
                PhotoTile(
                    asset = asset,
                    transfer = uiState.previewTransfers[asset.id],
                    onVisible = { onPreviewVisible(asset) },
                    onHidden = { onPreviewHidden(asset) },
                    onRetry = { onRetryPreview(asset) },
                    onSelect = { onSelect(asset) },
                )
            }
        }
        if (uiState.loadingMore) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        } else if (snapshot.nextCursor != null && !shouldAutoPagePhotos(filter)) {
            item(key = "filtered-load-more", span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    FilledTonalButton(onClick = onLoadMore) {
                        Text("Load more photos")
                    }
                }
            }
        }
    }
}

/**
 * App-bar subtitle: "128 photos · 4 videos", plus "Offline" when the grid is
 * showing cached metadata. Background sync is explained in the uploads sheet,
 * which is where it can be acted on.
 */
private fun libraryStatus(uiState: PhotosUiState, visible: List<PhotoSummary>): String {
    val counts = photoCountLabel(visible)
    return if (uiState.showingCachedMetadata) "$counts · Offline" else counts
}

private fun emptyLibraryMessage(snapshot: PhotosSnapshot, filter: PhotoFilter): String = when {
    snapshot.access.availability == PhotosAvailability.Indexing ->
        "Your iCloud library is still indexing."
    filter == PhotoFilter.Favorites -> "No favorites in the photos loaded so far."
    filter == PhotoFilter.Videos -> "No videos in the photos loaded so far."
    else -> "No photos are available yet."
}

@Composable
private fun SectionHeader(section: PhotoSection) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Text(
            text = section.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = photoCountLabel(section.assets),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Pinch to change grid density.
 *
 * Only multi-touch gestures are considered, and only after the pinch has
 * travelled past [groupingForPinch]'s threshold, so a one-finger scroll still
 * belongs to the grid and a two-finger scroll does not resize the library by
 * accident.
 */
private fun Modifier.pinchGrouping(
    grouping: PhotoGrouping,
    onGrouping: (PhotoGrouping) -> Unit,
): Modifier = this.pointerInput(grouping) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var zoom = 1f
        var handled = false
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            if (pressed >= 2 && !handled) {
                zoom *= event.calculateZoom()
                groupingForPinch(grouping, zoom)?.let { next ->
                    handled = true
                    onGrouping(next)
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
    }
}

@Composable
private fun AccessNotice(access: PhotosAccess, cached: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null)
            Text(
                text = if (cached) "Showing saved previews while iCloud reconnects." else access.detail,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PhotoTile(
    asset: PhotoSummary,
    transfer: PhotoTransfer?,
    onVisible: () -> Unit,
    onHidden: () -> Unit,
    onRetry: () -> Unit,
    onSelect: () -> Unit,
) {
    DisposableEffect(asset.id) {
        onVisible()
        onDispose { onHidden() }
    }
    val file = transfer?.takeIf { it.state == PhotoTransferState.Succeeded }
        ?.localPath?.let(::File)
    val decoded = when (asset.mediaKind) {
        PhotoMediaKind.Image -> rememberDecodedImage(file, maxDimensionPx = 512)
        PhotoMediaKind.Video -> rememberVideoPoster(file, maxDimensionPx = 512)
        PhotoMediaKind.Unknown -> null
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics {
                role = Role.Button
                contentDescription = asset.filename ?: "Photo"
            }
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        if (decoded != null) {
            Image(
                bitmap = decoded.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            when (transfer?.state) {
                PhotoTransferState.Failed -> TextButton(onClick = onRetry) { Text("Retry") }
                PhotoTransferState.Running, PhotoTransferState.Queued ->
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                else -> Icon(
                    Icons.Filled.AddPhotoAlternate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (asset.favorite) TileBadge(Icons.Filled.Favorite, "Favorite")
            if (asset.livePhoto) TileBadge(Icons.Filled.MotionPhotosOn, "Live Photo")
            if (asset.mediaKind == PhotoMediaKind.Video) TileBadge(Icons.Filled.PlayCircle, "Video")
        }
    }
}

@Composable
private fun TileBadge(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = Color.Black.copy(alpha = 0.55f)) {
        Icon(
            icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.padding(3.dp).size(15.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UploadsSheet(
    uiState: PhotosUiState,
    onDismiss: () -> Unit,
    onChooseUploads: () -> Unit,
    onAddFolder: () -> Unit,
    onScanFolder: (PhotoFolderSource) -> Unit,
    onRemoveFolder: (PhotoFolderSource) -> Unit,
    onUpload: (PhotoTransfer) -> Unit,
    onUploadAll: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Add to iCloud Photos", style = MaterialTheme.typography.headlineSmall)
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.CloudOff, contentDescription = null)
                    Column {
                        Text("Background sync is off", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Photos and folders are staged only when you choose them or tap Scan now.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = onChooseUploads, enabled = !uiState.planningUpload) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Choose photos")
                }
                OutlinedButton(onClick = onAddFolder, enabled = !uiState.planningUpload) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add folder")
                }
            }
            if (uiState.planningUpload) {
                Text("Staging ${uiState.planningDone} of ${uiState.planningTotal}")
                val fraction = if (uiState.planningTotal > 0) {
                    uiState.planningDone.toFloat() / uiState.planningTotal
                } else {
                    0f
                }
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            }
            uiState.sourceMessage?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }
            uiState.uploadError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            if (uiState.folderSources.isNotEmpty()) {
                HorizontalDivider()
                Text("Selected folders", style = MaterialTheme.typography.titleMedium)
                uiState.folderSources.forEach { source ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Folder, contentDescription = null)
                        Text(
                            source.displayName,
                            modifier = Modifier.padding(horizontal = 10.dp).weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        TextButton(
                            onClick = { onScanFolder(source) },
                            enabled = !uiState.planningUpload,
                        ) { Text("Scan now") }
                        TextButton(onClick = { onRemoveFolder(source) }) { Text("Remove") }
                    }
                }
            }
            if (uiState.uploadPlans.isNotEmpty()) {
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Staged uploads", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Button(
                        onClick = onUploadAll,
                        enabled = uiState.uploadPlans.any {
                            it.state in listOf(PhotoTransferState.Queued, PhotoTransferState.Failed)
                        },
                    ) {
                        Icon(Icons.Filled.Upload, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Upload all")
                    }
                }
                uiState.uploadPlans.forEach { transfer ->
                    UploadRow(transfer = transfer, onUpload = { onUpload(transfer) })
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun UploadRow(transfer: PhotoTransfer, onUpload: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(transfer.filename ?: "Staged photo", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = when (transfer.state) {
                    PhotoTransferState.Queued -> "Ready to upload"
                    PhotoTransferState.Running -> "Uploading ${transfer.percentLabel()}"
                    PhotoTransferState.Succeeded -> "Uploaded"
                    PhotoTransferState.Failed -> transfer.lastError ?: "Upload failed"
                    PhotoTransferState.Blocked -> transfer.lastError ?: "Upload blocked"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (transfer.state == PhotoTransferState.Failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(
            onClick = onUpload,
            enabled = transfer.state in listOf(PhotoTransferState.Queued, PhotoTransferState.Failed),
        ) { Text(if (transfer.state == PhotoTransferState.Failed) "Retry" else "Upload") }
    }
}

/**
 * Full-screen viewer.
 *
 * Swiping moves through the same timeline the grid shows, and only the settled
 * page is selected — which is what asks for that photo's original and cancels
 * the one before it, so flicking through twenty photos does not start twenty
 * full-quality downloads.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PhotoViewer(
    assets: List<PhotoSummary>,
    initialIndex: Int,
    uiState: PhotosUiState,
    onPageSettled: (PhotoSummary) -> Unit,
    onBack: () -> Unit,
    onRetryOriginal: (PhotoSummary) -> Unit,
) {
    BackHandler(onBack = onBack)
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, (assets.size - 1).coerceAtLeast(0)),
        pageCount = { assets.size },
    )
    var showInfo by remember { mutableStateOf(false) }
    var chromeVisible by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingLegacySave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val legacyMediaPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingLegacySave
        pendingLegacySave = null
        if (granted) {
            pending?.invoke()
        } else {
            Toast.makeText(
                context,
                "Storage permission is required to save photos",
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    LaunchedEffect(pagerState, assets) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { page -> assets.getOrNull(page)?.let(onPageSettled) }
    }
    val current = assets.getOrNull(pagerState.settledPage)
    // Save and Share act on the downloaded original only. Exporting is a
    // one-way copy into the Android gallery; it never writes to iCloud.
    val currentOriginal = current
        ?.let { uiState.originalTransfers[it.id] }
        ?.takeIf { it.state == PhotoTransferState.Succeeded }
        ?.localPath
        ?.let(::File)
        ?.takeIf { it.isFile && it.length() > 0 }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { page -> assets[page].id },
        ) { page ->
            val asset = assets[page]
            PhotoPage(
                asset = asset,
                preview = uiState.previewTransfers[asset.id],
                original = uiState.originalTransfers[asset.id],
                playbackEnabled = page == pagerState.settledPage && !pagerState.isScrollInProgress,
                onToggleChrome = { chromeVisible = !chromeVisible },
                onRetryOriginal = { onRetryOriginal(asset) },
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
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close photo",
                        tint = Color.White,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = current.filename ?: "Photo",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${pagerState.settledPage + 1} of ${assets.size}",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                if (currentOriginal != null) {
                    IconButton(
                        onClick = {
                            val save = {
                                scope.launch {
                                    val outcome = withContext(Dispatchers.IO) {
                                        savePhotoToGallery(context, current, currentOriginal)
                                    }
                                    Toast.makeText(
                                        context,
                                        when (outcome) {
                                            PhotoGalleryExportOutcome.Saved ->
                                                "Saved to ${PhotoLibraryExport.ALBUM}"
                                            PhotoGalleryExportOutcome.Unsupported ->
                                                "This format cannot be saved to the gallery"
                                            PhotoGalleryExportOutcome.Failed ->
                                                "Unable to save this photo"
                                        },
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                                Unit
                            }
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (requiresLegacyMediaWritePermission(Build.VERSION.SDK_INT, granted)) {
                                pendingLegacySave = save
                                legacyMediaPermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            } else {
                                save()
                            }
                        },
                    ) {
                        Icon(
                            Icons.Filled.SaveAlt,
                            contentDescription = "Save to gallery",
                            tint = Color.White,
                        )
                    }
                    IconButton(
                        onClick = {
                            if (!sharePhotoOriginal(context, current, currentOriginal)) {
                                Toast.makeText(
                                    context,
                                    "Unable to share this photo",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            }
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share photo", tint = Color.White)
                    }
                }
                IconButton(onClick = { showInfo = true }) {
                    Icon(Icons.Filled.Info, contentDescription = "Photo details", tint = Color.White)
                }
            }
        }
    }
    if (showInfo && current != null) {
        ModalBottomSheet(onDismissRequest = { showInfo = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Details", style = MaterialTheme.typography.headlineSmall)
                photoInfoRows(current).forEach { (label, value) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(120.dp),
                        )
                        Text(text = value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Text(
                    text = "Location, people and captions stay in iCloud — this client never " +
                        "requests them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PhotoPage(
    asset: PhotoSummary,
    preview: PhotoTransfer?,
    original: PhotoTransfer?,
    playbackEnabled: Boolean,
    onToggleChrome: () -> Unit,
    onRetryOriginal: () -> Unit,
) {
    val context = LocalContext.current
    val originalFile = original?.takeIf { it.state == PhotoTransferState.Succeeded }
        ?.localPath?.let(::File)
    val previewFile = preview?.takeIf { it.state == PhotoTransferState.Succeeded }
        ?.localPath?.let(::File)
    val shownFile = originalFile ?: previewFile
    var scale by remember(asset.id) { mutableFloatStateOf(1f) }
    var offset by remember(asset.id) { mutableStateOf(Offset.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(asset.id) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = {
                        if (asset.mediaKind != PhotoMediaKind.Image) return@detectTapGestures
                        scale = if (scale > 1f) 1f else DoubleTapZoom
                        offset = Offset.Zero
                    },
                )
            }
            .then(
                if (asset.mediaKind == PhotoMediaKind.Image) {
                    Modifier.zoomablePage(
                        assetId = asset.id,
                        scale = scale,
                        onScale = { scale = it },
                        offset = offset,
                        onOffset = { offset = it },
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when (asset.mediaKind) {
            PhotoMediaKind.Image -> {
                val decoded = rememberDecodedImage(shownFile, maxDimensionPx = 2048)
                if (decoded != null) {
                    Image(
                        bitmap = decoded.image,
                        contentDescription = asset.filename,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y,
                        ),
                    )
                }
            }
            PhotoMediaKind.Video -> if (originalFile != null) {
                AttachmentVideoPlayer(
                    file = originalFile,
                    controlsVisible = true,
                    playbackEnabled = playbackEnabled,
                    onOpenExternally = {
                        val mime = AttachmentMedia.suggestedMime(null, null, asset.filename)
                        if (!openAttachmentExternally(context, originalFile, mime)) {
                            Toast.makeText(context, "No app can open this video", Toast.LENGTH_SHORT).show()
                        }
                    },
                )
            } else {
                val poster = rememberVideoPoster(previewFile, maxDimensionPx = 1080)
                poster?.let {
                    Image(
                        bitmap = it.image,
                        contentDescription = asset.filename,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            PhotoMediaKind.Unknown -> Unit
        }
        if (originalFile == null) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = Color.Black.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when {
                        asset.originalSize == null || asset.mediaKind == PhotoMediaKind.Unknown ->
                            Text("Full-quality file is unavailable", color = Color.White)
                        original?.state == PhotoTransferState.Failed -> {
                            Text(original.lastError ?: "Full photo download failed", color = Color.White)
                            TextButton(onClick = onRetryOriginal) { Text("Retry") }
                        }
                        original?.state == PhotoTransferState.Running && original.totalBytes > 0 -> {
                            Text("Loading full quality ${original.percentLabel()}", color = Color.White)
                            LinearProgressIndicator(
                                progress = { original.progressFraction() },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        original?.state in listOf(PhotoTransferState.Running, PhotoTransferState.Queued) ||
                            original == null -> {
                            Text("Loading full quality", color = Color.White)
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                        else -> Text("Full-quality file is unavailable", color = Color.White)
                    }
                }
            }
        }
    }
}

private const val DoubleTapZoom = 2.5f
private const val MaxPageZoom = 6f

/**
 * Pinch-zoom and pan inside a pager.
 *
 * A drag is only claimed while zoomed in, and a pinch only while two fingers are
 * down. At fit scale a horizontal drag therefore still belongs to the pager, so
 * zooming a photo and swiping to the next one never fight.
 */
@Composable
private fun Modifier.zoomablePage(
    assetId: String,
    scale: Float,
    onScale: (Float) -> Unit,
    offset: Offset,
    onOffset: (Offset) -> Unit,
): Modifier {
    val latestScale by rememberUpdatedState(scale)
    val latestOffset by rememberUpdatedState(offset)
    val latestOnScale by rememberUpdatedState(onScale)
    val latestOnOffset by rememberUpdatedState(onOffset)
    return this.pointerInput(assetId) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var currentScale = latestScale
        var currentOffset = latestOffset
        do {
            val event = awaitPointerEvent()
            val pressed = event.changes.count { it.pressed }
            when {
                pressed >= 2 -> {
                    currentScale = (currentScale * event.calculateZoom()).coerceIn(1f, MaxPageZoom)
                    currentOffset = if (currentScale > 1f) {
                        currentOffset + event.calculatePan()
                    } else {
                        Offset.Zero
                    }
                    latestOnScale(currentScale)
                    latestOnOffset(currentOffset)
                    event.changes.forEach { it.consume() }
                }
                currentScale > 1f -> {
                    val pan = event.calculatePan()
                    if (pan != Offset.Zero) {
                        currentOffset += pan
                        latestOnOffset(currentOffset)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}
}

private fun PhotoTransfer.progressFraction(): Float =
    if (totalBytes > 0) (bytesDone.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

private fun PhotoTransfer.percentLabel(): String =
    if (totalBytes > 0) "${(progressFraction() * 100).toInt()}%" else ""

@LightDarkPreviews
@Composable
private fun PhotosPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        PhotosScreen(
            uiState = PhotosUiState(
                loading = false,
                snapshot = PhotosSnapshot(
                    access = PhotosAccess(PhotosAvailability.Ready, "Personal iCloud Photos is available"),
                    assets = listOf(previewPhoto("master-1", "IMG_1042.HEIC", favorite = true)),
                ),
            ),
            onBack = {},
            onRefresh = {},
            onLoadMore = {},
            onPreviewVisible = {},
            onPreviewHidden = {},
            onRetryPreview = {},
            onSelect = {},
            onCloseSelected = {},
            onRetryOriginal = {},
            onChooseUploads = {},
            onAddFolder = {},
            onScanFolder = {},
            onRemoveFolder = {},
            onUpload = {},
            onUploadAll = {},
        )
    }
}

private fun previewPhoto(id: String, filename: String, favorite: Boolean = false) = PhotoSummary(
    id = id,
    assetId = "asset-$id",
    filename = filename,
    mediaKind = PhotoMediaKind.Image,
    livePhoto = true,
    width = 4032,
    height = 3024,
    originalSize = 4_200_000,
    previewSize = 102_000,
    capturedAtMs = null,
    addedAtMs = null,
    favorite = favorite,
    hidden = false,
)

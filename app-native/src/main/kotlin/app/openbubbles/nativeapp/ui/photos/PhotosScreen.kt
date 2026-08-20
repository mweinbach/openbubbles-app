package app.openbubbles.nativeapp.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.photos.PhotoSummary
import app.openbubbles.core.photos.PhotoTransfer
import app.openbubbles.core.photos.PhotoTransferState
import app.openbubbles.core.photos.PhotosAccess
import app.openbubbles.core.photos.PhotosAvailability
import app.openbubbles.core.photos.PhotosSnapshot
import app.openbubbles.nativeapp.data.photos.PhotoFolderSource
import app.openbubbles.nativeapp.ui.attachmentviewer.AttachmentVideoPlayer
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.common.rememberVideoPoster
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews
import java.io.File

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
) {
    var showTransfers by remember { mutableStateOf(false) }
    val snapshot = uiState.snapshot
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Photos")
                        snapshot?.let {
                            Text(
                                text = "${it.assets.size} items · Background sync off",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    IconButton(onClick = { showTransfers = true }) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = "Uploads and folders")
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !uiState.refreshing && !uiState.loading && !uiState.loadingMore,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh Photos")
                    }
                },
            )
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

    val selected = snapshot?.assets?.firstOrNull { it.id == uiState.selectedAssetId }
    if (selected != null) {
        PhotoDetail(
            asset = selected,
            preview = uiState.previewTransfers[selected.id],
            original = uiState.originalTransfers[selected.id],
            onBack = onCloseSelected,
            onRetryOriginal = { onRetryOriginal(selected) },
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

@Composable
private fun PhotosGrid(
    uiState: PhotosUiState,
    snapshot: PhotosSnapshot,
    onLoadMore: () -> Unit,
    onPreviewVisible: (PhotoSummary) -> Unit,
    onPreviewHidden: (PhotoSummary) -> Unit,
    onRetryPreview: (PhotoSummary) -> Unit,
    onSelect: (PhotoSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(112.dp),
        modifier = modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (snapshot.access.availability != PhotosAvailability.Ready || uiState.showingCachedMetadata) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AccessNotice(snapshot.access, uiState.showingCachedMetadata)
            }
        }
        if (snapshot.assets.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (snapshot.access.availability == PhotosAvailability.Indexing) {
                            "Your iCloud library is still indexing."
                        } else {
                            "No photos are available yet."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        itemsIndexed(snapshot.assets, key = { _, asset -> asset.id }) { index, asset ->
            if (snapshot.nextCursor != null && index >= snapshot.assets.lastIndex - 4) {
                LaunchedEffect(snapshot.nextCursor) { onLoadMore() }
            }
            PhotoTile(
                asset = asset,
                transfer = uiState.previewTransfers[asset.id],
                onVisible = { onPreviewVisible(asset) },
                onHidden = { onPreviewHidden(asset) },
                onRetry = { onRetryPreview(asset) },
                onSelect = { onSelect(asset) },
            )
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
        }
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

@Composable
private fun PhotoDetail(
    asset: PhotoSummary,
    preview: PhotoTransfer?,
    original: PhotoTransfer?,
    onBack: () -> Unit,
    onRetryOriginal: () -> Unit,
) {
    BackHandler(onBack = onBack)
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
            .then(
                if (asset.mediaKind == PhotoMediaKind.Image) {
                    Modifier.pointerInput(asset.id) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (next > 1f) offset + pan else Offset.Zero
                            scale = next
                        }
                    }
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
                AttachmentVideoPlayer(file = originalFile, onPlaybackError = {})
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
        Row(
            modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close photo", tint = Color.White)
            }
            Text(
                asset.filename ?: "Photo",
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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

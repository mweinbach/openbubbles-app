package app.openbubbles.nativeapp.ui.attachmentviewer

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import app.openbubbles.core.attachment.AttachmentMedia
import app.openbubbles.core.attachment.AttachmentMediaKind
import app.openbubbles.core.attachment.TransferState
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.AttachmentProvider
import app.openbubbles.nativeapp.data.resolveLivePhotoPair
import app.openbubbles.nativeapp.ui.common.FallbackAspectRatio
import app.openbubbles.nativeapp.ui.common.HdrColorModeEffect
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.common.rememberPdfPageCount
import app.openbubbles.nativeapp.ui.common.sharedAttachment
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.defaultSpatialSpec
import app.openbubbles.nativeapp.ui.theme.fastEffectsSpec
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Viewer zoom limits for the pinch gesture. */
private const val MinZoom = 1f
private const val MaxZoom = 6f

/**
 * Fullscreen attachment viewer: pinch-to-zoom images, in-app video
 * playback, in-app PDF pages. Tap toggles the chrome. Other file types
 * keep the external Open / Share actions.
 */
@Composable
fun AttachmentViewerScreen(
    guid: String,
    provider: AttachmentProvider,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingLegacySave by remember { mutableStateOf<(() -> Unit)?>(null) }
    val legacyMediaPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val pending = pendingLegacySave
        pendingLegacySave = null
        if (granted) pending?.invoke() else {
            Toast.makeText(context, "Storage permission is required to save media", Toast.LENGTH_SHORT).show()
        }
    }
    val initialMeta = remember(guid, provider) { provider.byGuid(guid) }
    val meta by remember(guid, provider) { provider.observe(guid) }
        .collectAsStateWithLifecycle(initialValue = initialMeta)
    val resolvedMeta = meta

    // Explicit viewer download: joins the guid-deduplicated transfer and
    // survives collector cancellation (the transfer is manager-owned).
    var downloadAttempt by remember(guid) { mutableIntStateOf(0) }
    var downloadState by remember(guid) { mutableStateOf<TransferState?>(null) }
    var downloadCompletions by remember(guid) { mutableIntStateOf(0) }
    LaunchedEffect(guid, downloadAttempt) {
        if (downloadAttempt == 0) return@LaunchedEffect
        provider.download(guid).collect { state ->
            downloadState = state
            if (state is TransferState.Done) downloadCompletions++
        }
    }

    // Disk presence beats the persisted flag (same rule as the bubble).
    val file = remember(guid, resolvedMeta?.downloaded, downloadCompletions) {
        provider.localFile(guid)
    }
    var chromeVisible by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var videoFailed by remember(guid) { mutableStateOf(false) }
    val mediaKind = AttachmentMedia.kind(resolvedMeta?.mime, resolvedMeta?.uti, resolvedMeta?.name)
    val pdfPages = rememberPdfPageCount(file.takeIf { mediaKind == AttachmentMediaKind.PDF })
    val zoomable = mediaKind == AttachmentMediaKind.IMAGE && file != null
    val playbackMime = resolvedMeta?.playbackMime ?: resolvedMeta?.mime
    val livePhotoPair = remember(resolvedMeta, guid, downloadCompletions) {
        resolvedMeta?.takeIf { it.livePhotoMotionGuid != null }?.let { resolveLivePhotoPair(it, provider) }
    }

    // No BackHandler here: NavDisplay's own back handling covers the pop, and
    // an inner handler would swallow the predictive-back preview gesture.

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(
                if (zoomable) {
                    Modifier.pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val newScale = (scale * zoom).coerceIn(MinZoom, MaxZoom)
                            offset = if (newScale > 1f) offset + pan else Offset.Zero
                            scale = newScale
                        }
                    }
                } else {
                    Modifier
                },
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { chromeVisible = !chromeVisible },
                    onDoubleTap = {
                        if (!zoomable) return@detectTapGestures
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val resolvedLivePhotoPair = livePhotoPair
        val content: @Composable () -> Unit = when {
            resolvedMeta == null -> { { ViewerMessage("Attachment not found") } }
            file == null -> {
                {
                    AttachmentDownloadPane(
                        name = resolvedMeta.name,
                        sizeLabel = formatBytes(resolvedMeta.sizeBytes),
                        canDownload = remember(guid, provider) { provider.canDownload(guid) },
                        state = downloadState,
                        onDownload = { downloadAttempt++ },
                    )
                }
            }
            resolvedLivePhotoPair != null -> {
                { LivePhotoViewer(pair = resolvedLivePhotoPair) }
            }
            mediaKind == AttachmentMediaKind.IMAGE || resolvedMeta.isImage -> {
                {
                    val decoded = rememberDecodedImage(file = file, maxDimensionPx = 2048)
                    val aspect = decoded?.aspectRatio ?: FallbackAspectRatio
                    // Gain-mapped stills (Ultra HDR, compatible Apple HDR)
                    // render with real HDR headroom while on screen.
                    HdrColorModeEffect(decoded?.image)
                    if (decoded != null) {
                        Image(
                            bitmap = decoded.image,
                            contentDescription = resolvedMeta.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .aspectRatio(aspect)
                                .sharedAttachment(guid)
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offset.x,
                                    translationY = offset.y,
                                ),
                        )
                    } else {
                        ViewerMessage("Could not load image")
                    }
                }
            }
            mediaKind == AttachmentMediaKind.VIDEO && !videoFailed -> {
                {
                    AttachmentVideoPlayer(
                        file = file,
                        controlsVisible = chromeVisible,
                        onOpenExternally = { videoFailed = true },
                    )
                }
            }
            mediaKind == AttachmentMediaKind.PDF && (pdfPages == null || pdfPages > 0) -> {
                {
                    if (pdfPages == null) {
                        ViewerMessage("Opening PDF…")
                    } else {
                        AttachmentPdfViewer(
                            file = file,
                            name = resolvedMeta.name,
                            pageCount = pdfPages,
                        )
                    }
                }
            }
            else -> {
                {
                    ExternalAttachmentActions(
                        name = resolvedMeta.name,
                        onOpen = {
                            if (!openAttachmentExternally(context, file, playbackMime)) {
                                Toast.makeText(context, "No app can open this attachment", Toast.LENGTH_SHORT).show()
                            }
                        },
                        onShare = {
                            if (!shareAttachment(context, file, playbackMime)) {
                                Toast.makeText(context, "Unable to share this attachment", Toast.LENGTH_SHORT).show()
                            }
                        },
                    )
                }
            }
        }
        content()
        // Save-to-device is an explicit action, separate from Share. Live
        // Photos export as a single Google Motion Photo (with a two-file
        // fallback); HDR HEIC converts to Ultra HDR JPEG when readable;
        // everything else copies byte-identical.
        fun showToast(message: String) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        val saveToDevice: (() -> Unit)? = when {
            file == null || resolvedMeta == null -> null
            livePhotoPair != null -> {
                {
                    val pair = livePhotoPair
                    scope.launch {
                        val outcome = withContext(Dispatchers.IO) { saveLivePhotoToDevice(context, pair) }
                        showToast(
                            when (outcome) {
                                LivePhotoSaveOutcome.MotionPhoto -> "Saved as motion photo"
                                LivePhotoSaveOutcome.SeparateFiles -> "Saved Live Photo as photo + video"
                                LivePhotoSaveOutcome.StillOnly -> "Saved still photo"
                                LivePhotoSaveOutcome.MotionOnly -> "Saved Live Photo video"
                                LivePhotoSaveOutcome.Failed -> "Unable to save Live Photo"
                            },
                        )
                    }
                }
            }
            mediaKind == AttachmentMediaKind.IMAGE || resolvedMeta.isImage -> {
                {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            saveImageAttachmentToDevice(context, resolvedMeta, file)
                        }
                        showToast(if (saved) "Saved to Pictures" else "Unable to save image")
                    }
                }
            }
            mediaKind == AttachmentMediaKind.VIDEO -> {
                {
                    scope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            saveVideoAttachmentToDevice(context, resolvedMeta, file)
                        }
                        showToast(if (saved) "Saved to Movies" else "Unable to save video")
                    }
                }
            }
            else -> null
        }
        val onSaveToDevice = saveToDevice?.let { save ->
            {
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
            }
        }
        ViewerChrome(
            visible = chromeVisible,
            title = resolvedMeta?.name,
            subtitle = formatBytes(resolvedMeta?.sizeBytes),
            onBack = onBack,
            onShare = if (file == null) null else {
                {
                    if (!shareAttachment(context, file, playbackMime)) {
                        Toast.makeText(context, "Unable to share this attachment", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onSaveToDevice = onSaveToDevice,
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/**
 * Explicit remote-attachment state for the viewer: download with progress
 * when a transfer is possible, a labeled reason when it is not, and a
 * retryable error state.
 */
@Composable
private fun AttachmentDownloadPane(
    name: String?,
    sizeLabel: String,
    canDownload: Boolean,
    state: TransferState?,
    onDownload: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Text(
            text = name ?: "Attachment",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (sizeLabel.isNotEmpty()) {
            Text(
                text = sizeLabel,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        when (state) {
            is TransferState.Progress -> {
                val fraction = if (state.total > 0) {
                    (state.done.toFloat() / state.total.toFloat()).coerceIn(0f, 1f)
                } else {
                    null
                }
                if (fraction != null) {
                    LinearWavyProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                    )
                } else {
                    LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(top = 20.dp))
                }
                Text(
                    text = if (state.total > 0) {
                        "Downloading… ${formatBytes(state.done)} of ${formatBytes(state.total)}"
                    } else {
                        "Downloading…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            is TransferState.Failed -> {
                Text(
                    text = "Download failed — ${state.error}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Button(onClick = onDownload, modifier = Modifier.padding(top = 12.dp)) {
                    Text("Retry")
                }
            }
            TransferState.Done -> {
                // The observed metadata flips `downloaded` and the local file
                // re-resolves; this state is only visible for a frame.
                Text(
                    text = "Downloaded",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            null -> {
                if (canDownload) {
                    Button(onClick = onDownload, modifier = Modifier.padding(top = 16.dp)) {
                        Icon(Icons.Filled.Download, contentDescription = null)
                        Text("Download", modifier = Modifier.padding(start = 6.dp))
                    }
                } else {
                    Text(
                        text = "Not downloaded — no downloadable payload is available for this attachment",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ViewerChrome(
    visible: Boolean,
    title: String?,
    subtitle: String,
    onBack: () -> Unit,
    onShare: (() -> Unit)?,
    onSaveToDevice: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = slideInVertically(defaultSpatialSpec()) { -it / 2 } + fadeIn(defaultEffectsSpec()),
        exit = slideOutVertically(defaultSpatialSpec()) { -it / 2 } + fadeOut(fastEffectsSpec()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
            ) {
                Text(
                    text = title ?: "Attachment",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }
            }
            if (onShare != null) {
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = "Share attachment",
                        tint = Color.White,
                    )
                }
            }
            if (onSaveToDevice != null) {
                IconButton(onClick = onSaveToDevice) {
                    Icon(
                        imageVector = Icons.Filled.Download,
                        contentDescription = "Save to device",
                        tint = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun ExternalAttachmentActions(
    name: String?,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp),
    ) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Text(
            text = name ?: "Attachment",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 16.dp, bottom = 12.dp),
        )
        Row {
            Button(onClick = onOpen) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                Text("Open", modifier = Modifier.padding(start = 6.dp))
            }
            Button(
                onClick = onShare,
                modifier = Modifier.padding(start = 10.dp),
            ) {
                Icon(Icons.Filled.Share, contentDescription = null)
                Text("Share", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun ViewerMessage(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.BrokenImage,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.85f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(16.dp),
        )
    }
}

// --------------------------------------------------------------------- previews

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AttachmentViewerPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        AttachmentViewerScreen(
            guid = "demo-image-1",
            provider = object : AttachmentProvider {
                override fun byGuid(guid: String) = AttachmentMeta(
                    guid = guid, mime = "image/jpeg", name = "trailhead.jpg",
                    sizeBytes = 2_411_520L, isImage = true, downloaded = false,
                )

                override fun localFile(guid: String): File? = null
            },
            onBack = {},
        )
    }
}

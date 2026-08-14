package app.openbubbles.nativeapp.ui.attachmentviewer

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Image
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.AttachmentProvider
import app.openbubbles.nativeapp.ui.common.FallbackAspectRatio
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import java.io.File

/** Viewer zoom limits for the pinch gesture. */
private const val MinZoom = 1f
private const val MaxZoom = 6f

/**
 * Fullscreen attachment viewer: pinch-to-zoom / pan via transform gestures,
 * tap to toggle the chrome, back (gesture or button) returns. Non-image or
 * not-yet-downloaded attachments render an explanatory placeholder.
 */
@Composable
fun AttachmentViewerScreen(
    guid: String,
    provider: AttachmentProvider,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val meta = remember(guid) { provider.byGuid(guid) }
    // Disk presence beats the persisted flag (same rule as the bubble).
    val file = remember(guid) { provider.localFile(guid) }
    var chromeVisible by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    BackHandler(onBack = onBack)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val newScale = (scale * zoom).coerceIn(MinZoom, MaxZoom)
                    // Panning only matters once zoomed in; snap back when at 1x.
                    offset = if (newScale > 1f) offset + pan else Offset.Zero
                    scale = newScale
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { chromeVisible = !chromeVisible },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        val content: @Composable () -> Unit = when {
            meta == null -> { { ViewerMessage("Attachment not found") } }
            !meta.isImage -> { { ViewerMessage("${meta.name ?: "Attachment"} — preview not available yet") } }
            file == null -> { { ViewerMessage("${meta.name ?: "Attachment"} — not downloaded yet") } }
            else -> {
                {
                    val decoded = rememberDecodedImage(file = file, maxDimensionPx = 2048)
                    val aspect = decoded?.aspectRatio ?: FallbackAspectRatio
                    if (decoded != null) {
                        Image(
                            bitmap = decoded.image,
                            contentDescription = meta.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxSize()
                                .aspectRatio(aspect)
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
        }
        content()
        ViewerChrome(
            visible = chromeVisible,
            title = meta?.name,
            subtitle = formatBytes(meta?.sizeBytes),
            onBack = onBack,
        )
    }
}

@Composable
private fun ViewerChrome(
    visible: Boolean,
    title: String?,
    subtitle: String,
    onBack: () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }),
        exit = slideOutVertically(targetOffsetY = { -it }),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.6f))
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
            Column {
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
        }
    }
}

@Composable
private fun ViewerMessage(text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(
            imageVector = Icons.Filled.BrokenImage,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.8f),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
            modifier = Modifier.padding(16.dp),
        )
    }
}

// --------------------------------------------------------------------- previews

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AttachmentViewerPreview() {
    OpenBubblesTheme {
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

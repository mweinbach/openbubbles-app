package app.openbubbles.nativeapp.ui.attachmentviewer

import android.widget.VideoView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.openbubbles.nativeapp.data.LivePhotoPair
import app.openbubbles.nativeapp.ui.common.HdrColorModeEffect
import app.openbubbles.nativeapp.ui.common.rememberDecodedImage
import kotlinx.coroutines.delay

@Composable
internal fun LivePhotoViewer(
    pair: LivePhotoPair,
    modifier: Modifier = Modifier,
) {
    val decoded = rememberDecodedImage(file = pair.stillFile, maxDimensionPx = 2048)
    // Renders the HDR headroom of a gain-mapped still while it is on screen.
    HdrColorModeEffect(decoded?.image)
    var playing by remember(pair.still.guid) { mutableStateOf(false) }
    var videoView by remember(pair.still.guid) { mutableStateOf<VideoView?>(null) }

    LaunchedEffect(playing, pair.motionFile?.absolutePath) {
        if (!playing || pair.motionFile == null) return@LaunchedEffect
        delay(3_000)
        videoView?.pause()
        videoView?.seekTo(0)
        playing = false
    }
    DisposableEffect(pair.still.guid) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(enabled = pair.motionFile != null) { playing = true },
        contentAlignment = Alignment.Center,
    ) {
        decoded?.image?.let { image ->
            Image(
                bitmap = image,
                contentDescription = pair.still.name ?: "Live Photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (playing && pair.motionFile != null) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        setVideoPath(pair.motionFile.absolutePath)
                        setOnPreparedListener { player ->
                            player.setVolume(0f, 0f)
                            player.isLooping = true
                            start()
                        }
                        videoView = this
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Surface(
            shape = MaterialTheme.shapes.small,
            color = Color.Black.copy(alpha = 0.62f),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White)
                Text(
                    text = if (pair.motionFile == null) "LIVE · still only" else "LIVE",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

// Save-to-device exports (Motion Photo assembly, Ultra HDR conversion, and
// the two-file fallback) live in AttachmentExport.kt.

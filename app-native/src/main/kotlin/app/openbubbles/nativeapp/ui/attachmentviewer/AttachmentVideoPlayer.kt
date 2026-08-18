package app.openbubbles.nativeapp.ui.attachmentviewer

import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import java.io.File

/**
 * In-app video playback for a downloaded attachment. Uses the platform
 * player so HEVC / QuickTime payloads work on devices that have the codec.
 */
@Composable
fun AttachmentVideoPlayer(
    file: File,
    onPlaybackError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var playing by remember(file.absolutePath) { mutableStateOf(true) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    val onError = rememberUpdatedState(onPlaybackError)

    DisposableEffect(file.absolutePath) {
        onDispose {
            videoView?.stopPlayback()
            videoView = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics {
                role = Role.Button
                contentDescription = if (playing) "Pause video" else "Play video"
            }
            .clickable {
                val view = videoView ?: return@clickable
                if (view.isPlaying) {
                    view.pause()
                    playing = false
                } else {
                    view.start()
                    playing = true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { context ->
                VideoView(context).apply {
                    setVideoPath(file.absolutePath)
                    setOnPreparedListener { player ->
                        player.isLooping = false
                        start()
                        playing = true
                    }
                    setOnCompletionListener { playing = false }
                    setOnErrorListener { _, _, _ ->
                        onError.value()
                        true
                    }
                    videoView = this
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        if (!playing) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }
    }
}

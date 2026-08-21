package app.openbubbles.nativeapp.ui.attachmentviewer

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.defaultSpatialSpec
import app.openbubbles.nativeapp.ui.theme.fastEffectsSpec
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val POSITION_POLL_MS = 250L

/**
 * In-app video playback for a downloaded attachment, backed by ExoPlayer so
 * QuickTime/HEVC and HDR payloads from Apple devices play (or tone-map)
 * consistently instead of depending on `VideoView` quirks. Real controls:
 * play/pause, seek with position/duration, mute, buffering, and a retryable
 * error state with an external-open fallback.
 */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun AttachmentVideoPlayer(
    file: File,
    controlsVisible: Boolean,
    onOpenExternally: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val key = file.absolutePath
    var playing by remember(key) { mutableStateOf(true) }
    var playbackState by remember(key) { mutableIntStateOf(Player.STATE_IDLE) }
    var playbackError by remember(key) { mutableStateOf<PlaybackException?>(null) }
    var durationMs by remember(key) { mutableStateOf<Long?>(null) }
    var positionMs by remember(key) { mutableLongStateOf(0L) }
    var muted by remember(key) { mutableStateOf(false) }
    var dragFraction by remember(key) { mutableStateOf<Float?>(null) }

    val player = remember(key) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            prepare()
            playWhenReady = true
            addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        playing = isPlaying
                    }

                    override fun onPlaybackStateChanged(state: Int) {
                        playbackState = state
                        if (state == Player.STATE_READY) {
                            durationMs = duration.takeIf { it > 0 }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        playbackError = error
                    }
                },
            )
        }
    }
    DisposableEffect(player, lifecycleOwner) {
        var resumeAfterStop = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    resumeAfterStop = player.playWhenReady
                    player.pause()
                }
                Lifecycle.Event.ON_START -> if (resumeAfterStop) {
                    resumeAfterStop = false
                    player.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }
    LaunchedEffect(key) {
        while (isActive) {
            positionMs = player.currentPosition.coerceAtLeast(0L)
            delay(POSITION_POLL_MS)
        }
    }

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val presentationState = rememberPresentationState(player)
        PlayerSurface(
            player = player,
            modifier = Modifier
                .resizeWithContentScale(ContentScale.Fit, presentationState.videoSizeDp)
                .align(Alignment.Center),
        )
        if (presentationState.coverSurface) {
            Box(Modifier.fillMaxSize().background(Color.Black))
        }
        when {
            playbackError != null -> PlaybackErrorCard(
                onRetry = {
                    playbackError = null
                    player.prepare()
                    player.play()
                },
                onOpenExternally = onOpenExternally,
            )
            playbackState == Player.STATE_BUFFERING ->
                CircularWavyProgressIndicator(modifier = Modifier.size(56.dp))
            playbackState == Player.STATE_ENDED -> IconButton(
                onClick = {
                    player.seekTo(0)
                    player.play()
                },
                modifier = Modifier.size(64.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay,
                    contentDescription = "Replay video",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        AnimatedVisibility(
            visible = controlsVisible && playbackError == null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(defaultSpatialSpec()) { it / 2 } + fadeIn(defaultEffectsSpec()),
            exit = slideOutVertically(defaultSpatialSpec()) { it / 2 } + fadeOut(fastEffectsSpec()),
        ) {
            VideoPlayerControls(
                playing = playing,
                positionMs = positionMs,
                durationMs = durationMs,
                muted = muted,
                dragFraction = dragFraction,
                onPlayPause = {
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        if (playbackState == Player.STATE_ENDED) player.seekTo(0)
                        player.play()
                    }
                },
                onScrub = { fraction -> dragFraction = fraction.coerceIn(0f, 1f) },
                onScrubEnd = {
                    val duration = durationMs
                    val fraction = dragFraction
                    if (duration != null && fraction != null) {
                        clampSeekPositionMs((fraction * duration).toLong(), duration)
                            ?.let { target ->
                                player.seekTo(target)
                                positionMs = target
                            }
                    }
                    dragFraction = null
                },
                onToggleMute = {
                    muted = !muted
                    player.volume = if (muted) 0f else 1f
                },
            )
        }
    }
}

/**
 * Stateless control chrome for the video player: independent play/pause,
 * position/duration (unknown duration is labeled, never a bogus zero), a
 * clamped seek slider, and mute — each its own >=48dp target.
 */
@Composable
internal fun VideoPlayerControls(
    playing: Boolean,
    positionMs: Long,
    durationMs: Long?,
    muted: Boolean,
    dragFraction: Float?,
    onPlayPause: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onToggleMute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playing) "Pause video" else "Play video",
                    tint = Color.White,
                )
            }
            Text(
                text = formatPlaybackTime(positionMs),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Slider(
                value = dragFraction ?: playbackFraction(positionMs, durationMs),
                onValueChange = onScrub,
                onValueChangeFinished = onScrubEnd,
                enabled = durationMs != null,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .semantics { contentDescription = "Seek position" },
            )
            Text(
                text = durationMs?.let(::formatPlaybackTime) ?: "–:–",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            IconButton(onClick = onToggleMute) {
                Icon(
                    imageVector = if (muted) {
                        Icons.AutoMirrored.Filled.VolumeOff
                    } else {
                        Icons.AutoMirrored.Filled.VolumeUp
                    },
                    contentDescription = if (muted) "Unmute video" else "Mute video",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun PlaybackErrorCard(
    onRetry: () -> Unit,
    onOpenExternally: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Couldn't play this video",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Row {
            Button(onClick = onRetry) { Text("Retry") }
            TextButton(
                onClick = onOpenExternally,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Open externally", color = Color.White) }
        }
    }
}

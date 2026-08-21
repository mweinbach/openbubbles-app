package app.openbubbles.nativeapp.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.ui.common.formatBytes
import app.openbubbles.nativeapp.ui.theme.LocalReduceMotion
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews
import app.openbubbles.nativeapp.ui.theme.defaultEffectsSpec
import app.openbubbles.nativeapp.ui.theme.fastSpatialSpec
import app.openbubbles.nativeapp.ui.theme.smsServiceColors
import java.io.File
import kotlin.math.roundToInt

/** Stable player width; the bubble column's own cap coerces it when narrow. */
private val VoiceMemoWidth = 248.dp

/** Color roles of one voice-memo player, derived from the bubble it sits in. */
private data class VoiceMemoPalette(
    val bubble: Color,
    val onBubble: Color,
    val playCircle: Color,
    val onPlayCircle: Color,
    val wave: Color,
)

/**
 * Outgoing players invert the service bubble (white controls on blue/green,
 * the iMessage voice-memo look); incoming players carry the theme accent on
 * the tonal container, like every other incoming affordance.
 */
@Composable
private fun voiceMemoPalette(fromMe: Boolean, smsChat: Boolean): VoiceMemoPalette = when {
    fromMe && smsChat -> smsServiceColors().let {
        VoiceMemoPalette(it.container, it.content, it.content, it.container, it.content)
    }
    fromMe -> MaterialTheme.colorScheme.let {
        VoiceMemoPalette(it.primary, it.onPrimary, it.onPrimary, it.primary, it.onPrimary)
    }
    else -> MaterialTheme.colorScheme.let {
        VoiceMemoPalette(
            it.surfaceContainerHigh,
            it.onSurface,
            it.primary,
            it.onPrimary,
            it.primary,
        )
    }
}

/**
 * An audio attachment rendered as an inline voice-memo player: play/pause,
 * a seekable wave, and the duration — playback stays in the transcript
 * instead of opening a file. While the payload is not on disk the pill
 * falls back to the placeholder family every other attachment uses (tonal
 * surface, name, size, download chip).
 */
@Composable
fun VoiceMemoBubble(
    attachment: AttachmentMeta,
    attachmentFile: (String) -> File?,
    onDownloadAttachment: (AttachmentMeta) -> Unit,
    fromMe: Boolean,
    smsChat: Boolean,
    onLongPress: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(18.dp),
) {
    // Disk presence beats the persisted flag, matching the image/video path.
    val file = rememberAttachmentFile(attachment, attachmentFile)
    if (file != null) {
        val palette = voiceMemoPalette(fromMe, smsChat)
        Surface(
            shape = shape,
            color = palette.bubble,
            contentColor = palette.onBubble,
            modifier = modifier.width(VoiceMemoWidth),
        ) {
            VoiceMemoPlayerContent(
                playerKey = attachment.guid,
                file = file,
                playCircle = palette.playCircle,
                onPlayCircle = palette.onPlayCircle,
                wave = palette.wave,
                fallbackLabel = attachment.name,
                onLongPress = onLongPress,
                onDoubleTap = onDoubleTap,
            )
        }
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = modifier.width(VoiceMemoWidth),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                            CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = attachment.name ?: "Voice memo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    val size = formatBytes(attachment.sizeBytes)
                    if (size.isNotEmpty()) {
                        Text(
                            text = size,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DownloadChip(
                        attachment = attachment,
                        onDownload = onDownloadAttachment,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

/**
 * The shared player face: morphing play/pause circle, seekable wave, time
 * readout. Used by the transcript bubble and the composer's staged-memo
 * card; playback itself lives in [ChatAudioPlayer] so only one memo sounds
 * at a time wherever it renders.
 */
@Composable
internal fun VoiceMemoPlayerContent(
    playerKey: String,
    file: File,
    playCircle: Color,
    onPlayCircle: Color,
    wave: Color,
    fallbackLabel: String?,
    onLongPress: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val playback by ChatAudioPlayer.state.collectAsStateWithLifecycle()
    val mine = playback?.takeIf { it.key == playerKey }
    val playing = mine?.playing == true
    val declaredDuration by rememberAudioDurationMillis(file)
    val durationMillis = mine?.durationMillis?.takeIf { it > 0 }?.toLong() ?: declaredDuration
    val fraction = mine?.let {
        if (it.durationMillis > 0) {
            (it.positionMillis.toFloat() / it.durationMillis).coerceIn(0f, 1f)
        } else {
            0f
        }
    } ?: 0f
    val timeLabel = when {
        mine != null && (playing || mine.positionMillis > 0) && durationMillis != null ->
            "${formatRecordingTime(mine.positionMillis.toLong())} / ${formatRecordingTime(durationMillis)}"
        durationMillis != null -> formatRecordingTime(durationMillis)
        else -> fallbackLabel ?: "Voice memo"
    }
    Row(
        modifier = modifier.padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PlayPauseMorphButton(
            playing = playing,
            circle = playCircle,
            onCircle = onPlayCircle,
            onClick = { ChatAudioPlayer.toggle(playerKey, file) },
            onLongPress = onLongPress,
            onDoubleTap = onDoubleTap,
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            WavySeekBar(
                fraction = fraction,
                onSeek = { target ->
                    val duration = durationMillis ?: return@WavySeekBar
                    ChatAudioPlayer.seekTo(playerKey, (target * duration).roundToInt())
                },
                seekingEnabled = mine != null,
                playing = playing,
                color = wave,
                onLongPress = onLongPress,
                onDoubleTap = onDoubleTap,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current,
                modifier = Modifier.padding(start = 2.dp, top = 1.dp),
            )
        }
    }
}

/**
 * 48dp play/pause circle with the same press corner-morph as the send
 * circle: spatial springs carry the shape, color stays static.
 */
@Composable
internal fun PlayPauseMorphButton(
    playing: Boolean,
    circle: Color,
    onCircle: Color,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val cornerPercent by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (pressed) 30f else 50f,
        animationSpec = fastSpatialSpec(),
        label = "playPauseCorner",
    )
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(cornerPercent.roundToInt()))
            .background(circle)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = androidx.compose.ui.semantics.Role.Button,
                onClickLabel = if (playing) "Pause voice memo" else "Play voice memo",
                onClick = onClick,
                onLongClick = onLongPress,
                onDoubleClick = onDoubleTap,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Pause voice memo" else "Play voice memo",
            tint = onCircle,
            modifier = Modifier.size(24.dp),
        )
    }
}

/**
 * Seek bar over the platform's wavy determinate track: the wave flattens
 * while paused (and entirely under reduce-motion), a thumb rides the play
 * position in the carved gap, and drag/tap seeks. Semantics are declared
 * explicitly because the gestures are hand-rolled ([seekingEnabled] gates
 * them until the memo is loaded — before that the play button is the only
 * entry point, matching iOS).
 */
@Composable
internal fun WavySeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    seekingEnabled: Boolean,
    playing: Boolean,
    color: Color,
    onLongPress: (() -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = LocalReduceMotion.current
    val amplitude by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (playing && !reduceMotion) 1f else 0f,
        animationSpec = defaultEffectsSpec(),
        label = "waveAmplitude",
    )
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val displayFraction = (dragFraction ?: fraction).coerceIn(0f, 1f)
    val strokeWidth = 3.dp
    val thumbRadius = 5.dp
    val density = LocalDensity.current
    val stroke = remember(density, strokeWidth) {
        with(density) { Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round) }
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .semantics {
                contentDescription = "Voice memo seek"
                progressBarRangeInfo = ProgressBarRangeInfo(displayFraction, 0f..1f)
                if (seekingEnabled) {
                    setProgress { target ->
                        onSeek(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .pointerInput(seekingEnabled, onLongPress, onDoubleTap) {
                if (!seekingEnabled) return@pointerInput
                detectTapGestures(
                    onDoubleTap = onDoubleTap?.let { callback -> { _: Offset -> callback() } },
                    onLongPress = onLongPress?.let { callback -> { _: Offset -> callback() } },
                    onTap = { offset ->
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    },
                )
            }
            .pointerInput(seekingEnabled) {
                if (!seekingEnabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onSeek(checkNotNull(dragFraction))
                    },
                    onDragEnd = { dragFraction = null },
                    onDragCancel = { dragFraction = null },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragFraction =
                            ((dragFraction ?: fraction) + dragAmount / size.width).coerceIn(0f, 1f)
                        onSeek(checkNotNull(dragFraction))
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        LinearWavyProgressIndicator(
            progress = { displayFraction },
            color = color,
            trackColor = color.copy(alpha = 0.35f),
            stroke = stroke,
            trackStroke = stroke,
            gapSize = thumbRadius + 3.dp,
            amplitude = { progress -> if (progress > 0f && progress < 1f) amplitude else 0f },
            modifier = Modifier.fillMaxWidth(),
        )
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = color,
                radius = thumbRadius.toPx(),
                center = Offset(size.width * displayFraction, size.height / 2f),
            )
        }
    }
}

// --------------------------------------------------------------------- previews

@LightDarkPreviews
@Composable
private fun VoiceMemoBubblePreview() {
    OpenBubblesTheme(dynamicColor = false) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            // Player state: the file lambda resolves, so the pill renders
            // (duration falls back to the name under the JVM renderer).
            VoiceMemoBubble(
                attachment = AttachmentMeta(
                    guid = "vm-out", mime = "audio/mp4", name = "Audio Message.m4a",
                    sizeBytes = 184_320L, isImage = false, downloaded = true,
                ),
                attachmentFile = { File("/nonexistent/voice.m4a") },
                onDownloadAttachment = {},
                fromMe = true,
                smsChat = false,
            )
            VoiceMemoBubble(
                attachment = AttachmentMeta(
                    guid = "vm-in", mime = "audio/mp4", name = "Audio Message.m4a",
                    sizeBytes = 96_256L, isImage = false, downloaded = true,
                ),
                attachmentFile = { File("/nonexistent/voice.m4a") },
                onDownloadAttachment = {},
                fromMe = false,
                smsChat = false,
            )
            // Not-on-disk state: placeholder + download chip.
            VoiceMemoBubble(
                attachment = AttachmentMeta(
                    guid = "vm-pending", mime = "audio/mp4", name = "Audio Message.m4a",
                    sizeBytes = 61_440L, isImage = false, downloaded = false,
                ),
                attachmentFile = { null },
                onDownloadAttachment = {},
                fromMe = false,
                smsChat = false,
            )
        }
    }
}

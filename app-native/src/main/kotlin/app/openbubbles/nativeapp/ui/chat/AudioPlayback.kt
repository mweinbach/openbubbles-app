package app.openbubbles.nativeapp.ui.chat

import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Position refresh cadence while a voice memo plays. */
private const val PlaybackTickMillis = 100L

/**
 * The one voice memo allowed to make sound at a time, app-wide. Starting a
 * memo stops whichever was playing; pausing keeps the position so the wave
 * resumes where it stopped. Completion rewinds to 0:00 paused, ready to
 * replay.
 *
 * All public functions must be called on the main thread (Compose event
 * handlers already are); the [MediaPlayer] never leaves it. The player is
 * released as soon as playback stops or another memo takes over, so an idle
 * transcript holds no media resources.
 */
object ChatAudioPlayer {

    /** Snapshot of the memo currently loaded, or null when nothing is. */
    data class Playback(
        /** Caller-chosen identity (attachment guid, or a staged file path). */
        val key: String,
        val positionMillis: Int,
        val durationMillis: Int,
        /** False when paused mid-way; the loaded memo keeps its position. */
        val playing: Boolean,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null

    private val _state = MutableStateFlow<Playback?>(null)

    /** The loaded memo's live state; null when nothing is loaded. */
    val state: StateFlow<Playback?> = _state.asStateFlow()

    /** Play/pause for [file] under [key]; a different key takes over. */
    fun toggle(key: String, file: File) {
        val current = _state.value
        if (current?.key == key) {
            if (current.playing) pause() else resume()
            return
        }
        releasePlayer()
        val created = runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                setOnCompletionListener { onCompletion(key) }
                setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    true
                }
                prepare()
                start()
            }
        }.getOrNull() ?: return
        player = created
        _state.value = Playback(
            key = key,
            positionMillis = 0,
            durationMillis = created.duration.coerceAtLeast(0),
            playing = true,
        )
        startTicker()
    }

    /** Moves the loaded memo (matching [key]) to [positionMillis]. */
    fun seekTo(key: String, positionMillis: Int) {
        val current = _state.value ?: return
        if (current.key != key) return
        val target = positionMillis.coerceIn(0, current.durationMillis)
        runCatching { player?.seekTo(target) }
        _state.value = current.copy(positionMillis = target)
    }

    /** Stops playback and releases the player (position forgotten). */
    fun stop() = releasePlayer()

    private fun pause() {
        val current = _state.value ?: return
        runCatching { player?.pause() }
        ticker?.cancel()
        _state.value = current.copy(
            positionMillis = runCatching { player?.currentPosition }
                .getOrNull() ?: current.positionMillis,
            playing = false,
        )
    }

    private fun resume() {
        val current = _state.value ?: return
        val mediaPlayer = player ?: return
        runCatching { mediaPlayer.start() }
        _state.value = current.copy(playing = true)
        startTicker()
    }

    private fun onCompletion(key: String) {
        val current = _state.value ?: return
        if (current.key != key) return
        runCatching {
            player?.seekTo(0)
            player?.pause()
        }
        ticker?.cancel()
        _state.value = current.copy(positionMillis = 0, playing = false)
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                delay(PlaybackTickMillis)
                val mediaPlayer = player ?: break
                val current = _state.value ?: break
                if (!current.playing) break
                _state.value = current.copy(
                    positionMillis = runCatching { mediaPlayer.currentPosition }
                        .getOrNull() ?: current.positionMillis,
                )
            }
        }
    }

    private fun releasePlayer() {
        ticker?.cancel()
        ticker = null
        runCatching { player?.release() }
        player = null
        _state.value = null
    }
}

/**
 * Declared duration of an audio [file] in milliseconds, read off the main
 * thread; null while unknown (missing file, undecodable container, or the
 * preview renderer, where [MediaMetadataRetriever] is stubbed). Keyed on
 * the file so a re-emitted row does not re-decode.
 */
@Composable
fun rememberAudioDurationMillis(file: File?): State<Long?> =
    produceState<Long?>(initialValue = null, file) {
        if (file == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(file.absolutePath)
                    retriever
                        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                        ?.toLongOrNull()
                        ?.takeIf { it > 0L }
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }

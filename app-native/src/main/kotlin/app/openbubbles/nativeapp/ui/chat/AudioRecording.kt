package app.openbubbles.nativeapp.ui.chat

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.openbubbles.nativeapp.data.OutgoingAttachment
import java.io.File

/** Live level bars shown in the composer while a voice message records. */
private const val RecordingLevelBarCount = 20

/** Shorter takes are almost always accidental taps; they are discarded. */
private const val MinRecordingMillis = 800L

/** m:ss formatting for the composer's recording timer. */
internal fun formatRecordingTime(millis: Long): String {
    val totalSeconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

/**
 * Immutable snapshot of an in-progress voice recording for the composer UI.
 * Kept free of [MediaRecorder] so previews and screenshot tests can render
 * the recording state without hardware.
 */
data class RecordingUiState(
    val elapsedMillis: Long,
    /** Rolling mic levels (0f..1f), oldest first; the newest appends right. */
    val levels: List<Float>,
)

/**
 * One in-progress voice-message recording. Owns the [MediaRecorder] and its
 * cache file for as long as the composer shows the recording UI; [finish]
 * stops the take and returns it as a sendable attachment, [discard] deletes
 * it. [tick] must be pumped on a short cadence (~100ms) from the composition
 * so [elapsedMillis] and [levels] stay live.
 */
@Stable
class AudioRecordingSession private constructor(
    private val recorder: MediaRecorder,
    private val outputFile: File,
    private val startedAtElapsed: Long,
) {
    /** Elapsed recording time; advanced by [tick]. */
    var elapsedMillis by mutableLongStateOf(0L)
        private set

    /** Rolling mic levels (0f..1f) feeding the composer's live level bars. */
    var levels by mutableStateOf(List(RecordingLevelBarCount) { 0f })
        private set

    /** Polls the clock and the mic level; call every ~100ms while active. */
    fun tick() {
        elapsedMillis = SystemClock.elapsedRealtime() - startedAtElapsed
        val amplitude = runCatching { recorder.maxAmplitude }.getOrDefault(0)
        val level = (amplitude / 32767f).coerceIn(0f, 1f)
        levels = levels.drop(1) + level
    }

    /**
     * Stops the take and returns it as an [OutgoingAttachment] ready for the
     * existing send pipeline, or null when the take is unusable (too short,
     * or the recorder failed) — unusable takes are deleted.
     */
    fun finish(): OutgoingAttachment? {
        val elapsed = SystemClock.elapsedRealtime() - startedAtElapsed
        val stopped = stopSafely()
        val usable = stopped && elapsed >= MinRecordingMillis && outputFile.length() > 0
        if (!usable) {
            outputFile.delete()
            return null
        }
        return OutgoingAttachment(
            file = outputFile,
            mime = "audio/mp4",
            uti = utiForMime("audio/mp4"),
            name = outputFile.name,
            sizeBytes = outputFile.length(),
        )
    }

    /** Stops the take and deletes the file. */
    fun discard() {
        stopSafely()
        outputFile.delete()
    }

    private fun stopSafely(): Boolean = runCatching {
        recorder.stop()
        recorder.release()
    }.isSuccess

    companion object {
        /**
         * Starts recording AAC audio into the outgoing-attachment cache.
         * Returns null (and cleans up) when the mic cannot be opened. The
         * caller is responsible for the RECORD_AUDIO runtime permission.
         */
        fun start(context: Context): AudioRecordingSession? {
            val dir = File(context.cacheDir, "outgoing").apply { mkdirs() }
            val file = File(dir, "voice-${System.currentTimeMillis()}.m4a")
            @Suppress("DEPRECATION")
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            return runCatching {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44_100)
                recorder.setOutputFile(file.absolutePath)
                recorder.prepare()
                recorder.start()
                AudioRecordingSession(recorder, file, SystemClock.elapsedRealtime())
            }.onFailure {
                runCatching { recorder.release() }
                file.delete()
            }.getOrNull()
        }
    }
}

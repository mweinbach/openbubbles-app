package app.openbubbles.nativeapp.data

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

private const val PROGRESS_POLL_INTERVAL_MS = 500L

sealed interface VideoCompressionResult {
    data class Success(val file: File, val sizeBytes: Long) : VideoCompressionResult
    data class Failure(val message: String) : VideoCompressionResult
}

/**
 * Runs the user-confirmed 1080p/HEVC compression of an oversized video draft
 * (issue #49). The source is never mutated: the derived output is written to
 * a unique `.part` sibling under cache/outgoing, revalidated against the same
 * local draft policy, and only then atomically promoted. Cancellation and
 * failure delete only the app-owned partial.
 *
 * HDR sources keep HDR when the device encoder supports it; Media3 falls back
 * to tone-mapped SDR otherwise. Devices without an HEVC encoder fall back to
 * the closest supported format (still compressed, still sendable).
 */
suspend fun compressOutgoingVideo(
    context: Context,
    source: Uri,
    plan: VideoCompressionPlan,
    displayName: String?,
    maxBytes: Long = MAX_OUTGOING_DRAFT_BYTES,
    onProgress: (Float) -> Unit = {},
): VideoCompressionResult {
    val directory = File(context.cacheDir, "outgoing")
    val baseName = displayName.orEmpty()
        .substringBeforeLast('.')
        .replace(Regex("[<>:\"/\\\\|?*]"), "_")
        .ifBlank { "video" }
    val target = File(directory, "${UUID.randomUUID()}-$baseName.mp4")
    val partial = File(directory, ".${target.name}.part")

    try {
        withContext(Dispatchers.IO) { directory.mkdirs() }
        withContext(Dispatchers.Main) {
            runTransformer(context, source, plan, partial, onProgress)
        }
    } catch (cancellation: CancellationException) {
        withContext(NonCancellable + Dispatchers.IO) { partial.delete() }
        throw cancellation
    } catch (_: Exception) {
        withContext(NonCancellable + Dispatchers.IO) { partial.delete() }
        return VideoCompressionResult.Failure("Could not compress video")
    }

    return withContext(Dispatchers.IO) {
        val sizeBytes = partial.length()
        when {
            sizeBytes > maxBytes -> {
                partial.delete()
                VideoCompressionResult.Failure(
                    "Compressed video is still larger than ${maxBytes / (1024 * 1024)} MB",
                )
            }
            !isDerivedVideoWithinPolicy(sizeBytes, maxBytes) ||
                inspectOutgoingVideo(context, Uri.fromFile(partial), sizeBytes).width == null -> {
                partial.delete()
                VideoCompressionResult.Failure("Could not compress video")
            }
            else -> {
                promoteOwnedSibling(partial, target)
                VideoCompressionResult.Success(target, sizeBytes)
            }
        }
    }
}

/** Display name for the derived output ("clip.mov" -> "clip.mp4"). */
fun derivedVideoDisplayName(displayName: String?): String {
    val base = displayName.orEmpty().substringBeforeLast('.').ifBlank { "video" }
    return "$base.mp4"
}

@androidx.annotation.OptIn(UnstableApi::class)
private suspend fun runTransformer(
    context: Context,
    source: Uri,
    plan: VideoCompressionPlan,
    output: File,
    onProgress: (Float) -> Unit,
): Unit = suspendCancellableCoroutine { continuation ->
    val transformer = Transformer.Builder(context)
        .setVideoMimeType(MimeTypes.VIDEO_H265)
        .setEncoderFactory(
            DefaultEncoderFactory.Builder(context)
                .setRequestedVideoEncoderSettings(
                    // A non-default bitrate also forces a real re-encode, so an
                    // oversized source that is already <=1080p HEVC cannot be
                    // transmuxed back out at its original size.
                    VideoEncoderSettings.Builder()
                        .setBitrate(plan.targetVideoBitrate)
                        .build(),
                )
                .build(),
        )
        .addListener(
            object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    if (continuation.isActive) continuation.resumeWithException(exportException)
                }
            },
        )
        .build()

    val videoEffects = listOfNotNull(plan.targetHeight?.let { Presentation.createForHeight(it) })
    val editedItem = EditedMediaItem.Builder(MediaItem.fromUri(source))
        .setEffects(Effects(emptyList(), videoEffects))
        .build()

    val handler = Handler(Looper.getMainLooper())
    val progressHolder = ProgressHolder()
    val poll = object : Runnable {
        override fun run() {
            if (!continuation.isActive) return
            if (transformer.getProgress(progressHolder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                onProgress(progressHolder.progress / 100f)
            }
            handler.postDelayed(this, PROGRESS_POLL_INTERVAL_MS)
        }
    }
    continuation.invokeOnCancellation {
        // Transformer.cancel must run on the looper it was created with.
        handler.post { runCatching { transformer.cancel() } }
    }
    transformer.start(editedItem, output.absolutePath)
    handler.post(poll)
}

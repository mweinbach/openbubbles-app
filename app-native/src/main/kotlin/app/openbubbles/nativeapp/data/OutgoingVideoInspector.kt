package app.openbubbles.nativeapp.data

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri

/**
 * Reads the metadata [outgoingVideoDecision] needs from a video source
 * before enqueue. Best-effort: every unreadable field stays null and the
 * policy layer decides what that means.
 */
fun inspectOutgoingVideo(context: Context, uri: Uri, sizeBytes: Long?): OutgoingVideoMetadata {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(context, uri, null)
        var video: MediaFormat? = null
        for (index in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(index)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true) {
                video = format
                break
            }
        }
        OutgoingVideoMetadata(
            sizeBytes = sizeBytes,
            durationMs = video?.longOrNull(MediaFormat.KEY_DURATION)?.let { it / 1_000 },
            width = video?.intOrNull(MediaFormat.KEY_WIDTH),
            height = video?.intOrNull(MediaFormat.KEY_HEIGHT),
            rotationDegrees = video?.intOrNull(MediaFormat.KEY_ROTATION) ?: 0,
            videoMime = video?.getString(MediaFormat.KEY_MIME),
            isHdr = video?.let(::isHdrFormat) ?: false,
            frameRate = video?.frameRateOrNull(),
        )
    } catch (_: Exception) {
        OutgoingVideoMetadata(sizeBytes = sizeBytes, durationMs = null, width = null, height = null)
    } finally {
        extractor.release()
    }
}

/** Exact provider byte size, when the provider can report one. */
fun resolveContentLength(context: Context, uri: Uri): Long? = runCatching {
    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { descriptor ->
        descriptor.length.takeIf { it != AssetFileDescriptor.UNKNOWN_LENGTH && it >= 0 }
    }
}.getOrNull()

private fun isHdrFormat(format: MediaFormat): Boolean {
    val transfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
    if (transfer == MediaFormat.COLOR_TRANSFER_ST2084 ||
        transfer == MediaFormat.COLOR_TRANSFER_HLG
    ) {
        return true
    }
    return format.intOrNull(MediaFormat.KEY_COLOR_STANDARD) == MediaFormat.COLOR_STANDARD_BT2020
}

private fun MediaFormat.intOrNull(key: String): Int? =
    runCatching { if (containsKey(key)) getInteger(key) else null }.getOrNull()

private fun MediaFormat.longOrNull(key: String): Long? =
    runCatching { if (containsKey(key)) getLong(key) else null }.getOrNull()

/** KEY_FRAME_RATE may be stored as an integer or a float depending on muxer. */
private fun MediaFormat.frameRateOrNull(): Float? = runCatching {
    if (!containsKey(MediaFormat.KEY_FRAME_RATE)) return@runCatching null
    runCatching { getInteger(MediaFormat.KEY_FRAME_RATE).toFloat() }
        .getOrElse { getFloat(MediaFormat.KEY_FRAME_RATE) }
}.getOrNull()

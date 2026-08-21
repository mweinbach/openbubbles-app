package app.openbubbles.nativeapp.data

import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Local product policy for oversized outgoing videos (issue #49).
 *
 * The only hard boundary here is [MAX_OUTGOING_DRAFT_BYTES], which is this
 * app's own draft-staging ceiling — it is NOT a measured Apple/iMessage
 * limit. A video within the ceiling is sent untouched ("only if needed").
 * A video over the ceiling is offered an explicit, user-confirmed
 * compression to at most 1080p HEVC that keeps HDR when the source is HDR.
 */

/** Output short side. 3840x2160 -> 1920x1080; portrait 2160x3840 -> 1080x1920. */
internal const val VIDEO_COMPRESSION_TARGET_SHORT_SIDE = 1080

/** HEVC track MIME as reported by MediaExtractor/MediaFormat. */
internal const val HEVC_MIME = "video/hevc"

/**
 * Bits per pixel per frame for the HEVC re-encode. 0.07 gives ~4.4 Mbps for
 * 1080p30 SDR — comparable to iOS "High Efficiency" transcode output.
 */
internal const val VIDEO_COMPRESSION_BITS_PER_PIXEL = 0.07

/** 10-bit HDR needs headroom over the SDR budget to avoid banding. */
internal const val VIDEO_COMPRESSION_HDR_BITRATE_FACTOR = 1.25

internal const val MIN_VIDEO_COMPRESSION_BITRATE = 2_000_000
internal const val MAX_VIDEO_COMPRESSION_BITRATE = 12_000_000

/** Frame rate assumed for the bitrate budget when the container omits it. */
internal const val VIDEO_COMPRESSION_ASSUMED_FPS = 30.0

/** Audio is transmuxed, not re-encoded; this only feeds the size estimate. */
internal const val VIDEO_COMPRESSION_ESTIMATED_AUDIO_BITRATE = 128_000

/** Container overhead applied on top of the stream-payload size estimate. */
internal const val VIDEO_COMPRESSION_MUX_OVERHEAD = 1.05

/**
 * Metadata read from a selected or captured video before enqueue.
 *
 * [sizeBytes] is null only when the exact byte size could not be resolved
 * and the bounded draft copy already proved the source exceeds the ceiling;
 * callers with an unknown size must attempt the bounded copy first.
 */
data class OutgoingVideoMetadata(
    val sizeBytes: Long?,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val rotationDegrees: Int = 0,
    val videoMime: String? = null,
    val isHdr: Boolean = false,
    val frameRate: Float? = null,
)

/** Deterministic transcode parameters shown to the user before they confirm. */
data class VideoCompressionPlan(
    /**
     * Display-oriented output frame height (after rotation is applied), or
     * null to keep the source dimensions (already at or under 1080p).
     */
    val targetHeight: Int?,
    /** Requested HEVC encoder bitrate in bits per second. */
    val targetVideoBitrate: Int,
    /** Source is HDR; the transcode keeps HDR when the device can encode it. */
    val keepHdr: Boolean,
    /** Source video track is already HEVC (re-encoded anyway to shrink it). */
    val alreadyHevc: Boolean,
    /** Estimated output byte size, or null when duration is unknown. */
    val estimatedOutputBytes: Long?,
)

sealed interface OutgoingVideoDecision {
    /** Within the local draft policy — stage the original untouched. */
    data object SendOriginal : OutgoingVideoDecision

    /** Over the local draft policy; offer the explicit compression flow. */
    data class OfferCompression(val plan: VideoCompressionPlan) : OutgoingVideoDecision

    /** Over the local draft policy and the video track is unreadable. */
    data object RejectUnreadable : OutgoingVideoDecision
}

/**
 * Decides what to do with a video draft before it can enter the pending-send
 * list. A null [OutgoingVideoMetadata.sizeBytes] means the bounded copy
 * already proved the source is over the ceiling (see the metadata contract).
 */
fun outgoingVideoDecision(
    metadata: OutgoingVideoMetadata,
    maxBytes: Long = MAX_OUTGOING_DRAFT_BYTES,
): OutgoingVideoDecision {
    val size = metadata.sizeBytes
    if (size != null && size <= maxBytes) return OutgoingVideoDecision.SendOriginal
    val width = metadata.width ?: return OutgoingVideoDecision.RejectUnreadable
    val height = metadata.height ?: return OutgoingVideoDecision.RejectUnreadable
    if (width <= 0 || height <= 0) return OutgoingVideoDecision.RejectUnreadable
    return OutgoingVideoDecision.OfferCompression(videoCompressionPlan(metadata))
}

/**
 * Computes the 1080p/HEVC transcode parameters for an oversized video.
 * Requires readable, positive dimensions (checked by [outgoingVideoDecision]).
 */
internal fun videoCompressionPlan(metadata: OutgoingVideoMetadata): VideoCompressionPlan {
    val width = requireNotNull(metadata.width)
    val height = requireNotNull(metadata.height)
    val rotated = metadata.rotationDegrees % 180 != 0
    val displayWidth = if (rotated) height else width
    val displayHeight = if (rotated) width else height

    val shortSide = min(displayWidth, displayHeight)
    val scale = if (shortSide > VIDEO_COMPRESSION_TARGET_SHORT_SIDE) {
        VIDEO_COMPRESSION_TARGET_SHORT_SIDE.toDouble() / shortSide
    } else {
        1.0
    }
    val targetHeight = if (scale < 1.0) evenDimension(displayHeight * scale) else null
    val outputWidth = if (scale < 1.0) evenDimension(displayWidth * scale) else displayWidth
    val outputHeight = targetHeight ?: displayHeight

    val fps = metadata.frameRate?.toDouble()?.takeIf { it > 0.0 } ?: VIDEO_COMPRESSION_ASSUMED_FPS
    val hdrFactor = if (metadata.isHdr) VIDEO_COMPRESSION_HDR_BITRATE_FACTOR else 1.0
    val rawBitrate = outputWidth.toDouble() * outputHeight * fps *
        VIDEO_COMPRESSION_BITS_PER_PIXEL * hdrFactor
    val bitrate = rawBitrate
        .coerceIn(MIN_VIDEO_COMPRESSION_BITRATE.toDouble(), MAX_VIDEO_COMPRESSION_BITRATE.toDouble())
        .roundToInt()

    val estimatedBytes = metadata.durationMs?.takeIf { it > 0 }?.let { durationMs ->
        val payloadBits = (bitrate + VIDEO_COMPRESSION_ESTIMATED_AUDIO_BITRATE).toDouble() *
            durationMs / 1000.0
        (payloadBits / 8.0 * VIDEO_COMPRESSION_MUX_OVERHEAD).roundToLong()
    }

    return VideoCompressionPlan(
        targetHeight = targetHeight,
        targetVideoBitrate = bitrate,
        keepHdr = metadata.isHdr,
        alreadyHevc = HEVC_MIME.equals(metadata.videoMime, ignoreCase = true),
        estimatedOutputBytes = estimatedBytes,
    )
}

/** Codecs require even dimensions; round to the nearest even value >= 2. */
internal fun evenDimension(value: Double): Int {
    val rounded = value.roundToInt()
    val even = if (rounded % 2 == 0) rounded else rounded - 1
    return even.coerceAtLeast(2)
}

/**
 * Revalidation of a derived transcode output before it may be staged: the
 * file must be non-empty and within the same local draft policy.
 */
fun isDerivedVideoWithinPolicy(
    sizeBytes: Long,
    maxBytes: Long = MAX_OUTGOING_DRAFT_BYTES,
): Boolean = sizeBytes in 1..maxBytes

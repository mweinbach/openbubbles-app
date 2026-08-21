package app.openbubbles.nativeapp.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.openbubbles.nativeapp.data.DraftTooLargeException
import app.openbubbles.nativeapp.data.MAX_OUTGOING_DRAFT_BYTES
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.OutgoingVideoDecision
import app.openbubbles.nativeapp.data.OutgoingVideoMetadata
import app.openbubbles.nativeapp.data.VideoCompressionPlan
import app.openbubbles.nativeapp.data.copyWithByteLimit
import app.openbubbles.nativeapp.data.inspectOutgoingVideo
import app.openbubbles.nativeapp.data.outgoingVideoDecision
import app.openbubbles.nativeapp.data.promoteOwnedSibling
import app.openbubbles.nativeapp.data.resolveContentLength
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Best-effort MIME → UTI map for outgoing attachments. Anything unrecognized
 * falls back to the generic `public.data` (or the family UTI for image/video/
 * audio/text prefixes).
 */
fun utiForMime(mime: String?): String = when (mime?.lowercase()) {
    "image/jpeg", "image/jpg" -> "public.jpeg"
    "image/png" -> "public.png"
    "image/gif" -> "public.gif"
    "image/heic" -> "public.heic"
    "image/heif" -> "public.heif"
    "image/webp" -> "public.webp"
    "image/bmp" -> "public.bmp"
    "image/x-ms-bmp" -> "public.bmp"
    "video/quicktime" -> "public.movie"
    "video/mp4" -> "public.mpeg-4-movie"
    "video/x-msvideo" -> "public.avi"
    "video/3gpp" -> "public.3gpp"
    "audio/mpeg" -> "public.mp3"
    "audio/mp4" -> "public.mpeg-4-audio"
    "audio/aac" -> "public.mpeg-4-audio"
    "audio/x-aac" -> "public.mpeg-4-audio"
    "audio/wav" -> "public.wav"
    "audio/x-wav" -> "public.wav"
    "application/pdf" -> "com.adobe.pdf"
    "text/plain" -> "public.plain-text"
    "text/vcard" -> "public.vcard"
    "text/calendar" -> "public.icalendar"
    null -> "public.data"
    else -> when {
        mime.startsWith("image/") -> "public.image"
        mime.startsWith("video/") -> "public.movie"
        mime.startsWith("audio/") -> "public.audio"
        mime.startsWith("text/") -> "public.text"
        else -> "public.data"
    }
}

/** One oversized video waiting for the explicit compress-and-confirm flow. */
data class VideoCompressionRequest(
    /** Untouched source; only a derived cache/outgoing copy is ever staged. */
    val source: Uri,
    val displayName: String,
    val metadata: OutgoingVideoMetadata,
    val plan: VideoCompressionPlan,
    /** App-owned draft (camera capture) removed once the flow settles. */
    val ownedSource: File? = null,
)

/** Structured result of preparing one picked source for the draft. */
sealed interface PreparedOutgoingItem {
    data class Ready(val attachment: OutgoingAttachment) : PreparedOutgoingItem
    data class NeedsVideoCompression(val request: VideoCompressionRequest) : PreparedOutgoingItem
    data class Failed(val message: String) : PreparedOutgoingItem
}

private val maxDraftMegabytes = MAX_OUTGOING_DRAFT_BYTES / (1024 * 1024)

/**
 * Prepares a picked content [uri] for the draft. Sources within the local
 * draft policy are copied into the app cache exactly as before. A video over
 * the policy is inspected and routed to the explicit compression review
 * instead of failing with a generic error; the original is never copied or
 * modified. Non-video sources over the policy fail with a sized explanation.
 */
suspend fun prepareOutgoingItem(context: Context, uri: Uri): PreparedOutgoingItem =
    withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        val mime = runCatching { resolver.getType(uri) }.getOrNull() ?: "application/octet-stream"

        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0 && !cursor.isNull(nameIdx)) name = cursor.getString(nameIdx)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) size = cursor.getLong(sizeIdx)
                }
            }
        }
        val displayName = name ?: uri.lastPathSegment ?: "attachment"
        val isVideo = mime.lowercase().startsWith("video/")
        val resolvedSize = size ?: resolveContentLength(context, uri)

        if (resolvedSize != null && resolvedSize > MAX_OUTGOING_DRAFT_BYTES) {
            return@withContext if (isVideo) {
                oversizedVideoItem(context, uri, displayName, resolvedSize)
            } else {
                PreparedOutgoingItem.Failed("Attachment is larger than $maxDraftMegabytes MB")
            }
        }

        val dir = File(context.cacheDir, "outgoing").apply { mkdirs() }
        val target = File(dir, "${UUID.randomUUID()}-${displayName.replace(Regex("[<>:\"/\\\\|?*]"), "_")}")
        val partial = File(dir, ".${target.name}.part")
        try {
            resolver.openInputStream(uri)?.use { input ->
                copyWithByteLimit(input, partial, MAX_OUTGOING_DRAFT_BYTES)
            } ?: return@withContext PreparedOutgoingItem.Failed("Could not read attachment")
            promoteOwnedSibling(partial, target)
        } catch (cancellation: CancellationException) {
            partial.delete()
            target.delete()
            throw cancellation
        } catch (tooLarge: DraftTooLargeException) {
            partial.delete()
            target.delete()
            // The provider hid the size; the bounded copy proved it is over.
            return@withContext if (isVideo) {
                oversizedVideoItem(context, uri, displayName, sizeBytes = null)
            } else {
                PreparedOutgoingItem.Failed("Attachment is larger than $maxDraftMegabytes MB")
            }
        } catch (_: Throwable) {
            partial.delete()
            target.delete()
            return@withContext PreparedOutgoingItem.Failed("Could not read attachment")
        }

        PreparedOutgoingItem.Ready(
            OutgoingAttachment(
                file = target,
                mime = mime,
                uti = utiForMime(mime),
                name = displayName,
                sizeBytes = target.length(),
            ),
        )
    }

/** Routes a proven-oversized video through the compression policy. */
private fun oversizedVideoItem(
    context: Context,
    uri: Uri,
    displayName: String,
    sizeBytes: Long?,
): PreparedOutgoingItem {
    val metadata = inspectOutgoingVideo(context, uri, sizeBytes)
    return when (val decision = outgoingVideoDecision(metadata)) {
        is OutgoingVideoDecision.OfferCompression -> PreparedOutgoingItem.NeedsVideoCompression(
            VideoCompressionRequest(
                source = uri,
                displayName = displayName,
                metadata = metadata,
                plan = decision.plan,
            ),
        )
        OutgoingVideoDecision.RejectUnreadable -> PreparedOutgoingItem.Failed(
            "Video is larger than $maxDraftMegabytes MB and could not be read for compression",
        )
        // Unreachable: this helper is only called for proven-oversized sizes.
        OutgoingVideoDecision.SendOriginal -> PreparedOutgoingItem.Failed(
            "Could not read attachment",
        )
    }
}

/**
 * Copies a picked content [uri] into the app cache and resolves the display
 * name, MIME type and size — producing an [OutgoingAttachment] ready for the
 * send path. Returns null when the stream cannot be opened or the source is
 * outside the local draft policy (legacy single-result path; the chat
 * composer uses [prepareOutgoingItem] to surface the compression flow).
 */
suspend fun prepareOutgoingAttachment(context: Context, uri: Uri): OutgoingAttachment? =
    (prepareOutgoingItem(context, uri) as? PreparedOutgoingItem.Ready)?.attachment

/** One-shot prep for a picked uri, recomposed when the uri changes. */
@Composable
fun rememberPreparedAttachment(uri: Uri?): OutgoingAttachment? {
    val context = LocalContext.current
    return produceState<OutgoingAttachment?>(initialValue = null, uri) {
        if (uri == null) return@produceState
        value = prepareOutgoingAttachment(context, uri)
    }.value
}

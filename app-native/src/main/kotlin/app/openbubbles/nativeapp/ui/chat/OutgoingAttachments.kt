package app.openbubbles.nativeapp.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import app.openbubbles.nativeapp.data.OutgoingAttachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

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

/**
 * Copies a picked content [uri] into the app cache and resolves the display
 * name, MIME type and size — producing an [OutgoingAttachment] ready for the
 * send path. Returns null when the stream cannot be opened.
 */
suspend fun prepareOutgoingAttachment(context: Context, uri: Uri): OutgoingAttachment? =
    withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri) ?: "application/octet-stream"

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

            val dir = File(context.cacheDir, "outgoing").apply { mkdirs() }
            val target = File(dir, "${System.currentTimeMillis()}-${displayName.replace(Regex("[<>:\"/\\\\|?*]"), "_")}")
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return@runCatching null

            val realSize = if (size != null && size > 0) size else target.length()
            OutgoingAttachment(
                file = target,
                mime = mime,
                uti = utiForMime(mime),
                name = displayName,
                sizeBytes = realSize,
            )
        }.getOrNull()
    }

/** One-shot prep for a picked uri, recomposed when the uri changes. */
@Composable
fun rememberPreparedAttachment(uri: Uri?): OutgoingAttachment? {
    val context = LocalContext.current
    return produceState<OutgoingAttachment?>(initialValue = null, uri) {
        if (uri == null) return@produceState
        value = prepareOutgoingAttachment(context, uri)
    }.value
}

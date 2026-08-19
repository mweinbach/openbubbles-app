package app.openbubbles.nativeapp.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PickedPhotoUpload(
    val file: File,
    val previewFile: File,
    val filename: String,
    val mimeType: String,
    val orientation: Int,
    val capturedAtMs: Long? = null,
)

/**
 * Copies a picker grant into a short-lived private file. The shared transfer
 * coordinator then fsyncs a content-addressed copy into the durable Photos
 * upload staging directory before recording the intent.
 */
suspend fun preparePhotoUploadCandidate(
    context: Context,
    uri: Uri,
): PickedPhotoUpload = withContext(Dispatchers.IO) {
    val resolver = context.contentResolver
    val mimeType = resolver.getType(uri)
        ?: throw IllegalArgumentException("The selected photo has no MIME type")
    require(mimeType == "image/jpeg") {
        "The first iCloud Photos upload supports JPEG images only"
    }

    var displayName: String? = null
    resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && !cursor.isNull(index)) displayName = cursor.getString(index)
        }
    }
    val filename = sanitizeFilename(displayName ?: uri.lastPathSegment ?: "photo")
    val stagingRoot = File(context.cacheDir, "photos-upload-picker").apply { mkdirs() }
    val candidate = File(stagingRoot, "${UUID.randomUUID()}-$filename")
    val preview = File(stagingRoot, "${UUID.randomUUID()}-preview.jpg")
    try {
        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(candidate).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        } ?: error("The selected photo could not be opened")
        require(candidate.length() > 0) { "The selected photo is empty" }
        val exif = ExifInterface(candidate)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL,
        ).takeIf { it in 1..8 } ?: ExifInterface.ORIENTATION_NORMAL
        val capturedAtMs = exif.dateTimeOriginal
        writePreview(context, uri, preview)
        PickedPhotoUpload(
            file = candidate,
            previewFile = preview,
            filename = filename,
            mimeType = mimeType,
            orientation = orientation,
            capturedAtMs = capturedAtMs,
        )
    } catch (error: Throwable) {
        candidate.delete()
        preview.delete()
        throw error
    }
}

private fun writePreview(context: Context, uri: Uri, destination: File) {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        require(longest > 0) { "The selected JPEG could not be decoded" }
        val targetScale = 414f / longest.toFloat()
        val targetWidth = (info.size.width * targetScale).roundToInt().coerceAtLeast(1)
        val targetHeight = (info.size.height * targetScale).roundToInt().coerceAtLeast(1)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetSize(targetWidth, targetHeight)
    }
    try {
        FileOutputStream(destination).use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 85, output)) {
                "The selected photo preview could not be encoded"
            }
            output.fd.sync()
        }
    } finally {
        bitmap.recycle()
    }
    require(destination.length() > 0) { "The selected photo preview is empty" }
}

private fun sanitizeFilename(value: String): String {
    val sanitized = value
        .trim()
        .replace(Regex("[<>:\"/\\\\|?*\\p{Cntrl}]"), "_")
        .take(255)
    return sanitized.ifBlank { "photo" }
}

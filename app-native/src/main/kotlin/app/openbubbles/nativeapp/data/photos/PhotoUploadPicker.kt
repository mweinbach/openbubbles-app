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
import kotlin.math.min
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
    require(mimeType.startsWith("image/")) { "The selected item is not a photo" }

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
    val pickedName = sanitizeFilename(displayName ?: uri.lastPathSegment ?: "photo")
    val filename = pickedName.substringBeforeLast('.', pickedName).ifBlank { "photo" } + ".jpg"
    val stagingRoot = File(context.cacheDir, "photos-upload-picker").apply { mkdirs() }
    val candidate = File(stagingRoot, "${UUID.randomUUID()}-$filename")
    val preview = File(stagingRoot, "${UUID.randomUUID()}-preview.jpg")
    try {
        if (mimeType == "image/jpeg") {
            resolver.openInputStream(uri)?.use { input ->
                FileOutputStream(candidate).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            } ?: error("The selected photo could not be opened")
        } else {
            val bitmap = decodeBitmap(context, uri, maxDimension = 4096)
            try {
                writeJpeg(bitmap, candidate, quality = 95)
            } finally {
                bitmap.recycle()
            }
        }
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
            mimeType = "image/jpeg",
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
    val bitmap = decodeBitmap(context, uri, maxDimension = 414)
    try {
        writeJpeg(bitmap, destination, quality = 85)
    } finally {
        bitmap.recycle()
    }
    require(destination.length() > 0) { "The selected photo preview is empty" }
}

private fun decodeBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        require(longest > 0) { "The selected photo could not be decoded" }
        val targetScale = min(1f, maxDimension.toFloat() / longest.toFloat())
        val targetWidth = (info.size.width * targetScale).roundToInt().coerceAtLeast(1)
        val targetHeight = (info.size.height * targetScale).roundToInt().coerceAtLeast(1)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetSize(targetWidth, targetHeight)
    }
}

private fun writeJpeg(bitmap: Bitmap, destination: File, quality: Int) {
    FileOutputStream(destination).use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
            "The selected photo could not be encoded"
        }
        output.fd.sync()
    }
}

private fun sanitizeFilename(value: String): String {
    val sanitized = value
        .trim()
        .replace(Regex("[<>:\"/\\\\|?*\\p{Cntrl}]"), "_")
        .take(255)
    return sanitized.ifBlank { "photo" }
}

package app.openbubbles.nativeapp.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
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
        val capturedAtMs = exif.originalDateTimeMillis()
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeBitmapWithImageDecoder(context, uri, maxDimension)?.let { return it }
    }
    decodeBitmapWithBitmapFactory(context, uri, maxDimension)?.let { return it }
    throw IllegalArgumentException("The selected photo could not be decoded")
}

private fun decodeBitmapWithBitmapFactory(
    context: Context,
    uri: Uri,
    maxDimension: Int,
): Bitmap? = runCatching {
    val resolver = context.contentResolver
    val orientation = resolver.openInputStream(uri)?.use { stream ->
        runCatching {
            ExifInterface(stream).let { exif ->
                PhotoOrientation(
                    flipHorizontal = exif.isFlipped,
                    rotationDegrees = exif.rotationDegrees,
                )
            }
        }.getOrDefault(PhotoOrientation())
    } ?: PhotoOrientation()

    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = resolver.openInputStream(uri) ?: return@runCatching null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    val options = BitmapFactory.Options().apply {
        inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    val bitmap = resolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return@runCatching null
    bitmap.applyPhotoOrientation(orientation).scaledToMaxDimension(maxDimension)
}.getOrNull()

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeBitmapWithImageDecoder(
    context: Context,
    uri: Uri,
    maxDimension: Int,
): Bitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        require(longest > 0) { "The selected photo could not be decoded" }
        val targetScale = min(1f, maxDimension.toFloat() / longest.toFloat())
        val targetWidth = (info.size.width * targetScale).roundToInt().coerceAtLeast(1)
        val targetHeight = (info.size.height * targetScale).roundToInt().coerceAtLeast(1)
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.setTargetSize(targetWidth, targetHeight)
    }
}.getOrNull()

internal fun bitmapSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    require(maxDimension > 0) { "maxDimension must be positive" }
    var sample = 1
    while (width / sample / 2 >= maxDimension || height / sample / 2 >= maxDimension) {
        sample *= 2
    }
    return sample
}

private data class PhotoOrientation(
    val flipHorizontal: Boolean = false,
    val rotationDegrees: Int = 0,
)

private fun Bitmap.applyPhotoOrientation(orientation: PhotoOrientation): Bitmap {
    var oriented = this
    if (orientation.flipHorizontal) {
        oriented = oriented.transformed(Matrix().apply { setScale(-1f, 1f) })
    }
    if (orientation.rotationDegrees != 0) {
        oriented = oriented.transformed(
            Matrix().apply { setRotate(orientation.rotationDegrees.toFloat()) },
        )
    }
    return oriented
}

private fun Bitmap.transformed(matrix: Matrix): Bitmap {
    val transformed = Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    if (transformed !== this) recycle()
    return transformed
}

private fun Bitmap.scaledToMaxDimension(maxDimension: Int): Bitmap {
    val longest = max(width, height)
    if (longest <= maxDimension) return this
    val scale = maxDimension.toFloat() / longest.toFloat()
    val scaled = Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== this) recycle()
    return scaled
}

private fun ExifInterface.originalDateTimeMillis(): Long? = parseExifOriginalDateTime(
    dateTime = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
    subSeconds = getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL),
    offset = getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
)

private val ExifOffsetPattern = Regex("([+-])(\\d{2}):(\\d{2})")
private val ExifDateTimePatterns = listOf("yyyy:MM:dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss")

internal fun parseExifOriginalDateTime(
    dateTime: String?,
    subSeconds: String?,
    offset: String?,
): Long? {
    if (dateTime == null || dateTime.none { it in '1'..'9' }) return null
    val parsed = ExifDateTimePatterns.firstNotNullOfOrNull { pattern ->
        val position = ParsePosition(0)
        SimpleDateFormat(pattern, Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(dateTime, position)?.takeIf { position.index == dateTime.length }
    } ?: return null

    var millis = parsed.time
    if (offset != null) {
        val match = ExifOffsetPattern.matchEntire(offset) ?: return null
        val hours = match.groupValues[2].toInt()
        val minutes = match.groupValues[3].toInt()
        if (hours > 14 || minutes > 59) return null
        val offsetMillis = (hours * 60L + minutes) * 60_000L
        millis += if (match.groupValues[1] == "-") offsetMillis else -offsetMillis
    }
    val subSecondMillis = subSeconds
        ?.take(3)
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?.padEnd(3, '0')
        ?.toLong()
        ?: 0L
    return millis + subSecondMillis
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

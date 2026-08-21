package app.openbubbles.nativeapp.data.photos

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import app.openbubbles.core.photos.PhotoTimeZone
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
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
    /**
     * The device zone at capture time. The protocol layer prefers the EXIF
     * offset inside the file and uses this only to place a camera clock that
     * recorded no offset of its own.
     */
    val timeZone: PhotoTimeZone? = null,
)

/**
 * Copies a picker, document-tree, or MediaStore grant into a short-lived
 * private file. The shared transfer coordinator then fsyncs a content-addressed
 * copy into the durable Photos upload staging directory before recording the
 * intent.
 *
 * The staged JPEG is what iCloud receives, so it must carry the capture's
 * metadata: a JPEG source is copied byte for byte (unredacted when the media
 * location permission allows), and a source that has to be re-encoded gets its
 * EXIF copied onto the normalized JPEG.
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
    val source = PhotoSource(context, uri)
    val stagingRoot = File(context.cacheDir, "photos-upload-picker").apply { mkdirs() }
    val candidate = File(stagingRoot, "${UUID.randomUUID()}-$filename")
    val preview = File(stagingRoot, "${UUID.randomUUID()}-preview.jpg")
    try {
        if (mimeType == "image/jpeg") {
            source.open()?.use { input ->
                FileOutputStream(candidate).use { output ->
                    input.copyTo(output)
                    output.fd.sync()
                }
            } ?: error("The selected photo could not be opened")
        } else {
            val bitmap = decodeBitmap(source, maxDimension = 4096)
            try {
                writeJpeg(bitmap, candidate, quality = 95)
                copyExifToNormalizedJpeg(source, candidate, bitmap.width, bitmap.height)
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
        val zone = ZoneId.systemDefault()
        val exifDateTime = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
        val capturedAtMs = exif.originalDateTimeMillis(zone) ?: source.mediaStoreDateTakenMs()
        writePreview(source, preview)
        PickedPhotoUpload(
            file = candidate,
            previewFile = preview,
            filename = filename,
            mimeType = "image/jpeg",
            orientation = orientation,
            capturedAtMs = capturedAtMs,
            timeZone = uploadTimeZone(exifDateTime, capturedAtMs, zone, System.currentTimeMillis()),
        )
    } catch (error: Throwable) {
        candidate.delete()
        preview.delete()
        throw error
    }
}

/**
 * One readable photo. When the URI is a MediaStore item and the app holds
 * `ACCESS_MEDIA_LOCATION`, reads ask for the unredacted original so the GPS
 * tags survive; if the platform refuses, the redacted stream is used instead.
 */
private class PhotoSource(private val context: Context, val uri: Uri) {
    private val resolver = context.contentResolver
    private val isMediaStore = uri.authority == MediaStore.AUTHORITY
    private val originalUri: Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isMediaStore && hasMediaLocationPermission(context)) {
            runCatching { MediaStore.setRequireOriginal(uri) }.getOrNull()
        } else {
            null
        }

    @Volatile
    private var originalRefused = false

    fun open(): InputStream? {
        val original = originalUri
        if (original != null && !originalRefused) {
            try {
                resolver.openInputStream(original)?.let { return it }
            } catch (_: SecurityException) {
                originalRefused = true
            } catch (_: UnsupportedOperationException) {
                originalRefused = true
            } catch (_: java.io.IOException) {
                originalRefused = true
            } catch (_: IllegalArgumentException) {
                originalRefused = true
            }
        }
        return resolver.openInputStream(uri)
    }

    fun imageDecoderSource(): ImageDecoder.Source? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val original = originalUri?.takeIf { !originalRefused }
        return ImageDecoder.createSource(resolver, original ?: uri)
    }

    /** MediaStore's own capture stamp, for sources whose EXIF carries no date. */
    fun mediaStoreDateTakenMs(): Long? {
        if (!isMediaStore) return null
        return runCatching {
            resolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATE_TAKEN, MediaStore.MediaColumns.DATE_ADDED),
                null,
                null,
                null,
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val takenIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val taken = if (takenIndex >= 0 && !cursor.isNull(takenIndex)) cursor.getLong(takenIndex) else 0L
                if (taken > 0) return@use taken
                val addedIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
                val added = if (addedIndex >= 0 && !cursor.isNull(addedIndex)) cursor.getLong(addedIndex) else 0L
                if (added > 0) added * 1_000L else null
            }
        }.getOrNull()
    }
}

internal fun hasMediaLocationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun writePreview(source: PhotoSource, destination: File) {
    val bitmap = decodeBitmap(source, maxDimension = 414)
    try {
        writeJpeg(bitmap, destination, quality = 85)
    } finally {
        bitmap.recycle()
    }
    require(destination.length() > 0) { "The selected photo preview is empty" }
}

private fun decodeBitmap(source: PhotoSource, maxDimension: Int): Bitmap {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        decodeBitmapWithImageDecoder(source, maxDimension)?.let { return it }
    }
    decodeBitmapWithBitmapFactory(source, maxDimension)?.let { return it }
    throw IllegalArgumentException("The selected photo could not be decoded")
}

private fun decodeBitmapWithBitmapFactory(
    source: PhotoSource,
    maxDimension: Int,
): Bitmap? = runCatching {
    val orientation = source.open()?.use { stream ->
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
    val boundsStream = source.open() ?: return@runCatching null
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    val options = BitmapFactory.Options().apply {
        inSampleSize = bitmapSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    }
    val bitmap = source.open()?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return@runCatching null
    bitmap.applyPhotoOrientation(orientation).scaledToMaxDimension(maxDimension)
}.getOrNull()

@RequiresApi(Build.VERSION_CODES.P)
private fun decodeBitmapWithImageDecoder(
    source: PhotoSource,
    maxDimension: Int,
): Bitmap? = runCatching {
    val decoderSource = source.imageDecoderSource() ?: return@runCatching null
    ImageDecoder.decodeBitmap(decoderSource) { decoder, info, _ ->
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

/**
 * EXIF tags carried from a HEIC/PNG/WebP source onto its normalized JPEG.
 * Orientation and pixel dimensions are deliberately absent: the normalized
 * pixels are upright and resized, so those are written fresh. Maker notes,
 * thumbnails, and user comments are opaque blobs that do not survive
 * re-encoding meaningfully.
 */
internal val NormalizedJpegExifTags: List<String> = listOf(
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_SOFTWARE,
    ExifInterface.TAG_ARTIST,
    ExifInterface.TAG_COPYRIGHT,
    ExifInterface.TAG_IMAGE_DESCRIPTION,
    ExifInterface.TAG_X_RESOLUTION,
    ExifInterface.TAG_Y_RESOLUTION,
    ExifInterface.TAG_RESOLUTION_UNIT,
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    ExifInterface.TAG_EXIF_VERSION,
    ExifInterface.TAG_FLASHPIX_VERSION,
    ExifInterface.TAG_COLOR_SPACE,
    ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_EXPOSURE_PROGRAM,
    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_SENSITIVITY_TYPE,
    ExifInterface.TAG_ISO_SPEED,
    ExifInterface.TAG_RECOMMENDED_EXPOSURE_INDEX,
    ExifInterface.TAG_SHUTTER_SPEED_VALUE,
    ExifInterface.TAG_APERTURE_VALUE,
    ExifInterface.TAG_BRIGHTNESS_VALUE,
    ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
    ExifInterface.TAG_MAX_APERTURE_VALUE,
    ExifInterface.TAG_SUBJECT_DISTANCE,
    ExifInterface.TAG_METERING_MODE,
    ExifInterface.TAG_LIGHT_SOURCE,
    ExifInterface.TAG_FLASH,
    ExifInterface.TAG_FOCAL_LENGTH,
    ExifInterface.TAG_SUBJECT_AREA,
    ExifInterface.TAG_FOCAL_PLANE_X_RESOLUTION,
    ExifInterface.TAG_FOCAL_PLANE_Y_RESOLUTION,
    ExifInterface.TAG_FOCAL_PLANE_RESOLUTION_UNIT,
    ExifInterface.TAG_SENSING_METHOD,
    ExifInterface.TAG_FILE_SOURCE,
    ExifInterface.TAG_SCENE_TYPE,
    ExifInterface.TAG_CUSTOM_RENDERED,
    ExifInterface.TAG_EXPOSURE_MODE,
    ExifInterface.TAG_WHITE_BALANCE,
    ExifInterface.TAG_DIGITAL_ZOOM_RATIO,
    ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
    ExifInterface.TAG_SCENE_CAPTURE_TYPE,
    ExifInterface.TAG_GAIN_CONTROL,
    ExifInterface.TAG_CONTRAST,
    ExifInterface.TAG_SATURATION,
    ExifInterface.TAG_SHARPNESS,
    ExifInterface.TAG_SUBJECT_DISTANCE_RANGE,
    ExifInterface.TAG_IMAGE_UNIQUE_ID,
    ExifInterface.TAG_CAMERA_OWNER_NAME,
    ExifInterface.TAG_BODY_SERIAL_NUMBER,
    ExifInterface.TAG_LENS_SPECIFICATION,
    ExifInterface.TAG_LENS_MAKE,
    ExifInterface.TAG_LENS_MODEL,
    ExifInterface.TAG_LENS_SERIAL_NUMBER,
    ExifInterface.TAG_GPS_VERSION_ID,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_SATELLITES,
    ExifInterface.TAG_GPS_STATUS,
    ExifInterface.TAG_GPS_MEASURE_MODE,
    ExifInterface.TAG_GPS_DOP,
    ExifInterface.TAG_GPS_SPEED_REF,
    ExifInterface.TAG_GPS_SPEED,
    ExifInterface.TAG_GPS_TRACK_REF,
    ExifInterface.TAG_GPS_TRACK,
    ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    ExifInterface.TAG_GPS_IMG_DIRECTION,
    ExifInterface.TAG_GPS_MAP_DATUM,
    ExifInterface.TAG_GPS_DEST_BEARING_REF,
    ExifInterface.TAG_GPS_DEST_BEARING,
    ExifInterface.TAG_GPS_PROCESSING_METHOD,
    ExifInterface.TAG_GPS_DATESTAMP,
    ExifInterface.TAG_GPS_DIFFERENTIAL,
    ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
)

/** Tags a normalized JPEG must never inherit from its source. */
internal val NormalizedJpegExifExclusions: Set<String> = setOf(
    ExifInterface.TAG_ORIENTATION,
    ExifInterface.TAG_PIXEL_X_DIMENSION,
    ExifInterface.TAG_PIXEL_Y_DIMENSION,
    ExifInterface.TAG_IMAGE_WIDTH,
    ExifInterface.TAG_IMAGE_LENGTH,
    ExifInterface.TAG_MAKER_NOTE,
    ExifInterface.TAG_USER_COMMENT,
    ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH,
    ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH,
    ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
    ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
)

private fun copyExifToNormalizedJpeg(source: PhotoSource, jpeg: File, width: Int, height: Int) {
    val sourceExif = runCatching { source.open()?.use { ExifInterface(it) } }.getOrNull() ?: return
    runCatching {
        val target = ExifInterface(jpeg)
        var copied = 0
        for (tag in NormalizedJpegExifTags) {
            if (tag in NormalizedJpegExifExclusions) continue
            val value = sourceExif.getAttribute(tag) ?: continue
            target.setAttribute(tag, value)
            copied += 1
        }
        if (copied == 0) return
        target.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
        target.setAttribute(ExifInterface.TAG_PIXEL_X_DIMENSION, width.toString())
        target.setAttribute(ExifInterface.TAG_PIXEL_Y_DIMENSION, height.toString())
        target.saveAttributes()
    }
    // A source whose EXIF cannot be rewritten still uploads as a plain JPEG.
}

private fun ExifInterface.originalDateTimeMillis(zone: ZoneId): Long? = parseExifOriginalDateTime(
    dateTime = getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL),
    subSeconds = getAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL),
    offset = getAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL),
    zone = zone,
)

private val ExifOffsetPattern = Regex("([+-])(\\d{2}):(\\d{2})")
private val ExifDateTimePatterns = listOf("yyyy:MM:dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss")
private val ExifLocalDateTimeFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss", Locale.US)

/**
 * The instant a camera clock describes. An explicit EXIF offset is
 * authoritative; without one the wall-clock time is read in [zone], which is
 * the device's own zone for a photo that was taken on this device.
 */
internal fun parseExifOriginalDateTime(
    dateTime: String?,
    subSeconds: String?,
    offset: String?,
    zone: ZoneId = ZoneId.systemDefault(),
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
    } else {
        // `parsed` holds the wall-clock digits as if they were UTC; shift them
        // into the zone the clock was actually running in.
        val wallClock = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.of("UTC"))
        millis = wallClock.atZone(zone).toInstant().toEpochMilli()
    }
    val subSecondMillis = subSeconds
        ?.take(3)
        ?.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
        ?.padEnd(3, '0')
        ?.toLong()
        ?: 0L
    return millis + subSecondMillis
}

/** Wall-clock `yyyy:MM:dd HH:mm:ss` as a local date-time, or null. */
internal fun parseExifLocalDateTime(value: String?): LocalDateTime? {
    if (value == null || value.none { it in '1'..'9' }) return null
    return try {
        LocalDateTime.parse(value.trim(), ExifLocalDateTimeFormat)
    } catch (_: DateTimeParseException) {
        null
    }
}

/**
 * The device zone as it applied to this capture: its offset at the camera's
 * wall-clock time when that is known (so DST resolves correctly), otherwise at
 * the best-known capture instant, otherwise now.
 */
internal fun uploadTimeZone(
    exifDateTimeOriginal: String?,
    capturedAtMs: Long?,
    zone: ZoneId,
    nowMs: Long,
): PhotoTimeZone {
    val rules = zone.rules
    val local = parseExifLocalDateTime(exifDateTimeOriginal)
    val offset = if (local != null) {
        rules.getOffset(local)
    } else {
        rules.getOffset(Instant.ofEpochMilli(capturedAtMs ?: nowMs))
    }
    return PhotoTimeZone(name = zone.id, offsetSeconds = offset.totalSeconds)
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

package app.openbubbles.nativeapp.ui.attachmentviewer

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Locale
import app.openbubbles.nativeapp.data.AttachmentMeta
import app.openbubbles.nativeapp.data.ImageExportPlan
import app.openbubbles.nativeapp.data.LivePhotoPair
import app.openbubbles.nativeapp.data.exportedImageDisplayName
import app.openbubbles.nativeapp.data.exportedImageMime
import app.openbubbles.nativeapp.data.imageExportPlan
import app.openbubbles.nativeapp.data.motionPhotoDisplayName
import app.openbubbles.nativeapp.data.writeJpegMotionPhotoCarrier

/**
 * Explicit save-to-device exports for downloaded attachments, converting
 * Apple container formats to their Google/Android equivalents where that
 * makes the result render better in Android galleries:
 *
 * - Live Photo pair -> single Google Motion Photo file (falling back to the
 *   historical two-file export when assembly is not possible);
 * - HDR HEIC with a platform-readable gain map -> Ultra HDR JPEG;
 * - everything else -> byte-identical copy.
 */

/** Decode ceiling for re-encodes; bounds peak bitmap memory (~64 MB RGBA). */
private const val EXPORT_DECODE_MAX_DIMENSION = 4096
private const val MOTION_PHOTO_STILL_MAX_BYTES = 64L * 1024 * 1024

internal fun requiresLegacyMediaWritePermission(sdkInt: Int, permissionGranted: Boolean): Boolean =
    sdkInt <= Build.VERSION_CODES.P && !permissionGranted

internal enum class LivePhotoSaveOutcome { MotionPhoto, SeparateFiles, StillOnly, MotionOnly, Failed }

/** Saves a received image, converting HDR HEIC to Ultra HDR JPEG when readable. */
internal fun saveImageAttachmentToDevice(context: Context, meta: AttachmentMeta, file: File): Boolean {
    val name = meta.name ?: file.name
    val decoded = decodeBoundedBitmap(file)
    val plan = imageExportPlan(
        mime = meta.mime,
        uti = meta.uti,
        name = name,
        hasReadableGainmap = decoded?.hasReadableGainmap() == true,
        sdkInt = Build.VERSION.SDK_INT,
    )
    val saved = when (plan) {
        ImageExportPlan.ConvertToUltraHdrJpeg -> {
            val bitmap = decoded ?: return false
            val sourceExif = runCatching { ExifInterface(file) }.getOrNull()
            saveToMediaStore(
                context = context,
                displayName = exportedImageDisplayName(name, plan),
                mime = exportedImageMime(meta.mime, plan),
                video = false,
                dateTakenMillis = sourceExif?.let(::dateTakenMillis),
                afterWrite = { uri -> sourceExif?.let { copyExifMetadata(context, it, uri) } },
            ) { output ->
                // API 34+ JPEG compression carries the gain map through,
                // producing an Ultra HDR JPEG.
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) { "JPEG encode failed" }
            }
        }
        ImageExportPlan.CopyBytes -> saveToMediaStore(
            context = context,
            displayName = exportedImageDisplayName(name, plan),
            mime = exportedImageMime(meta.mime, plan),
            video = false,
        ) { output -> file.inputStream().use { it.copyTo(output) } }
    }
    return saved
}

/** Public-API EXIF timestamp reader; the convenience getter is library-restricted. */
private fun dateTakenMillis(exif: ExifInterface): Long? {
    val value = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) ?: return null
    return runCatching {
        SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.US).apply {
            isLenient = false
        }.parse(value)?.time
    }.getOrNull()
}

/** Saves a received video byte-identical into Movies/OpenBubbles. */
internal fun saveVideoAttachmentToDevice(context: Context, meta: AttachmentMeta, file: File): Boolean =
    saveToMediaStore(
        context = context,
        displayName = meta.name ?: file.name,
        mime = meta.playbackMime,
        video = true,
    ) { output -> file.inputStream().use { it.copyTo(output) } }

/**
 * Saves a Live Photo pair as one Google Motion Photo. When the still cannot
 * become a plain JPEG carrier (unreadable image, or it carries gain-map XMP
 * this writer must not clobber), falls back to the two-file export so no
 * content is ever lost.
 */
internal fun saveLivePhotoToDevice(context: Context, pair: LivePhotoPair): LivePhotoSaveOutcome {
    val motionFile = pair.motionFile
    if (motionFile != null) {
        val carrier = runCatching {
            val stillJpeg = jpegCarrierBytes(pair.stillFile) ?: return@runCatching null
            stillJpeg
        }.getOrNull()
        if (carrier != null) {
            val videoMime = pair.motion?.playbackMime ?: "video/quicktime"
            val saved = saveToMediaStore(
                context = context,
                displayName = motionPhotoDisplayName(pair.still.name ?: pair.stillFile.name),
                mime = "image/jpeg",
                video = false,
            ) { output ->
                check(writeJpegMotionPhotoCarrier(carrier, motionFile.length(), videoMime, output)) {
                    "Motion Photo carrier is unsupported"
                }
                motionFile.inputStream().use { it.copyTo(output) }
            }
            if (saved) return LivePhotoSaveOutcome.MotionPhoto
        }
    }
    return saveLivePhotoAsSeparateFiles(context, pair)
}

/** Historical two-file export: still into Pictures, motion into Movies. */
internal fun saveLivePhotoAsSeparateFiles(context: Context, pair: LivePhotoPair): LivePhotoSaveOutcome {
    val stillSaved = saveToMediaStore(
            context = context,
            displayName = pair.still.name ?: pair.stillFile.name,
            mime = pair.still.playbackMime,
            video = false,
        ) { output -> pair.stillFile.inputStream().use { it.copyTo(output) } }
    val motionFile = pair.motionFile
    val motionSaved = motionFile != null && saveToMediaStore(
            context = context,
            displayName = pair.motion?.name ?: motionFile.name,
            mime = pair.motion?.playbackMime ?: "video/quicktime",
            video = true,
        ) { output -> motionFile.inputStream().use { it.copyTo(output) } }
    return when {
        stillSaved && motionSaved -> LivePhotoSaveOutcome.SeparateFiles
        stillSaved -> LivePhotoSaveOutcome.StillOnly
        motionSaved -> LivePhotoSaveOutcome.MotionOnly
        else -> LivePhotoSaveOutcome.Failed
    }
}

/**
 * The still as plain JPEG bytes suitable for Motion Photo XMP insertion:
 * an existing JPEG passes through byte-identical; HEIC/HEIF (the Apple Live
 * Photo default) is decoded bounded and re-encoded. A decoded gain map is
 * dropped here on purpose — compressing it would emit gain-map XMP that the
 * Motion Photo writer refuses to clobber, and the caller's two-file fallback
 * preserves full fidelity for that case instead.
 */
internal fun jpegCarrierBytes(stillFile: File): ByteArray? {
    if (stillFile.length() !in 1..MOTION_PHOTO_STILL_MAX_BYTES) return null
    val bytes = runCatching { stillFile.readBytes() }.getOrNull() ?: return null
    if (bytes.size >= 2 && (bytes[0].toInt() and 0xFF) == 0xFF && (bytes[1].toInt() and 0xFF) == 0xD8) {
        return bytes
    }
    val bitmap = decodeBoundedBitmap(stillFile) ?: return null
    if (bitmap.hasReadableGainmap()) return null
    val output = java.io.ByteArrayOutputStream(bytes.size)
    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)) return null
    return output.toByteArray()
}

/** Decodes with a hard dimension ceiling; ImageDecoder path covers HEIC. */
private fun decodeBoundedBitmap(file: File): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val sample = boundedDecodeSample(bounds.outWidth, bounds.outHeight)
    BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: decodeBoundedWithImageDecoder(file)
}.getOrNull()

private fun decodeBoundedWithImageDecoder(file: File): Bitmap? {
    if (Build.VERSION.SDK_INT < 28) return null
    return runCatching {
        android.graphics.ImageDecoder.decodeBitmap(
            android.graphics.ImageDecoder.createSource(file),
        ) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            val width = info.size.width
            val height = info.size.height
            if (width > 0 && height > 0) {
                val sample = boundedDecodeSample(width, height)
                decoder.setTargetSize(
                    (width / sample).coerceAtLeast(1),
                    (height / sample).coerceAtLeast(1),
                )
            }
        }
    }.getOrNull()
}

internal fun boundedDecodeSample(
    width: Int,
    height: Int,
    maxDimension: Int = EXPORT_DECODE_MAX_DIMENSION,
): Int {
    if (width <= 0 || height <= 0 || maxDimension <= 0) return 1
    var sample = 1
    while (width / sample > maxDimension || height / sample > maxDimension) sample *= 2
    return sample
}

private fun Bitmap.hasReadableGainmap(): Boolean =
    Build.VERSION.SDK_INT >= 34 && runCatching { hasGainmap() }.getOrDefault(false)

/**
 * Scoped-storage export with pending-row cleanup on failure. Images land in
 * Pictures/OpenBubbles and videos in Movies/OpenBubbles unless the caller names
 * its own album with [relativePath] (the iCloud Photos export does).
 */
internal fun saveToMediaStore(
    context: Context,
    displayName: String,
    mime: String,
    video: Boolean,
    dateTakenMillis: Long? = null,
    relativePath: String? = null,
    afterWrite: (Uri) -> Unit = {},
    write: (OutputStream) -> Unit,
): Boolean = runCatching {
    val resolver = context.contentResolver
    val collection = if (video) {
        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        dateTakenMillis?.let { put(MediaStore.Images.ImageColumns.DATE_TAKEN, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                relativePath ?: if (video) "Movies/OpenBubbles" else "Pictures/OpenBubbles",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(collection, values) ?: error("MediaStore insert failed")
    try {
        resolver.openOutputStream(uri)?.use(write) ?: error("MediaStore output unavailable")
        afterWrite(uri)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    }
}.isSuccess

private val COPIED_EXIF_TAGS = listOf(
    ExifInterface.TAG_DATETIME,
    ExifInterface.TAG_DATETIME_ORIGINAL,
    ExifInterface.TAG_DATETIME_DIGITIZED,
    ExifInterface.TAG_SUBSEC_TIME,
    ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
    ExifInterface.TAG_SUBSEC_TIME_DIGITIZED,
    ExifInterface.TAG_OFFSET_TIME,
    ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
    ExifInterface.TAG_OFFSET_TIME_DIGITIZED,
    ExifInterface.TAG_ORIENTATION,
    ExifInterface.TAG_MAKE,
    ExifInterface.TAG_MODEL,
    ExifInterface.TAG_LENS_MODEL,
    ExifInterface.TAG_SOFTWARE,
    ExifInterface.TAG_ARTIST,
    ExifInterface.TAG_COPYRIGHT,
    ExifInterface.TAG_EXPOSURE_TIME,
    ExifInterface.TAG_F_NUMBER,
    ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
    ExifInterface.TAG_FOCAL_LENGTH,
    ExifInterface.TAG_FLASH,
    ExifInterface.TAG_WHITE_BALANCE,
    ExifInterface.TAG_GPS_LATITUDE,
    ExifInterface.TAG_GPS_LATITUDE_REF,
    ExifInterface.TAG_GPS_LONGITUDE,
    ExifInterface.TAG_GPS_LONGITUDE_REF,
    ExifInterface.TAG_GPS_ALTITUDE,
    ExifInterface.TAG_GPS_ALTITUDE_REF,
    ExifInterface.TAG_GPS_TIMESTAMP,
    ExifInterface.TAG_GPS_DATESTAMP,
)

private fun copyExifMetadata(context: Context, source: ExifInterface, destination: Uri) {
    context.contentResolver.openFileDescriptor(destination, "rw")?.use { descriptor ->
        val target = ExifInterface(descriptor.fileDescriptor)
        COPIED_EXIF_TAGS.forEach { tag -> source.getAttribute(tag)?.let { target.setAttribute(tag, it) } }
        target.saveAttributes()
    } ?: error("MediaStore EXIF destination unavailable")
}

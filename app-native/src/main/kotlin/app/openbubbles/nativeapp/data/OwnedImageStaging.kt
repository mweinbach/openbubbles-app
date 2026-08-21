package app.openbubbles.nativeapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.graphics.scale
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import kotlin.math.roundToInt

private const val MAX_SELECTED_IMAGE_BYTES = 25L * 1024L * 1024L
private const val PROFILE_IMAGE_EDGE = 1_024
private const val GROUP_ICON_EDGE = 570

/** Copies at most [maxBytes], failing before an untrusted provider can fill app storage. */
@Throws(IOException::class)
internal fun copyWithByteLimit(input: InputStream, output: File, maxBytes: Long): Long {
    require(maxBytes > 0L) { "maxBytes must be positive" }
    var written = 0L
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    FileOutputStream(output).use { sink ->
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (written > maxBytes - read) {
                throw IOException("File is larger than ${maxBytes / (1024 * 1024)} MB")
            }
            sink.write(buffer, 0, read)
            written += read
        }
        sink.fd.sync()
    }
    return written
}

/** True only for a direct child of [directory], after resolving symlinks and traversal. */
internal fun isOwnedFile(file: File, directory: File): Boolean = runCatching {
    val ownedDirectory = directory.canonicalFile
    val candidate = file.canonicalFile
    candidate.parentFile == ownedDirectory
}.getOrDefault(false)

internal fun deleteOwnedFile(file: File?, directory: File): Boolean =
    file != null && isOwnedFile(file, directory) && (!file.exists() || file.delete())

/** Atomically replaces [destination] with an already prepared sibling file. */
@Throws(IOException::class)
internal fun promoteOwnedSibling(staged: File, destination: File): File {
    require(staged.isFile) { "staged image is unavailable" }
    require(staged.canonicalFile.parentFile == destination.canonicalFile.parentFile) {
        "staged image is not an owned sibling"
    }
    try {
        Files.move(
            staged.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(staged.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
    }
    return destination
}

/**
 * Normalizes a provider image into a bounded, app-owned profile candidate.
 * The current avatar is untouched until [promoteProfileImage] is called.
 */
internal fun stageProfileImage(context: Context, uri: Uri): File = stageImage(
    context = context,
    uri = uri,
    directory = profileImagesDirectory(context),
    prefix = "pending-profile",
    edge = PROFILE_IMAGE_EDGE,
    squareCrop = true,
    upscale = false,
    format = Bitmap.CompressFormat.JPEG,
    quality = 90,
    extension = "jpg",
)

/** Normalizes a group icon to the 570px square PNG required by iMessage. */
internal fun stageGroupIcon(context: Context, uri: Uri): File = stageImage(
    context = context,
    uri = uri,
    directory = groupIconsDirectory(context),
    prefix = "outgoing",
    edge = GROUP_ICON_EDGE,
    squareCrop = true,
    upscale = true,
    format = Bitmap.CompressFormat.PNG,
    quality = 100,
    extension = "png",
)

internal fun promoteProfileImage(context: Context, staged: File): File =
    promoteOwnedSibling(staged, File(profileImagesDirectory(context), "avatar.img"))

internal fun profileImagesDirectory(context: Context): File =
    File(context.filesDir, "profile").apply { mkdirs() }

internal fun groupIconsDirectory(context: Context): File =
    File(context.filesDir, "group_icons").apply { mkdirs() }

internal fun groupIconDirectories(context: Context): List<File> =
    groupIconDirectories(context.filesDir, context.dataDir)

internal fun groupIconDirectories(filesDir: File, dataDir: File): List<File> = listOf(
    File(filesDir, "group_icons"),
    File(File(dataDir, "app_flutter"), "group_icons"),
).distinctBy { it.canonicalPath }

internal fun deleteOwnedGroupIcon(file: File?, context: Context): Boolean =
    deleteOwnedGroupIcon(file, groupIconDirectories(context))

internal fun deleteOwnedGroupIcon(file: File?, directories: List<File>): Boolean =
    directories.any { directory -> deleteOwnedFile(file, directory) }

internal data class ExifImageTransform(
    val flipHorizontal: Boolean,
    val rotationDegrees: Int,
)

/** EXIF orientations 1..8, applied as horizontal mirror then clockwise rotation. */
internal fun exifImageTransform(orientation: Int): ExifImageTransform = when (orientation) {
    ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> ExifImageTransform(true, 0)
    ExifInterface.ORIENTATION_ROTATE_180 -> ExifImageTransform(false, 180)
    ExifInterface.ORIENTATION_FLIP_VERTICAL -> ExifImageTransform(true, 180)
    ExifInterface.ORIENTATION_TRANSPOSE -> ExifImageTransform(true, 270)
    ExifInterface.ORIENTATION_ROTATE_90 -> ExifImageTransform(false, 90)
    ExifInterface.ORIENTATION_TRANSVERSE -> ExifImageTransform(true, 90)
    ExifInterface.ORIENTATION_ROTATE_270 -> ExifImageTransform(false, 270)
    else -> ExifImageTransform(false, 0)
}

private fun stageImage(
    context: Context,
    uri: Uri,
    directory: File,
    prefix: String,
    edge: Int,
    squareCrop: Boolean,
    upscale: Boolean,
    format: Bitmap.CompressFormat,
    quality: Int,
    extension: String,
): File {
    directory.mkdirs()
    val token = UUID.randomUUID().toString()
    val sourceFile = File(directory, ".$prefix-$token.source.part")
    val encodedPart = File(directory, ".$prefix-$token.$extension.part")
    val staged = File(directory, "$prefix-$token.$extension")
    var decoded: Bitmap? = null
    var oriented: Bitmap? = null
    var cropped: Bitmap? = null
    var output: Bitmap? = null
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            copyWithByteLimit(input, sourceFile, MAX_SELECTED_IMAGE_BYTES)
        } ?: throw IOException("Could not read selected image")

        val bounds = BitmapFactory.Options().also { it.inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sourceFile.absolutePath, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Could not decode selected image" }
        val sample = decodeSample(bounds.outWidth, bounds.outHeight, edge)
        val decodedBitmap = BitmapFactory.decodeFile(
            sourceFile.absolutePath,
            BitmapFactory.Options().apply { inSampleSize = sample },
        ) ?: error("Could not decode selected image")
        decoded = decodedBitmap
        val orientation = runCatching {
            ExifInterface(sourceFile).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val orientedBitmap = decodedBitmap.applyExifTransform(exifImageTransform(orientation))
        oriented = orientedBitmap

        cropped = if (squareCrop) {
            val side = minOf(orientedBitmap.width, orientedBitmap.height)
            Bitmap.createBitmap(
                orientedBitmap,
                (orientedBitmap.width - side) / 2,
                (orientedBitmap.height - side) / 2,
                side,
                side,
            )
        } else {
            orientedBitmap
        }
        val longest = maxOf(cropped.width, cropped.height)
        val requestedScale = edge.toFloat() / longest
        val scale = if (upscale) requestedScale else requestedScale.coerceAtMost(1f)
        output = if (scale != 1f) {
            cropped.scale(
                (cropped.width * scale).roundToInt().coerceAtLeast(1),
                (cropped.height * scale).roundToInt().coerceAtLeast(1),
            )
        } else {
            cropped
        }
        FileOutputStream(encodedPart).use { sink ->
            check(output.compress(format, quality, sink)) { "Could not encode selected image" }
            sink.fd.sync()
        }
        promoteOwnedSibling(encodedPart, staged)
        return staged
    } finally {
        sourceFile.delete()
        encodedPart.delete()
        if (output != null && output !== cropped) output.recycle()
        if (cropped != null && cropped !== oriented) cropped.recycle()
        if (oriented != null && oriented !== decoded) oriented.recycle()
        decoded?.recycle()
    }
}

private fun Bitmap.applyExifTransform(transform: ExifImageTransform): Bitmap {
    var result = this
    if (transform.flipHorizontal) {
        result = Bitmap.createBitmap(
            result,
            0,
            0,
            result.width,
            result.height,
            Matrix().apply { setScale(-1f, 1f) },
            true,
        )
    }
    if (transform.rotationDegrees != 0) {
        val beforeRotation = result
        result = Bitmap.createBitmap(
            beforeRotation,
            0,
            0,
            beforeRotation.width,
            beforeRotation.height,
            Matrix().apply { setRotate(transform.rotationDegrees.toFloat()) },
            true,
        )
        if (beforeRotation !== this && beforeRotation !== result) beforeRotation.recycle()
    }
    return result
}

private fun decodeSample(width: Int, height: Int, targetEdge: Int): Int {
    var sample = 1
    while (width / sample > targetEdge * 2 || height / sample > targetEdge * 2) sample *= 2
    return sample
}

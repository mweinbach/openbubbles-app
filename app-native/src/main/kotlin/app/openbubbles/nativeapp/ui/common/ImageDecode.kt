package app.openbubbles.nativeapp.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.util.LruCache
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.exifinterface.media.ExifInterface
import app.openbubbles.nativeapp.data.MemoryCaches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale

/** A decoded bitmap plus its display-oriented aspect ratio (width / height). */
data class DecodedImage(
    val image: ImageBitmap,
    val aspectRatio: Float,
)

/** Size-bounded decoded bitmap cache shared by visible lazy-list rows. */
object ImageDecodeCache {
    private const val MAX_SIZE_KB = 24 * 1024

    private val cache = object : LruCache<String, DecodedImage>(MAX_SIZE_KB) {
        override fun sizeOf(key: String, value: DecodedImage): Int =
            ((value.image.width.toLong() * value.image.height.toLong() * 4L) / 1024L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
    }

    init {
        MemoryCaches.register(::clear)
    }

    fun get(key: String): DecodedImage? = cache.get(key)

    fun put(key: String, image: DecodedImage) {
        cache.put(key, image)
    }

    fun clear() {
        cache.evictAll()
    }
}

/** Fallback aspect ratio when the file's dimensions cannot be read. */
const val FallbackAspectRatio = 4f / 3f

private data class ImageOrientation(
    val flipHorizontal: Boolean = false,
    val rotationDegrees: Int = 0,
)

private val NormalImageOrientation = ImageOrientation()

private fun ExifInterface.imageOrientation() = ImageOrientation(
    flipHorizontal = isFlipped,
    rotationDegrees = rotationDegrees,
)

private fun File.readImageOrientation(): ImageOrientation =
    runCatching { ExifInterface(this).imageOrientation() }
        .getOrDefault(NormalImageOrientation)

private fun readImageOrientation(openStream: () -> InputStream?): ImageOrientation =
    runCatching {
        openStream()?.use { ExifInterface(it).imageOrientation() }
    }.getOrNull() ?: NormalImageOrientation

/** Applies EXIF's required horizontal flip first, then clockwise rotation. */
private fun Bitmap.applyImageOrientation(orientation: ImageOrientation): Bitmap {
    var oriented = this
    if (orientation.flipHorizontal) {
        oriented = oriented.transformed(
            Matrix().apply { setScale(-1f, 1f) },
        )
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

/**
 * Decodes [file] off the main thread, downsampled so neither side exceeds
 * [maxDimensionPx] (bubbles use 512, the viewer a larger budget), then
 * normalizes EXIF orientation before caching. Returns null while decoding,
 * when the file is missing, or when decoding fails — callers render a
 * placeholder in that case.
 */
@Composable
fun rememberDecodedImage(
    file: File?,
    maxDimensionPx: Int = 512,
): DecodedImage? {
    val cacheKey = remember(file?.absolutePath, file?.lastModified(), file?.length(), maxDimensionPx) {
        file?.let { "file:${it.absolutePath}:${it.lastModified()}:${it.length()}:$maxDimensionPx" }
    }
    return produceState<DecodedImage?>(initialValue = null, cacheKey) {
        val key = cacheKey ?: return@produceState
        ImageDecodeCache.get(key)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            decodeLocalImage(file, maxDimensionPx)
        }
        if (decoded != null) ImageDecodeCache.put(key, decoded)
        value = decoded
    }.value
}

/**
 * Decodes a local still image. [BitmapFactory] handles JPEG/PNG/WebP;
 * [ImageDecoder] is the HEIC/HEIF/AVIF fallback on API 28+.
 */
internal fun decodeLocalImage(file: File?, maxDimensionPx: Int): DecodedImage? {
    if (file == null || !file.isFile) return null
    return decodeLocalImageWithBitmapFactory(file, maxDimensionPx)
        ?: decodeLocalImageWithImageDecoder(file, maxDimensionPx)
}

private fun decodeLocalImageWithBitmapFactory(file: File, maxDimensionPx: Int): DecodedImage? =
    runCatching {
        val orientation = file.readImageOrientation()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDimensionPx ||
            bounds.outHeight / (sample * 2) >= maxDimensionPx
        ) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeFile(file.absolutePath, options)
            ?: return@runCatching null
        val oriented = bitmap.applyImageOrientation(orientation)
        DecodedImage(
            image = oriented.asImageBitmap(),
            aspectRatio = oriented.width.toFloat() / oriented.height.toFloat(),
        )
    }.getOrNull()

private fun decodeLocalImageWithImageDecoder(file: File, maxDimensionPx: Int): DecodedImage? {
    if (Build.VERSION.SDK_INT < 28) return null
    return runCatching {
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            if (width > 0 && height > 0) {
                var sample = 1
                while (width / (sample * 2) >= maxDimensionPx ||
                    height / (sample * 2) >= maxDimensionPx
                ) {
                    sample *= 2
                }
                decoder.setTargetSize(
                    (width / sample).coerceAtLeast(1),
                    (height / sample).coerceAtLeast(1),
                )
            }
        }
        DecodedImage(
            image = bitmap.asImageBitmap(),
            aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat(),
        )
    }.getOrNull()
}

internal fun Bitmap.scaledToMaxDimension(maxDimensionPx: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest <= maxDimensionPx) return this
    val scale = maxDimensionPx.toFloat() / longest.toFloat()
    val scaled = Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true,
    )
    if (scaled !== this) recycle()
    return scaled
}

/**
 * Decodes an image referenced by a string URI (contact photo URIs are
 * `content://` lookups; plain file paths also work) off the main thread,
 * downsampled so neither side exceeds [maxDimensionPx], with EXIF orientation
 * normalized before caching. Returns null while decoding, for null/blank
 * input, or on failure — callers fall back.
 */
internal fun decodeUriImage(
    context: android.content.Context,
    uri: String?,
    maxDimensionPx: Int,
): DecodedImage? {
    if (uri.isNullOrBlank()) return null
    return decodeUriImageWithBitmapFactory(context, uri, maxDimensionPx)
        ?: decodeUriImageWithImageDecoder(context, uri, maxDimensionPx)
}

private fun decodeUriImageWithBitmapFactory(
    context: android.content.Context,
    uri: String,
    maxDimensionPx: Int,
): DecodedImage? = runCatching {
    fun openStream() = when {
        uri.startsWith("content://") || uri.startsWith("file://") ->
            context.contentResolver.openInputStream(uri.toUri())
        else -> File(uri).takeIf { it.isFile }?.inputStream()
    }

    val orientation = readImageOrientation(::openStream)
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    openStream()?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxDimensionPx ||
        bounds.outHeight / (sample * 2) >= maxDimensionPx
    ) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val bitmap = openStream()?.use {
        BitmapFactory.decodeStream(it, null, options)
    } ?: return@runCatching null
    val oriented = bitmap.applyImageOrientation(orientation)
    DecodedImage(
        image = oriented.asImageBitmap(),
        aspectRatio = oriented.width.toFloat() / oriented.height.toFloat(),
    )
}.getOrNull()

private fun decodeUriImageWithImageDecoder(
    context: android.content.Context,
    uri: String,
    maxDimensionPx: Int,
): DecodedImage? {
    if (Build.VERSION.SDK_INT < 28) return null
    return runCatching {
        val source = when {
            uri.startsWith("content://") || uri.startsWith("file://") ->
                ImageDecoder.createSource(context.contentResolver, uri.toUri())
            else -> {
                val file = File(uri)
                if (!file.isFile) return@runCatching null
                ImageDecoder.createSource(file)
            }
        }
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val width = info.size.width
            val height = info.size.height
            if (width > 0 && height > 0) {
                var sample = 1
                while (width / (sample * 2) >= maxDimensionPx ||
                    height / (sample * 2) >= maxDimensionPx
                ) {
                    sample *= 2
                }
                decoder.setTargetSize((width / sample).coerceAtLeast(1), (height / sample).coerceAtLeast(1))
            }
        }
        DecodedImage(
            image = bitmap.asImageBitmap(),
            aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat(),
        )
    }.getOrNull()
}

internal fun uriImageCacheKey(uri: String?, maxDimensionPx: Int): String? {
    val value = uri?.takeIf { it.isNotBlank() } ?: return null
    val fileMeta = when {
        value.startsWith("content://") -> ""
        value.startsWith("file://") -> {
            val path = value.toUri().path ?: return "uri:$value:$maxDimensionPx"
            File(path).takeIf { it.isFile }?.let { ":${it.lastModified()}:${it.length()}" }.orEmpty()
        }
        else -> File(value).takeIf { it.isFile }?.let { ":${it.lastModified()}:${it.length()}" }.orEmpty()
    }
    return "uri:$value$fileMeta:$maxDimensionPx"
}

@Composable
fun rememberDecodedUriImage(
    uri: String?,
    maxDimensionPx: Int = 256,
): DecodedImage? {
    val context = LocalContext.current
    val cacheKey = remember(uri, maxDimensionPx, File(uri.orEmpty()).let { it.lastModified() to it.length() }) {
        uriImageCacheKey(uri, maxDimensionPx)
    }
    return produceState<DecodedImage?>(initialValue = null, cacheKey) {
        val key = cacheKey ?: return@produceState
        ImageDecodeCache.get(key)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            decodeUriImage(context, uri, maxDimensionPx)
        }
        if (decoded != null) ImageDecodeCache.put(key, decoded)
        value = decoded
    }.value
}

/** Decodes and EXIF-normalizes embedded image bytes for rich-link previews. */
@Composable
fun rememberDecodedBytes(
    bytes: ByteArray?,
    maxDimensionPx: Int = 512,
): DecodedImage? {
    val cacheKey = remember(bytes?.contentHashCode(), bytes?.size, maxDimensionPx) {
        bytes?.takeIf { it.isNotEmpty() }?.let {
            "bytes:${it.contentHashCode()}:${it.size}:$maxDimensionPx"
        }
    }
    return produceState<DecodedImage?>(initialValue = null, cacheKey) {
        val key = cacheKey ?: return@produceState
        ImageDecodeCache.get(key)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.Default) {
            runCatching {
                val source = bytes ?: return@runCatching null
                val orientation = readImageOrientation { source.inputStream() }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxDimensionPx ||
                    bounds.outHeight / (sample * 2) >= maxDimensionPx
                ) {
                    sample *= 2
                }
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, options)
                    ?: return@runCatching null
                val oriented = bitmap.applyImageOrientation(orientation)
                DecodedImage(
                    image = oriented.asImageBitmap(),
                    aspectRatio = oriented.width.toFloat() / oriented.height.toFloat(),
                )
            }.getOrNull()
        }
        if (decoded != null) ImageDecodeCache.put(key, decoded)
        value = decoded
    }.value
}

/** Human-readable byte size: "412 KB", "18.9 MB". */
fun formatBytes(sizeBytes: Long?): String {
    if (sizeBytes == null || sizeBytes <= 0) return ""
    val kb = sizeBytes / 1024.0
    val mb = kb / 1024.0
    return when {
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        kb >= 1 -> String.format(Locale.US, "%d KB", kb.toLong())
        else -> "$sizeBytes B"
    }
}

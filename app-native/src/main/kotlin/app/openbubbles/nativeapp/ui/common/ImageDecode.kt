package app.openbubbles.nativeapp.ui.common

import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import app.openbubbles.nativeapp.data.MemoryCaches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** A decoded bitmap plus the source aspect ratio (width / height). */
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

/**
 * Decodes [file] off the main thread, downsampled so neither side exceeds
 * [maxDimensionPx] (bubbles use 512, the viewer a larger budget). Returns
 * null while decoding, when the file is missing, or when decoding fails —
 * callers render a placeholder in that case.
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
            runCatching {
                if (!file!!.isFile) return@runCatching null
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
                DecodedImage(
                    image = bitmap.asImageBitmap(),
                    aspectRatio = bounds.outWidth.toFloat() / bounds.outHeight.toFloat(),
                )
            }.getOrNull()
        }
        if (decoded != null) ImageDecodeCache.put(key, decoded)
        value = decoded
    }.value
}

/**
 * Decodes an image referenced by a string URI (contact photo URIs are
 * `content://` lookups; plain file paths also work) off the main thread,
 * downsampled so neither side exceeds [maxDimensionPx]. Returns null while
 * decoding, for null/blank input, or on failure — callers fall back.
 */
@Composable
fun rememberDecodedUriImage(
    uri: String?,
    maxDimensionPx: Int = 256,
): DecodedImage? {
    val context = LocalContext.current
    val cacheKey = remember(uri, maxDimensionPx) {
        uri?.takeIf { it.isNotBlank() }?.let { "uri:$it:$maxDimensionPx" }
    }
    return produceState<DecodedImage?>(initialValue = null, cacheKey) {
        val key = cacheKey ?: return@produceState
        ImageDecodeCache.get(key)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                fun openStream() = when {
                    uri!!.startsWith("content://") || uri.startsWith("file://") ->
                        context.contentResolver.openInputStream(Uri.parse(uri))
                    else -> File(uri).takeIf { it.isFile }?.inputStream()
                }

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
                DecodedImage(
                    image = bitmap.asImageBitmap(),
                    aspectRatio = bounds.outWidth.toFloat() / bounds.outHeight.toFloat(),
                )
            }.getOrNull()
        }
        if (decoded != null) ImageDecodeCache.put(key, decoded)
        value = decoded
    }.value
}

/** Decodes embedded image bytes off the main thread for rich-link previews. */
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
                DecodedImage(
                    image = bitmap.asImageBitmap(),
                    aspectRatio = bounds.outWidth.toFloat() / bounds.outHeight.toFloat(),
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

package app.openbubbles.nativeapp.ui.common

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorSpace
import android.graphics.Gainmap
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.os.Build
import android.util.LruCache
import androidx.core.graphics.scale
import androidx.core.net.toUri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.exifinterface.media.ExifInterface
import app.openbubbles.nativeapp.data.MemoryCaches
import app.openbubbles.nativeapp.data.extractWatchImageFromPosterSave
import app.openbubbles.nativeapp.data.resolveBackgroundImageFile
import com.radzivon.bartoshyk.avif.coder.Coder
import com.radzivon.bartoshyk.avif.coder.PreferredColorConfig
import com.radzivon.bartoshyk.avif.coder.ScaleMode
import com.radzivon.bartoshyk.avif.coder.ScalingQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.util.Locale
import kotlin.math.roundToInt

/** A decoded bitmap plus its display-oriented aspect ratio (width / height). */
data class DecodedImage(
    val image: ImageBitmap,
    val aspectRatio: Float,
)

/** Distinguishes an in-flight decode from a valid file this device cannot decode. */
data class DecodedImageResult(
    val image: DecodedImage?,
    val isLoading: Boolean,
)

/** Size-bounded decoded bitmap cache shared by visible lazy-list rows. */
object ImageDecodeCache {
    private const val MAX_SIZE_KB = 24 * 1024

    private val cache = object : LruCache<String, DecodedImage>(MAX_SIZE_KB) {
        override fun sizeOf(key: String, value: DecodedImage): Int {
            val bitmap = value.image.asAndroidBitmap()
            val gainmapBytes = if (Build.VERSION.SDK_INT >= 34) {
                bitmap.gainmap?.gainmapContents?.allocationByteCount?.toLong() ?: 0L
            } else {
                0L
            }
            return ((bitmap.allocationByteCount.toLong() + gainmapBytes) / 1024L)
                .coerceAtLeast(1L)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
        }
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
): DecodedImage? = rememberDecodedImageResult(file, maxDimensionPx).image

/**
 * Like [rememberDecodedImage], while exposing when both platform decoders have
 * actually finished. A null bitmap alone also means "still loading" and is
 * therefore insufficient for deciding whether to fall back to a preview.
 */
@Composable
fun rememberDecodedImageResult(
    file: File?,
    maxDimensionPx: Int = 512,
): DecodedImageResult {
    val cacheKey = remember(file?.absolutePath, file?.lastModified(), file?.length(), maxDimensionPx) {
        file?.let { "file:${it.absolutePath}:${it.lastModified()}:${it.length()}:$maxDimensionPx" }
    }
    return produceState(
        initialValue = DecodedImageResult(image = null, isLoading = cacheKey != null),
        cacheKey,
    ) {
        val key = cacheKey ?: run {
            value = DecodedImageResult(image = null, isLoading = false)
            return@produceState
        }
        ImageDecodeCache.get(key)?.let {
            value = DecodedImageResult(image = it, isLoading = false)
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            decodeLocalImage(file, maxDimensionPx)
        }
        if (decoded != null) ImageDecodeCache.put(key, decoded)
        value = DecodedImageResult(image = decoded, isLoading = false)
    }.value
}

/**
 * Resolves Flutter-era poster prefixes on a background thread, then decodes
 * the raster the same way [rememberDecodedImage] does.
 */
@Composable
fun rememberChatBackground(
    customPath: String?,
    syncedPath: String?,
    maxDimensionPx: Int = 1440,
): DecodedImage? {
    val cacheKey = remember(customPath, syncedPath, maxDimensionPx) {
        "bg:${customPath.orEmpty()}:${syncedPath.orEmpty()}:$maxDimensionPx"
    }
    return produceState<DecodedImage?>(initialValue = null, cacheKey) {
        ImageDecodeCache.get(cacheKey)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            sequenceOf(customPath, syncedPath)
                .mapNotNull { resolveBackgroundImageFile(it, ::extractWatchImageFromPosterSave) }
                .firstNotNullOfOrNull { decodeRasterFile(it, maxDimensionPx) }
        }
        if (decoded != null) ImageDecodeCache.put(cacheKey, decoded)
        value = decoded
    }.value
}

/**
 * Decodes a local still image. [BitmapFactory] handles JPEG/PNG/WebP;
 * [ImageDecoder] is the HEIC/HEIF/AVIF fallback on API 28+. A separate
 * software HEVC decoder recovers iPhone's 10-bit 4:4:4 originals when neither
 * platform decoder supports their codec profile, including their HDR gain map.
 */
internal fun decodeLocalImage(file: File?, maxDimensionPx: Int): DecodedImage? {
    if (file == null || !file.isFile) return null
    return decodeRasterFile(file, maxDimensionPx)
}

private fun decodeRasterFile(file: File, maxDimensionPx: Int): DecodedImage? {
    if (!file.isFile || maxDimensionPx <= 0) return null
    val header = readContainerHeader(file)
    if (prefersSoftwareHeicDecode(header)) {
        decodeFileWithSoftwareHeif(file, maxDimensionPx)?.let { return it }
    }
    return decodeFileWithBitmapFactory(file, maxDimensionPx)
        ?: decodeWithSdrFallback(
            decodeOriginal = {
                decodeFileWithImageDecoder(file, maxDimensionPx)
                    ?: decodeFileWithSoftwareHeif(file, maxDimensionPx)
            },
            decodeSdr = { decodeFileWithImageDecoder(file, maxDimensionPx, forceSdr = true) },
        )
}

private const val MaximumSoftwareHeifBytes = 64L * 1024L * 1024L
private const val MaximumContainerHeaderBytes = 256

internal data class SoftwareDecodeSize(val width: Int, val height: Int)

/** Fits the actual screen without enlarging originals or changing their aspect ratio. */
internal fun softwareDecodeSize(
    width: Int,
    height: Int,
    maxDimensionPx: Int,
): SoftwareDecodeSize? {
    if (width <= 0 || height <= 0 || maxDimensionPx <= 0) return null
    val longest = maxOf(width, height)
    if (longest <= maxDimensionPx) return SoftwareDecodeSize(width, height)
    val scale = maxDimensionPx.toDouble() / longest.toDouble()
    return SoftwareDecodeSize(
        width = (width * scale).roundToInt().coerceAtLeast(1),
        height = (height * scale).roundToInt().coerceAtLeast(1),
    )
}

private fun readContainerHeader(file: File): ByteArray = runCatching {
    file.inputStream().use { stream ->
        val header = ByteArray(MaximumContainerHeaderBytes)
        val count = stream.read(header)
        if (count > 0) header.copyOf(count) else byteArrayOf()
    }
}.getOrDefault(byteArrayOf())

private fun containerBrands(header: ByteArray): Set<String> {
    if (header.size < 16 || header.decodeToString(4, 8) != "ftyp") return emptySet()
    val declaredSize = ((header[0].toLong() and 0xffL) shl 24) or
        ((header[1].toLong() and 0xffL) shl 16) or
        ((header[2].toLong() and 0xffL) shl 8) or
        (header[3].toLong() and 0xffL)
    if (declaredSize < 16) return emptySet()
    val limit = minOf(header.size.toLong(), declaredSize).toInt()
    val brands = mutableSetOf(header.decodeToString(8, 12))
    var offset = 16
    while (offset + 4 <= limit) {
        brands += header.decodeToString(offset, offset + 4)
        offset += 4
    }
    return brands
}

internal fun isSoftwareDecodableHeif(header: ByteArray): Boolean =
    containerBrands(header).any { it in SoftwareHeifBrands }

/** Extended HEVC plus a tone-map image is the unsupported iPhone screenshot profile. */
internal fun prefersSoftwareHeicDecode(header: ByteArray): Boolean {
    val brands = containerBrands(header)
    return "tmap" in brands && ("heix" in brands || "hevx" in brands)
}

private val SoftwareHeifBrands = setOf(
    "heic", "heix", "hevc", "hevx", "heif", "mif1", "msf1", "avif", "avis",
)

/** Platform ImageDecoder and Media3 share a hardware codec; this decoder does not. */
private fun decodeFileWithSoftwareHeif(file: File, maxDimensionPx: Int): DecodedImage? =
    runCatching {
        val expectedBytes = file.length()
        if (expectedBytes <= 0L || expectedBytes > MaximumSoftwareHeifBytes) {
            return@runCatching null
        }
        val originalBytes = file.readBytes()
        if (originalBytes.size.toLong() != expectedBytes || !isSoftwareDecodableHeif(originalBytes)) {
            return@runCatching null
        }
        // avif-coder currently converts ICC-backed Display P3 to sRGB. Give
        // its software decoder the equivalent P3 CICP profile in memory so
        // the original 10-bit wide-gamut colors survive unchanged.
        val source = normalizeHeicDisplayP3ForSoftware(originalBytes)

        val coder = Coder()
        val sourceSize = coder.getSize(source) ?: return@runCatching null
        val targetSize = softwareDecodeSize(
            width = sourceSize.width,
            height = sourceSize.height,
            maxDimensionPx = maxDimensionPx,
        ) ?: return@runCatching null
        val colorConfig = if (Build.VERSION.SDK_INT >= 33) {
            PreferredColorConfig.RGBA_1010102
        } else {
            PreferredColorConfig.RGBA_F16
        }
        val bitmap = if (targetSize.width == sourceSize.width &&
            targetSize.height == sourceSize.height
        ) {
            coder.decode(source, colorConfig)
        } else {
            coder.decodeSampled(
                source,
                maxDimensionPx,
                maxDimensionPx,
                colorConfig,
                // getSize() in the upstream decoder double-swaps some EXIF
                // rotations. A square FIT box preserves the decoded image's
                // actual orientation and aspect ratio in either case.
                ScaleMode.FIT,
                ScalingQuality.HIGH,
            )
        }

        if (Build.VERSION.SDK_INT >= 34 && !bitmap.hasGainmap()) {
            attachHeicGainmap(bitmap, source, coder)
        }
        DecodedImage(
            image = bitmap.asImageBitmap(),
            aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat(),
        )
    }.getOrNull()

private fun attachHeicGainmap(bitmap: Bitmap, source: ByteArray, coder: Coder) {
    if (Build.VERSION.SDK_INT < 34) return
    runCatching {
        val toneMap = parseHeicToneMap(source) ?: return@runCatching
        val metadata = toneMap.metadata
        // ISO files using different alternate primaries require that actual
        // second ICC profile; guessing would visibly distort their HDR colors.
        if (!metadata.useBaseColorSpace) return@runCatching
        if (metadata.baseIsHdr && Build.VERSION.SDK_INT < 36) return@runCatching
        val gainmapBitmap = coder.decodeSampled(
            toneMap.gainMapImage,
            (bitmap.width / 2).coerceAtLeast(1),
            (bitmap.height / 2).coerceAtLeast(1),
            PreferredColorConfig.RGBA_8888,
            ScaleMode.RESIZE,
            ScalingQuality.HIGH,
        )
        val gainmap = Gainmap(gainmapBitmap).apply {
            setRatioMin(metadata.ratioMin[0], metadata.ratioMin[1], metadata.ratioMin[2])
            setRatioMax(metadata.ratioMax[0], metadata.ratioMax[1], metadata.ratioMax[2])
            setGamma(metadata.gamma[0], metadata.gamma[1], metadata.gamma[2])
            setEpsilonSdr(metadata.epsilonSdr[0], metadata.epsilonSdr[1], metadata.epsilonSdr[2])
            setEpsilonHdr(metadata.epsilonHdr[0], metadata.epsilonHdr[1], metadata.epsilonHdr[2])
            minDisplayRatioForHdrTransition = metadata.minDisplayRatioForHdrTransition
            displayRatioForFullHdr = metadata.displayRatioForFullHdr
            if (Build.VERSION.SDK_INT >= 36) {
                gainmapDirection = if (metadata.baseIsHdr) {
                    Gainmap.GAINMAP_DIRECTION_HDR_TO_SDR
                } else {
                    Gainmap.GAINMAP_DIRECTION_SDR_TO_HDR
                }
            }
        }
        bitmap.setGainmap(gainmap)
    }
}

/** Preserve the original colors and HDR whenever that decode actually succeeds. */
internal inline fun <T> decodeWithSdrFallback(
    decodeOriginal: () -> T?,
    decodeSdr: () -> T?,
): T? = decodeOriginal() ?: decodeSdr()

private fun decodeFileWithBitmapFactory(file: File, maxDimensionPx: Int): DecodedImage? =
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

private fun decodeFileWithImageDecoder(
    file: File,
    maxDimensionPx: Int,
    forceSdr: Boolean = false,
): DecodedImage? {
    if (Build.VERSION.SDK_INT < 28) return null
    return runCatching {
        val source = ImageDecoder.createSource(file)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            if (forceSdr) {
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }
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
        if (forceSdr && Build.VERSION.SDK_INT >= 34) bitmap.setGainmap(null)
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
    val scaled = scale(
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
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
        ?: decodeWithSdrFallback(
            decodeOriginal = { decodeUriImageWithImageDecoder(context, uri, maxDimensionPx) },
            decodeSdr = {
                decodeUriImageWithImageDecoder(context, uri, maxDimensionPx, forceSdr = true)
            },
        )
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
    forceSdr: Boolean = false,
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
            if (forceSdr) {
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
            }
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
        if (forceSdr && Build.VERSION.SDK_INT >= 34) bitmap.setGainmap(null)
        DecodedImage(
            image = bitmap.asImageBitmap(),
            aspectRatio = bitmap.width.toFloat() / bitmap.height.toFloat(),
        )
    }.getOrNull()
}

internal fun uriImageCacheKey(
    uri: String?,
    maxDimensionPx: Int,
    cacheGeneration: Int = 0,
): String? {
    val value = uri?.takeIf { it.isNotBlank() } ?: return null
    val fileMeta = runCatching {
        when {
            value.startsWith("content://") -> ""
            value.startsWith("file://") -> {
                val path = value.toUri().path ?: return "uri:$value:$maxDimensionPx:$cacheGeneration"
                File(path).takeIf { it.isFile }?.let { ":${it.lastModified()}:${it.length()}" }.orEmpty()
            }
            else -> File(value).takeIf { it.isFile }?.let { ":${it.lastModified()}:${it.length()}" }.orEmpty()
        }
    }.getOrDefault("")
    return "uri:$value$fileMeta:$maxDimensionPx:$cacheGeneration"
}

@Composable
fun rememberDecodedUriImage(
    uri: String?,
    maxDimensionPx: Int = 256,
    cacheGeneration: Int = 0,
): DecodedImage? {
    if (uri.isNullOrBlank()) return null
    val isInspection = LocalInspectionMode.current
    val context = LocalContext.current
    val fileMeta = remember(uri, isInspection) {
        if (isInspection) null
        else runCatching {
            when {
                uri.startsWith("content://") -> null
                uri.startsWith("file://") -> uri.toUri().path?.let { File(it) }?.takeIf { it.isFile }?.let { it.lastModified() to it.length() }
                else -> File(uri).takeIf { it.isFile }?.let { it.lastModified() to it.length() }
            }
        }.getOrNull()
    }
    val cacheKey = remember(uri, maxDimensionPx, cacheGeneration, fileMeta) {
        uriImageCacheKey(uri, maxDimensionPx, cacheGeneration)
    }
    return produceState<DecodedImage?>(initialValue = null, cacheKey) {
        if (isInspection) return@produceState
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

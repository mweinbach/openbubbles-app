package app.openbubbles.nativeapp.ui.common

import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** First-frame poster for a downloaded video, including HEVC / QuickTime. */
@Composable
fun rememberVideoPoster(
    file: File?,
    maxDimensionPx: Int = 512,
): DecodedImage? {
    val cacheKey = remember(file?.absolutePath, file?.lastModified(), file?.length(), maxDimensionPx) {
        file?.let { "video:${it.absolutePath}:${it.lastModified()}:${it.length()}:$maxDimensionPx" }
    }
    return produceState<DecodedImage?>(initialValue = null, cacheKey) {
        val key = cacheKey ?: return@produceState
        ImageDecodeCache.get(key)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) { decodeVideoPoster(file, maxDimensionPx) }
        if (decoded != null) ImageDecodeCache.put(key, decoded)
        value = decoded
    }.value
}

/** First-page raster of a downloaded PDF. */
@Composable
fun rememberPdfPreview(
    file: File?,
    maxDimensionPx: Int = 512,
    pageIndex: Int = 0,
): DecodedImage? {
    val cacheKey = remember(
        file?.absolutePath,
        file?.lastModified(),
        file?.length(),
        maxDimensionPx,
        pageIndex,
    ) {
        file?.let {
            "pdf:${it.absolutePath}:${it.lastModified()}:${it.length()}:$pageIndex:$maxDimensionPx"
        }
    }
    return produceState<DecodedImage?>(initialValue = null, cacheKey) {
        val key = cacheKey ?: return@produceState
        ImageDecodeCache.get(key)?.let {
            value = it
            return@produceState
        }
        val decoded = withContext(Dispatchers.IO) {
            decodePdfPage(file, pageIndex, maxDimensionPx)
        }
        if (decoded != null) ImageDecodeCache.put(key, decoded)
        value = decoded
    }.value
}

/** Null while the page count is being read; 0 when the file is not a PDF. */
@Composable
fun rememberPdfPageCount(file: File?): Int? {
    return produceState<Int?>(
        initialValue = null,
        file?.absolutePath,
        file?.lastModified(),
        file?.length(),
    ) {
        value = withContext(Dispatchers.IO) { pdfPageCount(file) }
    }.value
}

internal fun decodeVideoPoster(file: File?, maxDimensionPx: Int): DecodedImage? {
    if (file == null || !file.isFile) return null
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(file.absolutePath)
        val frame = retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?: retriever.frameAtTime
            ?: return null
        val scaled = frame.scaledToMaxDimension(maxDimensionPx)
        DecodedImage(
            image = scaled.asImageBitmap(),
            aspectRatio = scaled.width.toFloat() / scaled.height.toFloat(),
        )
    } catch (_: RuntimeException) {
        null
    } finally {
        runCatching { retriever.release() }
    }
}

internal fun decodePdfPage(file: File?, pageIndex: Int, maxDimensionPx: Int): DecodedImage? {
    if (file == null || !file.isFile || pageIndex < 0) return null
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                if (pageIndex >= renderer.pageCount) return@runCatching null
                renderer.openPage(pageIndex).use { page ->
                    val scale = previewScale(page.width, page.height, maxDimensionPx)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    val bitmap = createBitmap(width, height)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    DecodedImage(
                        image = bitmap.asImageBitmap(),
                        aspectRatio = width.toFloat() / height.toFloat(),
                    )
                }
            }
        }
    }.getOrNull()
}

internal fun pdfPageCount(file: File?): Int {
    if (file == null || !file.isFile) return 0
    return runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
            PdfRenderer(pfd).use { it.pageCount }
        }
    }.getOrDefault(0)
}

private fun previewScale(width: Int, height: Int, maxDimensionPx: Int): Float {
    val longest = maxOf(width, height).coerceAtLeast(1)
    return if (longest <= maxDimensionPx) 1f else maxDimensionPx.toFloat() / longest.toFloat()
}

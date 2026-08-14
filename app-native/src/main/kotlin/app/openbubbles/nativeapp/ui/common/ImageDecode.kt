package app.openbubbles.nativeapp.ui.common

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

/** A decoded bitmap plus the source aspect ratio (width / height). */
data class DecodedImage(
    val image: ImageBitmap,
    val aspectRatio: Float,
)

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
): DecodedImage? =
    produceState<DecodedImage?>(initialValue = null, file, maxDimensionPx) {
        if (file == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
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
    }.value

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

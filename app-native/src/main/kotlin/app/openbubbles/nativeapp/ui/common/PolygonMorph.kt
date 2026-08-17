package app.openbubbles.nativeapp.ui.common

import androidx.compose.foundation.shape.GenericShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * A [Shape] interpolating between two MaterialShapes polygons. There is no
 * `Morph.toShape()`, so the morph's cubics are emitted into a [GenericShape]
 * with per-evaluation bounds: intermediate morphs are not guaranteed to be
 * normalized even when both endpoints are. Progress is clamped — spatial
 * springs overshoot, and `Morph` extrapolation past 0/1 can self-intersect.
 *
 * The [Morph] mapping is built once per endpoint pair (keyed on the
 * polygons, never on progress — rebuilding it per frame stutters).
 */
@Composable
fun rememberPolygonMorph(
    start: RoundedPolygon,
    end: RoundedPolygon,
    progress: Float,
): Shape {
    val morph = remember(start, end) { Morph(start, end) }
    val clamped = progress.coerceIn(0f, 1f)
    return remember(morph, clamped) {
        GenericShape { size, _ ->
            val cubics = morph.asCubics(clamped)
            if (cubics.isEmpty()) return@GenericShape

            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            cubics.forEach { cubic ->
                val xs = floatArrayOf(
                    cubic.anchor0X, cubic.control0X, cubic.control1X, cubic.anchor1X,
                )
                val ys = floatArrayOf(
                    cubic.anchor0Y, cubic.control0Y, cubic.control1Y, cubic.anchor1Y,
                )
                for (i in xs.indices) {
                    minX = minOf(minX, xs[i])
                    maxX = maxOf(maxX, xs[i])
                    minY = minOf(minY, ys[i])
                    maxY = maxOf(maxY, ys[i])
                }
            }
            val boundsWidth = maxX - minX
            val boundsHeight = maxY - minY
            if (boundsWidth <= 0f || boundsHeight <= 0f) return@GenericShape

            // Uniform scale keeps the polygon undistorted; the offset centers it.
            val scale = minOf(size.width / boundsWidth, size.height / boundsHeight)
            val offsetX = (size.width - boundsWidth * scale) / 2f - minX * scale
            val offsetY = (size.height - boundsHeight * scale) / 2f - minY * scale

            cubics.forEachIndexed { index, cubic ->
                val ax0 = cubic.anchor0X * scale + offsetX
                val ay0 = cubic.anchor0Y * scale + offsetY
                val cx0 = cubic.control0X * scale + offsetX
                val cy0 = cubic.control0Y * scale + offsetY
                val cx1 = cubic.control1X * scale + offsetX
                val cy1 = cubic.control1Y * scale + offsetY
                val ax1 = cubic.anchor1X * scale + offsetX
                val ay1 = cubic.anchor1Y * scale + offsetY
                if (index == 0) moveTo(ax0, ay0)
                cubicTo(cx0, cy0, cx1, cy1, ax1, ay1)
            }
            close()
        }
    }
}

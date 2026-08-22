package app.openbubbles.nativeapp.ui.common

import android.content.pm.ActivityInfo
import android.graphics.ColorSpace
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * Keeps the window in HDR mode while a gain-mapped or PQ/HLG bitmap is shown.
 *
 * The window's ownership is remembered independently of the current image so
 * moving between HDR images does not briefly restore SDR and flash the display.
 * Android 15 adds bounded HDR headroom; Android 16 also exposes the display's
 * maximum ratio. Android 14 can still render gain maps without those controls.
 */
@Composable
fun HdrColorModeEffect(image: ImageBitmap?) {
    if (Build.VERSION.SDK_INT < 34) return
    val activity = LocalActivity.current ?: return
    val window = activity.window
    val bitmap = remember(image) { image?.asAndroidBitmap() }
    val gainmap = remember(bitmap) {
        runCatching { bitmap?.takeIf { it.hasGainmap() }?.gainmap }.getOrNull()
    }
    val hasHdrColorSpace = remember(bitmap) {
        val colorSpace = bitmap?.colorSpace ?: return@remember false
        colorSpace == ColorSpace.get(ColorSpace.Named.BT2020_PQ) ||
            colorSpace == ColorSpace.get(ColorSpace.Named.BT2020_HLG)
    }
    val showHdr = gainmap != null || hasHdrColorSpace
    val displayHeadroom = if (Build.VERSION.SDK_INT >= 36) {
        runCatching { activity.display?.highestHdrSdrRatio }.getOrNull()
            ?.takeIf { it.isFinite() && it >= MinimumHdrHeadroom }
    } else {
        null
    }
    val headroom = gainmap?.displayRatioForFullHdr?.let { imageHeadroom ->
        desiredHdrHeadroom(
            imageRatio = imageHeadroom,
            displayRatio = displayHeadroom ?: imageHeadroom,
        )
    } ?: AutomaticHdrHeadroom

    DisposableEffect(window) {
        val previousColorMode = window.colorMode
        val previousHeadroom = if (Build.VERSION.SDK_INT >= 35) {
            window.desiredHdrHeadroom
        } else {
            null
        }
        onDispose {
            window.colorMode = previousColorMode
            if (Build.VERSION.SDK_INT >= 35 && previousHeadroom != null) {
                window.desiredHdrHeadroom = previousHeadroom
            }
        }
    }

    SideEffect {
        if (showHdr) {
            if (Build.VERSION.SDK_INT >= 35 && window.desiredHdrHeadroom != headroom) {
                window.desiredHdrHeadroom = headroom
            }
            if (window.colorMode != ActivityInfo.COLOR_MODE_HDR) {
                window.colorMode = ActivityInfo.COLOR_MODE_HDR
            }
        } else {
            if (window.colorMode != ActivityInfo.COLOR_MODE_DEFAULT) {
                window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
            if (Build.VERSION.SDK_INT >= 35 && window.desiredHdrHeadroom != MinimumHdrHeadroom) {
                window.desiredHdrHeadroom = MinimumHdrHeadroom
            }
        }
    }
}

internal fun desiredHdrHeadroom(
    imageRatio: Float,
    displayRatio: Float,
    limit: Float = MaximumPlatformHdrHeadroom,
): Float {
    val validLimit = limit.takeIf { it.isFinite() && it >= MinimumHdrHeadroom }
        ?: return MinimumHdrHeadroom
    val validImageRatio = imageRatio.takeIf { it.isFinite() && it >= MinimumHdrHeadroom }
        ?: return MinimumHdrHeadroom
    val validDisplayRatio = displayRatio.takeIf { it.isFinite() && it >= MinimumHdrHeadroom }
        ?: return MinimumHdrHeadroom
    return minOf(validImageRatio, validDisplayRatio, validLimit)
}

private const val AutomaticHdrHeadroom = 0f
private const val MinimumHdrHeadroom = 1f
private const val MaximumPlatformHdrHeadroom = 10_000f

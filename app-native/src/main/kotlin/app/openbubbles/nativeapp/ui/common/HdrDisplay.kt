package app.openbubbles.nativeapp.ui.common

import android.content.pm.ActivityInfo
import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap

/**
 * Switches the window into HDR color mode while a gain-mapped (Ultra HDR or
 * compatible Apple HDR) bitmap is on screen, restoring the default on exit —
 * the Android equivalent of Apple's EDR headroom. Following the platform
 * guidance, the mode is flipped dynamically per displayed image rather than
 * declared globally. No-op below Android 14, where gain maps never decode.
 */
@Composable
fun HdrColorModeEffect(image: ImageBitmap?) {
    if (Build.VERSION.SDK_INT < 34) return
    val window = LocalActivity.current?.window
    val hasGainmap = remember(image) {
        runCatching { image?.asAndroidBitmap()?.hasGainmap() }.getOrNull() == true
    }
    DisposableEffect(window, hasGainmap) {
        if (window != null && hasGainmap) {
            window.colorMode = ActivityInfo.COLOR_MODE_HDR
        }
        onDispose {
            if (window != null && hasGainmap) {
                window.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            }
        }
    }
}

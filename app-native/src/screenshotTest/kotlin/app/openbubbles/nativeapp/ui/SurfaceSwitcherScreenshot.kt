package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.openbubbles.nativeapp.ui.navigation.TopLevelSurface
import app.openbubbles.nativeapp.ui.navigation.TopLevelSurfaceSwitcher
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * Every indicator state of the surface switcher in one fixture: each of the four
 * surfaces selected, plus the "no surface owns this destination" state the strip
 * shows on Settings.
 */
@PreviewTest
@Preview(name = "surface-switcher", device = Devices.PHONE, showBackground = true)
@Preview(
    name = "surface-switcher-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(name = "surface-switcher-large-text", device = Devices.PHONE, showBackground = true, fontScale = 1.5f)
@Composable
fun SurfaceSwitcherStatesScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        Surface {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                (TopLevelSurface.entries + null).forEach { selected ->
                    TopLevelSurfaceSwitcher(
                        current = selected,
                        onSelect = {},
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

/**
 * Right-to-left layout: the strip mirrors, so Messages sits at the start edge on
 * the right and a swipe toward the right advances instead of reversing.
 */
@PreviewTest
@Preview(
    name = "surface-switcher-rtl",
    device = Devices.PHONE,
    showBackground = true,
    locale = "ar",
)
@Composable
fun SurfaceSwitcherRtlScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        Surface {
            TopLevelSurfaceSwitcher(
                current = TopLevelSurface.PASSWORDS,
                onSelect = {},
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

/**
 * The narrow treatment used inside a two-pane list column, where the labels are
 * dropped and the icons carry the destination names.
 */
@PreviewTest
@Preview(name = "surface-switcher-narrow", widthDp = 300, heightDp = 120, showBackground = true)
@Composable
fun SurfaceSwitcherNarrowScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        Surface {
            TopLevelSurfaceSwitcher(
                current = TopLevelSurface.PHOTOS,
                onSelect = {},
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

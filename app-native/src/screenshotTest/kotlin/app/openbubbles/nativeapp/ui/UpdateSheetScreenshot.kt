package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import app.openbubbles.nativeapp.ui.settings.UpdateSheetContent
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.update.UpdateCoordinator
import com.android.tools.screenshot.PreviewTest

/**
 * Golden coverage for the update center: the ready-to-install state (with a
 * markdown changelog exercising headings, nested bullets, bold, code and a
 * link) and the up-to-date state in dark theme. The sheet content is
 * stateless, so "last checked" stays "2 h ago" on every render.
 */
private val sampleNotes = """
    ### Enhancements

    - Reworked **Settings** with clearer sections and icons
    - Update center with the full changelog and install reminders
        - Notification posts when the download finishes
    - Faster message send path with `sendMessageV2`
    - See the [release page](https://github.com/mweinbach/openbubbles-app/releases) for more

    ### Fixes

    - Fixes a crash when restoring a backup over a fresh install
    - Keeps quoted replies attached after an edit
""".trimIndent()

private val twoHoursAgo: Long
    get() = System.currentTimeMillis() - 2 * 60 * 60_000L

@PreviewTest
@Preview(name = "update-ready", device = Devices.PHONE, showBackground = true)
@Composable
fun UpdateSheetReadyScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        UpdateSheetContent(
            currentVersionName = "2.0.0",
            pendingUpdate = UpdateCoordinator.PendingUpdate(
                versionCode = 20002237,
                versionName = "2.0.1",
                notes = sampleNotes,
            ),
            skippedVersionName = null,
            lastCheckMs = twoHoursAgo,
            checking = false,
            status = null,
            error = null,
            onCheckNow = {},
            onInstall = {},
            onSkip = {},
        )
    }
}

@PreviewTest
@Preview(
    name = "update-current-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun UpdateSheetCurrentDarkScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        UpdateSheetContent(
            currentVersionName = "2.0.0",
            pendingUpdate = null,
            skippedVersionName = null,
            lastCheckMs = twoHoursAgo,
            checking = false,
            status = "You're up to date (version 2.0.0)",
            error = null,
            onCheckNow = {},
            onInstall = {},
            onSkip = {},
        )
    }
}

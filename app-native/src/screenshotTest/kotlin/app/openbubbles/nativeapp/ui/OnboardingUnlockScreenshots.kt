package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import app.openbubbles.nativeapp.data.HistorySyncWindow
import app.openbubbles.nativeapp.ui.onboarding.KeychainStepContent
import app.openbubbles.nativeapp.ui.onboarding.KeychainDeviceUi
import app.openbubbles.nativeapp.ui.onboarding.KeychainStepStage
import app.openbubbles.nativeapp.ui.onboarding.HistoryStep
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import com.android.tools.screenshot.PreviewTest

/**
 * The iCloud unlock step as a signed-in user first sees it: what encryption
 * is protecting, what it costs to skip, and no mention of the disabled
 * nearby-device path.
 */
@PreviewTest
@Preview(name = "onboarding-unlock-intro", device = Devices.PHONE, showBackground = true)
@Preview(
    name = "onboarding-unlock-intro-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun OnboardingKeychainIntroScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        KeychainStepContent(
            stage = KeychainStepStage.Intro,
            devices = emptyList(),
            selectedDevice = null,
            onSelectDevice = {},
            passcode = "",
            onPasscodeChange = {},
            joining = false,
            error = null,
            onFindDevices = {},
            onJoin = {},
            onContinue = {},
            onBack = {},
        )
    }
}

/** Device picked, passcode being collected — the one interactive state. */
@PreviewTest
@Preview(name = "onboarding-unlock-passcode", device = Devices.PHONE, showBackground = true)
@Composable
fun OnboardingKeychainPasscodeScreenshot() {
    val devices = listOf(
        KeychainDeviceUi(
            id = "iphone",
            numericLength = 6,
            displayName = "Maya's iPhone · iPhone 15 Pro",
        ),
        KeychainDeviceUi(
            id = "mac",
            numericLength = 6,
            displayName = "Studio Mac · Mac Studio",
        ),
    )
    OpenBubblesTheme(dynamicColor = false) {
        KeychainStepContent(
            stage = KeychainStepStage.Passcode,
            devices = devices,
            selectedDevice = devices.first(),
            onSelectDevice = {},
            passcode = "123456",
            onPasscodeChange = {},
            joining = false,
            error = null,
            onFindDevices = {},
            onJoin = {},
            onContinue = {},
            onBack = {},
        )
    }
}

/** The same step after the join succeeded, listing what it unlocked. */
@PreviewTest
@Preview(name = "onboarding-unlock-joined", device = Devices.PHONE, showBackground = true)
@Composable
fun OnboardingKeychainJoinedScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        KeychainStepContent(
            stage = KeychainStepStage.Joined,
            devices = emptyList(),
            selectedDevice = null,
            onSelectDevice = {},
            passcode = "",
            onPasscodeChange = {},
            joining = false,
            error = null,
            onFindDevices = {},
            onJoin = {},
            onContinue = {},
            onBack = {},
        )
    }
}

/** History window choice plus the warmth and quiet-notification warnings. */
@PreviewTest
@Preview(name = "onboarding-history", device = Devices.PHONE, showBackground = true)
@Preview(
    name = "onboarding-history-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun OnboardingHistoryStepScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        HistoryStep(
            canDownload = true,
            initialWindow = HistorySyncWindow.LAST_YEAR,
            onWindowChosen = {},
            onStartDownload = {},
            onSkip = {},
            onBack = {},
        )
    }
}

/** The same step when iCloud was left locked, so there is nothing to fetch. */
@PreviewTest
@Preview(name = "onboarding-history-locked", device = Devices.PHONE, showBackground = true)
@Composable
fun OnboardingHistoryStepLockedScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        HistoryStep(
            canDownload = false,
            initialWindow = HistorySyncWindow.ALL_HISTORY,
            onWindowChosen = {},
            onStartDownload = {},
            onSkip = {},
            onBack = {},
        )
    }
}

/** The locked download gate mid-run: progress, warmth, paused notifications. */
@PreviewTest
@Preview(name = "history-download-lock", device = Devices.PHONE, showBackground = true)
@Preview(
    name = "history-download-lock-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun HistoryDownloadLockScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        HistoryDownloadLockContent(
            statusLine = "Downloading messages — 18420 so far",
            failureMessage = null,
            onRetry = {},
            onDismiss = {},
        )
    }
}

/** The gate after a failed run: retry, or leave and use the app. */
@PreviewTest
@Preview(name = "history-download-lock-failed", device = Devices.PHONE, showBackground = true)
@Composable
fun HistoryDownloadLockFailureScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        HistoryDownloadLockContent(
            statusLine = "Download stopped",
            failureMessage = "Device is no longer in the iCloud clique; skipping sync",
            onRetry = {},
            onDismiss = {},
        )
    }
}

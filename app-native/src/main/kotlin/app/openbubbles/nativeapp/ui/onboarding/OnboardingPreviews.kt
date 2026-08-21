package app.openbubbles.nativeapp.ui.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.nativeapp.ui.tooling.LightDarkPreviews

@LightDarkPreviews
@Composable
private fun WelcomeStepPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        WelcomeStep(onGetStarted = {})
    }
}

@Preview(name = "Tour", device = Devices.PHONE, showBackground = true)
@Composable
private fun TourStepPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        TourStep(onContinue = {}, onSkip = {}, onBack = {})
    }
}

@Preview(name = "Permissions", device = Devices.PHONE, showBackground = true)
@Composable
private fun PermissionsStepPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        PermissionsStep(onContinue = {}, onBack = {}, modifier = Modifier)
    }
}

@Preview(name = "History download choice", device = Devices.PHONE, showBackground = true)
@Composable
private fun HistoryStepPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        HistoryStep(
            canDownload = true,
            onStartDownload = {},
            onSkip = {},
            onBack = {},
        )
    }
}

@Preview(name = "Onboarding flow", device = Devices.PHONE, showBackground = true)
@Composable
private fun OnboardingScreenPreview() {
    OpenBubblesTheme(dynamicColor = false) {
        OnboardingScreen(onSignedIn = {}, onFinished = {})
    }
}

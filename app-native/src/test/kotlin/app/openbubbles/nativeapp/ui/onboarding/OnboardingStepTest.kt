package app.openbubbles.nativeapp.ui.onboarding

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OnboardingStepTest {

    @Test
    fun stepsBeforeSignInWalkBackwards() {
        assertEquals(OnboardingStep.Welcome, OnboardingStep.Tour.previousStep())
        assertEquals(OnboardingStep.Tour, OnboardingStep.Permissions.previousStep())
        assertEquals(OnboardingStep.Permissions, OnboardingStep.Connect.previousStep())

        assertTrue(OnboardingStep.Tour.canGoBack())
        assertTrue(OnboardingStep.Permissions.canGoBack())
        assertTrue(OnboardingStep.Connect.canGoBack())
    }

    @Test
    fun signInIsAOneWayDoor() {
        // Walking back into sign-in would trigger a second Apple activation.
        assertFalse(OnboardingStep.Welcome.canGoBack())
        assertFalse(OnboardingStep.Keychain.canGoBack())
        assertEquals(OnboardingStep.Keychain, OnboardingStep.History.previousStep())
        assertTrue(OnboardingStep.History.canGoBack())
    }

    @Test
    fun keychainStageWaitsForTheConnectionBeforeAskingForAPasscode() {
        assertEquals(
            KeychainStepStage.Connecting,
            keychainStepStage(
                connected = false,
                inClique = null,
                loadingDevices = false,
                hasDevices = false,
            ),
        )
        assertEquals(
            KeychainStepStage.Intro,
            keychainStepStage(
                connected = true,
                inClique = false,
                loadingDevices = false,
                hasDevices = false,
            ),
        )
        assertEquals(
            KeychainStepStage.LoadingDevices,
            keychainStepStage(
                connected = true,
                inClique = false,
                loadingDevices = true,
                hasDevices = false,
            ),
        )
        assertEquals(
            KeychainStepStage.Passcode,
            keychainStepStage(
                connected = true,
                inClique = false,
                loadingDevices = false,
                hasDevices = true,
            ),
        )
    }

    @Test
    fun membershipShortCircuitsEveryOtherStage() {
        assertEquals(
            KeychainStepStage.Joined,
            keychainStepStage(
                connected = false,
                inClique = true,
                loadingDevices = true,
                hasDevices = true,
            ),
        )
    }
}

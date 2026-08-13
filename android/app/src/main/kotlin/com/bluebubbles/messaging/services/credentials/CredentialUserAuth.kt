package com.bluebubbles.messaging.services.credentials

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object CredentialUserAuth {
    private const val TITLE = "Confirm your identity"
    private const val SUBTITLE = "Authenticate to use your passkeys"
    private val defaultAuthenticators =
        BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    fun authenticateForPasskey(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (String?) -> Unit
    ) {
        val manager = BiometricManager.from(activity)
        val authenticators = when {
            manager.canAuthenticate(defaultAuthenticators) == BiometricManager.BIOMETRIC_SUCCESS ->
                defaultAuthenticators
            manager.canAuthenticate(BiometricManager.Authenticators.DEVICE_CREDENTIAL) == BiometricManager.BIOMETRIC_SUCCESS ->
                BiometricManager.Authenticators.DEVICE_CREDENTIAL
            else -> {
                onFailure("No biometric or lock screen authentication is available")
                return
            }
        }

        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    onFailure(errString.toString())
                }
            }
        )

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(TITLE)
            .setSubtitle(SUBTITLE)
            .setAllowedAuthenticators(authenticators)
            .build()

        prompt.authenticate(promptInfo)
    }
}

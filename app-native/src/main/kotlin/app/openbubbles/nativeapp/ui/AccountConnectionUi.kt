package app.openbubbles.nativeapp.ui

import app.openbubbles.nativeapp.service.ACCOUNT_TWO_FACTOR_REQUIRED_PREFIX
import app.openbubbles.nativeapp.service.registrationRequiresSignIn
import uniffi.rust_lib_bluebubbles.URegisterState

internal enum class AccountConnectionAction {
    SignIn,
    Retry,
}

internal enum class AccountConnectionTone {
    Neutral,
    Attention,
    Error,
}

internal data class AccountConnectionUiState(
    val title: String,
    val supporting: String,
    val action: AccountConnectionAction? = null,
    val actionLabel: String? = null,
    val busy: Boolean = false,
    val tone: AccountConnectionTone = AccountConnectionTone.Neutral,
)

/**
 * Converts the native push/IDS state into one user-facing recovery state.
 * A live APNs socket is not enough to call the account connected: IDS must
 * also report Registered.
 */
internal fun accountConnectionUiState(
    hasLiveState: Boolean,
    registration: URegisterState?,
    lastError: String?,
): AccountConnectionUiState? {
    if (hasLiveState && registration is URegisterState.Registered) return null

    if (registration == URegisterState.Registering) {
        return AccountConnectionUiState(
            title = "Reconnecting to iMessage",
            supporting = "Apple is refreshing this device's messaging registration.",
            busy = true,
        )
    }

    if (registration is URegisterState.Failed && registrationRequiresSignIn(registration)) {
        val needsTwoFactor = registration.error.startsWith(ACCOUNT_TWO_FACTOR_REQUIRED_PREFIX)
        return AccountConnectionUiState(
            title = if (needsTwoFactor) "Verify your Apple ID" else "Sign in again",
            supporting = if (needsTwoFactor) {
                "Registration renewal needs two-factor authentication. Messages may still " +
                    "arrive for now, but delivery can stop. Your local messages are safe."
            } else {
                "Apple rejected the saved registration credentials. Messages may still arrive " +
                    "for now, but delivery can stop. Your local messages are safe."
            },
            action = AccountConnectionAction.SignIn,
            actionLabel = "Continue",
            tone = AccountConnectionTone.Attention,
        )
    }

    if (registration is URegisterState.Failed) {
        return AccountConnectionUiState(
            title = "iMessage registration failed",
            supporting = registration.error,
            action = AccountConnectionAction.Retry,
            actionLabel = "Retry",
            tone = AccountConnectionTone.Error,
        )
    }

    if (hasLiveState) return null

    if (lastError?.contains("reconnecting automatically", ignoreCase = true) == true) {
        return AccountConnectionUiState(
            title = "Reconnecting to iMessage",
            supporting = "The Apple push connection was interrupted. Reconnecting automatically.",
            busy = true,
        )
    }

    if (!lastError.isNullOrBlank()) {
        return AccountConnectionUiState(
            title = "iMessage connection problem",
            supporting = lastError,
            action = AccountConnectionAction.Retry,
            actionLabel = "Retry",
            tone = AccountConnectionTone.Error,
        )
    }

    return AccountConnectionUiState(
        title = "Sign in to message",
        supporting = "Use your Apple ID to send and receive iMessages.",
        action = AccountConnectionAction.SignIn,
        actionLabel = "Sign in",
    )
}

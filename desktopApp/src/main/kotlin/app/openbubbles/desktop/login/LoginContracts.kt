package app.openbubbles.desktop.login

/**
 * UI-facing seam over the Rust login session (UniFFI `ULoginSession`) —
 * desktop copy of the native app's contract (no Android imports).
 *
 * Implementations may block on network I/O for up to ~30 seconds; callers
 * must invoke them from `Dispatchers.IO`.
 */
interface LoginHandle {
    /** Username persisted from a previous successful login, if any. */
    suspend fun savedUsername(): String?

    /**
     * Start (or resume) Apple ID login. `null`/`null` reuses previously
     * saved credentials; a username/password pair replaces them.
     */
    suspend fun login(username: String?, password: String?): LoginUiState

    /**
     * Submit a 2FA code — either the code shown on a trusted device or the
     * code received over SMS (the underlying session has one entry point).
     */
    suspend fun submitDeviceCode(code: String): LoginUiState

    /** Trusted phone numbers eligible to receive an SMS 2FA code. */
    suspend fun smsPhoneOptions(): List<SmsPhoneOption>

    /** Send the SMS code to the chosen phone. */
    suspend fun chooseSmsPhone(id: UInt): LoginUiState

    /** Switch a device-2FA prompt to SMS 2FA. */
    suspend fun requestSmsFallback(): LoginUiState

    /** HTML for the Apple account-update (terms) page. */
    suspend fun updateAccountPage(): String

    /** Finish the account-update (terms) flow. */
    suspend fun completeUpdateAccount(): LoginUiState

    /** Register the collected users with IDS; writes `id.plist` on success. */
    suspend fun register(): RegisterResult

    /** Release the underlying session. */
    fun close()
}

/** One trusted phone number offered as an SMS 2FA target. */
data class SmsPhoneOption(val id: UInt, val label: String)

/** UI mirror of the Rust `ULoginState`; transport details dropped. */
sealed class LoginUiState {
    object LoggedIn : LoginUiState()
    object NeedsLogin : LoginUiState()
    object NeedsDevice2Fa : LoginUiState()
    object Needs2faVerification : LoginUiState()
    object NeedsSms2Fa : LoginUiState()
    data class NeedsSms2faVerification(val phoneId: UInt) : LoginUiState()
    data class NeedsExtraStep(val detail: String) : LoginUiState()
}

/** Result of [LoginHandle.register]. */
sealed class RegisterResult {
    object Registered : RegisterResult()

    data class Blocked(
        val title: String,
        val body: String,
        val actionUrl: String?,
        val actionLabel: String?,
    ) : RegisterResult()
}

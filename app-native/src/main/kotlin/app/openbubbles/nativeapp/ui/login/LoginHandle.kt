package app.openbubbles.nativeapp.ui.login

/**
 * UI-facing seam over the Rust login session (UniFFI `ULoginSession`).
 *
 * Implementations may block on network I/O for up to ~30 seconds; callers
 * must invoke them from `Dispatchers.IO` (the view model wraps every call in
 * `runInterruptible`). [FakeLoginHandle] provides the same surface with
 * instant, deterministic states for previews and tests.
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

/**
 * Deterministic, instant [LoginHandle] for previews and tests.
 *
 * Scripted flow: sign in -> device 2FA -> any code but "000000" succeeds ->
 * registered -> done. "Use SMS instead" walks the phone chooser / SMS-code
 * path; the account-update path is reached through [requestSmsFallback]-
 * driven states when the fake is seeded with [nextState].
 *
 * Toggles:
 *  - [shouldFail] makes every call throw;
 *  - [blockRegistration] makes [register] return [RegisterResult.Blocked];
 *  - "000000" as a code is always rejected.
 */
class FakeLoginHandle(
    var shouldFail: Boolean = false,
    var blockRegistration: Boolean = false,
) : LoginHandle {
    /** Optional state forced as the next answer for scripted previews/tests. */
    var nextState: LoginUiState? = null

    /** Most recent login arguments, exposed for view-model routing tests. */
    var lastLogin: Pair<String?, String?>? = null

    /** Set once a phone is chosen, so wrong codes stay in the SMS state. */
    private var activePhoneId: UInt? = null

    override suspend fun savedUsername(): String? = PREVIEW_USERNAME

    override suspend fun login(username: String?, password: String?): LoginUiState {
        lastLogin = username to password
        failIfRequested()
        nextState?.let { return it }
        activePhoneId = null
        return LoginUiState.NeedsDevice2Fa
    }

    override suspend fun submitDeviceCode(code: String): LoginUiState {
        failIfRequested()
        nextState?.let { return it }
        if (code == REJECT_CODE) {
            val phoneId = activePhoneId
            return if (phoneId != null) {
                LoginUiState.NeedsSms2faVerification(phoneId)
            } else {
                LoginUiState.Needs2faVerification
            }
        }
        return if (code.isBlank()) LoginUiState.NeedsDevice2Fa else LoginUiState.LoggedIn
    }

    override suspend fun smsPhoneOptions(): List<SmsPhoneOption> {
        failIfRequested()
        return listOf(
            SmsPhoneOption(id = 1u, label = "iPhone ending in 42"),
            SmsPhoneOption(id = 2u, label = "Phone ending in 87"),
        )
    }

    override suspend fun chooseSmsPhone(id: UInt): LoginUiState {
        failIfRequested()
        nextState?.let { return it }
        activePhoneId = id
        return LoginUiState.NeedsSms2faVerification(id)
    }

    override suspend fun requestSmsFallback(): LoginUiState {
        failIfRequested()
        nextState?.let { return it }
        activePhoneId = null
        return LoginUiState.NeedsSms2Fa
    }

    override suspend fun updateAccountPage(): String {
        failIfRequested()
        return """
            <html>
              <head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
              <body style="font-family: sans-serif; margin: 16px;">
                <h2>Update your Apple ID</h2>
                <p>Apple requires you to review and accept the updated Terms
                   and Conditions before continuing.</p>
              </body>
            </html>
        """.trimIndent()
    }

    override suspend fun completeUpdateAccount(): LoginUiState {
        failIfRequested()
        nextState?.let { return it }
        return LoginUiState.LoggedIn
    }

    override suspend fun register(): RegisterResult {
        failIfRequested()
        return if (blockRegistration) {
            RegisterResult.Blocked(
                title = "Registration paused",
                body = "Apple stopped registration for this account. Review the " +
                    "support notice, then try again.",
                actionUrl = "https://support.apple.com/en-us/HT204411",
                actionLabel = "Learn more",
            )
        } else {
            RegisterResult.Registered
        }
    }

    override fun close() = Unit

    private fun failIfRequested() {
        if (shouldFail) {
            throw IllegalStateException("Fake login failure (turn off shouldFail to succeed)")
        }
    }

    companion object {
        const val PREVIEW_USERNAME = "preview@icloud.com"
        const val REJECT_CODE = "000000"
    }
}

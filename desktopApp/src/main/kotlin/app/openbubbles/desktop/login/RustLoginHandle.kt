package app.openbubbles.desktop.login

import app.openbubbles.desktop.DesktopGraph
import uniffi.rust_lib_bluebubbles.ULoginDelegate
import uniffi.rust_lib_bluebubbles.ULoginSession
import uniffi.rust_lib_bluebubbles.ULoginStage
import uniffi.rust_lib_bluebubbles.ULoginState
import uniffi.rust_lib_bluebubbles.URegistrationResult
import uniffi.rust_lib_bluebubbles.createLoginSession
import uniffi.rust_lib_bluebubbles.savedLoginUsername

/**
 * [LoginHandle] over the UniFFI `ULoginSession` — desktop copy of the
 * native app's adapter (verified free of Android imports).
 *
 * [path] is the app's config directory (the one holding `hw_info.plist` /
 * `gsa.plist` / `id.plist`) — the provisioning flow writes it, and this
 * adapter only reads it. The Rust session is created lazily on first use and
 * every method blocks (network, up to ~30s); the controller calls these from
 * `Dispatchers.IO`.
 *
 * Delegate callbacks fire synchronously on the calling thread; non-fatal
 * diagnostics are forwarded to [onDiagnostic] (fatal failures are thrown as
 * `UException` and surface as error text in the UI).
 */
class RustLoginHandle(
    private val path: String,
    private val onDiagnostic: (String) -> Unit = {},
) : LoginHandle {

    private val lock = Any()

    @Volatile
    private var session: ULoginSession? = null

    private fun session(): ULoginSession =
        session ?: synchronized(lock) {
            session ?: run {
                DesktopGraph.ensureRuntimeStarted()
                createLoginSession(path, Delegate()).also { session = it }
            }
        }

    override suspend fun savedUsername(): String? = savedLoginUsername(path)

    override suspend fun login(username: String?, password: String?): LoginUiState =
        session().login(username, password).toUiState()

    override suspend fun submitDeviceCode(code: String): LoginUiState =
        session().submit2faCode(code).toUiState()

    override suspend fun smsPhoneOptions(): List<SmsPhoneOption> =
        session().getSmsPhoneOptions().map { phone ->
            SmsPhoneOption(
                id = phone.id,
                label = "Phone ending in ${phone.lastTwoDigits}",
            )
        }

    override suspend fun chooseSmsPhone(id: UInt): LoginUiState =
        session().chooseSmsPhone(id).toUiState()

    override suspend fun requestSmsFallback(): LoginUiState =
        session().requestSmsFallback().toUiState()

    override suspend fun updateAccountPage(): String = session().getUpdateAccountPage()

    override suspend fun completeUpdateAccount(): LoginUiState =
        session().completeUpdateAccount().toUiState()

    override suspend fun register(): RegisterResult =
        when (val result = session().register()) {
            URegistrationResult.Registered -> RegisterResult.Registered
            is URegistrationResult.AppleBlocked -> RegisterResult.Blocked(
                title = result.title,
                body = result.body,
                actionUrl = result.actionUrl,
                actionLabel = result.actionLabel,
            )
        }

    override fun close() {
        synchronized(lock) {
            session?.close()
            session = null
        }
    }

    private fun ULoginState.toUiState(): LoginUiState = when (this) {
        ULoginState.LoggedIn -> LoginUiState.LoggedIn
        ULoginState.NeedsLogin -> LoginUiState.NeedsLogin
        ULoginState.NeedsDevice2Fa -> LoginUiState.NeedsDevice2Fa
        ULoginState.Needs2FaVerification -> LoginUiState.Needs2faVerification
        ULoginState.NeedsSms2Fa -> LoginUiState.NeedsSms2Fa
        is ULoginState.NeedsSms2FaVerification -> LoginUiState.NeedsSms2faVerification(phoneId)
        is ULoginState.NeedsExtraStep -> LoginUiState.NeedsExtraStep(detail)
    }

    private inner class Delegate : ULoginDelegate {
        override fun onStage(stage: ULoginStage) = Unit
        override fun onState(state: ULoginState) = Unit
        override fun onCircleSession(sid: String?) = Unit
        override fun onError(reason: String) = onDiagnostic(reason)
    }
}

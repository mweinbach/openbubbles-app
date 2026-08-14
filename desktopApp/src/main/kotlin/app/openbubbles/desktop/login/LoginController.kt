package app.openbubbles.desktop.login

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One screen of the Apple ID login flow; the state machine lives in
 * [LoginController], the composable renders whichever variant is current.
 */
sealed class LoginScreen {
    data class Form(
        val savedUsername: String?,
        val busy: Boolean,
        val error: String?,
    ) : LoginScreen()

    data class DeviceCode(
        val busy: Boolean,
        val error: String?,
    ) : LoginScreen()

    data class SmsPhoneChooser(
        val options: List<SmsPhoneOption>,
        val busy: Boolean,
        val error: String? = null,
    ) : LoginScreen()

    data class SmsCode(
        val phoneLabel: String,
        val busy: Boolean,
        val error: String?,
    ) : LoginScreen()

    data class ExtraStep(
        val html: String,
        val busy: Boolean,
        val error: String? = null,
    ) : LoginScreen()

    data class Done(
        val username: String,
    ) : LoginScreen()

    data class Blocked(
        val title: String,
        val body: String,
        val actionUrl: String?,
        val actionLabel: String?,
    ) : LoginScreen()
}

/**
 * Drives [LoginScreen] from a [LoginHandle] — the desktop port of the
 * native app's `LoginViewModel` (no androidx.lifecycle; a plain scope the
 * owner cancels). Every handle call runs on `Dispatchers.IO` and is
 * serialized by a mutex; a [Throwable] surfaces as an error string on the
 * current screen and is re-runnable via [retry].
 */
class LoginController(
    private val handle: LoginHandle,
    private val onRegistered: () -> Unit = {},
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val mutex = Mutex()

    private val _screen =
        MutableStateFlow<LoginScreen>(LoginScreen.Form(savedUsername = null, busy = false, error = null))
    val screen: StateFlow<LoginScreen> = _screen.asStateFlow()

    /** Username to report on [LoginScreen.Done] (typed, else the saved one). */
    private var effectiveUsername: String? = null

    /** Label of the phone the user picked, shown on the SMS-code screen. */
    private var pendingPhoneLabel: String? = null

    /** Last action, for [retry]. */
    private var retryAction: (suspend () -> Unit)? = null

    init {
        scope.launch {
            val saved = try {
                withContext(Dispatchers.IO) { handle.savedUsername() }
            } catch (_: Throwable) {
                null
            }
            val current = _screen.value
            if (current is LoginScreen.Form && current.savedUsername != saved) {
                _screen.value = current.copy(savedUsername = saved)
            }
        }
    }

    /** `null`/`null` signs in with the previously saved credentials. */
    fun submitCredentials(username: String?, password: String?) {
        runAction {
            val saved = (_screen.value as? LoginScreen.Form)?.savedUsername
            _screen.value = LoginScreen.Form(savedUsername = saved, busy = true, error = null)
            effectiveUsername = username?.trim()?.takeIf { it.isNotEmpty() } ?: saved
            follow(handle.login(username, password))
        }
    }

    /** Submit the trusted-device 2FA code. */
    fun submitCode(code: String) {
        runAction {
            _screen.value = LoginScreen.DeviceCode(busy = true, error = null)
            when (val state = handle.submitDeviceCode(code)) {
                LoginUiState.NeedsDevice2Fa,
                LoginUiState.Needs2faVerification,
                -> _screen.value = LoginScreen.DeviceCode(
                    busy = false,
                    error = "That code didn't work. Check your Apple device and try again.",
                )
                else -> follow(state)
            }
        }
    }

    /** Submit the SMS 2FA code (same entry point upstream). */
    fun submitSmsCode(code: String) {
        runAction {
            val label = (_screen.value as? LoginScreen.SmsCode)?.phoneLabel
            _screen.value = LoginScreen.SmsCode(phoneLabel = label ?: "your phone", busy = true, error = null)
            when (val state = handle.submitDeviceCode(code)) {
                is LoginUiState.NeedsSms2faVerification -> _screen.value = LoginScreen.SmsCode(
                    phoneLabel = label ?: "your phone",
                    busy = false,
                    error = "That code didn't work. Check the message and try again.",
                )
                else -> follow(state)
            }
        }
    }

    /** Send the SMS code to the chosen trusted phone. */
    fun pickPhone(id: UInt) {
        runAction {
            val options = (_screen.value as? LoginScreen.SmsPhoneChooser)?.options.orEmpty()
            _screen.value = LoginScreen.SmsPhoneChooser(options = options, busy = true, error = null)
            pendingPhoneLabel = options.firstOrNull { it.id == id }?.label
            follow(handle.chooseSmsPhone(id))
        }
    }

    /** Switch the device-2FA prompt to SMS 2FA. */
    fun requestSms() {
        runAction {
            _screen.value = LoginScreen.DeviceCode(busy = true, error = null)
            follow(handle.requestSmsFallback())
        }
    }

    /** Fetch the account-update HTML and show the extra step. */
    fun openExtraStep() {
        runAction { fetchExtraStep() }
    }

    /** Finish the account-update step. */
    fun acceptExtraStep() {
        runAction {
            val html = (_screen.value as? LoginScreen.ExtraStep)?.html.orEmpty()
            _screen.value = LoginScreen.ExtraStep(html = html, busy = true, error = null)
            follow(handle.completeUpdateAccount())
        }
    }

    /** Re-run the last failed action, keeping its arguments. */
    fun retry() {
        retryAction?.let { action ->
            scope.launch {
                mutex.withLock { withContext(Dispatchers.IO) { guarded(action) } }
            }
        }
    }

    fun close() {
        runCatching { handle.close() }
        scope.cancel()
    }

    // ------------------------------------------------------------------ internals

    private fun runAction(action: suspend () -> Unit) {
        retryAction = action
        scope.launch {
            mutex.withLock { withContext(Dispatchers.IO) { guarded(action) } }
        }
    }

    private suspend fun guarded(action: suspend () -> Unit) {
        try {
            action()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            applyError(t.message ?: "Something went wrong. Please try again.")
        }
    }

    /** Translate a handle state into the next screen (may chain handle calls). */
    private suspend fun follow(state: LoginUiState) {
        when (state) {
            LoginUiState.LoggedIn -> registerAndFinish()
            LoginUiState.NeedsLogin -> {
                val saved = (_screen.value as? LoginScreen.Form)?.savedUsername
                _screen.value = LoginScreen.Form(
                    savedUsername = saved,
                    busy = false,
                    error = "Please sign in again.",
                )
            }
            LoginUiState.NeedsDevice2Fa,
            LoginUiState.Needs2faVerification,
            -> _screen.value = LoginScreen.DeviceCode(busy = false, error = null)
            LoginUiState.NeedsSms2Fa -> {
                val options = handle.smsPhoneOptions()
                if (options.isEmpty()) {
                    _screen.value = LoginScreen.DeviceCode(
                        busy = false,
                        error = "No trusted phone numbers are available for SMS codes.",
                    )
                } else {
                    _screen.value = LoginScreen.SmsPhoneChooser(options = options, busy = false)
                }
            }
            is LoginUiState.NeedsSms2faVerification -> _screen.value = LoginScreen.SmsCode(
                phoneLabel = pendingPhoneLabel ?: "your phone",
                busy = false,
                error = null,
            )
            is LoginUiState.NeedsExtraStep -> fetchExtraStep()
        }
    }

    private suspend fun fetchExtraStep() {
        val current = _screen.value as? LoginScreen.ExtraStep
        _screen.value = LoginScreen.ExtraStep(html = current?.html.orEmpty(), busy = true, error = null)
        val html = handle.updateAccountPage()
        _screen.value = LoginScreen.ExtraStep(html = html, busy = false, error = null)
    }

    private suspend fun registerAndFinish() {
        when (val result = handle.register()) {
            RegisterResult.Registered -> {
                _screen.value = LoginScreen.Done(username = effectiveUsername ?: "Apple ID")
                onRegistered()
            }
            is RegisterResult.Blocked -> _screen.value = LoginScreen.Blocked(
                title = result.title,
                body = result.body,
                actionUrl = result.actionUrl,
                actionLabel = result.actionLabel,
            )
        }
    }

    private fun applyError(message: String) {
        _screen.value = when (val s = _screen.value) {
            is LoginScreen.Form -> s.copy(busy = false, error = message)
            is LoginScreen.DeviceCode -> s.copy(busy = false, error = message)
            is LoginScreen.SmsPhoneChooser -> s.copy(busy = false, error = message)
            is LoginScreen.SmsCode -> s.copy(busy = false, error = message)
            is LoginScreen.ExtraStep -> s.copy(busy = false, error = message)
            is LoginScreen.Done,
            is LoginScreen.Blocked,
            -> s
        }
    }
}

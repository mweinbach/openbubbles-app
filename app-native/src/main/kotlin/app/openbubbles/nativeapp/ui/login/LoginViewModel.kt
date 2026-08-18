package app.openbubbles.nativeapp.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
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
 * One screen of the Apple ID login flow. The state machine lives in
 * [LoginViewModel]; the composable renders whichever variant is current.
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

/** User intents surfaced by the login screens. */
interface LoginEvents {
    /** `null`/`null` signs in with the previously saved credentials. */
    fun submitCredentials(username: String?, password: String?)

    /** Submit the trusted-device 2FA code. */
    fun submitCode(code: String)

    /** Submit the SMS 2FA code (same entry point upstream). */
    fun submitSmsCode(code: String)

    /** Send the SMS code to the chosen trusted phone. */
    fun pickPhone(id: UInt)

    /** Switch the device-2FA prompt to SMS 2FA. */
    fun requestSms()

    /** Fetch the account-update HTML and show the extra step. */
    fun openExtraStep()

    /** Finish the account-update step. */
    fun acceptExtraStep()

    /** Re-run the last failed action, keeping its arguments. */
    fun retry()
}

/**
 * Drives [LoginScreen] from a [LoginHandle]. Every handle call runs on the
 * injected worker dispatcher (IO in production) and is serialized by a mutex; a
 * [Throwable] from the handle surfaces as an error string on the current
 * screen and is re-runnable via [retry].
 */
class LoginViewModel(
    private val scope: CoroutineScope,
    private val handle: LoginHandle,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel(), LoginEvents {

    private val mutex = Mutex()

    private val _screen =
        MutableStateFlow<LoginScreen>(LoginScreen.Form(savedUsername = null, busy = false, error = null))
    val screen: StateFlow<LoginScreen> = _screen.asStateFlow()

    /** Username to report on [LoginScreen.Done] (typed, else the saved one). */
    private var effectiveUsername: String? = null

    /** Label of the phone the user picked, shown on the SMS-code screen. */
    private var pendingPhoneLabel: String? = null

    /** Allow a typed same-account fallback after saved-session re-auth fails. */
    private var savedSessionFailed: Boolean = false

    /** Last action, for [retry]. */
    private var retryAction: (suspend () -> Unit)? = null

    init {
        scope.launch {
            // `withContext(IO)` rather than `runInterruptible`: the handle API
            // is suspend, and runInterruptible's block is not.
            val saved = try {
                withContext(workerDispatcher) { handle.savedUsername() }
            } catch (t: Throwable) {
                null
            }
            val current = _screen.value
            if (current is LoginScreen.Form && current.savedUsername != saved) {
                _screen.value = current.copy(savedUsername = saved)
            }
            // Repair handoff: default to the sessioned re-auth (no typed
            // password, no reset_user, no fresh iCloud provision). A failure
            // lands back on the form with the error, where the full
            // credential flow remains available as the fallback.
            if (app.openbubbles.nativeapp.data.RepairFlow.consumeSessionRepair() && saved != null) {
                submitCredentials(null, null)
            }
        }
    }

    override fun submitCredentials(username: String?, password: String?) {
        runAction {
            val saved = (_screen.value as? LoginScreen.Form)?.savedUsername
            _screen.value = LoginScreen.Form(savedUsername = saved, busy = true, error = null)
            val enteredUsername = username?.trim()?.takeIf { it.isNotEmpty() }
            effectiveUsername = enteredUsername ?: saved
            // A password login performs a fresh Apple iCloud activation. When
            // this is the already-saved account, reuse its session instead;
            // Apple hard-limits fresh activations per emulated Mac identity.
            val reuseSaved = !savedSessionFailed && saved != null &&
                enteredUsername.equals(saved, ignoreCase = true)
            val usingSavedSession = reuseSaved || (username == null && password == null)
            try {
                follow(handle.login(
                    username = if (reuseSaved) null else username,
                    password = if (reuseSaved) null else password,
                ))
            } catch (t: Throwable) {
                if (usingSavedSession) savedSessionFailed = true
                throw t
            }
        }
    }

    override fun submitCode(code: String) {
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

    override fun submitSmsCode(code: String) {
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

    override fun pickPhone(id: UInt) {
        runAction {
            val options = (_screen.value as? LoginScreen.SmsPhoneChooser)?.options.orEmpty()
            _screen.value = LoginScreen.SmsPhoneChooser(options = options, busy = true, error = null)
            pendingPhoneLabel = options.firstOrNull { it.id == id }?.label
            follow(handle.chooseSmsPhone(id))
        }
    }

    override fun requestSms() {
        runAction {
            _screen.value = LoginScreen.DeviceCode(busy = true, error = null)
            follow(handle.requestSmsFallback())
        }
    }

    override fun openExtraStep() {
        runAction { fetchExtraStep() }
    }

    override fun acceptExtraStep() {
        runAction {
            val html = (_screen.value as? LoginScreen.ExtraStep)?.html.orEmpty()
            _screen.value = LoginScreen.ExtraStep(html = html, busy = true, error = null)
            follow(handle.completeUpdateAccount())
        }
    }

    override fun retry() {
        retryAction?.let { action ->
            scope.launch {
                mutex.withLock { withContext(workerDispatcher) { guarded(action) } }
            }
        }
    }

    override fun onCleared() {
        handle.close()
        scope.cancel()
    }

    // ------------------------------------------------------------------ internals

    private fun runAction(action: suspend () -> Unit) {
        retryAction = action
        scope.launch {
            mutex.withLock { withContext(workerDispatcher) { guarded(action) } }
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
            RegisterResult.Registered ->
                _screen.value = LoginScreen.Done(username = effectiveUsername ?: "Apple ID")
            is RegisterResult.Blocked ->
                _screen.value = LoginScreen.Blocked(
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

    companion object {
        /** Production factory; the VM's scope is cancelled in [onCleared]. */
        fun factory(handle: LoginHandle): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LoginViewModel(
                    scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
                    handle = handle,
                )
            }
        }
    }
}

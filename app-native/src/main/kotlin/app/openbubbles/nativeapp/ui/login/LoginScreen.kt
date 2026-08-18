package app.openbubbles.nativeapp.ui.login

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.webkit.WebView
import androidx.core.net.toUri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material3.LocalTextStyle
import app.openbubbles.nativeapp.ui.common.pillTextFieldColors
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay

/**
 * Apple ID login flow: a single screen that switches on the view model's
 * [LoginScreen] state — credentials form, device 2FA, SMS phone chooser,
 * SMS code, Apple terms webview, blocked notice, and success.
 */
@Composable
fun LoginScreen(
    handle: LoginHandle,
    onFinished: (username: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shown on the form step — re-run device provisioning (e.g. switch to a relay code). */
    onRedoSetup: (() -> Unit)? = null,
    /** When true, skips the internal "Sign in" chrome so a host-supplied
     *  header (e.g. the onboarding connect step) is the only title. */
    embedded: Boolean = false,
) {
    val loginViewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory(handle))
    val state by loginViewModel.screen.collectAsStateWithLifecycle()
    LoginScreenBody(
        state = state,
        events = loginViewModel,
        onBack = onBack,
        onFinished = onFinished,
        modifier = modifier,
        onRedoSetup = onRedoSetup,
        embedded = embedded,
    )
}

@Composable
fun LoginScreenBody(
    state: LoginScreen,
    events: LoginEvents,
    onBack: () -> Unit,
    onFinished: (username: String) -> Unit,
    modifier: Modifier = Modifier,
    onRedoSetup: (() -> Unit)? = null,
    /** When true, skips the internal "Sign in" chrome so a host-supplied
     *  header (e.g. the onboarding connect step) is the only title. */
    embedded: Boolean = false,
) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .imePadding(),
        ) {
            if (state.isBusy()) {
                // Indeterminate account round-trips — the wavy indicator is
                // the expressive form of exactly this kind of wait.
                LinearWavyProgressIndicator(Modifier.fillMaxWidth())
            }
            when (state) {
                is LoginScreen.Form -> FormStep(state, events, onRedoSetup, embedded)
                is LoginScreen.DeviceCode -> DeviceCodeStep(state, events)
                is LoginScreen.SmsPhoneChooser -> SmsPhoneChooserStep(state, events)
                is LoginScreen.SmsCode -> SmsCodeStep(state, events)
                is LoginScreen.ExtraStep -> ExtraStepScreen(state, events)
                is LoginScreen.Done -> DoneStep(state, onFinished)
                is LoginScreen.Blocked -> BlockedStep(state)
            }
        }
}

// ---------------------------------------------------------------------- steps

@Composable
private fun FormStep(
    state: LoginScreen.Form,
    events: LoginEvents,
    onRedoSetup: (() -> Unit)?,
    embedded: Boolean,
) {
    var username by rememberSaveable(state.savedUsername) {
        mutableStateOf(state.savedUsername.orEmpty())
    }
    var password by remember { mutableStateOf("") }
    var passwordVisible by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Forms center-cap at 480dp on wide windows instead of stretching.
        Column(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        if (!embedded) {
            AppBadge()
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Sign in with your Apple ID",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "OpenBubbles connects directly to iMessage using your Apple ID.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
        } else {
            Spacer(Modifier.height(4.dp))
        }
        LoginTextField(
            value = username,
            onValueChange = { username = it },
            label = "Apple ID",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
        Spacer(Modifier.height(12.dp))
        LoginTextField(
            value = password,
            onValueChange = { password = it },
            label = "Password",
            visualTransformation = if (passwordVisible) {
                VisualTransformation.None
            } else {
                PasswordVisualTransformation()
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            trailingIcon = {
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = if (passwordVisible) "Hide password" else "Show password",
                    )
                }
            },
        )
        Spacer(Modifier.height(12.dp))
        ErrorRow(state.error, events)
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { events.submitCredentials(username.trim(), password) },
            shapes = ButtonDefaults.shapes(),
            enabled = !state.busy && username.isNotBlank() && password.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.busy) "Signing in…" else "Sign in")
        }
        if (state.savedUsername != null) {
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(
                onClick = { events.submitCredentials(null, null) },
                shapes = ButtonDefaults.shapes(),
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Continue as ${state.savedUsername}")
            }
        }
        if (onRedoSetup != null && !state.isBusy()) {
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onRedoSetup,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Use a different device setup method")
            }
        }
        }
    }
}

@Composable
private fun DeviceCodeStep(state: LoginScreen.DeviceCode, events: LoginEvents) {
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Two-Factor Authentication",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Enter the code shown on your trusted Apple device.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        CodeField(
            value = code,
            onValueChange = { code = it },
            modifier = Modifier.width(240.dp),
        )
        Spacer(Modifier.height(16.dp))
        ErrorRow(state.error, events)
        Button(
            onClick = { events.submitCode(code) },
            shapes = ButtonDefaults.shapes(),
            enabled = !state.busy && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Verify")
        }
        TextButton(
            onClick = events::requestSms,
            enabled = !state.busy,
        ) {
            Text("Use SMS instead")
        }
    }
}

@Composable
private fun SmsPhoneChooserStep(state: LoginScreen.SmsPhoneChooser, events: LoginEvents) {
    var selectedId by rememberSaveable { mutableIntStateOf(-1) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text("Verify with a text message", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Choose the phone number where Apple should send the code.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        state.options.forEach { option ->
            val rowId = option.id.toInt()
            // A single-select set is a radio group: the row carries the role
            // and selection, the RadioButton is its visual.
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = if (selectedId == rowId) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .selectable(
                        selected = selectedId == rowId,
                        onClick = { selectedId = rowId },
                        role = Role.RadioButton,
                    ),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RadioButton(selected = selectedId == rowId, onClick = null)
                    Text(option.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (state.options.isEmpty()) {
            Text(
                text = "No trusted phone numbers are available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        ErrorRow(state.error, events)
        Button(
            onClick = { events.pickPhone(selectedId.toUInt()) },
            shapes = ButtonDefaults.shapes(),
            enabled = !state.busy && selectedId >= 0,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun SmsCodeStep(state: LoginScreen.SmsCode, events: LoginEvents) {
    var code by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Phone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Enter the SMS code", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Code sent to ${state.phoneLabel}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        CodeField(
            value = code,
            onValueChange = { code = it },
            modifier = Modifier.width(240.dp),
        )
        Spacer(Modifier.height(16.dp))
        ErrorRow(state.error, events)
        Button(
            onClick = { events.submitSmsCode(code) },
            shapes = ButtonDefaults.shapes(),
            enabled = !state.busy && code.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Verify")
        }
    }
}

@SuppressLint("SetJavaScriptEnabled") // Apple's hosted login extra-step page requires JavaScript.
@Composable
private fun ExtraStepScreen(state: LoginScreen.ExtraStep, events: LoginEvents) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "Apple requires a quick account update before continuing.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.useWideViewPort = true
                    settings.loadWithOverviewMode = true
                }
            },
            update = { webView ->
                if (webView.tag != state.html) {
                    webView.tag = state.html
                    webView.loadDataWithBaseURL(null, state.html, "text/html", "utf-8", null)
                }
            },
            onRelease = { webView ->
                webView.stopLoading()
                webView.destroy()
            },
        )
        ErrorRow(state.error, events, modifier = Modifier.padding(top = 8.dp))
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = events::acceptExtraStep,
            shapes = ButtonDefaults.shapes(),
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun DoneStep(state: LoginScreen.Done, onFinished: (username: String) -> Unit) {
    LaunchedEffect(state.username) {
        delay(1_000)
        onFinished(state.username)
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Signed in", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.username,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockedStep(state: LoginScreen.Blocked) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = state.title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = state.body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!state.actionUrl.isNullOrBlank() && state.actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, state.actionUrl.toUri()))
                    } catch (_: Exception) {
                        // No handler for the URL; the text still tells the story.
                    }
                },
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(state.actionLabel)
            }
        }
    }
}

// ------------------------------------------------------------------- pieces

/** Simple monogram "app icon" — no assets in this module yet. */
@Composable
private fun AppBadge() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 0.dp,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "OB",
                style = MaterialTheme.typography.headlineMedium,
            )
        }
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    trailingIcon: @Composable (() -> Unit)? = null,
    textStyle: TextStyle = LocalTextStyle.current,
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        trailingIcon = trailingIcon,
        textStyle = textStyle,
        shape = MaterialTheme.shapes.medium,
        colors = pillTextFieldColors(),
    )
}

/** Centered 6-digit code field on a digit keyboard. */
@Composable
private fun CodeField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    LoginTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).take(6)) },
        label = "Code",
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        textStyle = LocalTextStyle.current.copy(
            textAlign = TextAlign.Center,
            fontSize = 26.sp,
            letterSpacing = 6.sp,
        ),
    )
}

@Composable
private fun ErrorRow(error: String?, events: LoginEvents, modifier: Modifier = Modifier) {
    if (error != null) {
        Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = events::retry) { Text("Try again") }
        }
    }
}

private fun LoginScreen.isBusy(): Boolean = when (this) {
    is LoginScreen.Form -> busy
    is LoginScreen.DeviceCode -> busy
    is LoginScreen.SmsPhoneChooser -> busy
    is LoginScreen.SmsCode -> busy
    is LoginScreen.ExtraStep -> busy
    is LoginScreen.Done -> false
    is LoginScreen.Blocked -> false
}

// ------------------------------------------------------------------- previews

private object NoopEvents : LoginEvents {
    override fun submitCredentials(username: String?, password: String?) = Unit
    override fun submitCode(code: String) = Unit
    override fun submitSmsCode(code: String) = Unit
    override fun pickPhone(id: UInt) = Unit
    override fun requestSms() = Unit
    override fun openExtraStep() = Unit
    override fun acceptExtraStep() = Unit
    override fun retry() = Unit
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginFormPreview() {
    OpenBubblesTheme {
        LoginScreenBody(
            state = LoginScreen.Form(
                savedUsername = FakeLoginHandle.PREVIEW_USERNAME,
                busy = false,
                error = null,
            ),
            events = NoopEvents,
            onBack = {},
            onFinished = {},
        )
    }
}

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LoginDeviceCodePreview() {
    OpenBubblesTheme {
        LoginScreenBody(
            state = LoginScreen.DeviceCode(busy = false, error = null),
            events = NoopEvents,
            onBack = {},
            onFinished = {},
        )
    }
}

/** Live-flow preview driven by the [FakeLoginHandle] (fully interactive in
 *  the IDE's interactive mode; static rendering shows the initial form). */
@Preview(showBackground = true)
@Composable
private fun LoginFakeFlowPreview() {
    val previewViewModel = remember {
        LoginViewModel(CoroutineScope(Dispatchers.Unconfined), FakeLoginHandle())
    }
    val state by previewViewModel.screen.collectAsState()
    OpenBubblesTheme {
        LoginScreenBody(
            state = state,
            events = previewViewModel,
            onBack = {},
            onFinished = {},
        )
    }
}

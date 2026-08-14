package app.openbubbles.desktop.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.awt.Desktop
import java.net.URI

/**
 * The Apple ID login flow: username/password form, device 2FA with SMS
 * fallback, the account-update (terms) step, and registration. Renders the
 * [LoginController]'s current [LoginScreen].
 */
@Composable
fun LoginScreen(controller: LoginController) {
    val screen by controller.screen.collectAsState()

    Surface(modifier = Modifier.fillMaxSize()) {
        when (val s = screen) {
            is LoginScreen.Form -> LoginForm(s, controller)
            is LoginScreen.DeviceCode -> DeviceCodeForm(s, controller)
            is LoginScreen.SmsPhoneChooser -> SmsPhoneChooser(s, controller)
            is LoginScreen.SmsCode -> SmsCodeForm(s, controller)
            is LoginScreen.ExtraStep -> ExtraStepScreen(s, controller)
            is LoginScreen.Done -> DoneScreen(s)
            is LoginScreen.Blocked -> BlockedScreen(s, controller)
        }
    }
}

@Composable
private fun LoginForm(state: LoginScreen.Form, controller: LoginController) {
    var username by remember(state.savedUsername) { mutableStateOf(state.savedUsername.orEmpty()) }
    var password by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Sign in", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "Use your Apple ID to register this device with iMessage.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Apple ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                val user = username.trim().takeIf { it.isNotEmpty() }
                val pass = password.takeIf { it.isNotEmpty() }
                controller.submitCredentials(user, pass)
            },
            enabled = !state.busy,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text("Signing in…")
            } else {
                Text("Sign in")
            }
        }
    }
}

@Composable
private fun DeviceCodeForm(state: LoginScreen.DeviceCode, controller: LoginController) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Two-Factor Authentication", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Enter the verification code shown on one of your trusted Apple devices.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
            label = { Text("Verification code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { controller.submitCode(code) },
            enabled = !state.busy && code.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text("Verifying…")
            } else {
                Text("Submit code")
            }
        }
        TextButton(
            onClick = { controller.requestSms() },
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Use SMS instead")
        }
    }
}

@Composable
private fun SmsPhoneChooser(state: LoginScreen.SmsPhoneChooser, controller: LoginController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Choose a phone", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Apple will text a verification code to the phone you choose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (state.busy) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(state.options) { option ->
                TextButton(
                    onClick = { controller.pickPhone(option.id) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(option.label, modifier = Modifier.fillMaxWidth())
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SmsCodeForm(state: LoginScreen.SmsCode, controller: LoginController) {
    var code by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("SMS code sent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(
            "Enter the code sent to ${state.phoneLabel}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = code,
            onValueChange = { value -> code = value.filter(Char::isDigit).take(6) },
            label = { Text("SMS code") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy,
        )
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = { controller.submitSmsCode(code) },
            enabled = !state.busy && code.length == 6,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            if (state.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.size(8.dp))
                Text("Verifying…")
            } else {
                Text("Submit code")
            }
        }
    }
}

/**
 * Apple's account-update (terms) page renders via JavaScript, which a plain
 * desktop window can't run: show a stripped text summary, offer to open the
 * real page in the system browser, and continue with
 * [LoginController.acceptExtraStep].
 */
@Composable
private fun ExtraStepScreen(state: LoginScreen.ExtraStep, controller: LoginController) {
    val text = remember(state.html) { stripHtml(state.html) }
    val url = remember(state.html) { firstUrlIn(state.html) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .padding(24.dp, 24.dp, 24.dp, 8.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Update your Apple ID", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "Apple requires extra account steps before you can continue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = text.ifBlank { "(No additional detail text was provided.)" },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (url != null) {
                OutlinedButton(
                    onClick = { browse(url) },
                    enabled = !state.busy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                ) {
                    Text("Open page in browser")
                }
            }
            Button(
                onClick = { controller.acceptExtraStep() },
                enabled = !state.busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                if (state.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(8.dp))
                    Text("Continuing…")
                } else {
                    Text("Continue")
                }
            }
        }
    }
}

@Composable
private fun DoneScreen(state: LoginScreen.Done) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(16.dp))
        Text(
            "Signed in as ${state.username}",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            "Registering with iMessage…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BlockedScreen(state: LoginScreen.Blocked, controller: LoginController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            state.title.ifBlank { "Registration paused" },
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(state.body, style = MaterialTheme.typography.bodyMedium)
        if (state.actionUrl != null) {
            OutlinedButton(
                onClick = { browse(state.actionUrl) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
            ) {
                Text(state.actionLabel ?: "Learn more")
            }
        }
        Button(
            onClick = { controller.retry() },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("Try again")
        }
    }
}

// ------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------

private val HTML_BLOCK = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
private val HTML_TAG = Regex("(?s)<[^>]+>")
private val HTML_ENTITY = Regex("&#(\\d+);")

/** Crude HTML → plain text (tags stripped, common entities decoded). */
internal fun stripHtml(html: String): String {
    val withoutBlocks = html.replace(HTML_BLOCK, " ")
    val withoutTags = withoutBlocks.replace(HTML_TAG, " ")
    return withoutTags
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(HTML_ENTITY) { m -> m.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: " " }
        .replace(Regex("[ \\t\\x0B\\f\\r]+"), " ")
        .replace(Regex("\\n\\s*\\n+"), "\n")
        .trim()
        .take(8000)
}

private val URL_IN_HTML = Regex("""https?://[^\s"'<>\\)]+""")

/** First absolute URL in the HTML, for the "open in browser" affordance. */
internal fun firstUrlIn(html: String): String? =
    URL_IN_HTML.find(html)?.value?.trimEnd('.', ',')?.takeIf { it.startsWith("http") }

/** Opens [url] in the system browser, ignoring failures. */
internal fun browse(url: String) {
    runCatching {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI.create(url))
        }
    }
}

package app.openbubbles.nativeapp

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.platform.ComposeView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import app.openbubbles.nativeapp.data.AppearancePrefs
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.DeviceContacts
import app.openbubbles.nativeapp.data.OfficialEngineProbe
import app.openbubbles.nativeapp.service.Notifications
import app.openbubbles.nativeapp.ui.OpenBubblesApp
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import app.openbubbles.core.Hello
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.isLocked
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

data class SmsComposeRequest(
    val recipients: List<String>,
    val body: String?,
    val useSms: Boolean = true,
)

internal fun parseSmsComposeRequest(
    action: String?,
    dataString: String?,
    extraText: String?,
): SmsComposeRequest? {
    if (action == Intent.ACTION_SEND) {
        val body = extraText?.take(20_000)?.takeIf { it.isNotBlank() } ?: return null
        return SmsComposeRequest(recipients = emptyList(), body = body, useSms = true)
    }
    if (
        (action != Intent.ACTION_SENDTO && action != Intent.ACTION_VIEW) ||
        dataString.isNullOrBlank()
    ) return null
    val scheme = dataString.substringBefore(':', "").lowercase()
    if (scheme !in setOf("sms", "smsto", "mms", "mmsto")) return null
    val schemeSpecific = dataString.substringAfter(':').removePrefix("//")
    val recipientPart = schemeSpecific.substringBefore('?')
    val recipients = recipientPart
        .split(',', ';')
        .mapNotNull { raw -> decodeComposeValue(raw, plusAsSpace = false).trim().takeIf { it.isNotEmpty() } }
        .take(32)
    val query = schemeSpecific.substringAfter('?', "")
    val queryBody = query.split('&')
        .mapNotNull { part ->
            val key = part.substringBefore('=', "")
            if (key.equals("body", ignoreCase = true)) {
                decodeComposeValue(part.substringAfter('=', ""), plusAsSpace = true)
            } else {
                null
            }
        }
        .firstOrNull()
    val body = (queryBody ?: extraText)?.take(20_000)?.takeIf { it.isNotBlank() }
    return SmsComposeRequest(recipients = recipients, body = body)
}

private fun decodeComposeValue(value: String, plusAsSpace: Boolean): String = runCatching {
    val encoded = if (plusAsSpace) value else value.replace("+", "%2B")
    URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
}.getOrDefault(value)

class NativeMainActivity : ComponentActivity() {
    private var debugLines: List<String> = emptyList()
    private var resumeRoute: String? = null
    private var pendingComposeRequest: SmsComposeRequest? by mutableStateOf(null)
    private var uiDetached = false
    private var uiReleaseJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppearancePrefs.init(this)

        // Notification deep link. Always read the extra: process death is
        // exactly the case where savedInstanceState != null, and OpenBubblesApp
        // consumes the guid idempotently (it navigates only when the chat is
        // not already on the restored stack, then clears the static).
        readPendingIntent(intent)

        // Boot the Rust runtime (state dirs + keystore) before any UI can
        // provision or sign in — onboarding reaches Rust before the push
        // service ever starts.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { app.openbubbles.nativeapp.data.RustBoot.ensureStarted(this@NativeMainActivity, filesDir.absolutePath) }
                .onFailure { android.util.Log.e("RustBoot", "boot failed", it) }
        }

        // Permission priming moved into the onboarding flow; returning users
        // (or taps into the signed-in app) just get a contact re-sync.
        if (DeviceContacts.hasPermission(this)) {
            syncContacts()
        }

        // Smoke test: load the Cargo-built librust_lib_bluebubbles.so and call
        // through UniFFI.
        // Shown as a small status footer on the chat list.
        val rustStatus = try {
            uniffiEnsureInitialized()
            "uniffi ok — isLocked=${isLocked()}"
        } catch (t: Throwable) {
            "rust load failed: ${t.message}"
        }

        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                android.util.Log.i("OfficialEngineProbe", OfficialEngineProbe.probe().summary())
            }
        }

        debugLines = listOf(Hello.greeting(), rustStatus)
        resumeRoute = savedInstanceState?.getString(STATE_RESUME_ROUTE)
        renderUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        resumeRoute?.let { outState.putString(STATE_RESUME_ROUTE, it) }
    }

    private fun renderUi() {
        setContent {
            OpenBubblesTheme {
                OpenBubblesApp(
                    debugLines = debugLines,
                    startChatGuid = pendingChatGuid,
                    startComposeRequest = pendingComposeRequest,
                    onComposeRequestConsumed = { pendingComposeRequest = null },
                    resumeRoute = resumeRoute,
                    onRouteChanged = { resumeRoute = it },
                )
            }
        }
        uiDetached = false
    }

    override fun onStart() {
        super.onStart()
        uiReleaseJob?.cancel()
        uiReleaseJob = null
        if (uiDetached) renderUi()
        // Self-update: schedule the twice-daily check and run a throttled (1h)
        // on-open check. No-op without a stored GitHub token.
        app.openbubbles.nativeapp.update.UpdateCoordinator.maybeCheckOnAppOpen(this)
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        uiReleaseJob?.cancel()
        uiReleaseJob = lifecycleScope.launch {
            delay(BACKGROUND_UI_RELEASE_MS)
            if (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) {
                releaseHiddenUi()
            }
        }
    }

    /**
     * The foreground push engine keeps this process important. Drop UI-only
     * state after a grace period so that does not also pin Compose, ViewModels,
     * decoded images, and conversation pages for the whole screen-off period.
     */
    private fun releaseHiddenUi() {
        val content = findViewById<ViewGroup>(android.R.id.content)
        (content.getChildAt(0) as? ComposeView)?.disposeComposition()
        content.removeAllViews()
        viewModelStore.clear()
        app.openbubbles.nativeapp.data.MemoryCaches.clear()
        uiDetached = true
    }

    override fun onDestroy() {
        uiReleaseJob?.cancel()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // Notification tap while the activity is alive (launchMode=singleTask
        // routes it here instead of stacking a second instance).
        readPendingIntent(intent)
    }

    private fun readPendingIntent(intent: Intent?) {
        pendingChatGuid = intent?.getStringExtra(EXTRA_CHAT_GUID)?.takeIf { it.isNotBlank() }
        pendingComposeRequest = parseSmsComposeRequest(
            action = intent?.action,
            dataString = intent?.dataString,
            extraText = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        )
        // Cancel the notification immediately; the conversation's live
        // repository owns its initial 30-row load after navigation.
        pendingChatGuid?.let { guid ->
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                Notifications.cancelForChatGuid(this@NativeMainActivity, guid)
            }
        }
    }

    private fun syncContacts() {
        lifecycleScope.launch {
            val raw = DeviceContacts.read(this@NativeMainActivity)
            CoreGraph.syncContacts(raw)
        }
    }

    companion object {
        /** Deep-link extra carrying the tapped notification's chat guid. */
        const val EXTRA_CHAT_GUID = "chat_guid"

        /** Keeps quick app switches warm but releases UI during longer background periods. */
        internal const val BACKGROUND_UI_RELEASE_MS = 60_000L

        /** Survives fold / unfold activity recreation so the open chat is restored. */
        internal const val STATE_RESUME_ROUTE = "resume_route"

        /**
         * Chat guid requested by a notification tap, consumed once by
         * [OpenBubblesApp] (which resolves it, navigates, and nulls it).
         * Compose state, so an onNewIntent tap recomposes the scaffold.
         */
        var pendingChatGuid: String? by mutableStateOf<String?>(null)
    }
}

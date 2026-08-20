package app.openbubbles.nativeapp

import android.content.Intent
import android.os.Bundle
import android.net.Uri
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
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
import app.openbubbles.nativeapp.data.applySuccessfulSnapshot
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
import kotlinx.serialization.Serializable

data class SmsComposeRequest(
    val recipients: List<String>,
    val body: String?,
    val useSms: Boolean = true,
)

@Serializable
data class IncomingShareRequest(
    val text: String? = null,
    val streams: List<String> = emptyList(),
    val mimeType: String? = null,
)

internal fun parseIncomingShareRequest(
    action: String?,
    mimeType: String?,
    extraText: String?,
    streams: List<String>,
): IncomingShareRequest? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    val text = extraText?.take(20_000)?.takeIf { it.isNotBlank() }
    val safeStreams = streams.filter { it.startsWith("content://") || it.startsWith("file://") }.take(32)
    return IncomingShareRequest(text, safeStreams, mimeType?.take(200))
        .takeIf { it.text != null || it.streams.isNotEmpty() }
}

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

/**
 * The single navigation decision for a [NativeMainActivity] launch intent,
 * shared by onCreate (cold) and onNewIntent (warm) so both apply the same
 * precedence: a tapped notification beats a share payload, which beats an
 * sms/mms compose uri, which beats an explicit route request. A plain MAIN
 * launch is [OpenHome] (the messaging app), never [None], so a standalone
 * Passwords stack can hand the task back to messaging.
 */
sealed interface MainLaunchAction {
    data class OpenChat(val guid: String) : MainLaunchAction
    data class Share(val request: IncomingShareRequest) : MainLaunchAction
    data class Compose(val request: SmsComposeRequest) : MainLaunchAction
    data class OpenRoute(val route: String, val standaloneTask: Boolean) : MainLaunchAction
    data object OpenHome : MainLaunchAction
    data object None : MainLaunchAction
}

internal fun decideMainLaunchAction(
    action: String?,
    dataString: String?,
    mimeType: String?,
    extraText: String?,
    streams: List<String>,
    chatGuid: String?,
    initialRoute: String?,
    standaloneTask: Boolean,
): MainLaunchAction {
    chatGuid?.takeIf { it.isNotBlank() }?.let { return MainLaunchAction.OpenChat(it) }
    parseIncomingShareRequest(action, mimeType, extraText, streams)?.let { return MainLaunchAction.Share(it) }
    parseSmsComposeRequest(action, dataString, extraText)?.let { return MainLaunchAction.Compose(it) }
    initialRoute?.takeIf { it.isNotBlank() }?.let { return MainLaunchAction.OpenRoute(it, standaloneTask) }
    if (action == Intent.ACTION_MAIN) return MainLaunchAction.OpenHome
    return MainLaunchAction.None
}

class NativeMainActivity : FragmentActivity() {
    private var debugLines: List<String> by mutableStateOf(emptyList())
    private var resumeRoute: String? = null
    private var pendingComposeRequest: SmsComposeRequest? by mutableStateOf(null)
    private var pendingShareRequest: IncomingShareRequest? by mutableStateOf(null)
    private var pendingRouteRequest: MainLaunchAction.OpenRoute? by mutableStateOf(null)
    private var pendingHomeRequest: Boolean by mutableStateOf(false)
    private var standaloneTask: Boolean by mutableStateOf(false)
    private var uiDetached = false
    private var uiReleaseJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppearancePrefs.init(this)

        // Launch action (notification deep link, share, compose, route).
        // Always read the extras: process death is exactly the case where
        // savedInstanceState != null, and OpenBubblesApp consumes the guid
        // idempotently (it navigates only when the chat is not already on
        // the restored stack, then clears the static).
        readPendingIntent(intent)

        // Boot the Rust runtime (state dirs + keystore) before any UI can
        // provision or sign in — onboarding reaches Rust before the push
        // service ever starts. The UniFFI smoke test lives in the same
        // coroutine: loading the source-built .so and JNA proxy
        // is a first-frame stall when run on the main thread.
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { app.openbubbles.nativeapp.data.RustBoot.ensureStarted(this@NativeMainActivity, filesDir.absolutePath) }
                .onFailure { android.util.Log.e("RustBoot", "boot failed", it) }
            val rustStatus = try {
                uniffiEnsureInitialized()
                "uniffi ok — isLocked=${isLocked()}"
            } catch (t: Throwable) {
                "rust load failed: ${t.message}"
            }
            debugLines = listOf(Hello.greeting(), rustStatus)
        }

        // Permission priming moved into the onboarding flow; returning users
        // (or taps into the signed-in app) just get a contact re-sync.
        if (DeviceContacts.hasPermission(this)) {
            syncContacts()
        }

        debugLines = listOf(Hello.greeting())
        resumeRoute = savedInstanceState?.getString(STATE_RESUME_ROUTE)
        if (savedInstanceState != null) {
            // The launch intent is redelivered after process death, but the
            // saved route already reflects any navigation since; only a fresh
            // launch may re-request its initial route.
            pendingRouteRequest = null
            standaloneTask = savedInstanceState.getBoolean(STATE_STANDALONE, standaloneTask)
        }
        pendingShareRequest = savedInstanceState?.getStringArrayList(STATE_SHARE_STREAMS)?.let { streams ->
            IncomingShareRequest(
                text = savedInstanceState.getString(STATE_SHARE_TEXT),
                streams = streams,
                mimeType = savedInstanceState.getString(STATE_SHARE_MIME),
            )
        } ?: pendingShareRequest
        renderUi()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        resumeRoute?.let { outState.putString(STATE_RESUME_ROUTE, it) }
        outState.putBoolean(STATE_STANDALONE, standaloneTask)
        pendingShareRequest?.let { request ->
            outState.putString(STATE_SHARE_TEXT, request.text)
            outState.putStringArrayList(STATE_SHARE_STREAMS, ArrayList(request.streams))
            outState.putString(STATE_SHARE_MIME, request.mimeType)
        }
    }

    private fun renderUi() {
        setContent {
            OpenBubblesTheme {
                OpenBubblesApp(
                    debugLines = debugLines,
                    startChatGuid = pendingChatGuid,
                    startComposeRequest = pendingComposeRequest,
                    onComposeRequestConsumed = { pendingComposeRequest = null },
                    startShareRequest = pendingShareRequest,
                    onShareRequestConsumed = { pendingShareRequest = null },
                    startRouteRequest = pendingRouteRequest,
                    onRouteRequestConsumed = { pendingRouteRequest = null },
                    startHomeRequest = pendingHomeRequest,
                    onHomeRequestConsumed = { pendingHomeRequest = false },
                    standaloneTask = standaloneTask,
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
        @Suppress("DEPRECATION")
        val streams = when (intent?.action) {
            Intent.ACTION_SEND -> listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)?.toString())
            Intent.ACTION_SEND_MULTIPLE -> intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                .orEmpty().map(Uri::toString)
            else -> emptyList()
        }
        val launch = decideMainLaunchAction(
            action = intent?.action,
            dataString = intent?.dataString,
            mimeType = intent?.type,
            extraText = intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            streams = streams,
            chatGuid = intent?.getStringExtra(EXTRA_CHAT_GUID),
            initialRoute = intent?.getStringExtra(EXTRA_INITIAL_ROUTE),
            standaloneTask = intent?.getBooleanExtra(EXTRA_STANDALONE_TASK, false) ?: false,
        )
        pendingChatGuid = (launch as? MainLaunchAction.OpenChat)?.guid
        pendingShareRequest = (launch as? MainLaunchAction.Share)?.request
        pendingComposeRequest = (launch as? MainLaunchAction.Compose)?.request
        pendingRouteRequest = launch as? MainLaunchAction.OpenRoute
        if (pendingRouteRequest?.standaloneTask == true) standaloneTask = true
        // A main-icon tap in normal mode stays a no-op so the resumed route
        // survives; in standalone mode it must hand the task back to messaging.
        pendingHomeRequest = launch is MainLaunchAction.OpenHome && standaloneTask
        if (pendingHomeRequest) standaloneTask = false
        // Cancel the notification immediately; the conversation's live
        // repository owns its initial 30-row load after navigation.
        pendingChatGuid?.let { guid ->
            lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                Notifications.cancelForChatGuid(this@NativeMainActivity, guid)
            }
        }
    }

    private fun syncContacts() {
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DeviceContacts.read(this@NativeMainActivity).applySuccessfulSnapshot { snapshot ->
                CoreGraph.syncDeviceContacts(snapshot)
            }
        }
    }

    companion object {
        /** Deep-link extra carrying the tapped notification's chat guid. */
        const val EXTRA_CHAT_GUID = "chat_guid"

        /**
         * Deep-link extra carrying an initial [Routes] value (e.g. the
         * credential-provider settings shortcut or the standalone Passwords
         * launcher opening [Routes.PASSWORDS]). Applied on cold and warm
         * launches via [decideMainLaunchAction].
         */
        const val EXTRA_INITIAL_ROUTE = "initial_route"

        /**
         * With [EXTRA_INITIAL_ROUTE]: the launch behaves like its own app.
         * The route becomes the back-stack root and back exits the activity
         * instead of revealing the messaging surfaces underneath.
         */
        const val EXTRA_STANDALONE_TASK = "standalone_task"

        /** Keeps quick app switches warm but releases UI during longer background periods. */
        internal const val BACKGROUND_UI_RELEASE_MS = 60_000L

        /** Survives fold / unfold activity recreation so the open chat is restored. */
        internal const val STATE_RESUME_ROUTE = "resume_route"
        private const val STATE_STANDALONE = "standalone_task_state"
        private const val STATE_SHARE_TEXT = "share_text"
        private const val STATE_SHARE_STREAMS = "share_streams"
        private const val STATE_SHARE_MIME = "share_mime"

        /**
         * Chat guid requested by a notification tap, consumed once by
         * [OpenBubblesApp] (which resolves it, navigates, and nulls it).
         * Compose state, so an onNewIntent tap recomposes the scaffold.
         */
        var pendingChatGuid: String? by mutableStateOf<String?>(null)
    }
}

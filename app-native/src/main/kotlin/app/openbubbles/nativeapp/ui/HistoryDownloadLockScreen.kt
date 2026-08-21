package app.openbubbles.nativeapp.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openbubbles.core.sync.SyncPhase
import app.openbubbles.core.sync.SyncProgress
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.CloudSyncWiring
import app.openbubbles.nativeapp.data.InitialHistoryDownload
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.service.NativePushService
import kotlinx.coroutines.delay

private const val HISTORY_CONNECTION_WAIT_MS = 15_000L

/**
 * How many times a resumed backfill that ends while still pending is silently
 * restarted before the manual "Try again" affordance is surfaced. Covers
 * transient CloudKit/APS dips and runs that stop without finalizing.
 */
internal const val MAX_HISTORY_AUTO_RETRIES = 4

/** Growing backoff between auto-retries: 2s, 4s, 8s, 16s, capped at 30s. */
internal fun historyRetryBackoffMs(attempt: Int): Long =
    (2_000L shl attempt.coerceIn(0, 5)).coerceAtMost(30_000L)

/**
 * Human-readable line for the phase the backfill is in. Record counts alone
 * read as noise to someone who just wants to know whether to put the phone
 * down, so each phase says what is being fetched. A leg that walks deletions
 * or already-synced records advances the cursor without adding rows, so a
 * zero count is suppressed rather than shown as a stalled "0 so far".
 */
internal fun historyDownloadStatusLine(progress: SyncProgress?): String = when (progress?.phase) {
    null, SyncPhase.IDLE -> "Getting ready…"
    SyncPhase.CHECKING -> "Checking your iCloud account…"
    SyncPhase.CHATS -> "Downloading conversations" + soFar(progress.chatsDone)
    SyncPhase.MESSAGES -> "Downloading messages" + soFar(progress.messagesDone)
    SyncPhase.ATTACHMENTS -> "Downloading photos and files" + soFar(progress.attachmentsDone)
    SyncPhase.DONE -> "Finishing up…"
    SyncPhase.FAILED -> "Download stopped"
}

private fun soFar(count: ULong): String = if (count > 0u) " — $count so far" else "…"

/**
 * Full-screen, deliberately non-dismissible gate over the whole app while the
 * one-time iCloud history backfill runs.
 *
 * The app is locked rather than merely busy for two reasons: a half-imported
 * store shows a chat list that reorders under the user's fingers, and the
 * download is heavy enough that the user needs to be told the device will run
 * warm instead of discovering it. Notifications are withheld by
 * [InitialHistoryDownload] for the same window, and one completion
 * notification replaces them.
 */
@Composable
internal fun HistoryDownloadLockScreen(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = AppContext.current
    val pushState by PushStateHolder.stateFlow.collectAsStateWithLifecycle()
    val syncing by CloudSyncWiring.syncing.collectAsStateWithLifecycle()
    val summary by CloudSyncWiring.lastSummary.collectAsStateWithLifecycle()
    val progress by CloudSyncWiring.manager?.progress?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(null) }
    // Process-local on purpose: after process death there is no coordinator
    // job to wait for, so the restored lock must issue a cursor-resuming run.
    var startRequested by remember { mutableStateOf(false) }
    var connectionUnavailable by rememberSaveable { mutableStateOf(false) }
    var connectionAttempt by rememberSaveable { mutableStateOf(0) }
    // Consecutive silent restarts of a run that ended while still pending.
    var autoRetryAttempt by rememberSaveable { mutableStateOf(0) }

    LaunchedEffect(context, pushState, connectionAttempt) {
        connectionUnavailable = false
        if (context == null) return@LaunchedEffect
        delay(HISTORY_CONNECTION_WAIT_MS)
        if (PushStateHolder.state == null || CloudSyncWiring.manager == null) {
            connectionUnavailable = true
        }
    }

    // A resumed lock (process death or an activity restart mid-download) has
    // to drive the run itself; cursors are persisted, so it picks up where it
    // stopped. [startRequested] belongs to this pending run rather than to the
    // coordinator's process-global last summary, which may describe an older
    // pre-onboarding sync.
    LaunchedEffect(context, pushState, syncing, startRequested) {
        if (context == null || pushState == null || syncing || startRequested) return@LaunchedEffect
        // PushStateHolder is installed just before the CloudKit manager. Retry
        // that narrow handoff instead of latching a null-manager attempt.
        while (
            !startRequested &&
            !connectionUnavailable &&
            PushStateHolder.state != null &&
            !CloudSyncWiring.syncing.value
        ) {
            startRequested = CloudSyncWiring.startInitialHistorySync(context)
            if (!startRequested) delay(100)
        }
    }

    // A run that ends while the backfill is still pending — a transient
    // CloudKit/APS dip, or a run that stopped without finalizing — would
    // otherwise strand the user on a spinner with no progress and no retry
    // (the start effect above is single-shot per process). Watch the coordinator
    // for a genuine running→idle edge and re-arm with a growing backoff; after
    // MAX_HISTORY_AUTO_RETRIES quiet attempts, fall through to the manual retry
    // UI below. A successful run clears the pending flag and removes this screen.
    LaunchedEffect(context) {
        if (context == null) return@LaunchedEffect
        var wasSyncing = false
        CloudSyncWiring.syncing.collect { running ->
            val justEnded = wasSyncing && !running
            wasSyncing = running
            if (!justEnded || !startRequested) return@collect
            if (
                !InitialHistoryDownload.isPending(context) ||
                connectionUnavailable ||
                autoRetryAttempt >= MAX_HISTORY_AUTO_RETRIES
            ) {
                return@collect
            }
            val attempt = autoRetryAttempt
            autoRetryAttempt = attempt + 1
            delay(historyRetryBackoffMs(attempt))
            if (
                InitialHistoryDownload.isPending(context) &&
                PushStateHolder.state != null &&
                !CloudSyncWiring.syncing.value
            ) {
                startRequested = false // re-triggers the start effect
            }
        }
    }

    // Back must not slip past the gate; leaving is an explicit choice below.
    BackHandler(enabled = true) { }

    val syncFailure = summary?.takeIf { it.error != null || it.cancelled }
    val autoRetriesExhausted = autoRetryAttempt >= MAX_HISTORY_AUTO_RETRIES
    val failureMessage = when {
        connectionUnavailable -> "OpenGarden couldn't reconnect to your Apple account. " +
            "Try again, or skip this download and repair the connection from Settings."
        // While silent retries remain, keep showing progress instead of an
        // error the app is already recovering from on its own. Once they are
        // spent, always surface the manual affordance — even if the run ended
        // without a specific error — so it can never sit on a frozen spinner.
        autoRetriesExhausted -> syncFailure?.error
            ?: "The download keeps stopping before it finishes. " +
            "Try again, or skip it and continue from Settings."
        else -> null
    }
    HistoryDownloadLockContent(
        statusLine = historyDownloadStatusLine(progress),
        failureMessage = failureMessage,
        onRetry = {
            connectionUnavailable = false
            startRequested = false
            autoRetryAttempt = 0
            connectionAttempt += 1
            context?.let(NativePushService::reloadAfterLogin)
        },
        onDismiss = onDismiss,
        modifier = modifier,
    )
}

/**
 * The gate's rendering, with the sync state passed in so each state can be
 * rendered (and screenshot tested) without a live iCloud account.
 */
@Composable
internal fun HistoryDownloadLockContent(
    statusLine: String,
    failureMessage: String?,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            if (failureMessage == null) {
                ContainedLoadingIndicator(modifier = Modifier.size(96.dp))
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = if (failureMessage == null) {
                    "Downloading your messages"
                } else {
                    "Download didn't finish"
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = failureMessage
                    ?: "This runs once. Leave OpenGarden open — it'll unlock itself " +
                    "and let you know the moment your conversations are ready.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (failureMessage == null) {
                Spacer(Modifier.height(28.dp))
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(14.dp))
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.titleSmall,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(36.dp))
                LockNote(
                    icon = Icons.Filled.DeviceThermostat,
                    text = "Your phone may get warm and drain faster while this runs. " +
                        "That's expected, and it stops when the download does.",
                )
                Spacer(Modifier.height(12.dp))
                LockNote(
                    icon = Icons.Filled.NotificationsOff,
                    text = "Message notifications are paused so a decade of old " +
                        "conversations doesn't fill your notification shade.",
                )
            } else {
                Spacer(Modifier.height(32.dp))
                Button(
                    onClick = onRetry,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                ) {
                    Text("Try again", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(4.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Skip and use OpenGarden") }
                Text(
                    text = "You can restart the download any time from " +
                        "Settings → iCloud.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LockNote(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

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

/**
 * Human-readable line for the phase the backfill is in. Record counts alone
 * read as noise to someone who just wants to know whether to put the phone
 * down, so each phase says what is being fetched.
 */
internal fun historyDownloadStatusLine(progress: SyncProgress?): String = when (progress?.phase) {
    null, SyncPhase.IDLE -> "Getting ready…"
    SyncPhase.CHECKING -> "Checking your iCloud account…"
    SyncPhase.CHATS -> "Downloading conversations — ${progress.chatsDone} so far"
    SyncPhase.MESSAGES -> "Downloading messages — ${progress.messagesDone} so far"
    SyncPhase.ATTACHMENTS -> "Downloading photos and files — ${progress.attachmentsDone} so far"
    SyncPhase.DONE -> "Finishing up…"
    SyncPhase.FAILED -> "Download stopped"
}

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
    var retryRequested by remember { mutableStateOf(false) }

    // A resumed lock (process death or an activity restart mid-download) has
    // to drive the run itself; cursors are persisted, so it picks up where it
    // stopped. The already-finished summary is what stops this from looping
    // on a permanent failure: only an explicit retry starts another run.
    LaunchedEffect(context, pushState, syncing, summary, retryRequested) {
        if (context == null || pushState == null || syncing) return@LaunchedEffect
        if (summary != null && !retryRequested) return@LaunchedEffect
        retryRequested = false
        CloudSyncWiring.startInitialHistorySync(context)
    }

    // Back must not slip past the gate; leaving is an explicit choice below.
    BackHandler(enabled = true) { }

    val failure = summary?.takeIf { it.error != null || it.cancelled }
    HistoryDownloadLockContent(
        statusLine = historyDownloadStatusLine(progress),
        failureMessage = failure?.let {
            it.error ?: "The download was stopped before it finished."
        },
        onRetry = { retryRequested = true },
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
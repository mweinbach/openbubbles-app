package app.openbubbles.nativeapp.service

import android.content.Context
import android.content.Intent
import androidx.core.content.edit
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Battery saver: drop the 24/7 APNs connection and instead poll iCloud for
 * new messages on WorkManager's 15-minute cadence (the platform minimum).
 * Messages can be delayed up to ~15 minutes; live mode is one toggle away.
 *
 * Trade-off rationale: the persistent connection pings Apple every 60s
 * (~1.4k radio wakeups/day). Poll mode boots the Rust state briefly per
 * interval, runs one incremental CloudKit sync, notifies what's new, and
 * tears down.
 */
object BatterySaver {
    const val ACTION_POLL_ONCE = "app.openbubbles.nativeapp.action.POLL_ONCE"
    internal const val EXTRA_POLL_REQUEST_ID = "app.openbubbles.nativeapp.extra.POLL_REQUEST_ID"
    internal const val POLL_COMPLETION_TIMEOUT_MS = 5 * 60 * 1_000L
    private const val PREFS = "native_setup"
    private const val KEY_ENABLED = "battery_saver"
    private const val WORK_NAME = "openbubbles-poll"
    private const val RESTART_WORK_NAME = "openbubbles-push-restart"
    private val pollRuns = BatterySaverPollRuns()

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /** @return the new enabled state */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) {
            // Arm durable recovery before dropping the only live connection.
            schedule(context)
            context.stopService(Intent(context, NativePushService::class.java))
        } else {
            cancelAccountWork(context)
            NativePushService.reloadAfterLogin(context)
        }
        return enabled
    }

    /**
     * Sign-out owns active polling/restart work, but the power preference is
     * device-wide and survives signing in to the next Apple account.
     */
    fun cancelAccountWork(context: Context) {
        WorkManager.getInstance(context.applicationContext).apply {
            cancelUniqueWork(WORK_NAME).result.get()
            cancelUniqueWork(RESTART_WORK_NAME).result.get()
        }
        pollRuns.cancelAll()
    }

    internal fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    internal fun schedulePushRestart(context: Context) {
        val request = OneTimeWorkRequestBuilder<PushRestartWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            RESTART_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    internal fun beginPoll(): BatterySaverPollRun = pollRuns.begin()

    internal fun completePoll(requestId: Long?, success: Boolean) {
        if (requestId != null) pollRuns.complete(requestId, success)
    }

    internal fun abandonPoll(requestId: Long) {
        pollRuns.abandon(requestId)
    }
}

internal data class BatterySaverPollRun(
    val requestId: Long,
    val completion: CompletableDeferred<Boolean>,
)

/** Couples a WorkManager run to the actual account-scoped CloudKit pass. */
internal class BatterySaverPollRuns {
    private val nextRequestId = AtomicLong()
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Boolean>>()

    fun begin(): BatterySaverPollRun {
        val requestId = nextRequestId.incrementAndGet()
        val completion = CompletableDeferred<Boolean>()
        pending[requestId] = completion
        return BatterySaverPollRun(requestId, completion)
    }

    fun complete(requestId: Long, success: Boolean): Boolean =
        pending.remove(requestId)?.complete(success) ?: false

    fun abandon(requestId: Long) {
        pending.remove(requestId)?.cancel()
    }

    fun cancelAll() {
        pending.keys.toList().forEach { complete(it, false) }
    }
}

/** Retries an OEM- or platform-deferred foreground-service start safely. */
class PushRestartWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!uniffi.rust_lib_bluebubbles.hasSavedUsers(context.filesDir.absolutePath)) {
            return Result.success()
        }
        if (BatterySaver.isEnabled(context)) {
            BatterySaver.schedule(context)
            return Result.success()
        }
        return if (NativePushService.start(context)) Result.success() else Result.retry()
    }
}

/**
 * Boots the push service in poll mode. The service performs one CloudKit
 * incremental sync, notifies newly-unread chats, and stops itself; the next
 * periodic run repeats. Starting a foreground service from the background is
 * restricted on Android 12+ — users of this mode have almost always granted
 * the battery-optimization exemption (we prompt at first login), which also
 * permits this; on failure the next period retries.
 */
class PollWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        if (!BatterySaver.isEnabled(context)) return Result.success()
        if (!uniffi.rust_lib_bluebubbles.hasSavedUsers(context.filesDir.absolutePath)) {
            return Result.success()
        }
        val run = BatterySaver.beginPoll()
        return try {
            if (!NativePushService.startPoll(context, run.requestId)) return Result.retry()
            val completed = withTimeoutOrNull(BatterySaver.POLL_COMPLETION_TIMEOUT_MS) {
                run.completion.await()
            } ?: false
            if (completed) Result.success() else Result.retry()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        } finally {
            BatterySaver.abandonPoll(run.requestId)
        }
    }
}

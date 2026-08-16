package app.openbubbles.nativeapp.service

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

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
    private const val PREFS = "native_setup"
    private const val KEY_ENABLED = "battery_saver"
    private const val WORK_NAME = "openbubbles-poll"
    private const val RESTART_WORK_NAME = "openbubbles-push-restart"

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /** @return the new enabled state */
    fun setEnabled(context: Context, enabled: Boolean): Boolean {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            // Stop the live connection; the worker owns wakeups now.
            context.stopService(Intent(context, NativePushService::class.java))
            schedule(context)
        } else {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            NativePushService.reloadAfterLogin(context)
        }
        return enabled
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
        return try {
            context.startForegroundService(
                Intent(context, NativePushService::class.java)
                    .setAction(BatterySaver.ACTION_POLL_ONCE),
            )
            Result.success()
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

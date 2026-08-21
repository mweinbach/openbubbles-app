package app.openbubbles.nativeapp.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.R
import app.openbubbles.nativeapp.telemetry.AppTelemetry
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext

/**
 * Orchestrates the self-update pipeline, mirroring the style of
 * `service.BatterySaver`: a plain object the worker, the settings UI, and the
 * install-result receiver all call into.
 *
 *   feed -> decision -> verified download -> "Update ready" notification
 *          -> (tap) install gate -> PackageInstaller commit
 */
object UpdateCoordinator {
    private const val TAG = "SelfUpdate"
    const val CHANNEL_UPDATES = "updates"
    private const val NOTIFICATION_ID = 4101
    private const val WORK_NAME = "openbubbles-update-check"
    private const val APP_OPEN_THROTTLE_MS = 60L * 60 * 1000 // 1h

    // Fired on install success or failure so UpdateInstallActivity can close.
    private val installFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val installFinishedEvents: SharedFlow<Unit> = installFinished.asSharedFlow()

    /** Result of a manual or background check, for the Settings UI. */
    sealed interface CheckResult {
        /** Check completed; the decision says what (if anything) happened. */
        data class Done(val decision: UpdateDecision, val downloaded: Boolean) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    /** Twice-daily background check — same pattern as `BatterySaver.schedule`. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()
        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                workName(),
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }

    /** App-open path: throttled, silent, never disturbs the user. */
    fun maybeCheckOnAppOpen(context: Context) {
        schedule(context)
        val last = UpdateSettings.lastCheckMs(context)
        if (System.currentTimeMillis() - last < APP_OPEN_THROTTLE_MS) return
        enqueueImmediateCheck(context, trigger = "app_open", replace = false)
    }

    /** FCM and app-open checks share one network-constrained, expedited lane. */
    fun enqueueImmediateCheck(context: Context, trigger: String, replace: Boolean) {
        runCatching {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    workName() + "-now",
                    if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                        .setInputData(workDataOf(UpdateCheckWorker.INPUT_TRIGGER to trigger))
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                        .build(),
                )
        }
    }

    suspend fun checkNow(context: Context, trigger: String = "manual"): CheckResult = withContext(Dispatchers.IO) {
        val installedCode = installedVersionCode(context)
        val feed = try {
            UpdateLedgerSource().fetch(installedCode)
        } catch (ledgerError: UpdateLedgerSource.SourceException) {
            Log.w(TAG, "Update Ledger check failed", ledgerError)
            AppTelemetry.event(context, "update_check", mapOf("trigger" to trigger, "result" to "failed"))
            AppTelemetry.nonFatal("update_check", ledgerError.javaClass.simpleName)
            return@withContext CheckResult.Failed(
                ledgerError.message ?: "update check failed",
            )
        }
        UpdateSettings.recordCheck(context)
        if (feed == null) {
            AppTelemetry.event(context, "update_check", mapOf("trigger" to trigger, "result" to "current"))
            return@withContext CheckResult.Done(UpdateDecision.UpToDate, false)
        }

        val decision = UpdateDecision.evaluate(
            installedCode = installedCode,
            manifest = feed.manifest,
            deferredCode = UpdateSettings.deferredVersionCode(context),
            highestSeenCode = UpdateSettings.highestSeenVersionCode(context),
        )
        when (decision) {
            is UpdateDecision.Available, is UpdateDecision.Mandatory -> {
                val manifest = feed.manifest
                UpdateSettings.recordSeenVersionCode(context, manifest.versionCode)
                val downloaded = try {
                    downloadVerified(context, feed)
                } catch (e: UpdateDownloader.DownloadException) {
                    Log.w(TAG, "download failed: ${e.message}")
                    AppTelemetry.event(context, "update_download", mapOf("result" to "failed"))
                    AppTelemetry.nonFatal("update_download", e.javaClass.simpleName)
                    return@withContext CheckResult.Failed(e.message ?: "download failed")
                }
                if (downloaded) {
                    UpdateSettings.clearDeferred(context)
                    postUpdateReadyNotification(context, manifest)
                    AppTelemetry.state("pending_update_build", manifest.versionCode.toString())
                    AppTelemetry.event(
                        context,
                        "update_download",
                        mapOf("result" to "ready", "version" to manifest.versionName),
                    )
                }
                AppTelemetry.event(
                    context,
                    "update_check",
                    mapOf("trigger" to trigger, "result" to decisionName(decision)),
                )
                CheckResult.Done(decision, downloaded)
            }
            is UpdateDecision.UpToDate, is UpdateDecision.RollbackBlocked,
            is UpdateDecision.Deferred,
            -> {
                AppTelemetry.event(
                    context,
                    "update_check",
                    mapOf("trigger" to trigger, "result" to decisionName(decision)),
                )
                CheckResult.Done(decision, false)
            }
        }
    }

    /**
     * Downloads and verifies the APK unless the exact version is already on
     * disk. @return true when a usable APK is present afterwards.
     */
    private fun downloadVerified(context: Context, feed: UpdateFeed): Boolean {
        val dir = UpdateDownloader.updatesDir(context.cacheDir)
        val target = UpdateDownloader.apkFileFor(dir, feed.manifest.versionCode)
        val downloader = UpdateDownloader(
            client = UpdateLedgerSource.defaultClient(),
        )
        if (!target.isFile) {
            downloader.download(feed, dir)
        }
        UpdateDownloader.purgeStale(dir, feed.manifest.versionCode)
        UpdateSettings.recordPending(context, feed.manifest)
        return true
    }

    // ------------------------------------------------------------------
    // Install path (notification tap / Settings button)
    // ------------------------------------------------------------------

    /**
     * Installs the pending verified update. Returns the outcome so callers
     * (Settings row, receiver) can react; never throws.
     */
    fun installNow(context: Context): InstallNowResult {
        val code = UpdateSettings.pendingVersionCode(context)
        if (code <= 0L) return InstallNowResult.NothingPending
        val dir = UpdateDownloader.updatesDir(context.cacheDir)
        val apk = UpdateDownloader.apkFileFor(dir, code)
        if (!apk.isFile) {
            UpdateSettings.clearPending(context)
            return InstallNowResult.NothingPending
        }
        if (!ApkInstaller.canInstall(context)) {
            return InstallNowResult.NeedsUnknownSourcesPermission
        }
        val manifest = UpdateManifest(
            versionCode = code,
            versionName = UpdateSettings.pendingVersionName(context) ?: code.toString(),
            apkAsset = apk.name,
            sha256 = "",
        )
        return try {
            ApkInstaller.install(context, apk, manifest)
            cancelNotification(context)
            InstallNowResult.Installing
        } catch (e: SecurityException) {
            InstallNowResult.Failed(e.message ?: "signature check failed")
        } catch (e: Exception) {
            Log.w(TAG, "install failed", e)
            InstallNowResult.Failed(e.message ?: "install failed")
        }
    }

    sealed interface InstallNowResult {
        data object NothingPending : InstallNowResult
        data object NeedsUnknownSourcesPermission : InstallNowResult
        data object Installing : InstallNowResult
        data class Failed(val message: String) : InstallNowResult
    }

    /**
     * Settings helper: the pending update when its verified APK still exists
     * and the user has not skipped exactly this version. (Skipping only sets
     * the deferral — the download stays — so the UI must honor it here or the
     * install prompt would reappear right after "Skip this version".)
     */
    fun pendingUpdate(context: Context): PendingUpdate? {
        val code = UpdateSettings.pendingVersionCode(context)
        if (code <= 0L) return null
        if (UpdateSettings.deferredVersionCode(context) == code) return null
        val dir = UpdateDownloader.updatesDir(context.cacheDir)
        if (!UpdateDownloader.apkFileFor(dir, code).isFile) return null
        return PendingUpdate(
            versionCode = code,
            versionName = UpdateSettings.pendingVersionName(context) ?: code.toString(),
            notes = UpdateSettings.pendingNotes(context),
        )
    }

    data class PendingUpdate(val versionCode: Long, val versionName: String, val notes: String?)

    // ------------------------------------------------------------------
    // PackageInstaller result callbacks (UpdateInstallReceiver)
    // ------------------------------------------------------------------

    fun onInstallSucceeded(context: Context) {
        cancelNotification(context)
        UpdateDownloader.updatesDir(context.cacheDir).deleteRecursively()
        UpdateSettings.clearPending(context)
        Log.i(TAG, "self-update installed")
        AppTelemetry.state("pending_update_build", "none")
        AppTelemetry.event(context, "update_install", mapOf("result" to "success"))
        installFinished.tryEmit(Unit)
    }

    fun onInstallFailed(context: Context, status: Int, message: String?) {
        cancelNotification(context)
        Log.w(TAG, "self-update failed: status=$status message=$message")
        AppTelemetry.event(
            context,
            "update_install",
            mapOf("result" to "failed", "status" to status.toString()),
        )
        AppTelemetry.nonFatal("update_install", "status_$status")
        notifyStatus(context, "Update not installed", message ?: "Installation failed (code $status)")
        installFinished.tryEmit(Unit)
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    /** Immediate FCM acknowledgement; the verified-download notification replaces it. */
    fun notifyUpdateAvailable(context: Context, versionName: String) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, NativeMainActivity::class.java)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.mipmap.ic_stat_icon)
            .setContentTitle("OpenGarden $versionName is available")
            .setContentText("Preparing the verified update from Update Ledger.")
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "update availability notification rejected", e)
        }
    }

    /** Collapsed line: "Tap to update — <first changelog line>", markup-free. */
    private fun updateReadyContentText(notes: String): String {
        val summary = changelogSummary(notes)
        return if (summary.isBlank()) "Tap to update" else "Tap to update — $summary"
    }

    private fun postUpdateReadyNotification(context: Context, manifest: UpdateManifest) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        // An activity, not a receiver: Android 12+ drops activity starts
        // relayed through notification-tapped receivers ("trampolines"), and
        // the PackageInstaller confirmation needs a visible app anyway.
        val install = PendingIntent.getActivity(
            context,
            0,
            Intent(context, UpdateInstallActivity::class.java)
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.mipmap.ic_stat_icon)
            .setContentTitle("OpenGarden ${manifest.versionName} ready to install")
            .setContentText(updateReadyContentText(manifest.notes))
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    changelogNotificationText(manifest.notes).ifBlank { "Tap to install the update." },
                ),
            )
            .setContentIntent(install)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            nm.notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS can be revoked between the enabled check
            // and the post; the update stays available from Settings.
            Log.w(TAG, "update notification rejected", e)
        }
    }

    private fun notifyStatus(context: Context, title: String, text: String) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.mipmap.ic_stat_icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(NOTIFICATION_ID + 1, notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "status notification rejected", e)
        }
    }

    private fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_UPDATES,
                "Updates",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "App update availability" },
        )
    }

    fun installedVersionCode(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).let {
            if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode
            else @Suppress("DEPRECATION") it.versionCode.toLong()
        }

    internal fun workName(): String = WORK_NAME

    private fun decisionName(decision: UpdateDecision): String = when (decision) {
        is UpdateDecision.Available -> "available"
        is UpdateDecision.Mandatory -> "mandatory"
        is UpdateDecision.UpToDate -> "current"
        is UpdateDecision.RollbackBlocked -> "rollback_blocked"
        is UpdateDecision.Deferred -> "deferred"
    }
}

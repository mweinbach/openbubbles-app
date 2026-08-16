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
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.openbubbles.nativeapp.R
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
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
    const val ACTION_INSTALL_NOW = "app.openbubbles.nativeapp.action.UPDATE_INSTALL_NOW"
    private const val APP_OPEN_THROTTLE_MS = 6L * 60 * 60 * 1000 // 6h

    /** Result of a manual or background check, for the Settings UI. */
    sealed interface CheckResult {
        /** Check completed; the decision says what (if anything) happened. */
        data class Done(val decision: UpdateDecision, val downloaded: Boolean) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    /** Daily background check — same pattern as `BatterySaver.schedule`. */
    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(1, TimeUnit.DAYS)
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
        runCatching {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(
                    workName() + "-now",
                    ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<UpdateCheckWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build(),
                        )
                        .build(),
                )
        }
    }

    suspend fun checkNow(context: Context): CheckResult = withContext(Dispatchers.IO) {
        val source = GitHubUpdateSource(
            token = { UpdateSettings.githubToken(context) },
        )
        val feed = try {
            source.fetch()
        } catch (e: GitHubUpdateSource.SourceException.NoReleases) {
            UpdateSettings.recordCheck(context)
            return@withContext CheckResult.Done(UpdateDecision.UpToDate, false)
        } catch (e: GitHubUpdateSource.SourceException) {
            return@withContext CheckResult.Failed(e.message ?: "update check failed")
        }
        UpdateSettings.recordCheck(context)

        val installedCode = installedVersionCode(context)
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
                    return@withContext CheckResult.Failed(e.message ?: "download failed")
                }
                if (downloaded) {
                    UpdateSettings.clearDeferred(context)
                    postUpdateReadyNotification(context, manifest)
                }
                CheckResult.Done(decision, downloaded)
            }
            is UpdateDecision.UpToDate, is UpdateDecision.RollbackBlocked,
            is UpdateDecision.Deferred,
            -> CheckResult.Done(decision, false)
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
            client = GitHubUpdateSource.defaultClient(),
            token = { UpdateSettings.githubToken(context) },
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

    /** Settings helper: the pending update when its verified APK still exists. */
    fun pendingUpdate(context: Context): PendingUpdate? {
        val code = UpdateSettings.pendingVersionCode(context)
        if (code <= 0L) return null
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
    }

    fun onInstallFailed(context: Context, status: Int, message: String?) {
        cancelNotification(context)
        Log.w(TAG, "self-update failed: status=$status message=$message")
        notifyStatus(context, "Update not installed", message ?: "Installation failed (code $status)")
    }

    // ------------------------------------------------------------------
    // Notifications
    // ------------------------------------------------------------------

    private fun postUpdateReadyNotification(context: Context, manifest: UpdateManifest) {
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val install = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, UpdateInstallReceiver::class.java)
                .setAction(ACTION_INSTALL_NOW)
                .setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.mipmap.ic_stat_icon)
            .setContentTitle("OpenBubbles ${manifest.versionName} ready to install")
            .setContentText("Tap to update${manifest.notes.take(80).let { if (it.isBlank()) "" else " — $it" }}")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    manifest.notes.take(1000).ifBlank { "Tap to install the update." },
                ),
            )
            .setContentIntent(install)
            .setAutoCancel(true)
            .setOngoing(false)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
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
        runCatching { nm.notify(NOTIFICATION_ID + 1, notification) }
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
}

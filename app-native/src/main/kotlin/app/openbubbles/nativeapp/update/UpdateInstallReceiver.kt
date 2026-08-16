package app.openbubbles.nativeapp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

/**
 * Terminal station of a PackageInstaller commit. Registered in the manifest,
 * never exported: only the system (holding our PendingIntent) can reach it.
 *
 * - `STATUS_PENDING_USER_ACTION`: the system wants explicit confirmation
 *   (always true for the very first self-update). Launch the confirmation
 *   activity it hands us.
 * - `STATUS_SUCCESS`: the new version is already live in place of this
 *   process; clean downloaded artifacts and cancel the prompt notification.
 *   The running process keeps serving until killed; Android restarts us via
 *   `MY_PACKAGE_REPLACED` (existing BootReceiver resumes the push service).
 * - failures: surface the status message; keep the verified APK so the user
 *   can retry without re-downloading.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // "Update ready" notification tap (or Settings install button
            // relaying through here). Streaming a multi-hundred-megabyte APK
            // into the installer session can outlast the ~10s broadcast
            // window, so hold the receiver open and work off the main thread.
            UpdateCoordinator.ACTION_INSTALL_NOW -> {
                val pendingResult = goAsync()
                executor.execute {
                    try {
                        when (UpdateCoordinator.installNow(context)) {
                            UpdateCoordinator.InstallNowResult.NeedsUnknownSourcesPermission ->
                                runCatching {
                                    context.startActivity(
                                        ApkInstaller.unknownSourcesIntent(context)
                                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            else -> Unit
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ApkInstaller.ACTION_INSTALL_RESULT -> handleInstallResult(context, intent)
        }
    }

    private fun handleInstallResult(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = intent.extraIntent()
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                UpdateCoordinator.onInstallSucceeded(context)
            }
            else -> {
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                UpdateCoordinator.onInstallFailed(context, status, message)
            }
        }
    }

    private fun Intent.extraIntent(): Intent? =
        if (Build.VERSION.SDK_INT >= 33) {
            getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(Intent.EXTRA_INTENT)
        }

    private companion object {
        // Serialized: at most one install pipeline should ever be running.
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    }
}

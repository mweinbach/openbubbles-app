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
 *   activity it hands us — [UpdateInstallActivity] is still in the
 *   foreground at this point, so the start is permitted.
 * - `STATUS_SUCCESS`: the new version is already live in place of this
 *   process; clean downloaded artifacts and cancel the prompt notification.
 *   The running process keeps serving until killed; Android restarts us via
 *   `MY_PACKAGE_REPLACED` (existing BootReceiver resumes the push service).
 * - failures: surface the status message; keep the verified APK so the user
 *   can retry without re-downloading.
 */
class UpdateInstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ApkInstaller.ACTION_INSTALL_RESULT) return
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
}

package app.openbubbles.nativeapp.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Target of the "Remind Me Later" action on the "Update ready" notification:
 * silences the push for [REMIND_LATER_DELAY_MS] and dismisses the
 * notification. Unlike "Skip this version" nothing is deferred — the update
 * stays installable from Settings, and the next check after the snooze (or a
 * newer release) posts the reminder again.
 *
 * A broadcast is safe here — unlike the install tap, nothing user-visible
 * must be launched, so the Android 12+ notification-trampoline restriction
 * does not apply. Registered in the manifest, never exported: only our own
 * notification holds the PendingIntent targeting it.
 */
class UpdateRemindLaterReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val code = UpdateSettings.pendingVersionCode(context)
        if (code > 0L) {
            UpdateSettings.snoozeReminder(
                context,
                code,
                System.currentTimeMillis() + REMIND_LATER_DELAY_MS,
            )
        }
        UpdateCoordinator.cancelNotification(context)
    }

    companion object {
        const val ACTION = "app.openbubbles.nativeapp.action.UPDATE_REMIND_LATER"

        /** How long "Remind Me Later" keeps the ready-push silent. */
        private const val REMIND_LATER_DELAY_MS = 24L * 60 * 60 * 1000 // 24h
    }
}

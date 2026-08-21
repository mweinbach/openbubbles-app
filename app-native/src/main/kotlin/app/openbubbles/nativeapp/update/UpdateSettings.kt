package app.openbubbles.nativeapp.update

import android.content.Context
import androidx.core.content.edit

/**
 * Update-subsystem preferences: check bookkeeping, deferral, rollback floor,
 * and the downloaded release pending installation. The public Update Ledger
 * feed does not require a client credential.
 */
object UpdateSettings {
    private const val PREFS = "native_update"
    private const val KEY_LAST_CHECK = "last_check_ms"
    private const val KEY_DEFERRED = "deferred_version_code"
    private const val KEY_HIGHEST_SEEN = "highest_seen_version_code"
    private const val KEY_PENDING_CODE = "pending_version_code"
    private const val KEY_PENDING_NAME = "pending_version_name"
    private const val KEY_PENDING_NOTES = "pending_version_notes"
    private const val KEY_SNOOZED_CODE = "reminder_snoozed_code"
    private const val KEY_SNOOZED_UNTIL = "reminder_snoozed_until_ms"

    fun lastCheckMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CHECK, 0L)

    fun recordCheck(context: Context, atMs: Long = System.currentTimeMillis()) {
        prefs(context).edit { putLong(KEY_LAST_CHECK, atMs) }
    }

    fun deferredVersionCode(context: Context): Long =
        prefs(context).getLong(KEY_DEFERRED, 0L)

    fun deferVersionCode(context: Context, versionCode: Long) {
        prefs(context).edit { putLong(KEY_DEFERRED, versionCode) }
    }

    /** Local rollback floor: the highest versionCode this device has been offered. */
    fun highestSeenVersionCode(context: Context): Long =
        prefs(context).getLong(KEY_HIGHEST_SEEN, 0L)

    fun recordSeenVersionCode(context: Context, versionCode: Long) {
        prefs(context).edit {
            putLong(KEY_HIGHEST_SEEN, maxOf(versionCode, highestSeenVersionCode(context)))
        }
    }

    fun clearDeferred(context: Context) {
        prefs(context).edit { remove(KEY_DEFERRED) }
    }

    // ------------------------------------------------------------------
    // "Remind me later" snooze on the ready-notification push
    // ------------------------------------------------------------------

    /**
     * Until-ms the given version's ready-push is snoozed through; 0 when no
     * snooze is active. Keyed to the versionCode so a newer release notifies
     * immediately instead of inheriting an older snooze.
     */
    fun reminderSnoozedUntilMs(context: Context, versionCode: Long): Long =
        prefs(context).let {
            if (it.getLong(KEY_SNOOZED_CODE, 0L) != versionCode) 0L
            else it.getLong(KEY_SNOOZED_UNTIL, 0L)
        }

    fun snoozeReminder(context: Context, versionCode: Long, untilMs: Long) {
        prefs(context).edit {
            putLong(KEY_SNOOZED_CODE, versionCode)
            putLong(KEY_SNOOZED_UNTIL, untilMs)
        }
    }

    // ------------------------------------------------------------------
    // Pending (downloaded, verified, not yet installed) update
    // ------------------------------------------------------------------

    fun recordPending(context: Context, manifest: UpdateManifest) {
        prefs(context).edit {
            putLong(KEY_PENDING_CODE, manifest.versionCode)
            putString(KEY_PENDING_NAME, manifest.versionName)
            putString(KEY_PENDING_NOTES, manifest.notes)
        }
    }

    fun clearPending(context: Context) {
        prefs(context).edit {
            remove(KEY_PENDING_CODE)
            remove(KEY_PENDING_NAME)
            remove(KEY_PENDING_NOTES)
        }
    }

    /** The last downloaded-and-verified update, or null when none recorded. */
    fun pendingVersionCode(context: Context): Long =
        prefs(context).getLong(KEY_PENDING_CODE, 0L)

    fun pendingVersionName(context: Context): String? =
        prefs(context).getString(KEY_PENDING_NAME, null)

    fun pendingNotes(context: Context): String? =
        prefs(context).getString(KEY_PENDING_NOTES, null)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

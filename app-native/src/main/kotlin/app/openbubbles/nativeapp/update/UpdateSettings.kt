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
    private const val KEY_HIGHEST_VERIFIED = "highest_verified_version_code"
    private const val KEY_PENDING_CODE = "pending_version_code"
    private const val KEY_PENDING_NAME = "pending_version_name"
    private const val KEY_PENDING_NOTES = "pending_version_notes"
    private const val KEY_PENDING_SHA256 = "pending_version_sha256"
    private const val KEY_PENDING_BYTES = "pending_version_bytes"
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

    internal fun hasVerifiedRollbackFloor(context: Context): Boolean =
        prefs(context).contains(KEY_HIGHEST_VERIFIED)

    /**
     * The rollback floor may contain only an installed or authenticated build.
     *
     * Older versions advanced [KEY_HIGHEST_SEEN] from an unauthenticated
     * advertisement, so that value must never become the new trusted floor.
     * Removing it and establishing the replacement is one synchronous write.
     */
    @Synchronized
    fun highestVerifiedVersionCode(
        context: Context,
        installedVersionCode: Long,
        authenticatedPendingVersionCode: Long = 0L,
    ): Long {
        val preferences = prefs(context)
        val storedVerifiedCode = preferences.getLong(KEY_HIGHEST_VERIFIED, 0L)
        val floor = trustedRollbackFloor(
            RollbackFloorEvidence(
                installedVersionCode = installedVersionCode,
                legacyAdvertisedVersionCode = preferences.getLong(KEY_HIGHEST_SEEN, 0L),
                verifiedVersionCode = storedVerifiedCode,
                authenticatedPendingVersionCode = authenticatedPendingVersionCode,
            ),
        )
        if (!preferences.contains(KEY_HIGHEST_VERIFIED) ||
            floor != storedVerifiedCode ||
            preferences.contains(KEY_HIGHEST_SEEN)
        ) {
            check(
                preferences.edit()
                    .putLong(KEY_HIGHEST_VERIFIED, floor)
                    .remove(KEY_HIGHEST_SEEN)
                    .commit(),
            ) { "failed to persist verified update rollback floor" }
        }
        return floor
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

    /** Publish an authenticated APK and its rollback floor in one durable transaction. */
    @Synchronized
    internal fun recordVerifiedPending(
        context: Context,
        manifest: UpdateManifest,
        installedVersionCode: Long,
    ): VerifiedUpdatePublication {
        val preferences = prefs(context)
        val currentFloor = preferences.getLong(KEY_HIGHEST_VERIFIED, 0L)
        if (!canPublishVerifiedUpdate(installedVersionCode, currentFloor, manifest.versionCode)) {
            return VerifiedUpdatePublication.ROLLBACK_BLOCKED
        }
        val floor = maxOf(
            installedVersionCode,
            currentFloor,
            manifest.versionCode,
        )
        val persisted = preferences.edit()
            .putLong(KEY_HIGHEST_VERIFIED, floor)
            .remove(KEY_HIGHEST_SEEN)
            .putLong(KEY_PENDING_CODE, manifest.versionCode)
            .putString(KEY_PENDING_NAME, manifest.versionName)
            .putString(KEY_PENDING_NOTES, manifest.notes)
            .putString(KEY_PENDING_SHA256, manifest.normalizedSha256())
            .putLong(KEY_PENDING_BYTES, manifest.bytes)
            .commit()
        return if (persisted) {
            VerifiedUpdatePublication.PUBLISHED
        } else {
            VerifiedUpdatePublication.PERSISTENCE_FAILED
        }
    }

    fun clearPending(context: Context) {
        prefs(context).edit {
            remove(KEY_PENDING_CODE)
            remove(KEY_PENDING_NAME)
            remove(KEY_PENDING_NOTES)
            remove(KEY_PENDING_SHA256)
            remove(KEY_PENDING_BYTES)
        }
    }

    /** The last downloaded-and-verified update, or null when none recorded. */
    fun pendingVersionCode(context: Context): Long =
        prefs(context).getLong(KEY_PENDING_CODE, 0L)

    fun pendingVersionName(context: Context): String? =
        prefs(context).getString(KEY_PENDING_NAME, null)

    fun pendingNotes(context: Context): String? =
        prefs(context).getString(KEY_PENDING_NOTES, null)

    internal fun pendingSha256(context: Context): String? =
        prefs(context).getString(KEY_PENDING_SHA256, null)

    internal fun pendingBytes(context: Context): Long =
        prefs(context).getLong(KEY_PENDING_BYTES, 0L)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Legacy advertised builds are retained as evidence but are deliberately untrusted. */
internal data class RollbackFloorEvidence(
    val installedVersionCode: Long,
    val legacyAdvertisedVersionCode: Long = 0L,
    val verifiedVersionCode: Long = 0L,
    val authenticatedPendingVersionCode: Long = 0L,
)

internal fun trustedRollbackFloor(evidence: RollbackFloorEvidence): Long =
    maxOf(
        evidence.installedVersionCode,
        evidence.verifiedVersionCode,
        evidence.authenticatedPendingVersionCode,
    )

internal enum class VerifiedUpdatePublication {
    PUBLISHED,
    ROLLBACK_BLOCKED,
    PERSISTENCE_FAILED,
}

internal fun canPublishVerifiedUpdate(
    installedVersionCode: Long,
    currentVerifiedFloor: Long,
    candidateVersionCode: Long,
): Boolean = candidateVersionCode > installedVersionCode &&
    candidateVersionCode >= currentVerifiedFloor

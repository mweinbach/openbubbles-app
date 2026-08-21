package app.openbubbles.nativeapp.data

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.openbubbles.core.sync.SyncMode
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val PREFS = "native_setup"
private const val KEY_PENDING = "initial_history_download_pending"
private const val KEY_STARTED = "initial_history_download_started"
private const val KEY_POST_SIGN_IN_ONBOARDING = "post_sign_in_onboarding_active"
private const val CHANNEL_HISTORY = "history-download"
private const val NOTIFICATION_ID_READY = 0x0B10

/**
 * The one-time "download my iCloud history" run the user arms at the end of
 * onboarding.
 *
 * While it is armed the app is locked behind a full-screen progress gate and
 * every incoming-message notification is withheld: a first backfill turns
 * years of unread conversations into a notification flood, and the download
 * itself is a sustained CPU/network burn the user has been warned about. The
 * flag is durable so a process death mid-download resumes the same lock
 * instead of dropping the user into a half-populated chat list with the
 * shade filling up behind it.
 */
object InitialHistoryDownload {

    private val _pending = MutableStateFlow(false)

    /** Whether the locked one-time download is still owed. */
    val pending: StateFlow<Boolean> = _pending.asStateFlow()

    /**
     * Seeds the in-memory flow from disk. Safe to call repeatedly; the
     * process may be started by the push service without any UI.
     */
    fun restore(context: Context) {
        _pending.value = isPending(context)
    }

    /**
     * Durable check for callers outside composition (the push service reads
     * this on every incoming message, possibly in a fresh process).
     */
    fun isPending(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PENDING, false)

    /** Suppresses connect-triggered incremental sync until onboarding chooses history behavior. */
    fun isPostSignInOnboardingActive(context: Context): Boolean =
        prefs(context).getBoolean(KEY_POST_SIGN_IN_ONBOARDING, false)

    @SuppressLint("UseKtx") // commit() boolean is checked; KTX edit() returns Unit.
    fun setPostSignInOnboardingActive(context: Context, active: Boolean) {
        check(prefs(context).edit().putBoolean(KEY_POST_SIGN_IN_ONBOARDING, active).commit()) {
            "failed to persist post-sign-in onboarding state"
        }
    }

    /**
     * Arms the download. Notifications are withheld and the lock gate takes
     * over from here until [finish] or [abandon].
     */
    fun arm(context: Context) {
        val app = context.applicationContext
        check(
            prefs(app).edit()
                .putBoolean(KEY_PENDING, true)
                .putBoolean(KEY_STARTED, false)
                .commit(),
        ) { "failed to persist initial history download state" }
        _pending.value = true
    }

    /** FULL only for the first attempt; retries after a crash resume committed cursors. */
    fun syncMode(context: Context): SyncMode =
        initialHistorySyncMode(prefs(context).getBoolean(KEY_STARTED, false))

    @SuppressLint("UseKtx") // commit() boolean is checked; KTX edit() returns Unit.
    fun markStarted(context: Context) {
        check(prefs(context).edit().putBoolean(KEY_STARTED, true).commit()) {
            "failed to persist initial history download start"
        }
    }

    /** The download completed; release the lock and tell the user. */
    fun finish(context: Context) {
        if (!isPending(context)) return
        persistPending(context, false)
        postReadyNotification(context)
    }

    /** The user gave up (or failed permanently); release without alerting. */
    fun abandon(context: Context) {
        persistPending(context, false)
    }

    @SuppressLint("UseKtx") // commit() boolean is checked; KTX edit() returns Unit.
    private fun persistPending(context: Context, value: Boolean) {
        val app = context.applicationContext
        check(
            prefs(app).edit()
                .putBoolean(KEY_PENDING, value)
                .putBoolean(KEY_STARTED, false)
                .commit(),
        ) {
            "failed to persist initial history download state"
        }
        _pending.value = value
    }

    private fun postReadyNotification(context: Context) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_HISTORY,
                "Message history",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "iCloud message history download" },
        )
        if (!nm.areNotificationsEnabled()) return
        val contentIntent = PendingIntent.getActivity(
            app,
            NOTIFICATION_ID_READY,
            Intent(app, NativeMainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "Your conversations finished downloading from iCloud."
        val notification = NotificationCompat.Builder(app, CHANNEL_HISTORY)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle("Your messages are ready")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID_READY, notification) }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

internal fun initialHistorySyncMode(started: Boolean): SyncMode =
    if (started) SyncMode.INCREMENTAL else SyncMode.FULL

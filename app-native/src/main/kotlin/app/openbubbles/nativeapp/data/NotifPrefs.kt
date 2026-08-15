package app.openbubbles.nativeapp.data

import android.content.Context

/**
 * Notification behavior preferences (SharedPreferences-backed), applied by
 * [app.openbubbles.nativeapp.service.Notifications] when posting:
 *
 *  - [hidePreviews]: notifications (and their lockscreen public versions)
 *    show "iMessage" / "New message" instead of chat content.
 *  - [replyEnabled]: hides the inline-reply action when off.
 *  - [notifyReactions]: controls tapback/reaction alerts.
 *
 * The native counterpart of the Flutter app's `hidePreviews` /
 * notification-reply settings; reads are cheap (framework-cached).
 */
class NotifPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Replace message content with generic placeholders on the notification. */
    var hidePreviews: Boolean
        get() = prefs.getBoolean(KEY_HIDE_PREVIEWS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_HIDE_PREVIEWS, value).apply()
        }

    /** Offer the RemoteInput "Reply" action on incoming-message notifications. */
    var replyEnabled: Boolean
        get() = prefs.getBoolean(KEY_REPLY_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_REPLY_ENABLED, value).apply()
        }

    /** Notify for tapbacks and custom emoji/sticker reactions. */
    var notifyReactions: Boolean
        get() = prefs.getBoolean(KEY_NOTIFY_REACTIONS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFY_REACTIONS, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "notification_prefs"
        const val KEY_HIDE_PREVIEWS = "hide_previews"
        const val KEY_REPLY_ENABLED = "reply_enabled"
        const val KEY_NOTIFY_REACTIONS = "notify_reactions"
    }
}

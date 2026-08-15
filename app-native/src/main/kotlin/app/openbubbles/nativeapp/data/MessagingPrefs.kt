package app.openbubbles.nativeapp.data

import android.content.Context

/** iMessage interaction preferences retained across process restarts. */
class MessagingPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Whether direct-chat read receipts may be delivered to the other person.
     * Disabled by default, matching the legacy client's privacy-preserving
     * default. Private receipts still synchronize read state to this user's
     * other Apple devices.
     */
    var sendReadReceipts: Boolean
        get() = prefs.getBoolean(KEY_SEND_READ_RECEIPTS, false)
        set(value) {
            prefs.edit().putBoolean(KEY_SEND_READ_RECEIPTS, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "messaging_prefs"
        const val KEY_SEND_READ_RECEIPTS = "send_read_receipts"
    }
}

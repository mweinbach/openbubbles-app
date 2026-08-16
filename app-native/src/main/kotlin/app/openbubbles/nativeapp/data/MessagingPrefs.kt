package app.openbubbles.nativeapp.data

import android.content.Context

/** iMessage interaction preferences retained across process restarts. */
class MessagingPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Registered iMessage handle preferred when a conversation does not
     * already have a sender associated with it. Stored in Rust form
     * (`tel:...` / `mailto:...`) so it can be matched without guessing the
     * address type.
     */
    var defaultSendingHandle: String?
        get() = prefs.getString(KEY_DEFAULT_SENDING_HANDLE, null)
        set(value) {
            prefs.edit().apply {
                if (value.isNullOrBlank()) {
                    remove(KEY_DEFAULT_SENDING_HANDLE)
                } else {
                    putString(KEY_DEFAULT_SENDING_HANDLE, value)
                }
            }.apply()
        }

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
        const val KEY_DEFAULT_SENDING_HANDLE = "default_sending_handle"
        const val KEY_SEND_READ_RECEIPTS = "send_read_receipts"
    }
}

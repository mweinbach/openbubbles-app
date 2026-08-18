package app.openbubbles.nativeapp.data

import android.content.Context
import androidx.core.content.edit

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
            prefs.edit {
                if (value.isNullOrBlank()) {
                    remove(KEY_DEFAULT_SENDING_HANDLE)
                } else {
                    putString(KEY_DEFAULT_SENDING_HANDLE, value)
                }
            }
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
            prefs.edit { putBoolean(KEY_SEND_READ_RECEIPTS, value) }
        }

    var sendTypingIndicators: Boolean
        get() = prefs.getBoolean(KEY_SEND_TYPING_INDICATORS, true)
        set(value) {
            prefs.edit { putBoolean(KEY_SEND_TYPING_INDICATORS, value) }
        }

    fun chatReadReceiptOverride(chatId: Long): Boolean? =
        chatOverride(KEY_CHAT_READ_OVERRIDE_IDS, KEY_CHAT_READ_OVERRIDE_PREFIX, chatId)

    fun setChatReadReceiptOverride(chatId: Long, value: Boolean?) {
        setChatOverride(KEY_CHAT_READ_OVERRIDE_IDS, KEY_CHAT_READ_OVERRIDE_PREFIX, chatId, value)
    }

    fun chatTypingOverride(chatId: Long): Boolean? =
        chatOverride(KEY_CHAT_TYPING_OVERRIDE_IDS, KEY_CHAT_TYPING_OVERRIDE_PREFIX, chatId)

    fun setChatTypingOverride(chatId: Long, value: Boolean?) {
        setChatOverride(KEY_CHAT_TYPING_OVERRIDE_IDS, KEY_CHAT_TYPING_OVERRIDE_PREFIX, chatId, value)
    }

    private fun chatOverride(idsKey: String, valuePrefix: String, chatId: Long): Boolean? {
        val id = chatId.toString()
        if (id !in prefs.getStringSet(idsKey, emptySet()).orEmpty()) return null
        return prefs.getBoolean(valuePrefix + id, false)
    }

    private fun setChatOverride(idsKey: String, valuePrefix: String, chatId: Long, value: Boolean?) {
        val id = chatId.toString()
        val ids = prefs.getStringSet(idsKey, emptySet()).orEmpty().toMutableSet()
        prefs.edit {
            if (value == null) {
                ids.remove(id)
                remove(valuePrefix + id)
            } else {
                ids.add(id)
                putBoolean(valuePrefix + id, value)
            }
            putStringSet(idsKey, ids)
        }
    }

    var showDeliveryTimestamps: Boolean
        get() = prefs.getBoolean(KEY_SHOW_DELIVERY_TIMESTAMPS, false)
        set(value) {
            prefs.edit { putBoolean(KEY_SHOW_DELIVERY_TIMESTAMPS, value) }
        }

    var shareFocusStatus: Boolean
        get() = prefs.getBoolean(KEY_SHARE_FOCUS_STATUS, false)
        set(value) {
            prefs.edit { putBoolean(KEY_SHARE_FOCUS_STATUS, value) }
        }

    var sendSubjectLines: Boolean
        get() = prefs.getBoolean(KEY_SEND_SUBJECT_LINES, false)
        set(value) {
            prefs.edit { putBoolean(KEY_SEND_SUBJECT_LINES, value) }
        }

    /**
     * Largest incoming image/video/audio payload (bytes) that downloads
     * automatically: 0 disables auto-download, [AUTO_DOWNLOAD_UNLIMITED]
     * fetches every supported payload. Defaults to
     * [DEFAULT_AUTO_DOWNLOAD_MAX_BYTES].
     */
    var autoDownloadMaxBytes: Long
        get() = prefs.getLong(KEY_AUTO_DOWNLOAD_MAX_BYTES, DEFAULT_AUTO_DOWNLOAD_MAX_BYTES)
        set(value) {
            prefs.edit { putLong(KEY_AUTO_DOWNLOAD_MAX_BYTES, value) }
        }

    companion object {
        /** Sentinel for [autoDownloadMaxBytes]: every supported payload auto-downloads. */
        const val AUTO_DOWNLOAD_UNLIMITED: Long = -1L

        /** Out-of-the-box ceiling: 10 MiB, matching the legacy client's behavior. */
        const val DEFAULT_AUTO_DOWNLOAD_MAX_BYTES: Long = 10L * 1024L * 1024L

        private const val PREFS_NAME = "messaging_prefs"
        private const val KEY_DEFAULT_SENDING_HANDLE = "default_sending_handle"
        private const val KEY_SEND_READ_RECEIPTS = "send_read_receipts"
        private const val KEY_SEND_TYPING_INDICATORS = "send_typing_indicators"
        private const val KEY_CHAT_READ_OVERRIDE_IDS = "chat_read_override_ids"
        private const val KEY_CHAT_READ_OVERRIDE_PREFIX = "chat_read_override_"
        private const val KEY_CHAT_TYPING_OVERRIDE_IDS = "chat_typing_override_ids"
        private const val KEY_CHAT_TYPING_OVERRIDE_PREFIX = "chat_typing_override_"
        private const val KEY_SHOW_DELIVERY_TIMESTAMPS = "show_delivery_timestamps"
        private const val KEY_SHARE_FOCUS_STATUS = "share_focus_status"
        private const val KEY_SEND_SUBJECT_LINES = "send_subject_lines"
        private const val KEY_AUTO_DOWNLOAD_MAX_BYTES = "auto_download_max_bytes"
    }
}

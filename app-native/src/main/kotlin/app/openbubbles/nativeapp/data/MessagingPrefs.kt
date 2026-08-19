package app.openbubbles.nativeapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** iMessage interaction preferences retained across process restarts. */
class MessagingPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        hydrateFrom(prefs)
    }

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

    var wifiOnlyAutoDownload: Boolean
        get() = prefs.getBoolean(KEY_WIFI_ONLY_AUTO_DOWNLOAD, false)
        set(value) {
            prefs.edit { putBoolean(KEY_WIFI_ONLY_AUTO_DOWNLOAD, value) }
        }

    var autoSaveMedia: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SAVE_MEDIA, false)
        set(value) {
            prefs.edit { putBoolean(KEY_AUTO_SAVE_MEDIA, value) }
        }

    var filterUnknownSenders: Boolean
        get() = prefs.getBoolean(KEY_FILTER_UNKNOWN_SENDERS, false)
        set(value) {
            prefs.edit { putBoolean(KEY_FILTER_UNKNOWN_SENDERS, value) }
            _filterUnknownSenders.value = value
        }

    var showAvatarsInDirectChats: Boolean
        get() = prefs.getBoolean(KEY_SHOW_AVATARS_IN_DIRECT_CHATS, true)
        set(value) {
            prefs.edit { putBoolean(KEY_SHOW_AVATARS_IN_DIRECT_CHATS, value) }
            _showAvatarsInDirectChats.value = value
        }

    var replaceEmoticons: Boolean
        get() = prefs.getBoolean(KEY_REPLACE_EMOTICONS, true)
        set(value) {
            prefs.edit { putBoolean(KEY_REPLACE_EMOTICONS, value) }
        }

    companion object {
        private val _filterUnknownSenders = MutableStateFlow(false)
        val filterUnknownSendersFlow: StateFlow<Boolean> = _filterUnknownSenders.asStateFlow()

        private val _showAvatarsInDirectChats = MutableStateFlow(true)
        val showAvatarsInDirectChatsFlow: StateFlow<Boolean> = _showAvatarsInDirectChats.asStateFlow()

        fun hydrate(context: Context) {
            hydrateFrom(
                context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
            )
        }

        private fun hydrateFrom(prefs: SharedPreferences) {
            _filterUnknownSenders.value = prefs.getBoolean(KEY_FILTER_UNKNOWN_SENDERS, false)
            _showAvatarsInDirectChats.value = prefs.getBoolean(KEY_SHOW_AVATARS_IN_DIRECT_CHATS, true)
        }

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
        private const val KEY_WIFI_ONLY_AUTO_DOWNLOAD = "wifi_only_auto_download"
        private const val KEY_AUTO_SAVE_MEDIA = "auto_save_media"
        private const val KEY_FILTER_UNKNOWN_SENDERS = "filter_unknown_senders"
        private const val KEY_SHOW_AVATARS_IN_DIRECT_CHATS = "show_avatars_in_direct_chats"
        private const val KEY_REPLACE_EMOTICONS = "replace_emoticons"
    }
}

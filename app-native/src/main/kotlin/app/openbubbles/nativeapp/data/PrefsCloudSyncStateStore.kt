package app.openbubbles.nativeapp.data

import android.content.Context
import android.util.Base64
import app.openbubbles.core.sync.CloudSyncManager
import app.openbubbles.core.sync.CloudSyncStateStore
import app.openbubbles.core.sync.SyncMode
import app.openbubbles.core.sync.UniffiCloudSyncPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.NativePushState
import java.util.concurrent.atomic.AtomicReference

/**
 * App-side CloudKit sync wiring: cursors + pending deletes in
 * SharedPreferences (mirroring the Dart app's prefs-based sync state), a
 * manager per live push state, and automatic incremental sync on connect.
 */
private const val PREFS = "cloud_sync"
private const val KEY_CHAT_CURSOR = "chatSyncToken"
private const val KEY_MESSAGE_CURSOR = "messageSyncToken"
private const val KEY_CHAT_DELETES = "chatDeletionIds"
private const val KEY_MESSAGE_DELETES = "messageDeletionIds"

object CloudSyncWiring {

    private val managerRef = AtomicReference<CloudSyncManager?>(null)
    val manager: CloudSyncManager? get() = managerRef.get()

    fun onStateInstalled(context: Context, state: NativePushState) {
        val store = CoreGraph.store ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stateStore = PrefsCloudSyncStateStore(prefs)
        val port = UniffiCloudSyncPort(state)
        managerRef.set(CloudSyncManager(store, port, stateStore))

        // Auto incremental sync on connect (Dart parity: startup + daily).
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { managerRef.get()?.sync(SyncMode.INCREMENTAL) }
        }
    }

    fun clear() {
        managerRef.set(null)
    }
}

private class PrefsCloudSyncStateStore(
    private val prefs: android.content.SharedPreferences,
) : CloudSyncStateStore {

    override fun chatCursor(): ByteArray? = decode(prefs.getString(KEY_CHAT_CURSOR, null))
    override fun messageCursor(): ByteArray? = decode(prefs.getString(KEY_MESSAGE_CURSOR, null))

    override fun saveChatCursor(cursor: ByteArray?) {
        prefs.edit().putString(KEY_CHAT_CURSOR, encode(cursor)).apply()
    }

    override fun saveMessageCursor(cursor: ByteArray?) {
        prefs.edit().putString(KEY_MESSAGE_CURSOR, encode(cursor)).apply()
    }

    override fun pendingChatDeletes(): List<String> =
        prefs.getStringSet(KEY_CHAT_DELETES, emptySet())?.toList().orEmpty()

    override fun pendingMessageDeletes(): List<String> =
        prefs.getStringSet(KEY_MESSAGE_DELETES, emptySet())?.toList().orEmpty()

    override fun savePendingChatDeletes(ids: List<String>) {
        prefs.edit().putStringSet(KEY_CHAT_DELETES, ids.toSet()).apply()
    }

    override fun savePendingMessageDeletes(ids: List<String>) {
        prefs.edit().putStringSet(KEY_MESSAGE_DELETES, ids.toSet()).apply()
    }

    private fun encode(bytes: ByteArray?): String? =
        bytes?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

    private fun decode(s: String?): ByteArray? =
        s?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
}

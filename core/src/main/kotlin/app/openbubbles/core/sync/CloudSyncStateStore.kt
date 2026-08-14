package app.openbubbles.core.sync

/**
 * Persistence for CloudKit sync bookkeeping: the per-zone continuation
 * cursors and the queues of local deletions still to push to iCloud.
 *
 * The Dart app kept exactly this state in SharedPreferences
 * (`chatSyncToken` / `messageSyncToken` base64 strings, and
 * `chatDeletionIds-1` / `messageDeletionIds-1` string lists) — the native
 * apps should implement this with their key-value store of choice
 * (SharedPreferences/DataStore on Android, java.util.prefs or a file on
 * desktop). [InMemoryCloudSyncStateStore] backs tests and headless use.
 *
 * Cursors are opaque CloudKit tokens: save the `nextCursor` of a page only
 * AFTER its records were applied, so a crash mid-page replays it (CloudKit
 * changes are idempotent upserts/deletes keyed by record id).
 */
interface CloudSyncStateStore {

    fun chatCursor(): ByteArray?

    fun messageCursor(): ByteArray?

    fun saveChatCursor(cursor: ByteArray?)

    fun saveMessageCursor(cursor: ByteArray?)

    /** CloudKit record ids of chats deleted locally, not yet pushed. */
    fun pendingChatDeletes(): List<String>

    /** CloudKit record ids of messages deleted locally, not yet pushed. */
    fun pendingMessageDeletes(): List<String>

    fun savePendingChatDeletes(ids: List<String>)

    fun savePendingMessageDeletes(ids: List<String>)
}

/** In-memory [CloudSyncStateStore] — tests, or wrap it for real storage. */
class InMemoryCloudSyncStateStore : CloudSyncStateStore {

    private var chatCursor: ByteArray? = null
    private var messageCursor: ByteArray? = null
    private var chatDeletes: List<String> = emptyList()
    private var messageDeletes: List<String> = emptyList()

    override fun chatCursor(): ByteArray? = chatCursor

    override fun messageCursor(): ByteArray? = messageCursor

    override fun saveChatCursor(cursor: ByteArray?) {
        chatCursor = cursor?.copyOf()
    }

    override fun saveMessageCursor(cursor: ByteArray?) {
        messageCursor = cursor?.copyOf()
    }

    override fun pendingChatDeletes(): List<String> = chatDeletes

    override fun pendingMessageDeletes(): List<String> = messageDeletes

    override fun savePendingChatDeletes(ids: List<String>) {
        chatDeletes = ids.toList()
    }

    override fun savePendingMessageDeletes(ids: List<String>) {
        messageDeletes = ids.toList()
    }
}

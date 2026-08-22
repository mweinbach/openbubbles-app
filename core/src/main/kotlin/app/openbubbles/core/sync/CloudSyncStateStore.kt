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

    fun attachmentCursor(): ByteArray?

    fun saveChatCursor(cursor: ByteArray?)

    fun saveMessageCursor(cursor: ByteArray?)

    fun saveAttachmentCursor(cursor: ByteArray?)

    /** CloudKit record ids of chats deleted locally, not yet pushed. */
    fun pendingChatDeletes(): List<String>

    /** CloudKit record ids of messages deleted locally, not yet pushed. */
    fun pendingMessageDeletes(): List<String>

    /** CloudKit record ids of attachments deleted locally, not yet pushed. */
    fun pendingAttachmentDeletes(): List<String>

    fun savePendingChatDeletes(ids: List<String>)

    fun savePendingMessageDeletes(ids: List<String>)

    fun savePendingAttachmentDeletes(ids: List<String>)

    /** Local-only privacy tombstones never become remote CloudKit deletions. */
    fun suppressedMessageRecordIds(): List<String> = emptyList()

    fun suppressedAttachmentRecordIds(): List<String> = emptyList()

    fun saveSuppressedMessageRecordIds(ids: List<String>) {}

    fun saveSuppressedAttachmentRecordIds(ids: List<String>) {}

    /** Remove only confirmed IDs so a concurrent new deletion is never lost. */
    fun acknowledgePendingChatDeletes(ids: Collection<String>) {
        val confirmed = ids.toHashSet()
        savePendingChatDeletes(pendingChatDeletes().filterNot(confirmed::contains))
    }

    fun acknowledgePendingMessageDeletes(ids: Collection<String>) {
        val confirmed = ids.toHashSet()
        savePendingMessageDeletes(pendingMessageDeletes().filterNot(confirmed::contains))
    }

    fun acknowledgePendingAttachmentDeletes(ids: Collection<String>) {
        val confirmed = ids.toHashSet()
        savePendingAttachmentDeletes(pendingAttachmentDeletes().filterNot(confirmed::contains))
    }

    fun acknowledgeSuppressedMessageTombstones(ids: Collection<String>) {
        val removed = ids.toHashSet()
        saveSuppressedMessageRecordIds(suppressedMessageRecordIds().filterNot(removed::contains))
    }

    fun acknowledgeSuppressedAttachmentTombstones(ids: Collection<String>) {
        val removed = ids.toHashSet()
        saveSuppressedAttachmentRecordIds(suppressedAttachmentRecordIds().filterNot(removed::contains))
    }

    /**
     * True once type-138 wallpapers have been queried or the message
     * zone has been rewound to recover ones the incremental cursor
     * already walked past. Tests default to done so scripted pages
     * keep their stored cursors.
     */
    fun wallpaperBackfillDone(): Boolean = true

    fun saveWallpaperBackfillDone(done: Boolean) {}
}

/** In-memory [CloudSyncStateStore] — tests, or wrap it for real storage. */
class InMemoryCloudSyncStateStore : CloudSyncStateStore {

    private var chatCursor: ByteArray? = null
    private var messageCursor: ByteArray? = null
    private var attachmentCursor: ByteArray? = null
    private var chatDeletes: List<String> = emptyList()
    private var messageDeletes: List<String> = emptyList()
    private var attachmentDeletes: List<String> = emptyList()
    private var suppressedMessages: List<String> = emptyList()
    private var suppressedAttachments: List<String> = emptyList()

    override fun chatCursor(): ByteArray? = chatCursor

    override fun messageCursor(): ByteArray? = messageCursor

    override fun attachmentCursor(): ByteArray? = attachmentCursor

    override fun saveChatCursor(cursor: ByteArray?) {
        chatCursor = cursor?.copyOf()
    }

    override fun saveMessageCursor(cursor: ByteArray?) {
        messageCursor = cursor?.copyOf()
    }

    override fun saveAttachmentCursor(cursor: ByteArray?) {
        attachmentCursor = cursor?.copyOf()
    }

    override fun pendingChatDeletes(): List<String> = chatDeletes

    override fun pendingMessageDeletes(): List<String> = messageDeletes

    override fun pendingAttachmentDeletes(): List<String> = attachmentDeletes

    override fun savePendingChatDeletes(ids: List<String>) {
        chatDeletes = ids.toList()
    }

    override fun savePendingMessageDeletes(ids: List<String>) {
        messageDeletes = ids.toList()
    }

    override fun savePendingAttachmentDeletes(ids: List<String>) {
        attachmentDeletes = ids.toList()
    }

    override fun suppressedMessageRecordIds(): List<String> = suppressedMessages

    override fun suppressedAttachmentRecordIds(): List<String> = suppressedAttachments

    override fun saveSuppressedMessageRecordIds(ids: List<String>) {
        suppressedMessages = ids.toList()
    }

    override fun saveSuppressedAttachmentRecordIds(ids: List<String>) {
        suppressedAttachments = ids.toList()
    }

    var wallpaperBackfillComplete: Boolean = true

    override fun wallpaperBackfillDone(): Boolean = wallpaperBackfillComplete

    override fun saveWallpaperBackfillDone(done: Boolean) {
        wallpaperBackfillComplete = done
    }
}

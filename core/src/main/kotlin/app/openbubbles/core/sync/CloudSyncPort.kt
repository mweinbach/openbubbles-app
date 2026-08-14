package app.openbubbles.core.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UChatSyncPage
import uniffi.rust_lib_bluebubbles.UMessageSyncPage
import uniffi.rust_lib_bluebubbles.USyncState

/**
 * Download-side CloudKit sync seam over the UniFFI batch-4 surface.
 *
 * The port is deliberately page-oriented (mirroring the FRB API shape:
 * `sync_chats` / `sync_messages` take a continuation token and return the
 * next one) so [CloudSyncManager] owns the zone loops, cursor persistence
 * and progress — tests inject pages through a fake, the real implementation
 * just forwards to [NativePushState].
 *
 * Throwing from these methods aborts the in-flight sync; the manager
 * reports the error in its summary and keeps the last persisted cursor.
 */
interface CloudSyncPort {

    /** Availability of CloudKit history sync for the live account. */
    suspend fun syncState(): USyncState

    /**
     * Circle membership check — the Dart loop skipped (and disabled cloud
     * syncing) once the device fell out of the iCloud clique.
     */
    suspend fun isInClique(): Boolean

    /**
     * Pull one page of chat changes. `cursor` is the previous page's
     * `nextCursor` (null for the first page); a page ends its zone when
     * `more == false`.
     */
    suspend fun chatsPage(cursor: ByteArray?): UChatSyncPage

    /** Pull one page of message changes. Same cursor contract. */
    suspend fun messagesPage(cursor: ByteArray?): UMessageSyncPage

    /** Push local chat deletions to iCloud (flush BEFORE pulling). */
    suspend fun deleteChatsRemote(recordIds: List<String>)

    /** Push local message deletions to iCloud (flush BEFORE pulling). */
    suspend fun deleteMessagesRemote(recordIds: List<String>)
}

/**
 * Production port: forwards to the UniFFI batch-4 methods on the live
 * [NativePushState], each on [Dispatchers.IO] (the exports are synchronous
 * `RUNTIME.block_on` calls).
 */
class UniffiCloudSyncPort(private val state: NativePushState) : CloudSyncPort {

    override suspend fun syncState(): USyncState =
        withContext(Dispatchers.IO) { state.cloudSyncState() }

    override suspend fun isInClique(): Boolean =
        withContext(Dispatchers.IO) { state.isInClique() }

    override suspend fun chatsPage(cursor: ByteArray?): UChatSyncPage =
        withContext(Dispatchers.IO) { state.syncChatsPage(cursor) }

    override suspend fun messagesPage(cursor: ByteArray?): UMessageSyncPage =
        withContext(Dispatchers.IO) { state.syncMessagesPage(cursor) }

    override suspend fun deleteChatsRemote(recordIds: List<String>) =
        withContext(Dispatchers.IO) { state.deleteChatsRemote(recordIds) }

    override suspend fun deleteMessagesRemote(recordIds: List<String>) =
        withContext(Dispatchers.IO) { state.deleteMessagesRemote(recordIds) }
}

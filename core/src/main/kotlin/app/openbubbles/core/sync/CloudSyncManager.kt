package app.openbubbles.core.sync

import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.intake.HandleResolver
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.core.repo.StoreDeletionCoordinators
import app.openbubbles.core.repo.StoreInvalidationCoordinators
import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.Handle
import app.openbubbles.db.Handle_
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.exception.UniqueViolationException
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.UAttachmentChange
import uniffi.rust_lib_bluebubbles.UChatChange
import uniffi.rust_lib_bluebubbles.UCloudAttachment
import uniffi.rust_lib_bluebubbles.UCloudChat
import uniffi.rust_lib_bluebubbles.UCloudMessage
import uniffi.rust_lib_bluebubbles.UMessageChange
import uniffi.rust_lib_bluebubbles.USyncState
import java.io.File
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean

/** Which cursors a sync run starts from. */
enum class SyncMode {
    /** Backfill: ignore stored cursors, page both zones from scratch. */
    FULL,

    /** Resume from the persisted cursors (periodic / on-demand refresh). */
    INCREMENTAL,
}

enum class SyncPhase { IDLE, CHECKING, CHATS, MESSAGES, ATTACHMENTS, DONE, FAILED }

/** Coarse per-run progress, emitted after every applied page. */
data class SyncProgress(
    val phase: SyncPhase,
    val chatsDone: ULong = 0u,
    val messagesDone: ULong = 0u,
    val attachmentsDone: ULong = 0u,
    val chatTombstones: ULong = 0u,
    val messageTombstones: ULong = 0u,
    val attachmentTombstones: ULong = 0u,
)

/** Final result of one [CloudSyncManager.sync] run. */
data class SyncSummary(
    val totalChats: ULong,
    val totalMessages: ULong,
    val totalAttachments: ULong = 0u,
    val chatTombstones: ULong = 0u,
    val messageTombstones: ULong = 0u,
    val attachmentTombstones: ULong = 0u,
    val durationMs: Long,
    /** Human-readable failure; non-null means the run aborted early. */
    val error: String? = null,
    /** Stopped by [CloudSyncManager.cancel]; applied pages stay applied. */
    val cancelled: Boolean = false,
)

/**
 * Kotlin port of the download half of the Dart CloudKit sync loop
 * (`RustPushService.doCloudKitSyncPrivate` + `Chat.findFromCloud` /
 * `Chat.applyFromCloud` / `Message.applyFromCloud`):
 *
 * 1. gate on [CloudSyncPort.syncState] + clique membership;
 * 2. flush pending local deletions to iCloud (before pulling, or the pull
 *    resurrects them);
 * 3. page the chat zone to completion (upsert by cloud guid / identifier /
 *    participants; tombstones hard-delete by ckRecordId; cloud state only
 *    applies when the cloud group version is newer). Group photos ride on
 *    the chat record and are downloaded after each page;
 * 4. page the message zone to completion (dedupe by guid — existing rows
 *    only get their ckRecordId refreshed; new rows are inserted with
 *    latest-message wiring).
 * 5. page attachment metadata to completion, link each record to its message,
 *    and retain its CloudKit id for on-demand payload download.
 *
 * Differences vs Dart, deliberate:
 * - Historical rows never set `Chat.hasUnreadMessage` (the intake path does
 *   for live deliveries; a backfill must not mark every chat unread).
 * - Duplicate cloud records (same guid, different record id) are collected
 *   per page and pushed to iCloud right after that page (Dart batched all
 *   of them at the end of the page too — same effect, earlier).
 * - `Chat.cloudData` (the serialized `CloudChat` plist the Dart app kept
 *   for round-tripping changes) is not persisted — the upload half is a
 *   later batch.
 * - No `syncHistoryTime` cutoff (Dart's "only sync the last N ms" pref):
 *   full history comes down; a cutoff can be layered by the caller later.
 *
 * The next CloudKit page is prefetched while the current page is committed,
 * hiding local database and cursor persistence latency behind the network.
 * All writes run on [Dispatchers.IO]; runs are single-flight ([sync]
 * serializes on a mutex). Safe to call from any thread.
 */
class CloudSyncManager(
    private val store: BoxStore,
    private val port: CloudSyncPort,
    private val syncStore: CloudSyncStateStore = InMemoryCloudSyncStateStore(),
    private val attachmentStore: AttachmentStore? = null,
    private val transcriptBackgroundHandler: TranscriptBackgroundHandler? = null,
    private val pageRetryDelaysMs: List<Long> = listOf(1_000L, 3_000L, 10_000L, 30_000L),
) {

    companion object {
        /** ns between the Unix and Apple (CoreData, 2001-01-01) epochs. */
        private const val APPLE_EPOCH_OFFSET_NS = 978307200_000_000_000L

        /** `MessageFlags.IS_FROM_ME`. */
        private const val FLAG_IS_FROM_ME = 1L shl 2

        private const val TRANSCRIPT_BACKGROUND_MESSAGE_TYPE = 138L

        /**
         * Commit history in bounded batches. A transaction per record caused
         * every imported message to invalidate the reactive chat query and
         * could leave hundreds of read transactions queued behind the writer.
         */
        private const val DB_WRITE_BATCH_SIZE = 128

        private val TAPBACK_TYPES = listOf(
            MessageMapper.REACTION_LOVE,
            MessageMapper.REACTION_LIKE,
            MessageMapper.REACTION_DISLIKE,
            MessageMapper.REACTION_LAUGH,
            MessageMapper.REACTION_EMPHASIZE,
            MessageMapper.REACTION_QUESTION,
            MessageMapper.REACTION_EMOJI,
            MessageMapper.REACTION_STICKERBACK,
        )

        internal const val CLOUD_PHOTO_KEY_PREFIX = "ck:"

        internal fun cloudPhotoKey(recordId: String, version: Long?): String =
            "$CLOUD_PHOTO_KEY_PREFIX$recordId:${version ?: 0}"

        internal fun isCloudPhotoKey(key: String?): Boolean =
            key?.startsWith(CLOUD_PHOTO_KEY_PREFIX) == true

        /**
         * Download a CloudKit group photo when the local cache is missing or
         * from an older cloud version. Live IconChange / user-set photos
         * (non-`ck:` keys) are left alone.
         */
        internal fun shouldDownloadGroupPhoto(
            lockChatIcon: Boolean?,
            customAvatarPath: String?,
            photoAttachmentGuid: String?,
            recordId: String,
            version: Long?,
            fileExists: Boolean,
        ): Boolean {
            if (lockChatIcon == true) return false
            val expected = cloudPhotoKey(recordId, version)
            if (customAvatarPath.isNullOrBlank() || !fileExists) return true
            if (photoAttachmentGuid != null && !isCloudPhotoKey(photoAttachmentGuid)) return false
            return photoAttachmentGuid != expected
        }

        /**
         * Clear a previously downloaded CloudKit photo only when the cloud
         * version moved forward and the record no longer carries an image.
         */
        internal fun shouldClearCloudGroupPhoto(
            photoAttachmentGuid: String?,
            recordId: String,
            cloudVersion: Long?,
        ): Boolean {
            val key = photoAttachmentGuid ?: return false
            val prefix = "$CLOUD_PHOTO_KEY_PREFIX$recordId:"
            if (!key.startsWith(prefix)) return false
            val stored = key.removePrefix(prefix).toLongOrNull() ?: return false
            val incoming = cloudVersion ?: return false
            return incoming > stored
        }

        /** Dart `fromNsSinceAppleEpoch`: Apple-epoch ns -> Date. */
        fun dateFromAppleNs(ns: Long): Date = Date((ns / 1_000_000) + APPLE_EPOCH_OFFSET_NS / 1_000_000)

        /** Dart `applyFromCloud` tapback mapping (2 sticker, 2000+/3000+ codes). */
        fun reactionTypeFromCode(code: Long?): String? = when {
            code == null -> null
            code == 2L -> MessageMapper.REACTION_STICKER
            code in 2000..2999 -> TAPBACK_TYPES.getOrNull((code - 2000).toInt())
            code in 3000..3999 -> TAPBACK_TYPES.getOrNull((code - 3000).toInt())?.let { "-$it" }
            else -> null
        }

        internal fun shouldReplaceLatestMessage(chat: Chat, candidateDate: Date?): Boolean =
            chat.dbLatestMessage.targetId == 0L ||
                (candidateDate != null &&
                    (chat.dbOnlyLatestMessageDate == null || candidateDate.after(chat.dbOnlyLatestMessageDate)))
    }

    private val chatBox = store.boxFor(Chat::class.java)
    private val messageBox = store.boxFor(Message::class.java)
    private val attachmentBox = store.boxFor(Attachment::class.java)
    private val invalidations = StoreInvalidationCoordinators.forStore(store)
    private val mutex = Mutex()
    private val cancelled = AtomicBoolean(false)
    private val deletedChatRecordsThisRun = hashSetOf<String>()
    private val deletedMessageRecordsThisRun = hashSetOf<String>()
    private val deletedAttachmentRecordsThisRun = hashSetOf<String>()

    private val _progress = MutableStateFlow(SyncProgress(SyncPhase.IDLE))

    init {
        require(pageRetryDelaysMs.all { it >= 0L }) { "page retry delays must be non-negative" }
    }

    /** Live progress; [SyncPhase.DONE] / [SyncPhase.FAILED] close a run. */
    val progress: StateFlow<SyncProgress> = _progress.asStateFlow()

    /** Request an early stop. Takes effect between pages; applied pages stay. */
    fun cancel() {
        cancelled.set(true)
    }

    /**
     * Run one sync. Returns the summary; failures are reported in
     * [SyncSummary.error] (and the progress flow) rather than thrown, so
     * UI callers can render them directly.
     */
    suspend fun sync(mode: SyncMode): SyncSummary = mutex.withLock {
        withContext(Dispatchers.IO) {
            val startedAt = System.currentTimeMillis()
            cancelled.set(false)
            deletedChatRecordsThisRun.clear()
            deletedMessageRecordsThisRun.clear()
            deletedAttachmentRecordsThisRun.clear()
            var state = SyncProgress(SyncPhase.CHECKING)
            fun update(phase: SyncPhase = state.phase) {
                state = state.copy(phase = phase)
                _progress.value = state
            }
            fun finish(error: String? = null, cancelledFlag: Boolean = false): SyncSummary {
                val done = error == null && !cancelledFlag
                _progress.value = state.copy(phase = if (done) SyncPhase.DONE else SyncPhase.FAILED)
                return SyncSummary(
                    totalChats = state.chatsDone,
                    totalMessages = state.messagesDone,
                    totalAttachments = state.attachmentsDone,
                    chatTombstones = state.chatTombstones,
                    messageTombstones = state.messageTombstones,
                    attachmentTombstones = state.attachmentTombstones,
                    durationMs = System.currentTimeMillis() - startedAt,
                    error = error,
                    cancelled = cancelledFlag,
                )
            }

            // 1. Gate on availability + clique membership (Dart disabled the
            //    feature and skipped when out of the clique).
            try {
                when (port.syncState()) {
                    USyncState.NEEDS_LOGIN -> return@withContext finish("iCloud login required for message sync")
                    USyncState.NOT_ENABLED -> return@withContext finish("iCloud message sync not enabled on this account")
                    USyncState.AVAILABLE -> Unit
                }
                if (!port.isInClique()) {
                    return@withContext finish("Device is no longer in the iCloud clique; skipping sync")
                }

                // 2. Flush local deletions before pulling (Dart's
                //    *DeletionIds-1 queues) or the pull resurrects them.
                //    Empty queues are skipped entirely: the routine
                //    reconnect-time incremental pass must not pay three
                //    remote calls and three state commits for nothing.
                syncStore.pendingMessageDeletes().takeIf { it.isNotEmpty() }?.let {
                    port.deleteMessagesRemote(it)
                    deletedMessageRecordsThisRun += it
                    syncStore.acknowledgePendingMessageDeletes(it)
                }
                syncStore.pendingAttachmentDeletes().takeIf { it.isNotEmpty() }?.let {
                    port.deleteAttachmentsRemote(it)
                    deletedAttachmentRecordsThisRun += it
                    syncStore.acknowledgePendingAttachmentDeletes(it)
                }
                syncStore.pendingChatDeletes().takeIf { it.isNotEmpty() }?.let {
                    port.deleteChatsRemote(it)
                    deletedChatRecordsThisRun += it
                    syncStore.acknowledgePendingChatDeletes(it)
                }

                val lookup = HistorySyncLookup(store)

                // 3. Chat zone.
                update(SyncPhase.CHATS)
                val chatsComplete = syncPages(
                    zone = "Chat",
                    initialCursor = if (mode == SyncMode.INCREMENTAL) syncStore.chatCursor() else null,
                    fetch = port::chatsPage,
                    nextCursor = { it.nextCursor },
                    more = { it.more },
                    status = { it.status },
                    apply = { applyChatPage(it.records, lookup) },
                    saveCursor = syncStore::saveChatCursor,
                    onCommitted = { page ->
                        state = state.copy(
                            chatsDone = state.chatsDone + page.records.count { it.chat != null }.toULong(),
                            chatTombstones = state.chatTombstones +
                                page.records.count { it.chat == null }.toULong(),
                        )
                        update()
                    },
                )
                if (!chatsComplete) {
                    return@withContext finish(cancelledFlag = true)
                }

                // 4. Message zone. Incremental FetchRecordChanges never
                // re-emits a type-138 wallpaper the cursor already passed,
                // so query those records independently first. Treat this as
                // a one-time best-effort migration: full repair still walks
                // the message zone, and repeatedly retrying a pathological
                // CloudKit query can monopolize the device on every startup.
                // Never turn a routine incremental sync into a full message
                // history rewind just because the optional query is empty.
                update(SyncPhase.MESSAGES)
                val shouldQueryBackgrounds = mode == SyncMode.INCREMENTAL &&
                    !syncStore.wallpaperBackfillDone()
                var backgroundQuerySucceeded = false
                val queriedBackgrounds = if (shouldQueryBackgrounds) {
                    try {
                        port.transcriptBackgrounds().also { backgroundQuerySucceeded = true }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }
                if (!queriedBackgrounds.isNullOrEmpty()) {
                    applyMessagePage(queriedBackgrounds, lookup)
                }
                val messagesComplete = syncPages(
                    zone = "Message",
                    initialCursor = if (mode == SyncMode.INCREMENTAL) {
                        syncStore.messageCursor()
                    } else {
                        null
                    },
                    fetch = port::messagesPage,
                    nextCursor = { it.nextCursor },
                    more = { it.more },
                    status = { it.status },
                    apply = { applyMessagePage(it.records, lookup) },
                    saveCursor = syncStore::saveMessageCursor,
                    onCommitted = { page ->
                        state = state.copy(
                            messagesDone = state.messagesDone + page.records.count { it.message != null }.toULong(),
                            messageTombstones = state.messageTombstones +
                                page.records.count { it.message == null }.toULong(),
                        )
                        update()
                    },
                )
                if (!messagesComplete) {
                    return@withContext finish(cancelledFlag = true)
                }
                if ((shouldQueryBackgrounds && backgroundQuerySucceeded) || mode == SyncMode.FULL) {
                    syncStore.saveWallpaperBackfillDone(true)
                }

                // 5. Attachment metadata zone. Payloads remain in CloudKit
                // until the user opens/downloads an attachment.
                update(SyncPhase.ATTACHMENTS)
                val attachmentsComplete = syncPages(
                    zone = "Attachment",
                    initialCursor = if (mode == SyncMode.INCREMENTAL) syncStore.attachmentCursor() else null,
                    fetch = port::attachmentsPage,
                    nextCursor = { it.nextCursor },
                    more = { it.more },
                    status = { it.status },
                    apply = { applyAttachmentPage(it.records) },
                    saveCursor = syncStore::saveAttachmentCursor,
                    onCommitted = { page ->
                        state = state.copy(
                            attachmentsDone = state.attachmentsDone +
                                page.records.count { it.attachment != null }.toULong(),
                            attachmentTombstones = state.attachmentTombstones +
                                page.records.count { it.attachment == null }.toULong(),
                        )
                        update()
                    },
                )
                if (!attachmentsComplete) {
                    return@withContext finish(cancelledFlag = true)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withContext finish(error = e.message ?: e.javaClass.simpleName)
            }

            finish()
        }
    }

    private suspend fun <T> fetchPageWithRetry(fetch: suspend () -> T): T {
        pageRetryDelaysMs.forEach { retryDelayMs ->
            try {
                return fetch()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                delay(retryDelayMs)
            }
        }
        return fetch()
    }

    private suspend fun <Page> syncPages(
        zone: String,
        initialCursor: ByteArray?,
        fetch: suspend (ByteArray?) -> Page,
        nextCursor: (Page) -> ByteArray,
        more: (Page) -> Boolean,
        status: (Page) -> Int,
        apply: suspend (Page) -> Unit,
        saveCursor: (ByteArray?) -> Unit,
        onCommitted: (Page) -> Unit,
    ): Boolean = supervisorScope {
        suspend fun fetchValidated(cursor: ByteArray?): Page = fetchPageWithRetry {
            fetch(cursor).also { page ->
                validatePageCursor(zone, cursor, nextCursor(page), more(page), status(page))
            }
        }

        if (cancelled.get()) return@supervisorScope false
        var page = fetchValidated(initialCursor)
        while (true) {
            val pageCursor = nextCursor(page)
            val hasMore = more(page)
            val nextPage = if (hasMore && !cancelled.get()) {
                async(Dispatchers.IO) { fetchValidated(pageCursor) }
            } else {
                null
            }

            apply(page)
            saveCursor(pageCursor)
            onCommitted(page)

            if (cancelled.get()) {
                nextPage?.cancelAndJoin()
                return@supervisorScope false
            }
            if (!hasMore) return@supervisorScope true
            page = requireNotNull(nextPage).await()
        }
        error("unreachable history sync page loop")
    }

    private fun validatePageCursor(
        zone: String,
        currentCursor: ByteArray?,
        nextCursor: ByteArray,
        more: Boolean,
        status: Int,
    ) {
        if (!more) return
        check(nextCursor.isNotEmpty()) {
            "$zone history sync returned no continuation cursor while more changes remain (status $status)"
        }
        check(currentCursor == null || !currentCursor.contentEquals(nextCursor)) {
            "$zone history sync did not advance its continuation cursor (status $status)"
        }
    }

    private class HistorySyncLookup(
        private val store: BoxStore,
    ) {
        private val chatBox = store.boxFor(Chat::class.java)
        private val handleBox = store.boxFor(Handle::class.java)
        private val chatsByIdentifier = HashMap<String, Long>()
        private val chatsByCloudGuid = HashMap<String, Long>()
        private val chatsByGuid = HashMap<String, Long>()
        private val chatsByGuidRef = HashMap<String, Long>()
        private val handlesByKey = HashMap<String, Long>()

        fun findByCloudGuid(cloudGuid: String): Chat? =
            chatsByCloudGuid[cloudGuid]?.let(chatBox::get)
                ?: chatBox.query()
                    .equal(Chat_.cloudGuid, cloudGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
                    ?.also(::putChat)

        fun findByIdentifier(identifier: String): Chat? =
            chatsByIdentifier[identifier]?.let(chatBox::get)
                ?: chatBox.query()
                    .equal(Chat_.chatIdentifier, identifier, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
                    ?.also(::putChat)

        /**
         * Identifier lookup that can tell SMS/iMessage twins apart. A phone
         * number's SMS and iMessage 1:1 chats share the same chatIdentifier,
         * so a bare `findByIdentifier` binds whichever row the query returns
         * first. The preferring variant falls back to the other twin (for
         * resolvers that must not drop a record); the exact variant returns
         * null instead so import paths fall through to creation rather than
         * adopting the wrong service's thread.
         */
        fun findByIdentifierPreferring(identifier: String, sms: Boolean): Chat? {
            val candidates = identifierCandidates(identifier)
            return (
                candidates.firstOrNull { (it.isRpSms == true) == sms }
                    ?: candidates.firstOrNull()
                )?.also(::putChat)
        }

        fun findByIdentifierForService(identifier: String, sms: Boolean): Chat? =
            identifierCandidates(identifier)
                .firstOrNull { (it.isRpSms == true) == sms }
                ?.also(::putChat)

        private fun identifierCandidates(identifier: String): List<Chat> =
            chatBox.query()
                .equal(Chat_.chatIdentifier, identifier, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.find() }

        fun chatForCloudMessage(chatId: String): Chat? =
            resolveChatRef(chatId, preferSmsForBareRefs = null)

        /**
         * Wallpapers are an iMessage feature; when a bare address or
         * identifier is ambiguous between service twins, the iMessage row
         * must win or the poster lands on a chat the transcript never reads.
         */
        fun chatForTranscriptBackground(chatId: String): Chat? =
            resolveChatRef(chatId, preferSmsForBareRefs = false)

        private fun resolveChatRef(chatId: String, preferSmsForBareRefs: Boolean?): Chat? {
            if (chatId.contains(';')) {
                // Exact guid forms resolve before the shared-identifier
                // fallback: the service prefix ("SMS;-;X" vs "iMessage;-;X")
                // is the only thing that tells the twins apart, and the
                // identifier fallback used to shadow it.
                exactChatRef(chatId)?.let { return it }
                val identifier = chatId.split(';').getOrNull(2)
                if (!identifier.isNullOrEmpty()) {
                    findByIdentifierPreferring(identifier, sms = chatId.startsWith("SMS;"))
                        ?.let { return it }
                }
            } else {
                // Transcript-background records reference their chat by the
                // bare identifier: the peer address for direct chats, a rust
                // guid for groups (Dart resolved these via findByHandle /
                // findByRustGuid).
                when (preferSmsForBareRefs) {
                    null -> findByIdentifier(chatId)?.let { return it }
                    else -> findByIdentifierPreferring(chatId, sms = preferSmsForBareRefs)
                        ?.let { return it }
                }
                exactChatRef(chatId)?.let { return it }
            }
            val address = MessageMapper.normalizeAddress(chatId)
            if (address.contains('@') || address.contains('+')) {
                directChatForAddress(address, preferIMessage = preferSmsForBareRefs == false)
                    ?.let { return it }
            }
            return null
        }

        private fun exactChatRef(ref: String): Chat? {
            findByCloudGuid(ref)?.let { return it }
            chatsByGuid[ref]?.let(chatBox::get)?.let { return it }
            chatsByGuidRef[ref]?.let(chatBox::get)?.let { return it }
            chatBox.query()
                .equal(Chat_.guid, ref, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
                ?.also(::putChat)
                ?.let { return it }
            return chatBox.query()
                .containsElement(Chat_.guidRefs, ref, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
                ?.also(::putChat)
        }

        /** `Chat.findByHandle`: the direct chat whose only participant is [address]. */
        fun directChatForAddress(address: String, preferIMessage: Boolean = false): Chat? {
            val builder = chatBox.query()
            builder.link(Chat_.handles)
                .equal(Handle_.address, address, QueryBuilder.StringOrder.CASE_SENSITIVE)
            val candidates = builder.build().use { it.find() }
                .filter { it.handles.size == 1 }
            val chosen = if (preferIMessage) {
                candidates.firstOrNull { it.isRpSms != true } ?: candidates.firstOrNull()
            } else {
                candidates.firstOrNull()
            }
            return chosen?.also(::putChat)
        }

        fun putChat(chat: Chat) {
            if (chat.id == 0L) return
            chat.chatIdentifier?.takeIf(String::isNotEmpty)?.let { chatsByIdentifier[it] = chat.id }
            chat.cloudGuid?.takeIf(String::isNotEmpty)?.let { chatsByCloudGuid[it] = chat.id }
            chat.guid?.takeIf(String::isNotEmpty)?.let { chatsByGuid[it] = chat.id }
            chat.guidRefs.orEmpty().filter(String::isNotEmpty).forEach { chatsByGuidRef[it] = chat.id }
        }

        fun removeChat(chat: Chat) {
            chatsByIdentifier.entries.removeAll { it.value == chat.id }
            chatsByCloudGuid.entries.removeAll { it.value == chat.id }
            chatsByGuid.entries.removeAll { it.value == chat.id }
            chatsByGuidRef.entries.removeAll { it.value == chat.id }
        }

        fun resolveHandle(rustHandle: String, service: String): Handle {
            val address = MessageMapper.normalizeAddress(rustHandle)
            val key = handleKey(address, service)
            return handlesByKey[key]?.let(handleBox::get)
                ?: HandleResolver.resolve(store, rustHandle, service).also(::putHandle)
        }

        private fun putHandle(handle: Handle) {
            val address = handle.address ?: return
            val service = handle.service ?: return
            if (handle.id != 0L) handlesByKey[handleKey(address, service)] = handle.id
        }

        private fun handleKey(address: String, service: String): String = "$address\u0000$service"
    }

    // ------------------------------------------------------------------
    // Chat zone (doCloudKitSyncPrivate chat loop + findFromCloud/applyFromCloud)
    // ------------------------------------------------------------------

    private data class PendingGroupPhoto(
        val chatId: Long,
        val recordId: String,
        val version: Long?,
    )

    private data class PendingChatBackground(
        val chatId: Long,
        val version: Long,
        val mmcsXml: String?,
    )

    private suspend fun applyChatPage(records: List<UChatChange>, lookup: HistorySyncLookup) {
        val pendingPhotos = ArrayList<PendingGroupPhoto>()
        val pendingBackgrounds = ArrayList<PendingChatBackground>()
        invalidations.coalesce {
            records.chunked(DB_WRITE_BATCH_SIZE).forEach { batch ->
                val removedAttachments = arrayListOf<Attachment>()
                val removedChats = arrayListOf<Chat>()
                store.runInTx {
                    val tombstonesByRecordId = chatsByRecordIdsLocked(
                        batch.filter { it.chat == null }.map(UChatChange::recordId),
                    )
                    for (record in batch) {
                        val cloud = record.chat
                        if (cloud == null) {
                            tombstonesByRecordId[record.recordId]
                                .orEmpty()
                                .forEach { deleteChatLocked(it, lookup, removedAttachments, removedChats) }
                            continue
                        }
                        if (record.recordId in deletedChatRecordsThisRun) continue
                        if (cloud.serviceName != "iMessage") continue // Dart: iMessage only
                        val chat = findOrImportChatLocked(cloud, lookup)
                        // Always refresh identifiers (Dart applies these before the
                        // version gate).
                        var dirty = chat.chatIdentifier != cloud.chatIdentifier ||
                            chat.ckRecordId != record.recordId ||
                            chat.cloudGuid != cloud.groupId
                        chat.chatIdentifier = cloud.chatIdentifier
                        chat.ckRecordId = record.recordId
                        chat.cloudGuid = cloud.groupId
                        val localVersion = chat.groupVersion ?: 1L
                        val cloudVersion = cloud.groupVersion?.toLong()
                        if (cloudVersion == null || cloudVersion <= localVersion) {
                            val synced = cloudVersion == localVersion
                            dirty = dirty || chat.ckSyncState != synced
                            chat.ckSyncState = synced
                        } else {
                            // Cloud is newer: apply the full chat state.
                            dirty = true
                            chat.style = cloud.style
                            chat.lastReadMessageGuid = cloud.lastSeenMessageGuid
                            chat.groupVersion = cloudVersion
                            chat.displayName = cloud.displayName
                            chat.dbOnlyLatestMessageDate = dateFromAppleNs(cloud.lastReadMessageTimestamp)
                            // Cloud chats include me — mirror Dart and keep all.
                            chat.handles.clear()
                            chat.handles.addAll(
                                cloud.participants.map { lookup.resolveHandle(it, "iMessage") },
                            )
                            if (cloud.lastAddressedHandle.isNotEmpty()) {
                                chat.usingHandle = MessageMapper.toRustHandle(cloud.lastAddressedHandle)
                            }
                            chat.ckSyncState = true
                        }
                        if (cloud.hasGroupPhoto) {
                            val fileExists = chat.customAvatarPath?.let { File(it).isFile } == true
                            if (shouldDownloadGroupPhoto(
                                    lockChatIcon = chat.lockChatIcon,
                                    customAvatarPath = chat.customAvatarPath,
                                    photoAttachmentGuid = chat.photoAttachmentGuid,
                                    recordId = record.recordId,
                                    version = cloudVersion,
                                    fileExists = fileExists,
                                )
                            ) {
                                pendingPhotos += PendingGroupPhoto(chat.id, record.recordId, cloudVersion)
                            }
                        } else if (shouldClearCloudGroupPhoto(
                                photoAttachmentGuid = chat.photoAttachmentGuid,
                                recordId = record.recordId,
                                cloudVersion = cloudVersion,
                            )
                        ) {
                            clearCloudGroupPhoto(chat)
                            dirty = true
                        }
                        // The record itself carries the chat's transcript
                        // background (`backgroundProperties`), so a background
                        // whose type-138 message has left the message-sync
                        // window still restores here. Newer-wins version gate
                        // mirrors the store's own idempotence check.
                        cloud.transcriptBackground?.let { background ->
                            val version = background.version
                                .takeIf { it <= Long.MAX_VALUE.toULong() }
                                ?.toLong()
                            val localVersion = chat.transcriptBackgroundVersion ?: Long.MIN_VALUE
                            if (version != null && version > localVersion) {
                                pendingBackgrounds += PendingChatBackground(
                                    chatId = chat.id,
                                    version = version,
                                    mmcsXml = background.mmcsXml,
                                )
                            }
                        }
                        if (dirty) chatBox.put(chat)
                        lookup.putChat(chat)
                    }
                }
                removedAttachments.forEach { attachmentStore?.deleteLocalFiles(it) }
                StoreDeletionCoordinators.deleteChatFiles(store, removedChats)
            }
        }
        downloadPendingGroupPhotos(pendingPhotos)
        applyPendingChatBackgrounds(pendingBackgrounds)
    }

    /**
     * Restore chat backgrounds carried on chat records. Downloads run off the
     * store transaction through the shared background store, which re-checks
     * the version before writing. Old wallpaper MMCS payloads are routinely
     * gone from Apple's CDN; skip those rather than fail the page (same
     * policy as the message zone).
     */
    private suspend fun applyPendingChatBackgrounds(jobs: List<PendingChatBackground>) {
        if (jobs.isEmpty()) return
        val handler = requireNotNull(transcriptBackgroundHandler) {
            "transcript background handler is unavailable"
        }
        jobs.groupBy(PendingChatBackground::chatId)
            .values
            .mapNotNull { candidates -> candidates.maxByOrNull(PendingChatBackground::version) }
            .sortedBy(PendingChatBackground::version)
            .forEach { job ->
                try {
                    handler.apply(
                        TranscriptBackgroundUpdate(
                            chatId = job.chatId,
                            version = job.version,
                            remove = false,
                            mmcsXml = job.mmcsXml,
                        ),
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Payload gone or undecodable; the next record carrying a
                    // newer version retries.
                }
            }
    }

    private fun clearCloudGroupPhoto(chat: Chat) {
        chat.customAvatarPath?.let { runCatching { File(it).delete() } }
        chat.customAvatarPath = null
        chat.photoAttachmentGuid = null
    }

    private suspend fun downloadPendingGroupPhotos(jobs: List<PendingGroupPhoto>) {
        val disk = attachmentStore ?: return
        for (job in jobs) {
            if (cancelled.get()) return
            if (job.chatId == 0L) continue
            val dest = runCatching { disk.groupIconFile(job.chatId, job.recordId, job.version) }
                .getOrNull() ?: continue
            dest.parentFile?.mkdirs()
            val downloaded = try {
                port.downloadGroupPhoto(job.recordId, dest.absolutePath)
                dest.isFile && dest.length() > 0L
            } catch (e: CancellationException) {
                dest.delete()
                throw e
            } catch (_: Exception) {
                dest.delete()
                false
            }
            if (!downloaded) continue
            store.runInTx {
                val chat = chatBox.get(job.chatId) ?: return@runInTx
                if (chat.lockChatIcon == true) return@runInTx
                chat.customAvatarPath?.takeIf { it != dest.absolutePath }?.let {
                    runCatching { File(it).delete() }
                }
                chat.customAvatarPath = dest.absolutePath
                chat.photoAttachmentGuid = cloudPhotoKey(job.recordId, job.version)
                chatBox.put(chat)
            }
        }
    }

    /** Tombstone: remove the chat, its messages and attachment rows. */
    private fun deleteChatLocked(
        chat: Chat,
        lookup: HistorySyncLookup,
        removedAttachments: MutableList<Attachment>,
        removedChats: MutableList<Chat>,
    ) {
        val messages = chat.messages.toList()
        messages.forEach { message ->
            message.dbAttachments.toList().forEach { removeAttachmentLocked(it, removedAttachments) }
        }
        messages.forEach { messageBox.remove(it) }
        chatBox.remove(chat)
        removedChats += chat
        lookup.removeChat(chat)
    }

    /** `Chat.findFromCloud`: cloud guid, then identifier, then exact participant set, else create. */
    private fun findOrImportChatLocked(cloud: UCloudChat, lookup: HistorySyncLookup): Chat {
        lookup.findByCloudGuid(cloud.groupId)?.let { return it }
        // The identifier alone cannot tell a phone number's SMS and iMessage
        // rows apart. An iMessage chat record must never adopt the SMS
        // thread: doing so leaves cloud history and wallpapers on the SMS
        // row while live APNs traffic builds a separate iMessage row. Exact
        // service matching falls through to participant matching (already
        // isRpSms-aware) and then creation. Only iMessage records reach here
        // today, but the check keeps this correct if that filter widens.
        lookup.findByIdentifierForService(cloud.chatIdentifier, sms = cloud.serviceName == "SMS")
            ?.let { return it }

        val participants = cloud.participants.map { MessageMapper.normalizeAddress(it) }.distinct()
        if (participants.isNotEmpty()) {
            val builder = chatBox.query().equal(Chat_.isRpSms, false)
            builder.link(Chat_.handles).`in`(
                Handle_.address,
                participants.toTypedArray(),
                QueryBuilder.StringOrder.CASE_SENSITIVE,
            )
            val candidates = builder.build().use { q -> q.find() }
            candidates.firstOrNull { chat ->
                val addresses = chat.handles.map { it.address }
                addresses.size == participants.size && addresses.containsAll(participants)
            }?.let {
                lookup.putChat(it)
                return it
            }
        }

        // Create (Dart: BackendSvc.createChat with existingGuid = groupId —
        // participants/name/style come from the cloud record at creation).
        val chat = Chat().apply {
            guid = cloud.guid
            chatIdentifier = cloud.chatIdentifier
            style = cloud.style
            isRpSms = false
            isArchived = false
            isPinned = false
            hasUnreadMessage = false
            senderIsKnown = true
            isRoutingStub = false
            lockChatName = false
            lockChatIcon = false
            displayName = cloud.displayName
            apnTitle = cloud.displayName
            groupVersion = cloud.groupVersion?.toLong()
            lastReadMessageGuid = cloud.lastSeenMessageGuid
            if (cloud.lastReadMessageTimestamp != 0L) {
                dbOnlyLatestMessageDate = dateFromAppleNs(cloud.lastReadMessageTimestamp)
            }
            guidRefs = ArrayList<String>().apply {
                add(cloud.guid)
                add(cloud.groupId)
            }
            handles.addAll(participants.map { lookup.resolveHandle(MessageMapper.toRustHandle(it), "iMessage") })
        }
        return try {
            chatBox.put(chat)
            chat.also(lookup::putChat)
        } catch (_: UniqueViolationException) {
            chatBox.query()
                .equal(Chat_.guid, cloud.guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { q -> q.findFirst() }
                ?.also(lookup::putChat)
                ?: chat
        }
    }

    private fun chatsByRecordIdsLocked(recordIds: Collection<String>): Map<String, List<Chat>> {
        val ids = recordIds.distinct()
        if (ids.isEmpty()) return emptyMap()
        return chatBox.query()
            .`in`(Chat_.ckRecordId, ids.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.find() }
            .groupBy(Chat::ckRecordId)
    }

    // ------------------------------------------------------------------
    // Message zone (message loop + Message.applyFromCloud)
    // ------------------------------------------------------------------

    private suspend fun applyMessagePage(records: List<UMessageChange>, lookup: HistorySyncLookup) {
        val transcriptBackgrounds = mutableListOf<TranscriptBackgroundUpdate>()
        invalidations.coalesce {
            records.chunked(DB_WRITE_BATCH_SIZE).forEach { batch ->
                val removedAttachments = arrayListOf<Attachment>()
                val acknowledgedLocalTombstones = hashSetOf<String>()
                store.runInTx {
                    val suppressedRecords = syncStore.suppressedMessageRecordIds().toHashSet()
                    val latestChatsToPersist = LinkedHashMap<Long, Chat>()
                    val cloudMessages = batch.mapNotNull(UMessageChange::message)
                    val messagesByGuid = messagesByGuidsLocked(
                        buildSet {
                            cloudMessages.forEach { cloud ->
                                add(cloud.guid)
                                cloud.associatedMessageGuid?.let(::add)
                            }
                        },
                    )
                    val attachmentsByGuid = attachmentsByGuidsLocked(
                        cloudMessages.flatMap(UCloudMessage::attachmentGuids),
                    )
                    val tombstonesByRecordId = messagesByRecordIdsLocked(
                        batch.filter { it.message == null }.map(UMessageChange::recordId),
                    )
                    for (record in batch) {
                        val cloud = record.message
                        if (cloud == null) {
                            if (record.recordId in suppressedRecords) {
                                acknowledgedLocalTombstones += record.recordId
                            }
                            tombstonesByRecordId[record.recordId]
                                .orEmpty()
                                .forEach { message ->
                                    messagesByGuid.remove(message.guid)
                                    deleteMessageLocked(message, latestChatsToPersist, removedAttachments)
                                }
                            continue
                        }
                        if (record.recordId in suppressedRecords ||
                            record.recordId in deletedMessageRecordsThisRun
                        ) continue
                        if (cloud.msgType == TRANSCRIPT_BACKGROUND_MESSAGE_TYPE) {
                            // A wallpaper record must never wedge the message
                            // zone: an undecodable payload or a chat we do not
                            // have (deleted locally, never imported) is skipped
                            // so the cursor keeps advancing — otherwise every
                            // incremental sync re-fails on the same page and no
                            // history lands after it (Dart never aborted here).
                            val background = cloud.transcriptBackground ?: continue
                            val chat = background.chatId
                                ?.let(lookup::chatForTranscriptBackground)
                                ?: lookup.chatForTranscriptBackground(cloud.chatId)
                                ?: cloud.sender.takeIf(String::isNotEmpty)?.let { sender ->
                                    lookup.directChatForAddress(
                                        MessageMapper.normalizeAddress(sender),
                                        preferIMessage = true,
                                    )
                                }
                                ?: continue
                            removeLegacyTranscriptBackgroundMessageLocked(
                                cloud.guid,
                                messagesByGuid,
                                latestChatsToPersist,
                                removedAttachments,
                            )
                            val version = background.version
                                .takeIf { it <= Long.MAX_VALUE.toULong() }
                                ?.toLong()
                                ?: continue
                            transcriptBackgrounds += TranscriptBackgroundUpdate(
                                chatId = chat.id,
                                version = version,
                                remove = background.remove,
                                mmcsXml = background.mmcsXml,
                            )
                            continue
                        }
                        applyMessageLocked(
                            record.recordId,
                            cloud,
                            lookup,
                            messagesByGuid,
                            attachmentsByGuid,
                            latestChatsToPersist,
                        )
                    }
                    latestChatsToPersist.values.forEach(chatBox::put)
                }
                removedAttachments.forEach { attachmentStore?.deleteLocalFiles(it) }
                if (acknowledgedLocalTombstones.isNotEmpty()) {
                    syncStore.acknowledgeSuppressedMessageTombstones(acknowledgedLocalTombstones)
                }
            }
        }
        if (transcriptBackgrounds.isNotEmpty()) {
            val handler = requireNotNull(transcriptBackgroundHandler) {
                "transcript background handler is unavailable"
            }
            transcriptBackgrounds
                .groupBy(TranscriptBackgroundUpdate::chatId)
                .values
                .mapNotNull { updates -> updates.maxByOrNull(TranscriptBackgroundUpdate::version) }
                .sortedBy(TranscriptBackgroundUpdate::version)
                .forEach { update ->
                    try {
                        handler.apply(update)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Apple drops the MMCS payloads of old wallpapers,
                        // so historical records routinely fail to
                        // download. Skip rather than abort: the next
                        // wallpaper change (or a FULL resync while the
                        // payload is still live) reapplies it.
                    }
                }
        }
        // Stale cloud duplicates found on this page are dropped remotely
        // after it applied (Dart's post-page dupDeleteMessages flush).
        flushDuplicateDeletes()
    }

    /** Tombstone: remove the row matched by ckRecordId. */
    private fun deleteMessageLocked(
        message: Message,
        latestChatsToPersist: MutableMap<Long, Chat>,
        removedAttachments: MutableList<Attachment>,
    ) {
        val chat = message.chat.target?.let { latestChatsToPersist[it.id] ?: it }
        val wasLatest = chat?.dbLatestMessage?.targetId == message.id
        message.dbAttachments.toList().forEach { removeAttachmentLocked(it, removedAttachments) }
        messageBox.remove(message)
        if (chat != null && wasLatest) {
            val latest = messageBox.query()
                .equal(Message_.chatId, chat.id)
                .isNull(Message_.dateDeleted)
                .orderDesc(Message_.dateCreated)
                .orderDesc(Message_.id)
                .build().use { it.findFirst() }
            chat.dbLatestMessage.target = latest
            chat.dbOnlyLatestMessageDate = latest?.dateCreated
            latestChatsToPersist[chat.id] = chat
        }
    }

    private fun removeLegacyTranscriptBackgroundMessageLocked(
        guid: String,
        messagesByGuid: MutableMap<String, Message>,
        latestChatsToPersist: MutableMap<Long, Chat>,
        removedAttachments: MutableList<Attachment>,
    ) {
        val message = messagesByGuid.remove(guid) ?: return
        val chat = message.chat.target?.let { latestChatsToPersist[it.id] ?: it }
        val wasLatest = chat?.dbLatestMessage?.targetId == message.id
        message.dbAttachments.toList().forEach { removeAttachmentLocked(it, removedAttachments) }
        messageBox.remove(message)
        if (chat != null && wasLatest) {
            val latest = messageBox.query()
                .equal(Message_.chatId, chat.id)
                .isNull(Message_.dateDeleted)
                .orderDesc(Message_.dateCreated)
                .orderDesc(Message_.id)
                .build().use { it.findFirst() }
            chat.dbLatestMessage.target = latest
            chat.dbOnlyLatestMessageDate = latest?.dateCreated
            latestChatsToPersist[chat.id] = chat
        }
    }

    private fun messagesByGuidsLocked(guids: Collection<String>): MutableMap<String, Message> {
        val values = guids.filter(String::isNotEmpty).distinct()
        if (values.isEmpty()) return HashMap()
        return messageBox.query()
            .`in`(Message_.guid, values.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.find() }
            .associateByTo(HashMap(), Message::guid)
    }

    private fun messagesByRecordIdsLocked(recordIds: Collection<String>): Map<String, List<Message>> {
        val values = recordIds.filter(String::isNotEmpty).distinct()
        if (values.isEmpty()) return emptyMap()
        return messageBox.query()
            .`in`(Message_.ckRecordId, values.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.find() }
            .filter { !it.ckRecordId.isNullOrEmpty() }
            .groupBy { requireNotNull(it.ckRecordId) }
    }

    private fun attachmentsByGuidsLocked(guids: Collection<String>): MutableMap<String, Attachment> {
        val values = guids.filter(String::isNotEmpty).distinct()
        if (values.isEmpty()) return HashMap()
        return attachmentBox.query()
            .`in`(Attachment_.guid, values.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.find() }
            .associateByTo(HashMap(), Attachment::guid)
    }

    /**
     * `Message.applyFromCloud`. Existing rows (matched by guid) are only
     * ckRecordId-refreshed — the Dart loop never overwrote local content.
     * A stale cloud duplicate under a different record id is deleted
     * remotely (Dart's dupDeleteMessages).
     */
    private fun applyMessageLocked(
        recordId: String,
        cloud: UCloudMessage,
        lookup: HistorySyncLookup,
        messagesByGuid: MutableMap<String, Message>,
        attachmentsByGuid: MutableMap<String, Attachment>,
        latestChatsToPersist: MutableMap<Long, Chat>,
    ) {
        val existing = messagesByGuid[cloud.guid]
        if (existing != null) {
            val oldRecordId = existing.ckRecordId
            if (oldRecordId != null && oldRecordId != recordId) {
                // Different cloud record for the same message: drop the
                // stale one. The deletion is remote-only; the in-tx local
                // half is nothing (the row keeps pointing at the new id).
                pendingDuplicateMessageDeletes += oldRecordId
            }
            if (oldRecordId != recordId || existing.ckSyncState != true) {
                existing.ckRecordId = recordId
                existing.ckSyncState = true
                messageBox.put(existing)
            }
            return
        }

        val chat = lookup.chatForCloudMessage(cloud.chatId) ?: return
        if (chat.isRpSms == true || chat.dateDeleted != null) return

        val message = Message().apply {
            guid = cloud.guid
            ckRecordId = recordId
            ckSyncState = true
            error = cloud.error.takeUnless { it == 0L }
            dateCreated = dateFromAppleNs(cloud.time)
            isFromMe = (cloud.flagsBits and FLAG_IS_FROM_ME) != 0L
            text = cloud.text
            subject = cloud.subject
            hasAttachments = cloud.hasAttachments
            balloonBundleId = cloud.balloonBundleId
            dbMetadata = cloud.linkJson
            hasApplePayloadData = cloud.hasPayloadData && cloud.linkJson == null
            dbMessageSummaryInfo = cloud.summaryInfoJson
            expressiveSendStyleId = cloud.effect
            dateRead = cloud.dateReadNs?.let(::dateFromAppleNs)
            dateDelivered = cloud.dateDeliveredNs?.let(::dateFromAppleNs)
            associatedMessageType = reactionTypeFromCode(cloud.associatedMessageType)
            associatedMessageGuid = cloud.associatedMessageGuid
            associatedMessagePart = cloud.associatedMessagePart?.toLong()
            threadOriginatorGuid = cloud.threadOriginatorGuid
            threadOriginatorPart = cloud.threadOriginatorPart
            associatedMessageEmoji = cloud.associatedMessageEmoji
            if (cloud.sender.isNotEmpty()) {
                val handle = lookup.resolveHandle(cloud.sender, "iMessage")
                handleRelation.target = handle
                handleId = handle.originalROWID
            }
        }
        // Wire the chat relation outside `apply` — Message.chat would shadow
        // the resolved chat row inside it.
        message.chat.target = chat

        try {
            messageBox.put(message)
        } catch (_: UniqueViolationException) {
            // Lost a race against a concurrent delivery of the same guid.
            return
        }
        messagesByGuid[cloud.guid] = message

        cloud.attachmentGuids.forEach { guid ->
            attachmentsByGuid[guid]?.let { attachment ->
                attachment.message.target = message
                attachmentBox.put(attachment)
            }
        }

        // Reaction bookkeeping (Message.save): flag the target message.
        if (message.associatedMessageGuid != null && message.associatedMessageType != null) {
            messagesByGuid[message.associatedMessageGuid]?.let { target ->
                if (!target.hasReactions) {
                    target.hasReactions = true
                    messageBox.put(target)
                }
            }
        }

        // Latest-message wiring (Chat.setLatestMessage) — but unlike the
        // live intake, historical backfills never touch unread state. Use the
        // persisted relation id/date instead of resolving the full latest
        // Message, which may contain multi-megabyte payload strings.
        if (shouldReplaceLatestMessage(chat, message.dateCreated)) {
            chat.dbLatestMessage.target = message
            chat.dbOnlyLatestMessageDate = message.dateCreated
            latestChatsToPersist[chat.id] = chat
        }
    }

    /**
     * Cloud-record ids found to be stale duplicates during page
     * application; pushed after the page that collected them (Dart's
     * post-page dupDeleteMessages flush).
     */
    private val pendingDuplicateMessageDeletes = ArrayDeque<String>()

    /** Push any stale-duplicate deletions collected by the last page. */
    private suspend fun flushDuplicateDeletes() {
        val ids = pendingDuplicateMessageDeletes.toList()
        pendingDuplicateMessageDeletes.clear()
        if (ids.isNotEmpty()) port.deleteMessagesRemote(ids)
    }

    // ------------------------------------------------------------------
    // Attachment zone
    // ------------------------------------------------------------------

    private suspend fun applyAttachmentPage(records: List<UAttachmentChange>) {
        invalidations.coalesce {
            records.chunked(DB_WRITE_BATCH_SIZE).forEach { batch ->
                val removedAttachments = arrayListOf<Attachment>()
                val acknowledgedLocalTombstones = hashSetOf<String>()
                store.runInTx {
                    val suppressedRecords = syncStore.suppressedAttachmentRecordIds().toHashSet()
                    val cloudAttachments = batch.mapNotNull(UAttachmentChange::attachment)
                    val attachmentsByGuid = attachmentsByGuidsLocked(
                        cloudAttachments.map(UCloudAttachment::guid),
                    )
                    val messagesByGuid = messagesByGuidsLocked(
                        cloudAttachments.mapNotNull(UCloudAttachment::messageGuid),
                    )
                    val tombstonesByRecordId = attachmentsByRecordIdsLocked(
                        batch.filter { it.attachment == null }.map(UAttachmentChange::recordId),
                    )
                    for (record in batch) {
                        val cloud = record.attachment
                        if (cloud == null) {
                            if (record.recordId in suppressedRecords) {
                                acknowledgedLocalTombstones += record.recordId
                            }
                            tombstonesByRecordId[record.recordId]
                                .orEmpty()
                                .forEach { attachment ->
                                    attachmentsByGuid.remove(attachment.guid)
                                    removeAttachmentLocked(attachment, removedAttachments)
                                }
                            continue
                        }
                        if (record.recordId in suppressedRecords ||
                            record.recordId in deletedAttachmentRecordsThisRun
                        ) continue
                        applyAttachmentLocked(record.recordId, cloud, attachmentsByGuid, messagesByGuid)
                    }
                }
                removedAttachments.forEach { attachmentStore?.deleteLocalFiles(it) }
                if (acknowledgedLocalTombstones.isNotEmpty()) {
                    syncStore.acknowledgeSuppressedAttachmentTombstones(acknowledgedLocalTombstones)
                }
            }
        }
        flushDuplicateAttachmentDeletes()
    }

    private fun removeAttachmentLocked(attachment: Attachment, removedAttachments: MutableList<Attachment>) {
        attachmentBox.remove(attachment)
        removedAttachments += attachment
    }

    private fun applyAttachmentLocked(
        recordId: String,
        cloud: UCloudAttachment,
        attachmentsByGuid: MutableMap<String, Attachment>,
        messagesByGuid: Map<String, Message>,
    ) {
        val existing = attachmentsByGuid[cloud.guid]
        if (existing != null) {
            existing.ckRecordId?.takeIf { it != recordId }
                ?.let(pendingDuplicateAttachmentDeletes::add)
            if (existing.ckRecordId != recordId || existing.metadata?.get("cloud") != recordId) {
                existing.ckRecordId = recordId
                existing.metadata = existing.metadata.orEmpty() + ("cloud" to recordId)
                attachmentBox.put(existing)
            }
            return
        }

        val message = cloud.messageGuid?.let(messagesByGuid::get)
        val attachment = Attachment().apply {
            guid = cloud.guid
            ckRecordId = recordId
            uti = cloud.uti
            mimeType = cloud.mimeType
            isOutgoing = cloud.isOutgoing
            transferName = cloud.transferName
            totalBytes = cloud.totalBytes
            isDownloaded = false
            metadata = mapOf("cloud" to recordId)
            this.message.target = message
        }
        try {
            attachmentBox.put(attachment)
            attachmentsByGuid[cloud.guid] = attachment
        } catch (_: UniqueViolationException) {
            // A live push may have inserted the same guid during the page.
        }
    }

    private fun attachmentsByRecordIdsLocked(recordIds: Collection<String>): Map<String, List<Attachment>> {
        val values = recordIds.filter(String::isNotEmpty).distinct()
        if (values.isEmpty()) return emptyMap()
        return attachmentBox.query()
            .`in`(Attachment_.ckRecordId, values.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.find() }
            .filter { !it.ckRecordId.isNullOrEmpty() }
            .groupBy { requireNotNull(it.ckRecordId) }
    }

    private val pendingDuplicateAttachmentDeletes = ArrayDeque<String>()

    private suspend fun flushDuplicateAttachmentDeletes() {
        val ids = pendingDuplicateAttachmentDeletes.toList()
        pendingDuplicateAttachmentDeletes.clear()
        if (ids.isNotEmpty()) port.deleteAttachmentsRemote(ids)
    }
}

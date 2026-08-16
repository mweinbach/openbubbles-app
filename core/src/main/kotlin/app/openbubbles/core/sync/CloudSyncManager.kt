package app.openbubbles.core.sync

import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.intake.HandleResolver
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.Handle_
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.exception.UniqueViolationException
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.UAttachmentChange
import uniffi.rust_lib_bluebubbles.UChatChange
import uniffi.rust_lib_bluebubbles.UCloudAttachment
import uniffi.rust_lib_bluebubbles.UCloudChat
import uniffi.rust_lib_bluebubbles.UCloudMessage
import uniffi.rust_lib_bluebubbles.UMessageChange
import uniffi.rust_lib_bluebubbles.USyncState
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
 *    applies when the cloud group version is newer);
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
 * All writes run on [Dispatchers.IO]; runs are single-flight ([sync]
 * serializes on a mutex). Safe to call from any thread.
 */
class CloudSyncManager(
    private val store: BoxStore,
    private val port: CloudSyncPort,
    private val syncStore: CloudSyncStateStore = InMemoryCloudSyncStateStore(),
    private val attachmentStore: AttachmentStore? = null,
) {

    companion object {
        /** ns between the Unix and Apple (CoreData, 2001-01-01) epochs. */
        private const val APPLE_EPOCH_OFFSET_NS = 978307200_000_000_000L

        /** `MessageFlags.IS_FROM_ME`. */
        private const val FLAG_IS_FROM_ME = 1L shl 2

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
    }

    private val chatBox = store.boxFor(Chat::class.java)
    private val messageBox = store.boxFor(Message::class.java)
    private val attachmentBox = store.boxFor(Attachment::class.java)
    private val mutex = Mutex()
    private val cancelled = AtomicBoolean(false)

    private val _progress = MutableStateFlow(SyncProgress(SyncPhase.IDLE))

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
                port.deleteMessagesRemote(syncStore.pendingMessageDeletes())
                syncStore.savePendingMessageDeletes(emptyList())
                port.deleteAttachmentsRemote(syncStore.pendingAttachmentDeletes())
                syncStore.savePendingAttachmentDeletes(emptyList())
                port.deleteChatsRemote(syncStore.pendingChatDeletes())
                syncStore.savePendingChatDeletes(emptyList())

                // 3. Chat zone.
                update(SyncPhase.CHATS)
                var chatCursor = if (mode == SyncMode.INCREMENTAL) syncStore.chatCursor() else null
                while (true) {
                    if (cancelled.get()) return@withContext finish(cancelledFlag = true)
                    val page = port.chatsPage(chatCursor)
                    applyChatPage(page.records)
                    state = state.copy(
                        chatsDone = state.chatsDone + page.records.count { it.chat != null }.toULong(),
                        chatTombstones = state.chatTombstones + page.records.count { it.chat == null }.toULong(),
                    )
                    chatCursor = page.nextCursor
                    syncStore.saveChatCursor(chatCursor)
                    update()
                    if (!page.more) break
                }

                // 4. Message zone.
                update(SyncPhase.MESSAGES)
                var messageCursor = if (mode == SyncMode.INCREMENTAL) syncStore.messageCursor() else null
                while (true) {
                    if (cancelled.get()) return@withContext finish(cancelledFlag = true)
                    val page = port.messagesPage(messageCursor)
                    applyMessagePage(page.records)
                    state = state.copy(
                        messagesDone = state.messagesDone + page.records.count { it.message != null }.toULong(),
                        messageTombstones = state.messageTombstones + page.records.count { it.message == null }.toULong(),
                    )
                    messageCursor = page.nextCursor
                    syncStore.saveMessageCursor(messageCursor)
                    update()
                    if (!page.more) break
                }

                // 5. Attachment metadata zone. Payloads remain in CloudKit
                // until the user opens/downloads an attachment.
                update(SyncPhase.ATTACHMENTS)
                var attachmentCursor = if (mode == SyncMode.INCREMENTAL) syncStore.attachmentCursor() else null
                while (true) {
                    if (cancelled.get()) return@withContext finish(cancelledFlag = true)
                    val page = port.attachmentsPage(attachmentCursor)
                    applyAttachmentPage(page.records)
                    state = state.copy(
                        attachmentsDone = state.attachmentsDone +
                            page.records.count { it.attachment != null }.toULong(),
                        attachmentTombstones = state.attachmentTombstones +
                            page.records.count { it.attachment == null }.toULong(),
                    )
                    attachmentCursor = page.nextCursor
                    syncStore.saveAttachmentCursor(attachmentCursor)
                    update()
                    if (!page.more) break
                }
            } catch (e: Exception) {
                return@withContext finish(error = e.message ?: e.javaClass.simpleName)
            }

            finish()
        }
    }

    // ------------------------------------------------------------------
    // Chat zone (doCloudKitSyncPrivate chat loop + findFromCloud/applyFromCloud)
    // ------------------------------------------------------------------

    private fun applyChatPage(records: List<UChatChange>) {
        records.chunked(DB_WRITE_BATCH_SIZE).forEach { batch ->
            store.runInTx {
                for (record in batch) {
                    val cloud = record.chat
                    if (cloud == null) {
                        deleteChatByRecordIdLocked(record.recordId)
                        continue
                    }
                    if (cloud.serviceName != "iMessage") continue // Dart: iMessage only
                    val chat = findOrImportChatLocked(cloud)
                    // Always refresh identifiers (Dart applies these before the
                    // version gate).
                    chat.chatIdentifier = cloud.chatIdentifier
                    chat.ckRecordId = record.recordId
                    chat.cloudGuid = cloud.groupId
                    val localVersion = chat.groupVersion ?: 1L
                    val cloudVersion = cloud.groupVersion?.toLong()
                    if (cloudVersion == null || cloudVersion <= localVersion) {
                        chat.ckSyncState = cloudVersion == localVersion
                    } else {
                        // Cloud is newer: apply the full chat state.
                        chat.style = cloud.style
                        chat.lastReadMessageGuid = cloud.lastSeenMessageGuid
                        chat.groupVersion = cloudVersion
                        chat.displayName = cloud.displayName
                        chat.dbOnlyLatestMessageDate = dateFromAppleNs(cloud.lastReadMessageTimestamp)
                        // Cloud chats include me — mirror Dart and keep all.
                        chat.handles.clear()
                        chat.handles.addAll(
                            cloud.participants.map { HandleResolver.resolve(store, it, "iMessage") },
                        )
                        if (cloud.lastAddressedHandle.isNotEmpty()) {
                            chat.usingHandle = MessageMapper.toRustHandle(cloud.lastAddressedHandle)
                        }
                        chat.ckSyncState = true
                    }
                    chatBox.put(chat)
                }
            }
        }
    }

    /** Tombstone: remove the chat, its messages and attachment rows. */
    private fun deleteChatByRecordIdLocked(recordId: String) {
        chatBox.query()
            .equal(Chat_.ckRecordId, recordId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { q -> q.find() }
            .forEach { chat ->
                val messages = chat.messages.toList()
                messages.forEach { message ->
                    message.dbAttachments.toList().forEach(::removeAttachmentLocked)
                }
                messages.forEach { messageBox.remove(it) }
                chatBox.remove(chat)
            }
    }

    /** `Chat.findFromCloud`: cloud guid, then identifier, then exact participant set, else create. */
    private fun findOrImportChatLocked(cloud: UCloudChat): Chat {
        chatBox.query()
            .equal(Chat_.cloudGuid, cloud.groupId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { q -> q.findFirst() }?.let { return it }

        chatBox.query()
            .equal(Chat_.chatIdentifier, cloud.chatIdentifier, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { q -> q.findFirst() }?.let { return it }

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
            }?.let { return it }
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
            handles.addAll(participants.map { HandleResolver.resolve(store, MessageMapper.toRustHandle(it), "iMessage") })
        }
        return try {
            chatBox.put(chat)
            chat
        } catch (_: UniqueViolationException) {
            chatBox.query()
                .equal(Chat_.guid, cloud.guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { q -> q.findFirst() } ?: chat
        }
    }

    // ------------------------------------------------------------------
    // Message zone (message loop + Message.applyFromCloud)
    // ------------------------------------------------------------------

    private suspend fun applyMessagePage(records: List<UMessageChange>) {
        records.chunked(DB_WRITE_BATCH_SIZE).forEach { batch ->
            store.runInTx {
                for (record in batch) {
                    val cloud = record.message
                    if (cloud == null) {
                        deleteMessageByRecordIdLocked(record.recordId)
                        continue
                    }
                    applyMessageLocked(record.recordId, cloud)
                }
            }
        }
        // Stale cloud duplicates found on this page are dropped remotely
        // after it applied (Dart's post-page dupDeleteMessages flush).
        flushDuplicateDeletes()
    }

    /** Tombstone: remove the row matched by ckRecordId. */
    private fun deleteMessageByRecordIdLocked(recordId: String) {
        messageBox.query()
            .equal(Message_.ckRecordId, recordId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { q -> q.find() }
            .forEach { message ->
                message.dbAttachments.toList().forEach(::removeAttachmentLocked)
                messageBox.remove(message)
            }
    }

    /**
     * `Message.applyFromCloud`. Existing rows (matched by guid) are only
     * ckRecordId-refreshed — the Dart loop never overwrote local content.
     * A stale cloud duplicate under a different record id is deleted
     * remotely (Dart's dupDeleteMessages).
     */
    private fun applyMessageLocked(recordId: String, cloud: UCloudMessage) {
        val existing = messageBox.query()
            .equal(Message_.guid, cloud.guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { q -> q.findFirst() }

        if (existing != null) {
            val oldRecordId = existing.ckRecordId
            if (oldRecordId != null && oldRecordId != recordId) {
                // Different cloud record for the same message: drop the
                // stale one. The deletion is remote-only; the in-tx local
                // half is nothing (the row keeps pointing at the new id).
                pendingDuplicateMessageDeletes += oldRecordId
            }
            existing.ckRecordId = recordId
            existing.ckSyncState = true
            messageBox.put(existing)
            return
        }

        val chat = chatForCloudMessage(cloud.chatId) ?: return
        if (chat.isRpSms == true) return

        val message = Message().apply {
            guid = cloud.guid
            ckRecordId = recordId
            ckSyncState = true
            error = cloud.error
            dateCreated = dateFromAppleNs(cloud.time)
            isFromMe = (cloud.flagsBits and FLAG_IS_FROM_ME) != 0L
            text = cloud.text
            subject = cloud.subject
            hasAttachments = cloud.hasAttachments
            balloonBundleId = cloud.balloonBundleId
            hasApplePayloadData = cloud.hasPayloadData
            dbMessageSummaryInfo = cloud.summaryInfoJson
            expressiveSendStyleId = cloud.effect
            dateRead = cloud.dateReadNs?.let(::dateFromAppleNs)
            dateDelivered = cloud.dateDeliveredNs?.let(::dateFromAppleNs)
            associatedMessageType = reactionTypeFromCode(cloud.associatedMessageType)
            associatedMessageGuid = cloud.associatedMessageGuid
            threadOriginatorGuid = cloud.threadOriginatorGuid
            threadOriginatorPart = cloud.threadOriginatorPart
            associatedMessageEmoji = cloud.associatedMessageEmoji
            if (cloud.sender.isNotEmpty()) {
                val handle = HandleResolver.resolve(store, cloud.sender, "iMessage")
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

        cloud.attachmentGuids.forEach { guid ->
            attachmentBox.query()
                .equal(Attachment_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
                ?.let { attachment ->
                    attachment.message.target = message
                    attachmentBox.put(attachment)
                }
        }

        // Reaction bookkeeping (Message.save): flag the target message.
        if (message.associatedMessageGuid != null && message.associatedMessageType != null) {
            messageBox.query()
                .equal(Message_.guid, message.associatedMessageGuid!!, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { q -> q.findFirst() }
                ?.let { target ->
                    if (!target.hasReactions) {
                        target.hasReactions = true
                        messageBox.put(target)
                    }
                }
        }

        // Latest-message wiring (Chat.setLatestMessage) — but unlike the
        // live intake, historical backfills never touch unread state.
        val latest = chat.dbLatestMessage.target
        val isNewer = latest == null ||
            (message.dateCreated != null &&
                (latest.dateCreated == null || message.dateCreated.after(latest.dateCreated)))
        if (isNewer) {
            chat.dbLatestMessage.target = message
            chat.dbOnlyLatestMessageDate = message.dateCreated
            if (chat.dateDeleted != null) chat.dateDeleted = null // Chat.unDelete
            chatBox.put(chat)
        }
    }

    /** `Message.applyFromCloud` chat resolution for `chatId`. */
    private fun chatForCloudMessage(chatId: String): Chat? {
        if (chatId.contains(";")) {
            val parts = chatId.split(";")
            val identifier = parts.getOrNull(2) ?: return null
            chatBox.query()
                .equal(Chat_.chatIdentifier, identifier, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { q -> q.findFirst() }?.let { return it }
        }
        chatBox.query()
            .equal(Chat_.cloudGuid, chatId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { q -> q.findFirst() }?.let { return it }
        // findByRustGuid parity: guid or guidRefs.
        chatBox.query()
            .equal(Chat_.guid, chatId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { q -> q.findFirst() }?.let { return it }
        chatBox.query()
            .containsElement(Chat_.guidRefs, chatId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { q -> q.findFirst() }?.let { return it }
        return null
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
        records.chunked(DB_WRITE_BATCH_SIZE).forEach { batch ->
            store.runInTx {
                for (record in batch) {
                    val cloud = record.attachment
                    if (cloud == null) {
                        deleteAttachmentByRecordIdLocked(record.recordId)
                        continue
                    }
                    applyAttachmentLocked(record.recordId, cloud)
                }
            }
        }
        flushDuplicateAttachmentDeletes()
    }

    private fun deleteAttachmentByRecordIdLocked(recordId: String) {
        attachmentBox.query()
            .equal(Attachment_.ckRecordId, recordId, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.find() }
            .forEach(::removeAttachmentLocked)
    }

    private fun removeAttachmentLocked(attachment: Attachment) {
        attachmentStore?.deleteLocalFiles(attachment)
        attachmentBox.remove(attachment)
    }

    private fun applyAttachmentLocked(recordId: String, cloud: UCloudAttachment) {
        val existing = attachmentBox.query()
            .equal(Attachment_.guid, cloud.guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
        if (existing != null) {
            existing.ckRecordId?.takeIf { it != recordId }
                ?.let(pendingDuplicateAttachmentDeletes::add)
            existing.ckRecordId = recordId
            existing.metadata = existing.metadata.orEmpty() + ("cloud" to recordId)
            attachmentBox.put(existing)
            return
        }

        val message = cloud.messageGuid?.let { guid ->
            messageBox.query()
                .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
        }
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
        } catch (_: UniqueViolationException) {
            // A live push may have inserted the same guid during the page.
        }
    }

    private val pendingDuplicateAttachmentDeletes = ArrayDeque<String>()

    private suspend fun flushDuplicateAttachmentDeletes() {
        val ids = pendingDuplicateAttachmentDeletes.toList()
        pendingDuplicateAttachmentDeletes.clear()
        if (ids.isNotEmpty()) port.deleteAttachmentsRemote(ids)
    }
}

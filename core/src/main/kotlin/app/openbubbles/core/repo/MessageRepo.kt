package app.openbubbles.core.repo

import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.intake.HandleResolver
import app.openbubbles.core.model.AttachmentStamp
import app.openbubbles.core.model.MessageItem
import app.openbubbles.core.model.InteractivePayloadParser
import app.openbubbles.core.model.MessageKind
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.core.model.MessageReaction
import app.openbubbles.core.model.MessageStatus
import app.openbubbles.core.model.StickerPlacement
import app.openbubbles.db.Attachment
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.ContactV2
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.query.Query
import io.objectbox.query.QueryCondition
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Date
import java.util.WeakHashMap

/** CloudKit zone identities; local-only operations never publish this value. */
data class CloudDeletionRecordIds(
    val chatRecordIds: Set<String> = emptySet(),
    val messageRecordIds: Set<String> = emptySet(),
    val attachmentRecordIds: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = chatRecordIds.isEmpty() && messageRecordIds.isEmpty() && attachmentRecordIds.isEmpty()
}

/** Platform-owned durable queue for explicitly synchronized Apple deletions. */
interface CloudDeletionSink {
    fun enqueue(recordIds: CloudDeletionRecordIds)
    fun restore(recordIds: CloudDeletionRecordIds)
    fun suppressLocally(recordIds: CloudDeletionRecordIds)
}

/**
 * Per-store ownership lets transient repository instances share the Android
 * attachment root and account-fenced durable CloudKit deletion queue without
 * moving Android types into :core or changing the ObjectBox model.
 */
object StoreDeletionCoordinators {
    private data class Registration(
        val attachmentsRoot: File,
        val privateRoots: List<File>,
        val cloudDeletionSink: CloudDeletionSink,
    )

    private val lock = Any()
    private val registrations = WeakHashMap<BoxStore, Registration>()
    private val ownedChatDirectories = setOf("chat_backgrounds", "chat_avatars", "group_icons")

    fun register(
        store: BoxStore,
        attachmentsRoot: File,
        privateRoots: List<File> = emptyList(),
        cloudDeletionSink: CloudDeletionSink,
    ) {
        synchronized(lock) {
            registrations[store] = Registration(
                attachmentsRoot = attachmentsRoot,
                privateRoots = (privateRoots + attachmentsRoot).distinct(),
                cloudDeletionSink = cloudDeletionSink,
            )
        }
    }

    fun unregister(store: BoxStore) {
        synchronized(lock) { registrations.remove(store) }
    }

    internal fun enqueue(
        store: BoxStore,
        recordIds: CloudDeletionRecordIds,
        requireRegistration: Boolean,
    ) {
        if (recordIds.isEmpty) return
        val registration = synchronized(lock) { registrations[store] }
        if (registration == null) {
            check(!requireRegistration) { "CloudKit deletion queue is unavailable for this account" }
            return
        }
        registration.cloudDeletionSink.enqueue(recordIds)
    }

    internal fun restore(store: BoxStore, recordIds: CloudDeletionRecordIds) {
        if (recordIds.isEmpty) return
        synchronized(lock) { registrations[store] }?.cloudDeletionSink?.restore(recordIds)
    }

    internal fun suppressLocally(store: BoxStore, recordIds: CloudDeletionRecordIds) {
        if (recordIds.isEmpty) return
        synchronized(lock) { registrations[store] }?.cloudDeletionSink?.suppressLocally(recordIds)
    }

    internal fun deleteAttachmentFiles(
        store: BoxStore,
        attachments: Collection<Attachment>,
        fallback: AttachmentStore? = null,
    ) {
        if (attachments.isEmpty()) return
        val registration = synchronized(lock) { registrations[store] }
        val disk = fallback ?: registration?.let { AttachmentStore(store, it.attachmentsRoot) } ?: return
        attachments.distinctBy { it.guid }.forEach(disk::deleteLocalFiles)
    }

    internal fun deleteChatFiles(store: BoxStore, chats: Collection<Chat>) {
        if (chats.isEmpty()) return
        val registration = synchronized(lock) { registrations[store] } ?: return
        val roots = registration.privateRoots.flatMap { root ->
            ownedChatDirectories.map { directory -> root.toPath().resolve(directory).normalize().toAbsolutePath() }
        }
        chats.flatMap { chat ->
            listOfNotNull(chat.customAvatarPath, chat.customBackgroundPath, chat.transcriptPosterPath)
        }.distinct().forEach { storedPath ->
            val candidate = File(storedPath).toPath().normalize().toAbsolutePath()
            val root = roots.firstOrNull { candidate.parent == it } ?: return@forEach
            if (Files.isSymbolicLink(root)) return@forEach
            if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) Files.delete(candidate)
        }
    }
}

internal fun cloudDeletionRecordIds(
    chats: Collection<Chat> = emptyList(),
    messages: Collection<Message> = emptyList(),
    attachments: Collection<Attachment> = emptyList(),
    includeChats: Boolean = false,
): CloudDeletionRecordIds {
    val appleChats = chats.filter { it.isRpSms != true }
    val appleChatIds = appleChats.mapTo(HashSet()) { it.id }
    val appleMessages = messages.filter { message ->
        val chat = message.chat.target
        chat != null && chat.isRpSms != true &&
            (appleChatIds.isEmpty() || chat.id in appleChatIds)
    }
    val appleMessageIds = appleMessages.mapTo(HashSet()) { it.id }
    return CloudDeletionRecordIds(
        chatRecordIds = if (includeChats) {
            appleChats.mapNotNullTo(LinkedHashSet()) { it.ckRecordId?.takeIf(String::isNotBlank) }
        } else {
            emptySet()
        },
        messageRecordIds = appleMessages.mapNotNullTo(LinkedHashSet()) {
            it.ckRecordId?.takeIf(String::isNotBlank)
        },
        attachmentRecordIds = attachments.mapNotNullTo(LinkedHashSet()) { attachment ->
            attachment.ckRecordId?.takeIf { it.isNotBlank() && attachment.message.targetId in appleMessageIds }
        },
    )
}

/**
 * ObjectBox-backed repository for a chat's transcript.
 *
 * Reads: newest-first pages (load-more by offset) plus a reactive flow of the
 * newest page. Writes: staging an outgoing message so the UI can render the
 * bubble while the rust send queue works; receipts / guid swaps are applied by
 * [MessageIngestor] when the push pipeline echoes the send.
 */
class MessageRepo(
    private val store: BoxStore,
    private val chatRepo: ChatRepo = ChatRepo(store),
    /**
     * Data root holding `attachments/` (the Flutter-era `app_flutter`
     * directory on Android). When absent the projection reports no payload
     * identity, which is what non-rendering callers and host tests want.
     */
    attachmentsRoot: File? = null,
) {

    private val chatBox = store.boxFor(Chat::class.java)
    private val messageBox = store.boxFor(Message::class.java)
    private val invalidations = StoreInvalidationCoordinators.forStore(store)
    private val attachmentDisk = attachmentsRoot?.let { AttachmentStore(store, it) }

    data class OutgoingAttachmentStage(
        val guid: String,
        val mimeType: String,
        val uti: String,
        val transferName: String,
        val totalBytes: Long,
    )

    /** Newest-first page of messages for a chat ([offset] skips older rows). */
    fun messages(chatId: Long, limit: Int = 50, offset: Int = 0): List<MessageItem> =
        store.callInReadTx {
            messageQuery(conversationChatIds(chatId)).use { query ->
                projectPage(query.find(offset.toLong(), limit.toLong()))
            }
        }

    /** Newest-first page strictly older than [beforeId]'s time/id cursor. */
    fun messagesBefore(chatId: Long, beforeId: Long, limit: Int): List<MessageItem> =
        store.callInReadTx {
            val anchor = messageBox.get(beforeId) ?: return@callInReadTx emptyList()
            val chatIds = conversationChatIds(chatId)
            if (anchor.chat.targetId !in chatIds) return@callInReadTx emptyList()
            messageQuery(chatIds, anchor).use { query ->
                projectPage(query.find(0, limit.toLong()))
            }
        }

    /**
     * Projects a page with one batched reaction query instead of one query
     * per reacted-to message (the projection re-runs on every DB write a
     * subscription observes, so the per-row queries compounded fast).
     */
    private fun projectPage(page: List<Message>): List<MessageItem> {
        val reactionTargets = page
            .filter { it.hasReactions && kindOf(it) == MessageKind.TEXT }
            .map { it.guid }
        val reactionChatIds = page.map { it.chat.targetId }.filter { it != 0L }.distinct()
        val reactionsByTarget = activeReactionsByTarget(reactionTargets, reactionChatIds)
        return page.map { message -> toItem(message, reactionsByTarget[message.guid].orEmpty()) }
    }

    /**
     * Reactive newest-first bounded page. The entity-type subscription is only
     * an invalidation signal; every emission executes [messages] with a native
     * ObjectBox limit instead of materializing the entire transcript first.
     *
     * The first page comes from the subscription-readiness signal rather than a
     * merged `flowOf(Unit)`: a separately merged initial value can query before
     * the change subscription is installed, and any commit landing in that
     * window is never observed. The window reopens on every resubscription
     * (the UI restarts this flow whenever its bounded window grows), which is
     * exactly when an incoming attachment download completes — the resulting
     * stale transcript only repairs itself when the conversation is reopened.
     */
    fun observeMessages(chatId: Long, limit: Int = 50): Flow<List<MessageItem>> =
        invalidations.changesForWithInitial(
            StoreEntityChange.MESSAGE,
            StoreEntityChange.ATTACHMENT,
            StoreEntityChange.CONTACT,
        )
            .conflate()
            .map { messages(chatId, limit) }
            // The subscriptions are store-wide, so a write in any other chat
            // re-runs this page too. MessageItem is a pure DB projection;
            // dropping identical pages here spares every downstream stage
            // (UI mapping, enrichment, recomposition) for unrelated writes.
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    /** Invalidates warmed UI projections when transcript display data changes. */
    fun observeTranscriptChanges(): Flow<Unit> =
        invalidations.changesForWithInitial(
            StoreEntityChange.MESSAGE,
            StoreEntityChange.ATTACHMENT,
            StoreEntityChange.CONTACT,
        ).conflate()

    private fun messageQuery(chatIds: List<Long>, before: Message? = null): Query<Message> {
        var chatCondition: QueryCondition<Message> = Message_.chatId.equal(chatIds.first())
        chatIds.drop(1).forEach { chatId ->
            chatCondition = chatCondition.or(Message_.chatId.equal(chatId))
        }
        var condition = chatCondition
            .and(Message_.associatedMessageGuid.isNull())
            .and(Message_.dateDeleted.isNull())
        if (before != null) {
            val cursor = before.dateCreated?.let { date ->
                Message_.dateCreated.less(date).or(
                    Message_.dateCreated.equal(date).and(Message_.id.less(before.id)),
                )
            } ?: Message_.id.less(before.id)
            condition = condition.and(cursor)
        }
        return messageBox.query(condition)
            .orderDesc(Message_.dateCreated)
            .orderDesc(Message_.id)
            .build()
    }

    fun messageByGuid(guid: String): Message? =
        messageBox.query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    /** Stable numeric identity survives staging-guid promotion and process restart. */
    fun messageById(messageId: Long): Message? = messageBox.get(messageId)

    fun bookmarked(chatId: Long, limit: Int = 0): List<MessageItem> {
        val chatIds = conversationChatIds(chatId)
        var condition: QueryCondition<Message> = Message_.chatId.equal(chatIds.first())
        chatIds.drop(1).forEach { id -> condition = condition.or(Message_.chatId.equal(id)) }
        return store.callInReadTx {
            val found = messageBox.query(
                condition
                    .and(Message_.isBookmarked.equal(true))
                    .and(Message_.associatedMessageGuid.isNull())
                    .and(Message_.dateDeleted.isNull()),
            ).orderDesc(Message_.dateCreated).build().use { it.find() }
            projectPage(if (limit > 0) found.take(limit) else found)
        }
    }

    /** Observe bookmarked rows and the attachment/contact data in their projections. */
    fun observeBookmarked(chatId: Long, limit: Int = 0): Flow<List<MessageItem>> =
        invalidations.changesForWithInitial(
            StoreEntityChange.MESSAGE,
            StoreEntityChange.ATTACHMENT,
            StoreEntityChange.CONTACT,
        )
            .conflate()
            .map { bookmarked(chatId, limit) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun recentlyDeleted(chatId: Long? = null, limit: Int = 0): List<MessageItem> {
        var condition: QueryCondition<Message> = Message_.dateDeleted.notNull()
            .and(Message_.associatedMessageGuid.isNull())
        if (chatId != null) {
            val chatIds = conversationChatIds(chatId)
            var chatCondition: QueryCondition<Message> = Message_.chatId.equal(chatIds.first())
            chatIds.drop(1).forEach { id -> chatCondition = chatCondition.or(Message_.chatId.equal(id)) }
            condition = condition.and(chatCondition)
        }
        return store.callInReadTx {
            val found = messageBox.query(condition)
                .orderDesc(Message_.dateDeleted)
                .orderDesc(Message_.dateCreated)
                .build().use { it.find() }
            projectPage(if (limit > 0) found.take(limit) else found)
        }
    }

    /** Observe recoverable rows across all chats or one grouped conversation. */
    fun observeRecentlyDeleted(
        chatId: Long? = null,
        limit: Int = 0,
    ): Flow<List<MessageItem>> =
        invalidations.changesForWithInitial(
            StoreEntityChange.MESSAGE,
            StoreEntityChange.ATTACHMENT,
            StoreEntityChange.CONTACT,
        )
            .conflate()
            .map { recentlyDeleted(chatId, limit) }
            .distinctUntilChanged()
            .flowOn(Dispatchers.IO)

    fun setBookmarked(messageIds: Collection<Long>, bookmarked: Boolean) {
        if (messageIds.isEmpty()) return
        store.runInTx {
            messageBox.get(messageIds.toLongArray()).forEach { message ->
                message.isBookmarked = bookmarked
                messageBox.put(message)
            }
        }
    }

    fun markForwarded(messageIds: Collection<Long>) {
        if (messageIds.isEmpty()) return
        store.runInTx {
            messageBox.get(messageIds.toLongArray()).forEach { message ->
                message.hasBeenForwarded = true
                messageBox.put(message)
            }
        }
    }

    fun deleteLocal(messageIds: Collection<Long>) {
        deleteMessages(messageIds, synchronizeAppleDevices = false)
    }

    /** Explicitly remove synced Apple messages everywhere; carrier traffic stays local. */
    fun deleteEverywhere(messageIds: Collection<Long>) {
        deleteMessages(messageIds, synchronizeAppleDevices = true)
    }

    private fun deleteMessages(messageIds: Collection<Long>, synchronizeAppleDevices: Boolean) {
        if (messageIds.isEmpty()) return
        val attachmentBox = store.boxFor(Attachment::class.java)
        val affectedChats = linkedSetOf<Long>()
        val removedAttachments = arrayListOf<Attachment>()
        store.runInTx {
            val selected = messageBox.get(messageIds.toLongArray())
            selected.forEach { message -> removedAttachments += message.dbAttachments.toList() }
            if (synchronizeAppleDevices) {
                StoreDeletionCoordinators.enqueue(
                    store = store,
                    recordIds = cloudDeletionRecordIds(messages = selected, attachments = removedAttachments),
                    requireRegistration = true,
                )
            } else {
                StoreDeletionCoordinators.suppressLocally(
                    store,
                    cloudDeletionRecordIds(messages = selected, attachments = removedAttachments),
                )
            }
            selected.forEach { message ->
                affectedChats += message.chat.targetId
                message.dbAttachments.toList().forEach(attachmentBox::remove)
                messageBox.remove(message)
            }
            affectedChats.forEach(::refreshChatLatest)
        }
        StoreDeletionCoordinators.deleteAttachmentFiles(store, removedAttachments, attachmentDisk)
    }

    fun cancelOutgoing(messageId: Long): Boolean {
        val message = messageBox.get(messageId) ?: return false
        if (!message.isFromMe || statusOf(message) !in setOf(MessageStatus.SENDING, MessageStatus.FAILED)) {
            return false
        }
        deleteLocal(listOf(messageId))
        return true
    }

    fun clearTranscript(chatId: Long) {
        val ids = conversationChatIds(chatId)
        val messages = ids.flatMap { id ->
            messageBox.query().equal(Message_.chatId, id).build().use { it.find() }
        }
        deleteLocal(messages.map { it.id })
    }

    fun restoreDeleted(messageIds: Collection<Long>) {
        if (messageIds.isEmpty()) return
        store.runInTx {
            val selected = messageBox.get(messageIds.toLongArray())
            StoreDeletionCoordinators.restore(
                store,
                cloudDeletionRecordIds(
                    messages = selected,
                    attachments = selected.flatMap { it.dbAttachments.toList() },
                ),
            )
            selected.forEach { message ->
                message.dateDeleted = null
                messageBox.put(message)
                refreshChatLatest(message.chat.targetId)
            }
        }
    }

    /**
     * Newest-first messages whose body contains [text] (case-insensitive),
     * across every conversation. Reactions (which duplicate their target's
     * text in some sync paths) and deleted rows are excluded.
     */
    fun searchText(text: String, limit: Int = 25): List<MessageItem> {
        val needle = text.trim()
        if (needle.isEmpty()) return emptyList()
        return store.callInReadTx {
            messageBox.query(
                Message_.text.contains(needle, QueryBuilder.StringOrder.CASE_INSENSITIVE)
                    .and(Message_.associatedMessageGuid.isNull())
                    .and(Message_.dateDeleted.isNull()),
            )
                .apply { link(Message_.chat).isNull(Chat_.dateDeleted) }
                .orderDesc(Message_.dateCreated)
                .orderDesc(Message_.id)
                .build()
                .use { query -> query.find(0, limit.toLong()).map(::toItem) }
        }
    }

    /**
     * Newest-first link-carrying messages matching [text]: the body or the
     * parsed link metadata contains the needle, and the message actually
     * carries a URL (rich-link metadata or an http(s) address in the body).
     */
    fun searchLinks(text: String, limit: Int = 25): List<MessageItem> {
        val needle = text.trim()
        if (needle.isEmpty()) return emptyList()
        val matchesNeedle = Message_.text.contains(needle, QueryBuilder.StringOrder.CASE_INSENSITIVE)
            .or(Message_.dbMetadata.contains(needle, QueryBuilder.StringOrder.CASE_INSENSITIVE))
        val carriesLink = Message_.dbMetadata.notNull()
            .or(Message_.text.contains("http://", QueryBuilder.StringOrder.CASE_INSENSITIVE))
            .or(Message_.text.contains("https://", QueryBuilder.StringOrder.CASE_INSENSITIVE))
        return store.callInReadTx {
            messageBox.query(
                matchesNeedle.and(carriesLink)
                    .and(Message_.associatedMessageGuid.isNull())
                    .and(Message_.dateDeleted.isNull()),
            )
                .apply { link(Message_.chat).isNull(Chat_.dateDeleted) }
                .orderDesc(Message_.dateCreated)
                .orderDesc(Message_.id)
                .build()
                .use { query -> query.find(0, limit.toLong()).map(::toItem) }
        }
    }

    /** Root plus every reply attached to the same root part, oldest first. */
    fun threadMessages(chatId: Long, rootGuid: String, part: Long): List<MessageItem> {
        val chatIds = conversationChatIds(chatId)
        return store.callInReadTx {
            val root = messageBox.query()
                .equal(Message_.guid, rootGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
                ?.takeIf { it.chat.targetId in chatIds }
            val sourceChatId = root?.chat?.targetId ?: chatId
            val replies = messageBox.query()
                .equal(Message_.chatId, sourceChatId)
                .equal(Message_.threadOriginatorGuid, rootGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .isNull(Message_.associatedMessageGuid)
                .isNull(Message_.dateDeleted)
                .order(Message_.dateCreated)
                .order(Message_.id)
                .build().use { query ->
                    query.find().filter { MessageMapper.replyPartIndex(it.threadOriginatorPart) == part }
                }
            buildList {
                if (root != null) add(toItem(root))
                addAll(replies.map(::toItem))
            }
        }
    }

    /**
     * Appends a staged outgoing message — the local echo shown while the send
     * is in flight (`OutgoingMsgHandler` equivalent). The row uses the rust
     * staging guid so the push echo ([UPushMessage.IMessage] with the same id)
     * or [app.openbubbles.core.intake.MessageIngestor] update/swap it in place:
     * - guid = staging guid (findable by guid and staging lookups),
     * - sendingServiceId set → [MessageStatus.SENDING] until SendConfirm,
     * - wired to the chat, latest-message pointer + date advanced.
     *
     * @param sender the rust-style handle ("mailto:…" / "tel:…") I sent from.
     */
    suspend fun stageOutgoingMessage(
        chatGuid: String,
        sender: String,
        text: String,
        stagingGuid: String,
        sendingServiceId: String? = DEFAULT_SENDING_SERVICE_ID,
        expressiveSendStyleId: String? = null,
        threadOriginatorGuid: String? = null,
        threadOriginatorPart: String? = null,
        subject: String? = null,
        attributedBody: String? = null,
    ): Message = withContext(Dispatchers.IO) {
        val chat = chatBox.query()
            .equal(Chat_.guid, chatGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
            ?: throw IllegalArgumentException("No chat $chatGuid")

        store.callInTx {
            val message = Message().apply {
                guid = stagingGuid
                this.stagingGuid = stagingGuid
                this.text = text
                isFromMe = true
                dateCreated = Date()
                this.sendingServiceId = sendingServiceId
                this.expressiveSendStyleId = expressiveSendStyleId
                this.threadOriginatorGuid = threadOriginatorGuid
                this.threadOriginatorPart = threadOriginatorPart
                this.subject = subject
                dbAttributedBody = attributedBody
            }
            HandleResolver.resolve(store, sender, "iMessage").let {
                message.handleRelation.target = it
                message.handleId = it.originalROWID
            }
            message.chat.target = chat
            messageBox.put(message)

            // setLatestMessage + from-me unread semantics.
            val latest = chat.dbLatestMessage.target
            if (latest == null || latest.dateCreated == null ||
                (message.dateCreated.after(latest.dateCreated))
            ) {
                chat.dbLatestMessage.target = message
                chat.dbOnlyLatestMessageDate = message.dateCreated
                chat.hasUnreadMessage = false
                chatBox.put(chat)
            }
            message
        }
    }

    /**
     * Stages an outgoing attachment message as one committed database state.
     * The message row must be inserted before its relation targets can be
     * written, but keeping every put in the same transaction prevents the UI
     * from observing an empty/caption-only bubble between those writes.
     */
    suspend fun stageOutgoingMessageWithAttachments(
        chatGuid: String,
        sender: String,
        text: String,
        stagingGuid: String,
        attachments: List<OutgoingAttachmentStage>,
        sendingServiceId: String? = DEFAULT_SENDING_SERVICE_ID,
        subject: String? = null,
        threadOriginatorGuid: String? = null,
        threadOriginatorPart: String? = null,
    ): Message = withContext(Dispatchers.IO) {
        require(attachments.isNotEmpty()) { "attachment send requires at least one attachment" }
        val chat = chatBox.query()
            .equal(Chat_.guid, chatGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
            ?: throw IllegalArgumentException("No chat $chatGuid")
        val attachmentBox = store.boxFor(Attachment::class.java)

        store.callInTx {
            val message = Message().apply {
                guid = stagingGuid
                this.stagingGuid = stagingGuid
                this.text = text
                isFromMe = true
                dateCreated = Date()
                this.sendingServiceId = sendingServiceId
                this.subject = subject
                this.threadOriginatorGuid = threadOriginatorGuid
                this.threadOriginatorPart = threadOriginatorPart
                hasAttachments = true
            }
            HandleResolver.resolve(store, sender, "iMessage").let {
                message.handleRelation.target = it
                message.handleId = it.originalROWID
            }
            message.chat.target = chat
            messageBox.put(message)

            attachments.forEach { staged ->
                attachmentBox.put(
                    Attachment().apply {
                        guid = staged.guid
                        isOutgoing = true
                        mimeType = staged.mimeType
                        uti = staged.uti
                        transferName = staged.transferName
                        totalBytes = staged.totalBytes
                        isDownloaded = true
                        this.message.target = message
                    },
                )
            }

            val latest = chat.dbLatestMessage.target
            if (latest == null || latest.dateCreated == null || message.dateCreated.after(latest.dateCreated)) {
                chat.dbLatestMessage.target = message
                chat.dbOnlyLatestMessageDate = message.dateCreated
                chat.hasUnreadMessage = false
                chatBox.put(chat)
            }
            message
        }
    }

    /**
     * Resolves a staged send into a visible retryable failure. There is no
     * durable outbound queue in the native rewrite, so leaving a row in
     * SENDING when the live push state is absent would strand it forever.
     */
    fun failOutgoing(stagingGuid: String, errorText: String): Message? = store.callInTx {
        val message = messageBox.query()
            .equal(Message_.guid, stagingGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
            ?: messageBox.query()
                .equal(Message_.stagingGuid, stagingGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
            ?: return@callInTx null
        // Send-path failures look up by guid. Read receipts reuse an
        // incoming guid as their envelope id; never paint that bubble failed.
        if (!message.isFromMe) return@callInTx null
        message.sendingServiceId = null
        message.error = 1L
        message.errorMessage = errorText.take(200)
        messageBox.put(message)
        message
    }

    /** Re-arm only a durable, failed self-send without changing its payload identity. */
    fun retryOutgoing(messageId: Long): Message? = store.callInTx {
        val message = messageBox.get(messageId) ?: return@callInTx null
        if (!message.isFromMe ||
            message.dateDeleted != null ||
            message.chat.target?.dateDeleted != null ||
            message.dateDelivered != null ||
            message.dateRead != null ||
            message.sendingServiceId != null ||
            statusOf(message) != MessageStatus.FAILED
        ) {
            return@callInTx null
        }
        if (message.guid.startsWith("error")) {
            val recoverableGuid = message.stagingGuid
                ?.takeIf { it.isNotBlank() && !it.startsWith("error") }
                ?: return@callInTx null
            val conflicting = messageByGuid(recoverableGuid)
            if (conflicting != null && conflicting.id != message.id) return@callInTx null
            message.guid = recoverableGuid
        }
        message.error = null
        message.errorMessage = null
        message.sendingServiceId = DEFAULT_SENDING_SERVICE_ID
        messageBox.put(message)
        message
    }

    /** Delivery status for a bubble, mirroring `Message.indicatorToShow`. */
    fun statusOf(message: Message): MessageStatus {
        if (
            message.guid.startsWith("error") ||
            message.error?.let { it != 0L } == true ||
            message.errorMessage != null
        ) {
            return MessageStatus.FAILED
        }
        if (!message.isFromMe) return MessageStatus.SENT
        if (message.sendingServiceId != null) return MessageStatus.SENDING
        if (message.guid.startsWith("temp") && message.chat.target?.isRpSms != true) {
            return MessageStatus.SENDING
        }
        if (message.dateRead != null) return MessageStatus.READ
        if (message.dateDelivered != null) return MessageStatus.DELIVERED
        return MessageStatus.SENT
    }

    internal fun toItem(message: Message): MessageItem =
        toItem(
            message,
            if (message.hasReactions && kindOf(message) == MessageKind.TEXT) {
                activeReactionsFor(message.guid)
            } else {
                emptyList()
            },
        )

    /**
     * Path, length and mtime of the payload a renderer would actually open.
     * [AttachmentStore.existingFile] already rejects an empty or size-mismatched
     * file, so this is null until a validated payload is on disk.
     */
    private fun payloadIdentity(attachment: Attachment): String? {
        val disk = attachmentDisk ?: return null
        val file = runCatching { disk.existingFile(attachment) }.getOrNull() ?: return null
        return "${file.path}:${file.length()}:${file.lastModified()}"
    }

    private fun toItem(message: Message, activeReactions: List<Message>): MessageItem {
        val kind = kindOf(message)
        val tapbacks = activeReactions
            .filterNot { it.associatedMessageType?.removePrefix("-") in STICKER_TYPES }
        val activeReaction = tapbacks.maxByOrNull { it.dateCreated?.time ?: Long.MIN_VALUE }
        return MessageItem(
            id = message.id,
            guid = message.guid,
            text = message.text ?: "",
            subject = message.subject,
            isFromMe = message.isFromMe,
            senderAddress = message.handleRelation.target?.address,
            date = message.dateCreated,
            dateDelivered = message.dateDelivered,
            dateRead = message.dateRead,
            status = statusOf(message),
            kind = kind,
            groupEventText = if (kind == MessageKind.GROUP_EVENT) chatRepo.groupEventText(message) else null,
            reactionType = activeReaction?.associatedMessageType
                ?: if (kind == MessageKind.REACTION) message.associatedMessageType else null,
            reactionEmoji = activeReaction?.associatedMessageEmoji
                ?: if (kind == MessageKind.REACTION) message.associatedMessageEmoji else null,
            reactions = tapbacks.mapNotNull { reaction ->
                val type = reaction.associatedMessageType ?: return@mapNotNull null
                MessageReaction(
                    type = type,
                    emoji = reaction.associatedMessageEmoji,
                    senderAddress = reaction.handleRelation.target?.address,
                    isFromMe = reaction.isFromMe,
                    date = reaction.dateCreated,
                    targetPart = reaction.associatedMessagePart ?: 0L,
                )
            },
            hasAttachments = message.hasAttachments,
            attachmentCount = if (message.hasAttachments) message.dbAttachments.size else 0,
            attachmentStamps = if (message.hasAttachments) {
                message.dbAttachments.map { attachment ->
                    AttachmentStamp(
                        id = attachment.id,
                        downloaded = attachment.isDownloaded,
                        name = attachment.transferName,
                        sizeBytes = attachment.totalBytes,
                        mime = attachment.mimeType,
                        uti = attachment.uti,
                        payload = payloadIdentity(attachment),
                    )
                }
            } else {
                emptyList()
            },
            threadOriginatorGuid = message.threadOriginatorGuid,
            threadOriginatorPart = MessageMapper.replyPartIndex(message.threadOriginatorPart),
            threadOriginatorLocator = message.threadOriginatorPart,
            replyPartLocators = MessageMapper.decodeReplyPartLocators(message.dbAttributedBody),
            associatedMessageGuid = message.associatedMessageGuid,
            expressiveSendStyleId = message.expressiveSendStyleId,
            richLinkMetadataJson = message.dbMetadata.takeIf {
                message.balloonBundleId == "com.apple.messages.URLBalloonProvider"
            },
            interactivePayload = if (message.balloonBundleId == "com.apple.messages.URLBalloonProvider") {
                null
            } else {
                InteractivePayloadParser.parse(
                    bundleId = message.balloonBundleId,
                    payloadJson = message.dbPayloadData,
                    summaryInfoJson = message.dbMessageSummaryInfo,
                    fallbackText = message.text,
                )
            },
            stickers = activeReactions.flatMap(::stickerPlacements),
            chatId = message.chat.targetId,
            isSms = message.chat.target?.isRpSms == true,
            isBookmarked = message.isBookmarked == true,
            hasBeenForwarded = message.hasBeenForwarded,
            dateDeleted = message.dateDeleted,
            errorCode = message.error,
            errorMessage = message.errorMessage,
            partCount = 1 + if (message.hasAttachments) message.dbAttachments.size else 0,
        )
    }

    private fun refreshChatLatest(chatId: Long) {
        val chat = chatBox.get(chatId) ?: return
        val latest = messageBox.query()
            .equal(Message_.chatId, chatId)
            .isNull(Message_.associatedMessageGuid)
            .isNull(Message_.dateDeleted)
            .orderDesc(Message_.dateCreated)
            .orderDesc(Message_.id)
            .build().use { it.findFirst() }
        chat.dbLatestMessage.target = latest
        chat.dbOnlyLatestMessageDate = latest?.dateCreated
        if (latest == null) chat.hasUnreadMessage = false
        chatBox.put(chat)
    }

    private fun conversationChatIds(chatId: Long): List<Long> =
        chatRepo.relatedDirectChatIds(chatId).ifEmpty { listOf(chatId) }

    /**
     * Collapses reaction rows onto their target bubble. Each sender owns one
     * active tapback per part; a later `-type` row removes the matching part.
     */
    private fun activeReactionsFor(messageGuid: String): List<Message> {
        val reactions = messageBox.query()
            .equal(
                Message_.associatedMessageGuid,
                messageGuid,
                QueryBuilder.StringOrder.CASE_SENSITIVE,
            )
            .isNull(Message_.dateDeleted)
            .order(Message_.dateCreated)
            .build().use { it.find() }
        return collapseReactions(reactions)
    }

    /** One `IN` query for every reacted-to message of a page, grouped by target. */
    private fun activeReactionsByTarget(
        messageGuids: List<String>,
        chatIds: List<Long>,
    ): Map<String, List<Message>> {
        if (messageGuids.isEmpty() || chatIds.isEmpty()) return emptyMap()
        val rows = messageBox.query(
            Message_.associatedMessageGuid
                .oneOf(messageGuids.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE)
                .and(Message_.chatId.oneOf(chatIds.toLongArray()))
                .and(Message_.dateDeleted.isNull()),
        )
            .order(Message_.dateCreated)
            .build().use { it.find() }
        // groupBy keeps encounter order, so each group stays dateCreated-ordered.
        return rows.groupBy { it.associatedMessageGuid!! }
            .mapValues { (_, group) -> collapseReactions(group) }
    }

    private fun collapseReactions(reactions: List<Message>): List<Message> {
        val bySenderAndPart = linkedMapOf<Pair<String, Long>, Message>()
        val stickers = mutableListOf<Message>()
        reactions.forEach { reaction ->
            val type = reaction.associatedMessageType ?: return@forEach
            if (type.removePrefix("-") in STICKER_TYPES) {
                if (!type.startsWith("-")) stickers += reaction
                return@forEach
            }
            val senderKey = if (reaction.isFromMe) {
                "me"
            } else {
                "handle:${reaction.handleId ?: reaction.handleRelation.targetId}"
            }
            val key = senderKey to (reaction.associatedMessagePart ?: 0L)
            if (type.startsWith("-")) {
                val removedType = type.removePrefix("-")
                if (bySenderAndPart[key]?.associatedMessageType == removedType) {
                    bySenderAndPart.remove(key)
                }
            } else {
                bySenderAndPart[key] = reaction
            }
        }
        return (bySenderAndPart.values + stickers)
            .sortedBy { it.dateCreated?.time ?: Long.MIN_VALUE }
    }

    private fun stickerPlacements(reaction: Message): List<StickerPlacement> {
        val type = reaction.associatedMessageType?.removePrefix("-") ?: return emptyList()
        if (type !in STICKER_TYPES) return emptyList()
        val targetPart = reaction.associatedMessagePart ?: 0L
        return reaction.dbAttachments.mapNotNull { attachment ->
            val metadata = attachment.metadata ?: return@mapNotNull null
            fun number(key: String, fallback: Double): Double = when (val value = metadata[key]) {
                is Number -> value.toDouble()
                is String -> value.toDoubleOrNull() ?: fallback
                else -> fallback
            }
            val guid = attachment.guid ?: return@mapNotNull null
            StickerPlacement(
                reactionGuid = reaction.guid,
                attachmentGuid = guid,
                targetPart = targetPart,
                messageWidth = number("sticker.msgWidth", 300.0),
                normalizedX = number("sticker.normalizedX", 0.5).coerceIn(0.0, 1.0),
                normalizedY = number("sticker.normalizedY", 0.5).coerceIn(0.0, 1.0),
                rotation = number("sticker.rotation", 0.0),
                scale = number("sticker.scale", 1.0).coerceIn(0.1, 4.0),
                effectType = number("sticker.effectType", 0.0).toLong(),
                downloaded = attachment.isDownloaded,
                payload = payloadIdentity(attachment),
            )
        }
    }

    private fun kindOf(message: Message): MessageKind = when {
        message.associatedMessageType != null || message.associatedMessageGuid != null -> MessageKind.REACTION
        message.groupTitle != null ||
            (message.itemType ?: 0L) > 0L ||
            (message.groupActionType ?: 0L) > 0L -> MessageKind.GROUP_EVENT
        else -> MessageKind.TEXT
    }

    companion object {
        /** Marker written to Message.sendingServiceId while a send is in flight. */
        const val DEFAULT_SENDING_SERVICE_ID = "rustpush"
        private val STICKER_TYPES = setOf(MessageMapper.REACTION_STICKER, MessageMapper.REACTION_STICKERBACK)
    }
}

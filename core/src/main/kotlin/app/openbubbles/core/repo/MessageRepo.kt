package app.openbubbles.core.repo

import app.openbubbles.core.intake.HandleResolver
import app.openbubbles.core.model.AttachmentStamp
import app.openbubbles.core.model.MessageItem
import app.openbubbles.core.model.InteractivePayloadParser
import app.openbubbles.core.model.MessageKind
import app.openbubbles.core.model.MessageMapper
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import java.util.Date

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
) {

    private val chatBox = store.boxFor(Chat::class.java)
    private val messageBox = store.boxFor(Message::class.java)
    private val invalidations = StoreInvalidationCoordinators.forStore(store)

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
     */
    fun observeMessages(chatId: Long, limit: Int = 50): Flow<List<MessageItem>> =
        merge(
            flowOf(Unit),
            invalidations.changesFor(
                StoreEntityChange.MESSAGE,
                StoreEntityChange.ATTACHMENT,
                StoreEntityChange.CONTACT,
            ),
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
        merge(
            flowOf(Unit),
            invalidations.changesFor(
                StoreEntityChange.MESSAGE,
                StoreEntityChange.ATTACHMENT,
                StoreEntityChange.CONTACT,
            ),
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
        if (messageIds.isEmpty()) return
        val attachmentBox = store.boxFor(Attachment::class.java)
        val affectedChats = linkedSetOf<Long>()
        store.runInTx {
            messageBox.get(messageIds.toLongArray()).forEach { message ->
                affectedChats += message.chat.targetId
                message.dbAttachments.toList().forEach(attachmentBox::remove)
                messageBox.remove(message)
            }
            affectedChats.forEach(::refreshChatLatest)
        }
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
            messageBox.get(messageIds.toLongArray()).forEach { message ->
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
        if (message.sendingServiceId != null || message.guid.startsWith("temp")) return MessageStatus.SENDING
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

    private fun toItem(message: Message, activeReactions: List<Message>): MessageItem {
        val kind = kindOf(message)
        val activeReaction = activeReactions
            .filterNot { it.associatedMessageType?.removePrefix("-") in STICKER_TYPES }
            .maxByOrNull { it.dateCreated?.time ?: Long.MIN_VALUE }
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
     * active tapback; a later `-type` row removes that sender's matching one.
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
        val bySender = linkedMapOf<String, Message>()
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
            if (type.startsWith("-")) {
                val removedType = type.removePrefix("-")
                if (bySender[senderKey]?.associatedMessageType == removedType) {
                    bySender.remove(senderKey)
                }
            } else {
                bySender[senderKey] = reaction
            }
        }
        return bySender.values + stickers
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

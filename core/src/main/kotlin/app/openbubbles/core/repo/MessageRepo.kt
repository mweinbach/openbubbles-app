package app.openbubbles.core.repo

import app.openbubbles.core.intake.HandleResolver
import app.openbubbles.core.model.MessageItem
import app.openbubbles.core.model.MessageKind
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.core.model.MessageStatus
import app.openbubbles.core.model.StickerPlacement
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.query.Query
import io.objectbox.query.QueryCondition
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
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

    /** Newest-first page of messages for a chat ([offset] skips older rows). */
    fun messages(chatId: Long, limit: Int = 50, offset: Int = 0): List<MessageItem> =
        messageQuery(chatId).use { query ->
            // Relation-backed fields are projected while the query's native
            // read transaction is scoped to this worker thread.
            query.find(offset.toLong(), limit.toLong()).map(::toItem)
        }

    /** Newest-first page strictly older than [beforeId]'s time/id cursor. */
    fun messagesBefore(chatId: Long, beforeId: Long, limit: Int): List<MessageItem> {
        val anchor = messageBox.get(beforeId) ?: return emptyList()
        return messageQuery(chatId, anchor).use { query ->
            query.find(0, limit.toLong()).map(::toItem)
        }
    }

    /**
     * Reactive newest-first bounded page. The entity-type subscription is only
     * an invalidation signal; every emission executes [messages] with a native
     * ObjectBox limit instead of materializing the entire transcript first.
     */
    fun observeMessages(chatId: Long, limit: Int = 50): Flow<List<MessageItem>> =
        store.subscribe(Message::class.java)
            .asFlow()
            .conflate()
            .map { messages(chatId, limit) }
            .flowOn(Dispatchers.IO)

    private fun messageQuery(chatId: Long, before: Message? = null): Query<Message> {
        var condition: QueryCondition<Message> = Message_.chatId.equal(chatId)
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

    /** Root plus every reply attached to the same root part, oldest first. */
    fun threadMessages(chatId: Long, rootGuid: String, part: Long): List<MessageItem> {
        val root = messageByGuid(rootGuid)?.takeIf { it.chat.targetId == chatId }
        val replies = messageBox.query()
            .equal(Message_.chatId, chatId)
            .equal(Message_.threadOriginatorGuid, rootGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .equal(Message_.threadOriginatorPart, part.toString(), QueryBuilder.StringOrder.CASE_SENSITIVE)
            .isNull(Message_.associatedMessageGuid)
            .isNull(Message_.dateDeleted)
            .order(Message_.dateCreated)
            .order(Message_.id)
            .build().use { it.find() }
        return buildList {
            if (root != null) add(toItem(root))
            addAll(replies.map(::toItem))
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
        message.sendingServiceId = null
        message.error = 1L
        message.errorMessage = errorText.take(200)
        messageBox.put(message)
        message
    }

    /** Delivery status for a bubble, mirroring `Message.indicatorToShow`. */
    fun statusOf(message: Message): MessageStatus {
        if (message.guid.startsWith("error") || message.error != null || message.errorMessage != null) {
            return MessageStatus.FAILED
        }
        if (!message.isFromMe) return MessageStatus.SENT
        if (message.sendingServiceId != null || message.guid.startsWith("temp")) return MessageStatus.SENDING
        if (message.dateRead != null) return MessageStatus.READ
        if (message.dateDelivered != null) return MessageStatus.DELIVERED
        return MessageStatus.SENT
    }

    internal fun toItem(message: Message): MessageItem {
        val kind = kindOf(message)
        val activeReactions = if (kind == MessageKind.TEXT && message.hasReactions) {
            activeReactionsFor(message.guid)
        } else {
            emptyList()
        }
        val activeReaction = activeReactions
            .filterNot { it.associatedMessageType?.removePrefix("-") in STICKER_TYPES }
            .maxByOrNull { it.dateCreated?.time ?: Long.MIN_VALUE }
        return MessageItem(
            id = message.id,
            guid = message.guid,
            text = message.text ?: "",
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
            threadOriginatorGuid = message.threadOriginatorGuid,
            threadOriginatorPart = message.threadOriginatorPart?.toLongOrNull(),
            associatedMessageGuid = message.associatedMessageGuid,
            expressiveSendStyleId = message.expressiveSendStyleId,
            stickers = activeReactions.flatMap(::stickerPlacements),
        )
    }

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
        val bySender = linkedMapOf<String, Message>()
        reactions.forEach { reaction ->
            val type = reaction.associatedMessageType ?: return@forEach
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
        return bySender.values.toList()
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

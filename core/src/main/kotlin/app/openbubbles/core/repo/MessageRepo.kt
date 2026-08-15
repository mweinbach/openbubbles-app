package app.openbubbles.core.repo

import app.openbubbles.core.intake.HandleResolver
import app.openbubbles.core.model.MessageItem
import app.openbubbles.core.model.MessageKind
import app.openbubbles.core.model.MessageStatus
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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
        messageBox.query()
            .equal(Message_.chatId, chatId)
            .isNull(Message_.associatedMessageGuid)
            .isNull(Message_.dateDeleted)
            .orderDesc(Message_.dateCreated)
            .build()
            .use { it.find(offset.toLong(), limit.toLong()) }
            .map { toItem(it) }

    /** Reactive newest-first page (re-emits on any Message-table change). */
    fun observeMessages(chatId: Long, limit: Int = 50): Flow<List<MessageItem>> =
        messageBox.query()
            .equal(Message_.chatId, chatId)
            .isNull(Message_.associatedMessageGuid)
            .isNull(Message_.dateDeleted)
            .orderDesc(Message_.dateCreated)
            .build()
            .subscribe()
            .asFlow()
            .map { page -> page.take(limit).map { toItem(it) } }
            .flowOn(Dispatchers.IO)

    fun messageByGuid(guid: String): Message? =
        messageBox.query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

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
        val activeReaction = if (kind == MessageKind.TEXT && message.hasReactions) {
            activeReactionFor(message.guid)
        } else {
            null
        }
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
            associatedMessageGuid = message.associatedMessageGuid,
            expressiveSendStyleId = message.expressiveSendStyleId,
        )
    }

    /**
     * Collapses reaction rows onto their target bubble. Each sender owns one
     * active tapback; a later `-type` row removes that sender's matching one.
     */
    private fun activeReactionFor(messageGuid: String): Message? {
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
        return bySender.values.maxByOrNull { it.dateCreated?.time ?: Long.MIN_VALUE }
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
    }
}

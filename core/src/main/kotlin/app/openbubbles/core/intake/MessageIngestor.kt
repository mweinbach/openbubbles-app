package app.openbubbles.core.intake

import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.model.DeleteMessageCommand
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.core.model.MessageSummaryPartList
import app.openbubbles.core.model.addMessageSummaryPart
import app.openbubbles.core.model.decodeDeleteMessageCommand
import app.openbubbles.core.sync.TranscriptBackgroundHandler
import app.openbubbles.core.sync.TranscriptBackgroundUpdate
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.UConversation
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.UPushMessage
import java.security.SecureRandom

/**
 * Ephemeral typing state for a chat participant. Emitted through
 * [MessageIngestor.typing]; entries expire one minute after the last
 * `UMessage.Typing(typing = true)` — same timeout the Flutter app uses.
 */
data class TypingIndicator(
    val chatGuid: String,
    val senderAddress: String,
    val expiresAtMs: Long,
)

/**
 * Kotlin port of the Flutter app's rustpush message-intake layer:
 * `RustPushBackend.handleMsgInner` (variant dispatch),
 * `reflectMessageDyn` (entity mapping), `Chat.findByRust`/`chatForMessage`
 * (chat resolution) and `IncomingMessageHandler` (dedupe + persistence).
 *
 * All writes run on [Dispatchers.IO] under a single [Mutex]; ObjectBox
 * transactions (`store.runInTx`) provide atomicity per event. The caller is
 * the push/queue consumer: every decoded [UPushMessage] goes through
 * [ingest].
 *
 * MVP scope: text/attachment-metadata messages, tapbacks, delivered/read
 * receipts, typing, renames, participant changes, group-photo events, send
 * confirmation/error handling, and Apple chat backgrounds (via the transcript
 * background handler, shared with history sync). Other posters, FaceTime,
 * FindMy, shared albums, CloudKit sync, SMS forwarding and scheduled sends
 * are out of scope.
 */
class MessageIngestor(
    private val store: BoxStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val attachmentStore: AttachmentStore? = null,
    private val transcriptBackgroundHandler: TranscriptBackgroundHandler? = null,
) {
    /**
     * Persistence result used by notification consumers. [isNewIncomingMessage]
     * is true only when this call inserted a previously unseen incoming message
     * or reaction. Replayed journal entries, edits, receipts, and history that
     * already exists in ObjectBox must never produce another notification.
     */
    data class IngestResult(
        val chat: Chat?,
        val isNewIncomingMessage: Boolean,
    )

    companion object {
        /** How long a typing indicator survives without a refresh (Dart uses 1 minute). */
        private const val TYPING_TIMEOUT_MS = 60_000L
        private val RNG = SecureRandom()
        private const val ALPHANUM = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"

        fun randomString(length: Int): String = buildString {
            repeat(length) { append(ALPHANUM[RNG.nextInt(ALPHANUM.length)]) }
        }

        /** Dart's send path stages outgoing rows as `temp-XXXXXXXX`. */
        fun tempGuid(): String = "temp-${randomString(8)}"
    }

    private val chatBox = store.boxFor(Chat::class.java)
    private val messageBox = store.boxFor(Message::class.java)
    private val attachmentBox = store.boxFor(Attachment::class.java)
    private val mutex = Mutex()

    private val _typing = MutableStateFlow<List<TypingIndicator>>(emptyList())

    /** Live typing indicators keyed by chat; swept automatically on expiry. */
    val typing: StateFlow<List<TypingIndicator>> = _typing.asStateFlow()

    /** Entry point for the push queue consumer. Safe to call from any thread. */
    /**
     * Ingest one push message. Returns the affected [Chat] for message-type
     * pushes (so callers can notify with real chat context), null otherwise.
     */
    suspend fun ingest(msg: UPushMessage, myHandles: Set<String>): Chat? =
        ingestWithResult(msg, myHandles).chat

    /** Ingest with the deduplication signal required by the push service. */
    suspend fun ingestWithResult(msg: UPushMessage, myHandles: Set<String>): IngestResult {
        return withContext(Dispatchers.IO) {
            mutex.withLock {
                val inst = (msg as? UPushMessage.IMessage)?.inst
                val notifiableType = inst?.message is UMessage.Normal || inst?.message is UMessage.React
                val existedBefore = inst?.id?.let(::findMessageByGuidOrStaging) != null
                val chat = ingestLocked(msg, myHandles)
                IngestResult(
                    chat = chat,
                    isNewIncomingMessage = chat != null &&
                        notifiableType &&
                        !existedBefore &&
                        inst.sender != null &&
                        inst.sender !in myHandles &&
                        !inst.verificationFailed,
                )
            }
        }
    }

    private fun ingestLocked(push: UPushMessage, myHandles: Set<String>): Chat? {
        return when (push) {
            is UPushMessage.IMessage -> ingestInst(push.inst, myHandles)
            is UPushMessage.SendConfirm -> {
                handleSendConfirm(push.uuid, push.error)
                null
            }
            is UPushMessage.StatusUpdate -> handleStatusUpdate(push.user, push.allowed)
            // RegistrationState / FaceTime / Idms / photostreams /
            // beacons carry UI-only or not-yet-typed payloads — the service
            // layer listens for them directly. Nothing to persist.
            else -> null
        }
    }

    /** Mirror a recipient's Focus/notification-silencing status onto its DM. */
    private fun handleStatusUpdate(user: String, allowed: Boolean): Chat? {
        val chat = findByRust(
            conversation = UConversation(
                participants = listOf(user),
                cvName = null,
                senderGuid = null,
                afterGuid = null,
            ),
            service = "iMessage",
            myHandles = emptySet(),
            createIfMissing = false,
        ) ?: return null
        chat.notifsSilenced = !allowed
        chatBox.put(chat)
        return chat
    }

    // ------------------------------------------------------------------
    // UMessageInst dispatch (handleMsgInner port)
    // ------------------------------------------------------------------

    private fun ingestInst(inst: UMessageInst, myHandles: Set<String>): Chat? {
        when (val msg = inst.message) {
            is UMessage.Normal -> return ingestNormal(inst, msg, myHandles)
            is UMessage.React -> return ingestReaction(inst, msg, myHandles)
            is UMessage.Rename -> ingestRename(inst, msg, myHandles)
            is UMessage.ChangeParticipants -> ingestChangeParticipants(inst, msg, myHandles)
            is UMessage.IconChange -> ingestIconChange(inst, myHandles)
            is UMessage.Delivered -> handleReceipt(inst, myHandles, read = false)
            is UMessage.Read -> handleReceipt(inst, myHandles, read = true)
            is UMessage.Typing -> handleTyping(inst, msg.typing, myHandles)
            is UMessage.Unsend -> return handleUnsend(inst, msg)
            is UMessage.Edit -> return handleEdit(inst, msg)
            is UMessage.SmsConfirmSent -> handleSmsConfirmSent(inst, msg.status)
            is UMessage.Error -> handleErrorReceipt(inst, msg, myHandles)
            is UMessage.MarkUnread -> {
                chatForInst(inst, myHandles)?.let { chat ->
                    chat.hasUnreadMessage = true
                    chatBox.put(chat)
                }
            }
            is UMessage.NotifyAnyways -> findMessageByGuidOrStaging(inst.id)?.let { m ->
                m.wasDeliveredQuietly = false
                messageBox.put(m)
            }
            is UMessage.MoveToRecycleBin -> return handleMoveToRecycleBin(inst, msg, myHandles)
            is UMessage.RecoverChat -> return handleRecoverChat(inst, msg, myHandles)
            is UMessage.PermanentDelete -> return handlePermanentDelete(inst, msg, myHandles)
            is UMessage.SetTranscriptBackground -> return ingestTranscriptBackground(inst, msg, myHandles)
            // MessageReadOnDevice / EnableSmsActivation / profile & extension
            // updates / deletions ride on later batches.
            else -> Unit
        }
        return null
    }

    private fun ingestNormal(inst: UMessageInst, msg: UMessage.Normal, myHandles: Set<String>): Chat? {
        // Dart: skip empty messages (no text and no attachment parts).
        if (MessageMapper.rawText(msg.parts).isEmpty() && !MessageMapper.hasAttachmentParts(msg.parts)) return null
        val chat = chatForInst(inst, myHandles) ?: return null
        val mapped = MessageMapper.mapNormal(inst, msg, myHandles)
        persistMapped(mapped, chat, inst, myHandles)
        // A real message clears the sender's typing indicator.
        inst.sender?.let { sender ->
            clearTyping(chat.guid, MessageMapper.normalizeAddress(sender))
        }
        return chat
    }

    private fun ingestReaction(inst: UMessageInst, msg: UMessage.React, myHandles: Set<String>): Chat? {
        val chat = chatForInst(inst, myHandles) ?: return null
        persistMapped(MessageMapper.mapReaction(inst, msg, myHandles), chat, inst, myHandles)
        return chat
    }

    /**
     * Apple chat-background changes from a live push: resolve the chat now
     * (never creating one — a background for a chat we have not seen will be
     * applied by the history sync that imports the chat), then hand the
     * poster payload to the background store (MMCS download + atomic disk
     * write, shared with history sync) off the ingest lock. Stale or
     * replayed versions are dropped by the store's version check.
     */
    private fun ingestTranscriptBackground(
        inst: UMessageInst,
        msg: UMessage.SetTranscriptBackground,
        myHandles: Set<String>,
    ): Chat? {
        val chat = chatForTranscriptBackground(inst, msg.chatId, myHandles) ?: return null
        val handler = transcriptBackgroundHandler ?: return chat
        scope.launch {
            runCatching {
                handler.apply(
                    TranscriptBackgroundUpdate(
                        chatId = chat.id,
                        version = msg.version.toLong(),
                        remove = msg.remove,
                        mmcsXml = msg.mmcsXml,
                    ),
                )
            }
        }
        return chat
    }

    /**
     * Wallpaper pushes arrive without ConversationData, so [chatForInst]
     * alone cannot place them. Mirror the Dart handler: `cid` is the peer
     * address for direct chats and a rust guid for groups; when it is
     * absent, the background belongs to the direct chat with the sender.
     */
    private fun chatForTranscriptBackground(
        inst: UMessageInst,
        chatId: String?,
        myHandles: Set<String>,
    ): Chat? {
        chatForInst(inst, myHandles, createIfMissing = false)?.let { return it }
        chatId?.let { cid ->
            val address = MessageMapper.normalizeAddress(cid)
            if (address.contains('@') || address.contains('+')) {
                directChatForAddress(address)?.let { return it }
            }
            chatByGuidOrGuidRef(cid)?.let { return it }
            chatBox.query()
                .equal(Chat_.chatIdentifier, address, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
                ?.let { return it }
        }
        val sender = inst.sender ?: return null
        return directChatForAddress(MessageMapper.normalizeAddress(sender))
    }

    /** `Chat.findByRustGuid`: direct guid match first, then any guidRefs entry. */
    private fun chatByGuidOrGuidRef(guid: String): Chat? {
        chatBox.query()
            .equal(Chat_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
            ?.let { return it }
        return chatBox.query()
            .containsElement(Chat_.guidRefs, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
    }

    /** `Chat.findByHandle`: the direct chat whose only participant is [address]. */
    private fun directChatForAddress(address: String): Chat? {
        val builder = chatBox.query()
        builder.link(Chat_.handles)
            .equal(Handle_.address, address, QueryBuilder.StringOrder.CASE_SENSITIVE)
        return builder.build().use { it.find() }.firstOrNull { it.handles.size == 1 }
    }

    private fun ingestRename(inst: UMessageInst, msg: UMessage.Rename, myHandles: Set<String>) {        if (inst.verificationFailed) return
        val chat = chatForInst(inst, myHandles) ?: return
        // handleMsgInner: rename updates the chat name (unless the user locked
        // it) plus the APN title used for conversation matching.
        if (chat.lockChatName != true) chat.displayName = msg.newName
        chat.apnTitle = msg.newName
        chatBox.put(chat)
        persistMapped(MessageMapper.mapRename(inst, msg, myHandles), chat, inst, myHandles)
    }

    private fun ingestIconChange(inst: UMessageInst, myHandles: Set<String>) {
        val chat = chatForInst(inst, myHandles) ?: return
        // Icon download goes through the attachment batch APIs; the event
        // row itself is persisted now.
        persistMapped(MessageMapper.mapIconChange(inst, myHandles), chat, inst, myHandles)
    }

    private fun ingestChangeParticipants(
        inst: UMessageInst,
        msg: UMessage.ChangeParticipants,
        myHandles: Set<String>,
    ) {
        if (inst.verificationFailed) return
        val conversation = inst.conversation ?: return
        val chat = chatForInst(inst, myHandles) ?: return

        val oldMembers = chat.handles.map { MessageMapper.toRustHandle(it.address) }.toSet()
        val newMembers = msg.newParticipants.toSet()
        val added = newMembers.filter { it !in oldMembers && it !in myHandles }
        val removed = oldMembers.filter { it !in newMembers && it !in myHandles }

        if (added.isEmpty() && removed.isEmpty()) {
            chat.groupVersion = msg.groupVersion.toLong()
            chatBox.put(chat)
            return
        }

        // Replace the participant set (updateChatParticipants).
        val newHandles = newMembers.filter { it !in myHandles }.map { handleFor(it, serviceFor(inst)) }
        chat.handles.clear()
        chat.handles.addAll(newHandles)
        chat.groupVersion = msg.groupVersion.toLong()
        chatBox.put(chat)

        // Synthesize an event row per membership change so the transcript
        // shows "X added Y" / "X left".
        val events = ArrayList<Message>(added.size + removed.size)
        for (member in added) {
            val h = handleFor(member, serviceFor(inst))
            events += MessageMapper.mapParticipantEvent(
                inst, MessageMapper.normalizeAddress(member), h.originalROWID,
                added = true, senderLeft = false, myHandles = myHandles, index = events.size,
            )
        }
        for (member in removed) {
            val h = handleFor(member, serviceFor(inst))
            val senderLeft = inst.sender != null && member == inst.sender
            events += MessageMapper.mapParticipantEvent(
                inst, MessageMapper.normalizeAddress(member), h.originalROWID,
                added = false, senderLeft = senderLeft, myHandles = myHandles, index = events.size,
            )
        }
        // Rewrite guids so each event is unique (Dart reused the message id and
        // silently dropped collisions via its swallowed UniqueViolation).
        events.forEachIndexed { index, event ->
            event.guid = if (index == 0) inst.id else "${inst.id}-p-$index"
            persistMapped(MessageMapper.Mapped(event, emptyList()), chat, inst, myHandles)
        }
    }

    private fun handleReceipt(inst: UMessageInst, myHandles: Set<String>, read: Boolean) {
        val message = findMessageByGuidOrStaging(inst.id) ?: return
        if (inst.verificationFailed) return
        val chat = message.chat.target

        // Receipt routed to one of my other devices: do not touch the message,
        // but a read receipt still clears the local unread flag.
        val fromMyHandle = inst.sender != null && myHandles.contains(inst.sender)
        if (fromMyHandle && chat != null && chat.isRpSms != true) {
            if (read) {
                chat.hasUnreadMessage = false
                chatBox.put(chat)
            }
            return
        }

        val date = MessageMapper.dateFromMs(inst.sentTimestamp)
        if (read) message.dateRead = date else message.dateDelivered = date
        messageBox.put(message)
    }

    private fun handleTyping(inst: UMessageInst, typing: Boolean, myHandles: Set<String>) {
        if (inst.verificationFailed) return
        val sender = inst.sender ?: return
        val chat = chatForInst(inst, myHandles, createIfMissing = false) ?: return
        if (typing) {
            setTyping(chat.guid, MessageMapper.normalizeAddress(sender))
        } else {
            clearTyping(chat.guid, MessageMapper.normalizeAddress(sender))
        }
    }

    private fun handleUnsend(inst: UMessageInst, unsend: UMessage.Unsend): Chat? {
        val target = findMessageByGuidOrStaging(unsend.tuuid) ?: return null
        target.verificationFailed = inst.verificationFailed
        target.dateEdited = MessageMapper.dateFromMs(inst.sentTimestamp)
        target.dbMessageSummaryInfo = addMessageSummaryPart(
            target.dbMessageSummaryInfo,
            MessageSummaryPartList.RETRACTED,
            unsend.editPart,
        )
        // The flattened native model can safely clear a single text part.
        // Multipart attachment messages retain their visible content until
        // attributed-run parity lands, avoiding accidental whole-message loss.
        if (!target.hasAttachments && unsend.editPart == 0uL) target.text = ""
        messageBox.put(target)
        return target.chat.target
    }

    private fun handleEdit(inst: UMessageInst, edit: UMessage.Edit): Chat? {
        val target = findMessageByGuidOrStaging(edit.tuuid) ?: return null
        val replacementText = MessageMapper.mapParts(edit.parts, target.guid, target.isFromMe).first
        if (edit.editPart == 0uL || !target.hasAttachments) {
            target.text = replacementText
        }
        target.verificationFailed = inst.verificationFailed
        target.dateEdited = MessageMapper.dateFromMs(inst.sentTimestamp)
        target.dbMessageSummaryInfo = addMessageSummaryPart(
            target.dbMessageSummaryInfo,
            MessageSummaryPartList.EDITED,
            edit.editPart,
        )
        messageBox.put(target)
        return target.chat.target
    }

    private fun handleMoveToRecycleBin(
        inst: UMessageInst,
        move: UMessage.MoveToRecycleBin,
        myHandles: Set<String>,
    ): Chat? {
        if (inst.verificationFailed) return null
        val command = decodeDeleteMessageCommand(move.json)
        val deletedAt = java.util.Date(command.recoverableDeleteDateMs ?: inst.sentTimestamp.toLong())
        if (command.messageGuids.isNotEmpty()) {
            val affected = linkedSetOf<Chat>()
            store.runInTx {
                command.messageGuids.forEach { guid ->
                    findMessageByGuidOrStaging(guid)?.let { message ->
                        message.dateDeleted = deletedAt
                        messageBox.put(message)
                        message.chat.target?.let(affected::add)
                    }
                }
                affected.forEach(::refreshChatLatest)
            }
            return affected.firstOrNull()
        }

        val chat = findOperatedChat(command, myHandles) ?: return null
        store.runInTx {
            chat.dateDeleted = deletedAt
            chat.hasUnreadMessage = false
            chat.messages.forEach { message ->
                message.dateDeleted = deletedAt
                messageBox.put(message)
            }
            refreshChatLatest(chat)
        }
        return chat
    }

    private fun handleRecoverChat(
        inst: UMessageInst,
        recover: UMessage.RecoverChat,
        myHandles: Set<String>,
    ): Chat? {
        if (inst.verificationFailed) return null
        val chat = findOperatedChat(decodeDeleteMessageCommand(recover.json), myHandles) ?: return null
        store.runInTx {
            chat.dateDeleted = null
            chat.messages.forEach { message ->
                message.dateDeleted = null
                messageBox.put(message)
            }
            refreshChatLatest(chat)
        }
        return chat
    }

    private fun handlePermanentDelete(
        inst: UMessageInst,
        delete: UMessage.PermanentDelete,
        myHandles: Set<String>,
    ): Chat? {
        if (inst.verificationFailed) return null
        val command = decodeDeleteMessageCommand(delete.json)
        if (command.messageGuids.isNotEmpty()) {
            val affected = linkedSetOf<Chat>()
            store.runInTx {
                command.messageGuids.forEach { guid ->
                    findMessageByGuidOrStaging(guid)?.let { message ->
                        message.chat.target?.let(affected::add)
                        removeAttachments(message)
                        messageBox.remove(message)
                    }
                }
                affected.forEach(::refreshChatLatest)
            }
            return affected.firstOrNull()
        }

        val chat = findOperatedChat(command, myHandles) ?: return null
        store.runInTx {
            if (chat.dateDeleted != null) {
                chat.messages.toList().forEach { message ->
                    removeAttachments(message)
                    messageBox.remove(message)
                }
                chatBox.remove(chat)
            } else {
                chat.messages.filter { it.dateDeleted != null }.forEach { message ->
                    removeAttachments(message)
                    messageBox.remove(message)
                }
                refreshChatLatest(chat)
            }
        }
        return chat
    }

    private fun removeAttachments(message: Message) {
        message.dbAttachments.toList().forEach { attachment ->
            attachmentStore?.deleteLocalFiles(attachment)
            attachmentBox.remove(attachment)
        }
    }

    private fun findOperatedChat(command: DeleteMessageCommand, myHandles: Set<String>): Chat? {
        command.chatGuid?.let { guid ->
            chatBox.query().equal(Chat_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }?.let { return it }
        }
        command.groupId?.let { groupId ->
            chatBox.query()
                .containsElement(Chat_.guidRefs, groupId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }?.let { return it }
        }
        if (command.participants.isEmpty()) return null
        val rustParticipants = command.participants.map { participant ->
            if (participant.startsWith("tel:") || participant.startsWith("mailto:")) {
                participant
            } else {
                MessageMapper.toRustHandle(participant)
            }
        }
        return findByRust(
            UConversation(
                participants = rustParticipants,
                cvName = null,
                senderGuid = command.groupId,
                afterGuid = null,
            ),
            if (command.chatGuid?.startsWith("SMS") == true) "SMS" else "iMessage",
            myHandles,
            createIfMissing = false,
        )
    }

    private fun refreshChatLatest(chat: Chat) {
        val latest = messageBox.query()
            .equal(Message_.chatId, chat.id)
            .isNull(Message_.dateDeleted)
            .orderDesc(Message_.dateCreated)
            .build().use { it.findFirst() }
        chat.dbLatestMessage.target = latest
        chat.dbOnlyLatestMessageDate = latest?.dateCreated
        if (latest == null) chat.hasUnreadMessage = false
        chatBox.put(chat)
    }

    private fun handleSendConfirm(uuid: String, error: String?) {
        val message = findMessageByGuidOrStaging(uuid) ?: return
        if (error == null) {
            message.sendingServiceId = null
            messageBox.put(message)
        } else {
            markFailed(message, error)
        }
    }

    private fun handleSmsConfirmSent(inst: UMessageInst, status: Boolean) {
        val message = findMessageByGuidOrStaging(inst.id) ?: return
        if (inst.verificationFailed) return
        if (status) {
            // Promote the staged guid to the real one (Dart SmsConfirmSent).
            val staging = message.stagingGuid
            if (staging != null) {
                message.guid = staging
                message.stagingGuid = null
                messageBox.put(message)
            }
        } else {
            markFailed(message, "Failed to send SMS")
        }
    }

    private fun handleErrorReceipt(inst: UMessageInst, msg: UMessage.Error, myHandles: Set<String>) {
        val mistakeFor = findMessageByGuidOrStaging(msg.forUuid) ?: return
        if (mistakeFor.dateDelivered != null) return // stray device complaining post-delivery
        // Error receipts are only honored for messages sent from my handles.
        if (inst.sender == null || !myHandles.contains(inst.sender)) return
        mistakeFor.error = msg.status.toLong()
        markFailed(mistakeFor, msg.statusStr)
    }

    /**
     * Dart `markFailed`: keep the original guid recoverable in stagingGuid and
     * move the row to an `error-…` guid so the UI marks it failed.
     */
    private fun markFailed(message: Message, errorText: String) {
        val guid = message.guid
        if (!guid.contains("temp") && !guid.contains("error")) {
            message.stagingGuid = guid
        }
        message.guid = "error-protocol: $errorText-${randomString(8)}"
        message.errorMessage = errorText
        message.sendingServiceId = null
        messageBox.put(message)
    }

    // ------------------------------------------------------------------
    // Chat resolution (chatForMessage + Chat.findByRust port)
    // ------------------------------------------------------------------

    /** "iMessage" unless the payload says SMS (`getService`). */
    private fun serviceFor(inst: UMessageInst): String =
        if (inst.message is UMessage.Normal && (inst.message as UMessage.Normal).isSms) "SMS" else "iMessage"

    private fun chatForInst(
        inst: UMessageInst,
        myHandles: Set<String>,
        createIfMissing: Boolean = true,
    ): Chat? {
        val conversation = inst.conversation ?: return null
        // 1. Replay: an existing row for this guid already knows its chat
        //    (also covers Edit/Unsend targets).
        findMessageByGuidOrStaging(inst.id)?.chat?.target?.let { return it }
        // 2. `afterGuid` — the conversation anchor message.
        conversation.afterGuid?.let { after ->
            findMessageByGuidOrStaging(after)?.chat?.target?.let { chat ->
                val sender = inst.sender
                if (sender == null || chat.handles.any {
                        it.address == MessageMapper.normalizeAddress(sender)
                    }) return chat
            }
        }
        return findByRust(conversation, serviceFor(inst), myHandles, createIfMissing)
    }

    private fun findByRust(
        conversation: UConversation,
        service: String,
        myHandles: Set<String>,
        createIfMissing: Boolean,
    ): Chat? {
        val isSms = service == "SMS"
        // Direct guid / guidRefs match wins (Chat.findByRustGuid).
        conversation.senderGuid?.let { senderGuid ->
            chatBox.query().equal(Chat_.guid, senderGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { q -> q.findFirst() }?.let { return it }
            chatBox.query()
                .containsElement(Chat_.guidRefs, senderGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { q -> q.findFirst() }?.let { return it }
        }

        // rustParticipantsToBB: chat handles exclude my own.
        val participants = conversation.participants
            .filter { it !in myHandles }
            .map { MessageMapper.normalizeAddress(it) }
            .distinct()
        if (participants.isEmpty()) return null

        val cvName = conversation.cvName
        val builder = chatBox.query().equal(Chat_.isRpSms, isSms)
        if (cvName != null) {
            builder.equal(Chat_.apnTitle, cvName, QueryBuilder.StringOrder.CASE_SENSITIVE)
        }
        builder.link(Chat_.handles).`in`(
            Handle_.address,
            participants.toTypedArray(),
            QueryBuilder.StringOrder.CASE_SENSITIVE,
        )
        val candidates = builder.build().use { it.find() }

        // Exact participant-set match (Dart does this in memory over the
        // link-query candidates).
        val candidate = candidates.firstOrNull { chat ->
            val addresses = chat.handles.map { h -> h.address }
            addresses.size == participants.size && addresses.containsAll(participants)
        }
        if (candidate != null) return candidate
        if (!createIfMissing) return null

        // Create — BackendSvc.createChat equivalent, with a deterministic guid
        // derived from the sorted participant set so re-derivation is stable.
        val guid = conversation.senderGuid ?: derivedChatGuid(participants, isSms)
        val participantHandles = participants.map { handleFor(it, service) }
        val mine = conversation.participants.filter { it in myHandles }
        val chat = Chat().apply {
            this.guid = guid
            chatIdentifier = if (participants.size == 1) participants[0] else guid
            isRpSms = isSms
            isArchived = false
            isPinned = false
            hasUnreadMessage = false
            senderIsKnown = false
            isRoutingStub = false
            lockChatName = false
            lockChatIcon = false
            displayName = cvName
            apnTitle = cvName
            usingHandle = mine.firstOrNull()
            guidRefs = ArrayList<String>().apply { add(guid); conversation.senderGuid?.let { add(it) } }
            handles.addAll(participantHandles)
        }
        return try {
            chatBox.put(chat)
            chat
        } catch (_: UniqueViolationException) {
            // Lost a race against a concurrent create; return the winner.
            chatBox.query().equal(Chat_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
        }
    }

    /** Stable guid for a participant-derived chat: service + sorted addresses. */
    fun derivedChatGuid(participants: List<String>, isSms: Boolean): String =
        "rp-${if (isSms) "sms" else "imsg"}-${participants.sorted().joinToString(",")}"

    // ------------------------------------------------------------------
    // Handle resolution (rustHandleToBB port)
    // ------------------------------------------------------------------

    /** Get-or-create a Handle for a rust-style (tel:/mailto:) address. */
    fun handleFor(rustHandle: String, service: String): Handle =
        HandleResolver.resolve(store, rustHandle, service)

    // ------------------------------------------------------------------
    // Persistence (Message.save + Chat.addMessageLocal + IncomingMsgHandler)
    // ------------------------------------------------------------------

    private fun findMessageByGuidOrStaging(guid: String?): Message? {
        if (guid == null) return null
        messageBox.query().equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }?.let { return it }
        messageBox.query().equal(Message_.stagingGuid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }?.let { return it }
        return null
    }

    private fun persistMapped(
        mapped: MessageMapper.Mapped,
        chat: Chat,
        inst: UMessageInst,
        myHandles: Set<String>,
    ) {
        store.runInTx {
            val incoming = mapped.message
            val existing = findMessageByGuidOrStaging(incoming.guid)

            val saved: Message
            if (existing == null) {
                // New message path: resolve the sender handle (rustHandleToBB —
                // done even for my own handles, matching Dart).
                inst.sender?.let { sender ->
                    val handle = handleFor(sender, serviceFor(inst))
                    incoming.handleRelation.target = handle
                    incoming.handleId = handle.originalROWID
                }
                incoming.isFromMe = inst.sender != null && myHandles.contains(inst.sender)
                incoming.chat.target = chat
                saved = incoming
            } else {
                // Update path (echo of a staged send, replay, or field refresh):
                // incoming content wins; receipts/dates already on the row win.
                existing.apply {
                    if (existing.stagingGuid == incoming.guid) {
                        // Staging promotion: real guid replaces the temp one,
                        // and the send is confirmed (no longer in flight).
                        this.stagingGuid = null
                        this.guid = incoming.guid
                        this.sendingServiceId = null
                    }
                    text = incoming.text ?: text
                    subject = incoming.subject ?: subject
                    threadOriginatorGuid = incoming.threadOriginatorGuid
                    threadOriginatorPart = incoming.threadOriginatorPart
                    expressiveSendStyleId = incoming.expressiveSendStyleId
                    groupTitle = incoming.groupTitle
                    itemType = incoming.itemType
                    groupActionType = incoming.groupActionType
                    otherHandle = incoming.otherHandle
                    associatedMessageGuid = incoming.associatedMessageGuid
                    associatedMessageType = incoming.associatedMessageType
                    associatedMessageEmoji = incoming.associatedMessageEmoji
                    associatedMessagePart = incoming.associatedMessagePart
                    hasAttachments = incoming.hasAttachments || hasAttachments
                    balloonBundleId = incoming.balloonBundleId
                    dbPayloadData = incoming.dbPayloadData
                    dbMetadata = incoming.dbMetadata
                    hasApplePayloadData = incoming.hasApplePayloadData
                    verificationFailed = incoming.verificationFailed
                }
                saved = existing
            }

            try {
                messageBox.put(saved)
            } catch (_: UniqueViolationException) {
                return@runInTx // duplicate delivery racing us; nothing to do
            }

            persistAttachments(mapped.attachments, saved)

            // Reaction bookkeeping (Message.save): flag the target message.
            if (saved.associatedMessageGuid != null && saved.associatedMessageType != null) {
                findMessageByGuidOrStaging(saved.associatedMessageGuid)?.let { target ->
                    if (!target.hasReactions) {
                        target.hasReactions = true
                        messageBox.put(target)
                    }
                }
            } else if (!saved.hasReactions) {
                messageBox.query()
                    .equal(Message_.associatedMessageGuid, saved.guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }?.let { reaction ->
                        saved.hasReactions = true
                        messageBox.put(saved)
                    }
            }

            // Chat.setLatestMessage + unread semantics (addMessageLocal).
            val latest = chat.dbLatestMessage.target
            val isNewer = latest == null ||
                (saved.dateCreated != null && (latest.dateCreated == null || saved.dateCreated.after(latest.dateCreated)))
            if (isNewer) {
                chat.dbLatestMessage.target = saved
                chat.dbOnlyLatestMessageDate = saved.dateCreated
                if (chat.dateDeleted != null) chat.dateDeleted = null // Chat.unDelete
                if (saved.isFromMe) chat.hasUnreadMessage = false else chat.hasUnreadMessage = true
                if (chat.usingHandle == null) {
                    chat.usingHandle = inst.sender?.takeIf { it in myHandles }
                        ?: inst.conversation?.participants?.firstOrNull { it in myHandles }
                }
            }
            chatBox.put(chat)
        }
    }

    /**
     * Persists attachment metadata and promotes outgoing temp rows in place.
     * The send UI writes local payloads under `temp-…_attN`; the Rust echo
     * names the same parts `<message-id>_N`. Keeping the existing ObjectBox id
     * preserves the message backlink while the Android layer renames the
     * matching on-disk directory.
     */
    private fun persistAttachments(incoming: List<Attachment>, saved: Message) {
        if (incoming.isEmpty()) return
        val staged = attachmentBox.query()
            .equal(Attachment_.messageId, saved.id)
            .build()
            .use { it.find() }
            .filter { it.isOutgoing && it.guid?.startsWith("temp-") == true }
            .sortedBy { it.guid }
            .toMutableList()

        incoming.forEach { replacement ->
            val replacementGuid = replacement.guid ?: return@forEach
            val alreadyPresent = attachmentBox.query()
                .equal(Attachment_.guid, replacementGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build()
                .use { it.findFirst() }
            val stagedRow = staged.removeFirstOrNull()
            val target = alreadyPresent ?: stagedRow ?: replacement
            mergeAttachment(target, replacement, saved, promotedLocal = stagedRow === target)
            try {
                attachmentBox.put(target)
                if (alreadyPresent != null && stagedRow != null && stagedRow.id != alreadyPresent.id) {
                    attachmentBox.remove(stagedRow)
                }
            } catch (_: UniqueViolationException) {
                // Another delivery inserted the real guid first. Keep that
                // winner and remove only our stale temp row.
                if (stagedRow != null && stagedRow.id != target.id) attachmentBox.remove(stagedRow)
            }
        }
    }

    private fun mergeAttachment(
        target: Attachment,
        replacement: Attachment,
        saved: Message,
        promotedLocal: Boolean,
    ) {
        target.guid = replacement.guid
        target.originalROWID = replacement.originalROWID ?: target.originalROWID
        target.uti = replacement.uti ?: target.uti
        target.mimeType = replacement.mimeType ?: target.mimeType
        target.isOutgoing = replacement.isOutgoing || target.isOutgoing
        target.transferName = replacement.transferName ?: target.transferName
        target.totalBytes = replacement.totalBytes ?: target.totalBytes
        target.height = replacement.height ?: target.height
        target.width = replacement.width ?: target.width
        target.webUrl = replacement.webUrl ?: target.webUrl
        target.dbMetadata = replacement.dbMetadata ?: target.dbMetadata
        target.hasLivePhoto = replacement.hasLivePhoto || target.hasLivePhoto
        target.ckRecordId = replacement.ckRecordId ?: target.ckRecordId
        target.isDownloaded = promotedLocal || replacement.isDownloaded || target.isDownloaded
        val mergedMetadata = (target.metadata ?: emptyMap()) + (replacement.metadata ?: emptyMap())
        if (mergedMetadata.isNotEmpty()) target.metadata = mergedMetadata
        target.message.target = saved
    }

    // ------------------------------------------------------------------
    // Typing indicator bookkeeping
    // ------------------------------------------------------------------

    private fun setTyping(chatGuid: String, senderAddress: String) {
        val now = System.currentTimeMillis()
        val kept = _typing.value.filter { !(it.chatGuid == chatGuid && it.senderAddress == senderAddress) }
        _typing.value = kept + TypingIndicator(chatGuid, senderAddress, now + TYPING_TIMEOUT_MS)
        scheduleSweep(now + TYPING_TIMEOUT_MS)
    }

    private fun clearTyping(chatGuid: String, senderAddress: String) {
        _typing.value = _typing.value.filterNot {
            it.chatGuid == chatGuid && it.senderAddress == senderAddress
        }
    }

    private fun scheduleSweep(untilMs: Long) {
        scope.launch {
            delay(maxOf(1, untilMs - System.currentTimeMillis()))
            val now = System.currentTimeMillis()
            val swept = _typing.value.filter { it.expiresAtMs > now }
            if (swept.size != _typing.value.size) _typing.value = swept
        }
    }

    fun close() {
        scope.cancel()
    }
}

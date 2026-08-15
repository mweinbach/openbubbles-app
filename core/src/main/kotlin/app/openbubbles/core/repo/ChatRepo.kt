package app.openbubbles.core.repo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.HandleDisplayInfo
import app.openbubbles.core.intake.HandleResolver
import app.openbubbles.core.model.ChatListItem
import app.openbubbles.core.model.ChatMute
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.core.model.isGroupConversation
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.ContactV2
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.db.Handle_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge

/**
 * ObjectBox-backed repository for the chat list.
 *
 * Read side mirrors the Flutter app's `ChatsSvc`/`Chat.getChatsAsync`
 * semantics: active (non-deleted) chats ordered pinned-first (by pin index),
 * then by latest-message date desc, with a latest-message projection and
 * unread state. All queries run on [Dispatchers.IO]; flows re-emit on every
 * ObjectBox write that touches the underlying query.
 */
class ChatRepo(private val store: BoxStore) {

    private val chatBox = store.boxFor(Chat::class.java)
    private val messageBox = store.boxFor(Message::class.java)
    private val handleBox = store.boxFor(Handle::class.java)
    private val contactSync = ContactSync(store)

    /** Chat DTO projection of active chats, ordered for the list UI. */
    fun chats(limit: Int = 0): List<ChatListItem> {
        val query = chatBox.query()
            .isNull(Chat_.dateDeleted)
            .orderDesc(Chat_.isPinned)
            .order(Chat_.pinIndex)
            .orderDesc(Chat_.dbOnlyLatestMessageDate)
            .build()
        return query.use {
            val found = if (limit > 0) it.find(0, limit.toLong()) else it.find()
            val contactInfo = contactSync.displayInfoByHandleId()
            // Project while the query and its creator thread are still alive;
            // relation reads in toItem must not escape to a collector thread.
            found.map { chat -> toItem(chat, contactInfo) }
        }
    }

    /**
     * Reactive version of [chats]. The entity subscription is only an
     * invalidation signal: query subscriptions hand entity lists backed by a
     * read transaction to another coroutine, which is unsafe under a busy
     * history import. Conflation also drops obsolete refreshes while a page is
     * being committed instead of queueing one full chat projection per write.
     */
    fun observeChats(): Flow<List<ChatListItem>> =
        merge(
            store.subscribe(Chat::class.java).asFlow().map { Unit },
            // Contact imports/relinks do not mutate Chat rows. Include their
            // invalidations so an open chat list replaces raw addresses with
            // names immediately instead of waiting for the next message.
            store.subscribe(ContactV2::class.java).asFlow().drop(1).map { Unit },
        )
            .conflate()
            .map { chats() }
            .flowOn(Dispatchers.IO)

    fun chatByGuid(guid: String): Chat? =
        chatBox.query()
            .equal(Chat_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    /**
     * Get-or-create a chat for an explicit participant set (new-conversation
     * UI). Mirrors the ingestor's `findByRust` creation path: exact
     * participant-set match first, then create with a deterministic guid so
     * an incoming message for the same participants resolves to this row.
     *
     * @param addresses rust-style handles (`tel:+1555...` / `mailto:me@...`),
     *   already normalized, self excluded.
     */
    fun findOrCreateByAddresses(addresses: List<String>, service: String): Chat {
        val isSms = service == "SMS"
        val participants = addresses.map { MessageMapper.normalizeAddress(it) }.distinct()
        require(participants.isNotEmpty()) { "chat needs at least one participant" }

        val builder = chatBox.query().equal(Chat_.isRpSms, isSms)
        builder.link(Chat_.handles).`in`(
            Handle_.address,
            participants.toTypedArray(),
            QueryBuilder.StringOrder.CASE_SENSITIVE,
        )
        val candidates = builder.build().use { it.find() }
        candidates.firstOrNull { chat ->
            val chatAddresses = chat.handles.map { it.address }
            chatAddresses.size == participants.size && chatAddresses.containsAll(participants)
        }?.let { return it }

        val guid = "rp-${if (isSms) "sms" else "imsg"}-${participants.sorted().joinToString(",")}"
        val participantHandles = participants.map { HandleResolver.resolve(store, it, service) }
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
            displayName = null
            guidRefs = ArrayList<String>().apply { add(guid) }
            handles.addAll(participantHandles)
        }
        return try {
            chatBox.put(chat)
            chat
        } catch (_: io.objectbox.exception.UniqueViolationException) {
            chatBox.query().equal(Chat_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }!!
        }
    }

    /**
     * Mark a chat read (`Chat.toggleHasUnread(false)` + last-read pointer).
     * Sending the read receipt to peers is the service layer's job.
     */
    fun markRead(chatId: Long) {
        val chat = chatBox.get(chatId) ?: return
        chat.hasUnreadMessage = false
        chat.lastReadMessageGuid = chat.dbLatestMessage.target?.guid
        chatBox.put(chat)
    }

    fun setPinned(chatId: Long, pinned: Boolean) {
        val chat = chatBox.get(chatId) ?: return
        chat.isPinned = pinned
        if (pinned) {
            // New pins go to the top of the pin order.
            val maxPin = chatBox.query().isNull(Chat_.dateDeleted).build().use { q ->
                q.find().maxOfOrNull { it.pinIndex ?: 0L } ?: -1L
            }
            chat.pinIndex = maxPin + 1
        } else {
            chat.pinIndex = null
        }
        chatBox.put(chat)
    }

    fun setMuted(chatId: Long, muted: Boolean) {
        val chat = chatBox.get(chatId) ?: return
        chat.muteType = if (muted) "mute" else null
        chat.muteArgs = null
        chatBox.put(chat)
    }

    fun setMutedUntil(chatId: Long, untilEpochMs: Long) {
        val chat = chatBox.get(chatId) ?: return
        chat.muteType = "temporary_mute"
        chat.muteArgs = java.time.Instant.ofEpochMilli(untilEpochMs).toString()
        chatBox.put(chat)
    }

    fun setArchived(chatId: Long, archived: Boolean) {
        val chat = chatBox.get(chatId) ?: return
        chat.isArchived = archived
        if (archived) {
            chat.isPinned = false
            chat.pinIndex = null
        }
        chatBox.put(chat)
    }

    /** Soft-delete so a genuinely new incoming message can restore the chat. */
    fun softDelete(chatId: Long): String? {
        val chat = chatBox.get(chatId) ?: return null
        chat.dateDeleted = java.util.Date()
        chat.hasUnreadMessage = false
        chatBox.put(chat)
        return chat.ckRecordId
    }

    /**
     * Unread message count: incoming, non-reaction messages newer than the
     * last-read message (all incoming messages when nothing was read yet).
     * Zero once the chat-level unread flag is cleared.
     */
    fun unreadCount(chat: Chat): Int {
        if (!chat.hasUnreadMessage) return 0
        val lastReadDate = chat.lastReadMessageGuid?.let { guid ->
            messageBox.query()
                .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }?.dateCreated
        }
        val builder = messageBox.query()
            .equal(Message_.chatId, chat.id)
            .equal(Message_.isFromMe, false)
            .isNull(Message_.associatedMessageGuid)
            .isNull(Message_.dateDeleted)
        if (lastReadDate != null) builder.greater(Message_.dateCreated, lastReadDate)
        return builder.build().use { it.count().toInt() }
    }

    // ------------------------------------------------------------------
    // DTO projection
    // ------------------------------------------------------------------

    internal fun toItem(chat: Chat): ChatListItem =
        toItem(chat, contactSync.displayInfoByHandleId())

    private fun toItem(
        chat: Chat,
        contactInfo: Map<Long, HandleDisplayInfo>,
    ): ChatListItem {
        val latest = chat.dbLatestMessage.target
        return ChatListItem(
            id = chat.id,
            guid = chat.guid,
            title = deriveTitle(chat, contactInfo),
            snippet = latest?.let { snippet(it) },
            date = latest?.dateCreated ?: chat.dbOnlyLatestMessageDate,
            hasUnread = chat.hasUnreadMessage,
            unreadCount = unreadCount(chat),
            pinned = chat.isPinned,
            muted = ChatMute.shouldMute(chat),
            archived = chat.isArchived,
            isSms = chat.isRpSms == true,
            participantCount = chat.handles.size,
            avatarAddress = chat.handles.singleOrNull()?.let { it.formattedAddress ?: it.address },
            avatarPath = chat.customAvatarPath,
            isGroup = chat.isGroupConversation(),
        )
    }

    /** Chat list snippet for the latest message. */
    private fun snippet(message: Message): String {
        if (message.itemType != null && message.itemType > 0) return groupEventText(message)
        if (message.associatedMessageGuid != null && message.associatedMessageType != null) {
            return reactionSnippet(message)
        }
        if (!message.text.isNullOrBlank()) return message.text
        return if (message.hasAttachments) "Attachment" else ""
    }

    private fun reactionSnippet(message: Message): String {
        val rawType = message.associatedMessageType ?: return "Reaction"
        val removed = rawType.startsWith("-")
        val type = rawType.removePrefix("-")
        val label = when (type) {
            "love" -> "loved"
            "like" -> "liked"
            "dislike" -> "disliked"
            "laugh" -> "laughed at"
            "emphasize" -> "emphasized"
            "question" -> "questioned"
            "emoji" -> "reacted ${message.associatedMessageEmoji.orEmpty()} to"
            else -> "reacted to"
        }
        val actor = if (message.isFromMe) {
            "You"
        } else {
            message.handleRelation.target?.let(::handleDisplayName) ?: "Someone"
        }
        val targetText = message.associatedMessageGuid?.let { guid ->
            messageBox.query()
                .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
                ?.text
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        } ?: "a message"
        return if (removed) {
            "$actor removed a reaction from “$targetText”"
        } else {
            "$actor $label “$targetText”"
        }
    }

    /** Port of `Chat.getChatCreatorSubtitle`, including linked contacts. */
    internal fun deriveTitle(chat: Chat): String =
        deriveTitle(chat, contactSync.displayInfoByHandleId())

    private fun deriveTitle(
        chat: Chat,
        contactInfo: Map<Long, HandleDisplayInfo>,
    ): String {
        if (!chat.displayName.isNullOrEmpty()) return chat.displayName
        val handles = chat.handles
        return when {
            handles.isEmpty() ->
                if (chat.chatIdentifier?.startsWith("urn:biz") == true) "Business Chat"
                else chat.chatIdentifier ?: "Unnamed chat"
            handles.size == 1 -> handleDisplayName(handles[0], contactInfo)
            handles.size <= 4 -> handles.dropLast(1)
                .joinToString(", ") { handleShortName(it, contactInfo) } +
                " & " + handleShortName(handles.last(), contactInfo)
            else -> handles.take(3).joinToString(", ") { handleShortName(it, contactInfo) } +
                " & ${handles.size - 3} others"
        }
    }

    private fun handleDisplayName(
        handle: Handle,
        contactInfo: Map<Long, HandleDisplayInfo>,
    ): String = contactInfo[handle.id]?.name
        ?.takeIf { it.isNotBlank() }
        ?: handle.formattedAddress
        ?: handle.address

    private fun handleShortName(
        handle: Handle,
        contactInfo: Map<Long, HandleDisplayInfo>,
    ): String = handleDisplayName(handle, contactInfo).split(' ', limit = 2).firstOrNull()
        ?: handleDisplayName(handle, contactInfo)

    internal fun handleDisplayName(handle: Handle): String =
        handle.formattedAddress ?: handle.address

    internal fun handleShortName(handle: Handle): String =
        handleDisplayName(handle).split(' ', limit = 2).firstOrNull()
            ?: handleDisplayName(handle)

    /**
     * Port of `Message.buildGroupEventText` for rename / participant / photo
     * events (itemType 1–3).
     */
    internal fun groupEventText(message: Message): String {
        val name = when {
            message.isFromMe -> "You"
            message.handleRelation.target != null -> handleDisplayName(message.handleRelation.target)
            else -> "Unknown"
        }
        val other = message.otherHandle?.let { rowId ->
            handleBox.query().equal(Handle_.originalROWID, rowId).build().use { it.findFirst() }
        }?.let { handleDisplayName(it) } ?: "someone"

        return when (message.itemType?.toInt()) {
            1 -> if (message.groupActionType == 0L) "$name added $other to the conversation."
            else "$name removed $other from the conversation."
            2 -> if (message.groupTitle != null) "$name named the conversation \"${message.groupTitle}\"."
            else "$name removed the name from the conversation."
            3 -> when (message.groupActionType) {
                0L -> "$name left the conversation."
                1L -> "$name changed the group photo."
                else -> "$name removed the group photo."
            }
            else -> "Group event"
        }
    }
}

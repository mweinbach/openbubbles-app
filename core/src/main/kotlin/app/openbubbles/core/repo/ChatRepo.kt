package app.openbubbles.core.repo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.HandleDisplayInfo
import app.openbubbles.core.intake.HandleResolver
import app.openbubbles.core.model.ChatListItem
import app.openbubbles.core.model.ChatMute
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.core.model.GROUP_CHAT_STYLE
import app.openbubbles.core.model.isGroupConversation
import app.openbubbles.core.model.otherDirectHandle
import app.openbubbles.core.model.otherDirectHandles
import app.openbubbles.core.model.participantAddresses
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.ContactV2
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.db.Handle_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
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
class ChatRepo(
    private val store: BoxStore,
    private val selfAddresses: () -> Set<String> = { emptySet() },
) {

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
            val found = it.find()
            val contactInfo = contactSync.displayInfoByHandleId()
            val contactIds = contactSync.contactIdsByHandleId()
            val addressInfo = contactSync.displayInfoByMatchKey()
            val lookups = batchProjectionLookups(found)
            // Project while the query and its creator thread are still alive;
            // relation reads in toItem must not escape to a collector thread.
            val grouped = groupContactChats(found, contactInfo, contactIds, addressInfo, lookups)
            if (limit > 0) grouped.take(limit) else grouped
        }
    }

    /**
     * Message rows the list projection needs, fetched once per emission
     * instead of per chat: last-read dates for unread counting and target
     * texts for reaction snippets. `observeChats` re-projects on every DB
     * write, so per-chat guid queries here multiply across the whole table.
     */
    private class ProjectionLookups(
        val lastReadDateByGuid: Map<String, java.util.Date?>,
        val reactionTargetTextByGuid: Map<String, String?>,
    )

    private fun batchProjectionLookups(chats: List<Chat>): ProjectionLookups {
        val lastReadGuids = chats
            .filter { it.hasUnreadMessage }
            .mapNotNull { it.lastReadMessageGuid }
        val reactionGuids = chats
            .mapNotNull { it.dbLatestMessage.target }
            .filter { it.associatedMessageGuid != null && it.associatedMessageType != null }
            .mapNotNull { it.associatedMessageGuid }
        val wanted = (lastReadGuids + reactionGuids).distinct()
        if (wanted.isEmpty()) return ProjectionLookups(emptyMap(), emptyMap())
        val rows = messageBox.query()
            .`in`(Message_.guid, wanted.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.find() }
            .associateBy { it.guid }
        return ProjectionLookups(
            lastReadDateByGuid = lastReadGuids.distinct().associateWith { rows[it]?.dateCreated },
            reactionTargetTextByGuid = reactionGuids.distinct().associateWith { rows[it]?.text },
        )
    }

    /** Recoverably deleted conversations, newest deletion first. */
    fun recentlyDeleted(limit: Int = 0): List<ChatListItem> {
        val found = chatBox.query()
            .notNull(Chat_.dateDeleted)
            .orderDesc(Chat_.dateDeleted)
            .build().use { it.find() }
        val contactInfo = contactSync.displayInfoByHandleId()
        val addressInfo = contactSync.displayInfoByMatchKey()
        val projected = found.map { toItem(it, contactInfo, addressInfo) }
        return if (limit > 0) projected.take(limit) else projected
    }

    /**
     * Protocol chat ids represented by one direct-contact conversation.
     *
     * Only exact links to the same stored [ContactV2] are considered. Names,
     * partial phone numbers, and other ambiguous metadata never merge chats.
     */
    fun relatedDirectChatIds(chatId: Long): List<Long> {
        val chat = chatBox.get(chatId) ?: return emptyList()
        refreshRelatedChats()
        val contactId = cachedContactIdByChatId[chatId]
            // Soft-deleted or brand-new chats are not in the active-chat
            // cache; resolve their contact directly so grouping still works.
            ?: directContactId(chat, contactSync.contactIdsByHandleId())
            ?: return listOf(chatId)
        return cachedChatIdsByContactId[contactId].orEmpty().ifEmpty { listOf(chatId) }
    }

    // ------------------------------------------------------------------
    // Related-chat cache
    //
    // relatedDirectChatIds used to scan every active chat (walking handle
    // relations per row) on each call, and it is called on every transcript
    // page load, notification, and chat mutation. The grouping only depends
    // on the chat, handle, and contact tables, so it follows ContactSync's
    // memoization pattern: mutators in this class invalidate synchronously,
    // store observers catch outside writers, and an active-row-count probe
    // catches creations, soft deletes, and restores before the asynchronous
    // observer fires.
    // ------------------------------------------------------------------

    private val relatedLock = Any()

    @Volatile
    private var relatedDirty = true
    private var relatedObserversRegistered = false
    private var relatedActiveChatCount = -1L

    @Volatile
    private var cachedContactIdByChatId: Map<Long, Long> = emptyMap()

    @Volatile
    private var cachedChatIdsByContactId: Map<Long, List<Long>> = emptyMap()

    /** Drops the memoized contact-to-chats grouping; next read rebuilds it. */
    fun invalidateRelatedChats() {
        relatedDirty = true
    }

    private fun activeChatCount(): Long =
        chatBox.query().isNull(Chat_.dateDeleted).build().use { it.count() }

    private fun ensureRelatedObservers() {
        if (relatedObserversRegistered) return
        synchronized(relatedLock) {
            if (relatedObserversRegistered) return
            store.subscribe(Chat::class.java).observer { invalidateRelatedChats() }
            store.subscribe(Handle::class.java).observer { invalidateRelatedChats() }
            store.subscribe(ContactV2::class.java).observer { invalidateRelatedChats() }
            relatedObserversRegistered = true
        }
    }

    private fun refreshRelatedChats() {
        ensureRelatedObservers()
        if (!relatedDirty && activeChatCount() == relatedActiveChatCount) return
        synchronized(relatedLock) {
            val activeCount = activeChatCount()
            if (!relatedDirty && activeCount == relatedActiveChatCount) return
            // Clear before reading: a write landing mid-rebuild re-dirties
            // and the next read rebuilds again instead of serving a torn set.
            relatedDirty = false
            val contactIds = contactSync.contactIdsByHandleId()
            val byChat = HashMap<Long, Long>()
            val byContact = HashMap<Long, MutableList<Long>>()
            chatBox.query().isNull(Chat_.dateDeleted).build().use { query ->
                query.find().forEach { candidate ->
                    val contactId = directContactId(candidate, contactIds) ?: return@forEach
                    byChat[candidate.id] = contactId
                    byContact.getOrPut(contactId) { mutableListOf() } += candidate.id
                }
            }
            relatedActiveChatCount = activeCount
            cachedContactIdByChatId = byChat
            cachedChatIdsByContactId = byContact.mapValues { (_, ids) -> ids.distinct().sorted() }
        }
    }

    /**
     * Everyone in this conversation except the local account.
     *
     * Direct chats that were contact-grouped (phone + email threads) union
     * every related protocol chat. A 1:1 row with no linked handles still
     * falls back to [Chat.chatIdentifier] so the contact sheet matches the
     * conversation list.
     */
    fun participantAddresses(chatId: Long): List<String> {
        val self = selfAddresses()
        return relatedDirectChatIds(chatId)
            .ifEmpty { listOf(chatId) }
            .flatMap { id -> chatBox.get(id)?.participantAddresses(self).orEmpty() }
            .distinct()
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
            // ChatListItem is a pure projection of DB state, so an identical
            // list is an identical UI frame — drop it instead of recomposing
            // the whole chat list on every unrelated write during a sync.
            .distinctUntilChanged()
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
            chatMatchesParticipants(chat, participants, isSms)
        }?.let { return it }

        if (!isSms && participants.size == 1) {
            existingDirectChatForAddress(participants.single())?.let { return it }
        }

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
            val firstPin = chatBox.query().isNull(Chat_.dateDeleted).build().use { q ->
                q.find()
                    .asSequence()
                    .filter { it.isPinned && it.id != chatId }
                    .mapNotNull { it.pinIndex }
                    .minOrNull() ?: 0L
            }
            chat.pinIndex = firstPin - 1L
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

    /**
     * User-selected send-from address for this chat (rust form, or null to
     * follow the default). Overrides both the global default sending handle
     * and the address the conversation was received on.
     */
    fun setSenderOverride(chatId: Long, handle: String?) {
        val chat = chatBox.get(chatId) ?: return
        chat.senderOverride = handle?.takeIf { it.isNotBlank() }
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

    fun setLockChatName(chatId: Long, locked: Boolean) {
        val chat = chatBox.get(chatId) ?: return
        chat.lockChatName = locked
        chatBox.put(chat)
    }

    fun setLockChatIcon(chatId: Long, locked: Boolean) {
        val chat = chatBox.get(chatId) ?: return
        chat.lockChatIcon = locked
        chatBox.put(chat)
    }

    fun setAutoSendReadReceipts(chatId: Long, enabled: Boolean) {
        val chat = chatBox.get(chatId) ?: return
        chat.autoSendReadReceipts = enabled
        chatBox.put(chat)
    }

    fun setAutoSendTypingIndicators(chatId: Long, enabled: Boolean) {
        val chat = chatBox.get(chatId) ?: return
        chat.autoSendTypingIndicators = enabled
        chatBox.put(chat)
    }

    fun setCustomAvatarPath(chatId: Long, path: String?) {
        val chat = chatBox.get(chatId) ?: return
        chat.customAvatarPath = path?.takeIf { it.isNotBlank() }
        chatBox.put(chat)
    }

    fun isBlocked(chatId: Long): Boolean {
        val chat = chatBox.get(chatId) ?: return false
        val remote = otherHandles(chat)
        return remote.isNotEmpty() && remote.all { it.blocked == true }
    }

    fun setBlocked(chatId: Long, blocked: Boolean, archive: Boolean = false) {
        val chat = chatBox.get(chatId) ?: return
        store.runInTx {
            otherHandles(chat).forEach { handle ->
                handle.blocked = blocked
                handleBox.put(handle)
            }
            if (archive || !blocked) {
                chat.isArchived = archive
                if (archive) {
                    chat.isPinned = false
                    chat.pinIndex = null
                }
            }
            if (blocked) {
                chat.muteType = "mute"
                chat.muteArgs = null
            }
            chatBox.put(chat)
        }
    }

    fun restoreDeleted(chatId: Long) {
        val chat = chatBox.get(chatId) ?: return
        store.runInTx {
            chat.dateDeleted = null
            chat.messages.forEach { message ->
                message.dateDeleted = null
                messageBox.put(message)
            }
            chatBox.put(chat)
        }
    }

    fun permanentlyDelete(chatId: Long) {
        val chat = chatBox.get(chatId) ?: return
        store.runInTx {
            chat.messages.toList().forEach(messageBox::remove)
            chatBox.remove(chat)
        }
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
    fun unreadCount(chat: Chat): Int = unreadCount(chat, lookups = null)

    private fun unreadCount(chat: Chat, lookups: ProjectionLookups?): Int {
        if (!chat.hasUnreadMessage) return 0
        val lastReadDate = chat.lastReadMessageGuid?.let { guid ->
            if (lookups != null) {
                lookups.lastReadDateByGuid[guid]
            } else {
                messageBox.query()
                    .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }?.dateCreated
            }
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
        toItem(
            chat,
            contactSync.displayInfoByHandleId(),
            contactSync.displayInfoByMatchKey(),
        )

    private fun toItem(
        chat: Chat,
        contactInfo: Map<Long, HandleDisplayInfo>,
        addressInfo: Map<String, HandleDisplayInfo>,
        lookups: ProjectionLookups? = null,
    ): ChatListItem {
        val latest = chat.dbLatestMessage.target
        val others = otherHandles(chat)
        val isGroup = isGroupConversation(chat.style, others.size)
        val identity = directIdentity(chat, contactInfo, addressInfo)
        val groupPhoto = chat.customAvatarPath?.takeIf { path ->
            path.isNotBlank() && File(path).isFile
        }
        return ChatListItem(
            id = chat.id,
            guid = chat.guid,
            title = deriveTitle(chat, contactInfo, addressInfo),
            snippet = latest?.let { snippet(it, isGroup, contactInfo, addressInfo, lookups) },
            date = latest?.dateCreated ?: chat.dbOnlyLatestMessageDate,
            hasUnread = chat.hasUnreadMessage,
            unreadCount = unreadCount(chat, lookups),
            pinned = chat.isPinned,
            muted = ChatMute.shouldMute(chat),
            notifsSilenced = chat.notifsSilenced,
            archived = chat.isArchived,
            isSms = chat.isRpSms == true,
            participantCount = others.size,
            avatarAddress = identity?.address,
            avatarPath = groupPhoto ?: identity?.info?.avatar,
            isGroup = isGroup,
            customBackgroundPath = chat.customBackgroundPath,
            transcriptBackgroundPath = chat.transcriptPosterPath,
            transcriptBackgroundVersion = chat.transcriptBackgroundVersion,
            senderOverride = chat.senderOverride,
            receivedOnHandle = chat.usingHandle,
            dateDeleted = chat.dateDeleted,
            lockChatName = chat.lockChatName == true,
            lockChatIcon = chat.lockChatIcon == true,
            autoSendReadReceipts = chat.autoSendReadReceipts,
            autoSendTypingIndicators = chat.autoSendTypingIndicators,
            blocked = others.isNotEmpty() && others.all { it.blocked == true },
        )
    }

    private data class ContactChatProjection(
        val chat: Chat,
        val item: ChatListItem,
        val contactInfo: HandleDisplayInfo?,
    )

    private data class DirectIdentity(
        val address: String?,
        val info: HandleDisplayInfo?,
    )

    private fun groupContactChats(
        chats: List<Chat>,
        contactInfo: Map<Long, HandleDisplayInfo>,
        contactIds: Map<Long, Long>,
        addressInfo: Map<String, HandleDisplayInfo>,
        lookups: ProjectionLookups? = null,
    ): List<ChatListItem> {
        val grouped = linkedMapOf<String, MutableList<ContactChatProjection>>()
        chats.forEach { chat ->
            val identity = directIdentity(chat, contactInfo, addressInfo)
            val contactId = directContactId(chat, contactIds)
            val key = contactId?.let { "contact:$it" } ?: "chat:${chat.id}"
            grouped.getOrPut(key) { mutableListOf() } += ContactChatProjection(
                chat = chat,
                item = toItem(chat, contactInfo, addressInfo, lookups),
                contactInfo = identity?.info,
            )
        }
        return grouped.values.map(::mergeContactChats)
    }

    private fun mergeContactChats(group: List<ContactChatProjection>): ChatListItem {
        if (group.size == 1) {
            val only = group.single()
            return only.item.copy(
                avatarPath = only.item.avatarPath ?: only.contactInfo?.avatar,
                memberChatIds = listOf(only.chat.id),
                preferredChatId = only.chat.id,
            )
        }

        val canonical = group.minBy { it.chat.id }
        val newest = group.maxWithOrNull(
            compareBy<ContactChatProjection> { it.item.date?.time ?: Long.MIN_VALUE }
                .thenBy { it.chat.id },
        ) ?: canonical
        val identity = canonical.contactInfo
        return canonical.item.copy(
            title = identity?.name?.takeIf(String::isNotBlank) ?: canonical.item.title,
            snippet = newest.item.snippet,
            date = newest.item.date,
            hasUnread = group.any { it.item.hasUnread },
            unreadCount = group.sumOf { it.item.unreadCount },
            pinned = group.any { it.item.pinned },
            muted = group.all { it.item.muted },
            archived = group.all { it.item.archived },
            avatarPath = newest.item.avatarPath ?: identity?.avatar,
            customBackgroundPath = newest.item.customBackgroundPath
                ?: canonical.item.customBackgroundPath,
            transcriptBackgroundPath = newest.item.transcriptBackgroundPath
                ?: canonical.item.transcriptBackgroundPath,
            transcriptBackgroundVersion = newest.item.transcriptBackgroundVersion
                ?: canonical.item.transcriptBackgroundVersion,
            memberChatIds = group.map { it.chat.id }.distinct().sorted(),
            preferredChatId = newest.chat.id,
            // The newest member carries the latest routing state; overrides
            // set through the UI are written to every member chat anyway.
            senderOverride = newest.item.senderOverride
                ?: canonical.item.senderOverride,
            receivedOnHandle = newest.item.receivedOnHandle
                ?: canonical.item.receivedOnHandle,
        )
    }

    private fun directContactId(
        chat: Chat,
        contactIds: Map<Long, Long>,
    ): Long? {
        if (chat.isRpSms == true || chat.style == GROUP_CHAT_STYLE) return null
        val handle = otherHandle(chat) ?: return null
        return contactIds[handle.id]
    }

    /**
     * The other person in a 1:1 chat, plus any synced name/photo. CloudKit
     * rows often include our own handle; when that makes [otherHandle]
     * ambiguous, [Chat.chatIdentifier] is still the peer address.
     */
    private fun directIdentity(
        chat: Chat,
        contactInfo: Map<Long, HandleDisplayInfo>,
        addressInfo: Map<String, HandleDisplayInfo>,
    ): DirectIdentity? {
        if (isGroupConversation(chat.style, otherHandles(chat).size)) return null
        val handle = otherHandle(chat)
        val address = handle?.let { it.formattedAddress ?: it.address }
            ?: chat.chatIdentifier?.takeIf { it.isNotBlank() }
        val info = handle?.let { contactInfo[it.id] }
            ?: ContactSync.displayInfoForMatchKeys(address, addressInfo)
            ?: ContactSync.displayInfoForMatchKeys(chat.chatIdentifier, addressInfo)
        if (address == null && info == null) return null
        return DirectIdentity(address = address, info = info)
    }

    private fun otherHandle(chat: Chat): Handle? =
        chat.otherDirectHandle(selfAddresses())

    private fun otherHandles(chat: Chat): List<Handle> =
        chat.otherDirectHandles(selfAddresses())

    private fun chatMatchesParticipants(
        chat: Chat,
        participants: List<String>,
        isSms: Boolean,
    ): Boolean {
        if ((chat.isRpSms == true) != isSms) return false
        if (chat.style == GROUP_CHAT_STYLE) {
            val addresses = chat.handles.map { MessageMapper.normalizeAddress(it.address) }.toSet()
            return addresses.size == participants.size && addresses.containsAll(participants)
        }
        val others = otherHandles(chat).map { MessageMapper.normalizeAddress(it.address) }
        return others.size == participants.size && others.containsAll(participants)
    }

    private fun existingDirectChatForAddress(address: String): Chat? {
        val contactId = contactSync.contactIdForAddress(address) ?: return null
        val contactIds = contactSync.contactIdsByHandleId()
        return chatBox.query()
            .isNull(Chat_.dateDeleted)
            .equal(Chat_.isRpSms, false)
            .build().use { query ->
                query.find()
                    .filter { candidate -> directContactId(candidate, contactIds) == contactId }
                    .maxWithOrNull(
                        compareBy<Chat> { it.dbOnlyLatestMessageDate?.time ?: Long.MIN_VALUE }
                            .thenBy { it.id },
                    )
            }
    }

    /** Chat list snippet for the latest message. */
    private fun snippet(
        message: Message,
        isGroup: Boolean,
        contactInfo: Map<Long, HandleDisplayInfo>,
        addressInfo: Map<String, HandleDisplayInfo>,
        lookups: ProjectionLookups? = null,
    ): String {
        if (message.itemType != null && message.itemType > 0) {
            return groupEventText(message, contactInfo, addressInfo)
        }
        if (message.associatedMessageGuid != null && message.associatedMessageType != null) {
            return reactionSnippet(message, contactInfo, addressInfo, lookups)
        }
        val body = when {
            !message.text.isNullOrBlank() -> message.text
            message.hasAttachments -> "Attachment"
            else -> ""
        }
        if (body.isEmpty()) return ""
        if (!isGroup || message.isFromMe) return body
        val handle = message.handleRelation.target ?: return body
        val displayName = handleDisplayName(handle, contactInfo, addressInfo)
        return if (isHandleAddress(displayName, handle)) body else "${handleShortName(handle, contactInfo, addressInfo)}: $body"
    }

    private fun reactionSnippet(
        message: Message,
        contactInfo: Map<Long, HandleDisplayInfo>,
        addressInfo: Map<String, HandleDisplayInfo>,
        lookups: ProjectionLookups? = null,
    ): String {
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
        val actor = snippetActor(message, contactInfo, addressInfo)
        val targetText = message.associatedMessageGuid?.let { guid ->
            val raw = if (lookups != null) {
                lookups.reactionTargetTextByGuid[guid]
            } else {
                messageBox.query()
                    .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
                    ?.text
            }
            raw?.trim()?.takeIf { it.isNotEmpty() }
        } ?: "a message"
        return if (removed) {
            "$actor removed a reaction from “$targetText”"
        } else {
            "$actor $label “$targetText”"
        }
    }

    private fun snippetActor(
        message: Message,
        contactInfo: Map<Long, HandleDisplayInfo>,
        addressInfo: Map<String, HandleDisplayInfo>,
    ): String {
        if (message.isFromMe) return "You"
        val handle = message.handleRelation.target ?: return "Someone"
        return handleShortName(handle, contactInfo, addressInfo)
    }

    /** Port of `Chat.getChatCreatorSubtitle`, including linked contacts. */
    internal fun deriveTitle(chat: Chat): String =
        deriveTitle(
            chat,
            contactSync.displayInfoByHandleId(),
            contactSync.displayInfoByMatchKey(),
        )

    private fun deriveTitle(
        chat: Chat,
        contactInfo: Map<Long, HandleDisplayInfo>,
        addressInfo: Map<String, HandleDisplayInfo> = emptyMap(),
    ): String {
        if (!chat.displayName.isNullOrEmpty()) return chat.displayName
        val other = otherHandle(chat)
        if (other != null) return handleDisplayName(other, contactInfo, addressInfo)
        ContactSync.displayInfoForMatchKeys(chat.chatIdentifier, addressInfo)
            ?.name?.takeIf { it.isNotBlank() }
            ?.let { return it }
        val handles = otherHandles(chat).ifEmpty { chat.handles }
        return when {
            handles.isEmpty() ->
                if (chat.chatIdentifier?.startsWith("urn:biz") == true) "Business Chat"
                else chat.chatIdentifier ?: "Unnamed chat"
            handles.size == 1 -> handleDisplayName(handles[0], contactInfo, addressInfo)
            handles.size <= 4 -> handles.dropLast(1)
                .joinToString(", ") { handleShortName(it, contactInfo, addressInfo) } +
                " & " + handleShortName(handles.last(), contactInfo, addressInfo)
            else -> handles.take(3).joinToString(", ") { handleShortName(it, contactInfo, addressInfo) } +
                " & ${handles.size - 3} others"
        }
    }

    private fun handleDisplayName(
        handle: Handle,
        contactInfo: Map<Long, HandleDisplayInfo>,
        addressInfo: Map<String, HandleDisplayInfo> = emptyMap(),
    ): String = contactInfo[handle.id]?.name?.takeIf { it.isNotBlank() }
        ?: ContactSync.displayInfoForMatchKeys(handle.formattedAddress, addressInfo)?.name
        ?: ContactSync.displayInfoForMatchKeys(handle.address, addressInfo)?.name
        ?: handle.formattedAddress?.takeIf { it.isNotBlank() }
        ?: handle.address

    private fun handleShortName(
        handle: Handle,
        contactInfo: Map<Long, HandleDisplayInfo>,
        addressInfo: Map<String, HandleDisplayInfo> = emptyMap(),
    ): String = handleDisplayName(handle, contactInfo, addressInfo)
        .split(' ', limit = 2)
        .firstOrNull()
        ?: handleDisplayName(handle, contactInfo, addressInfo)

    internal fun handleDisplayName(handle: Handle): String =
        handleDisplayName(
            handle,
            contactSync.displayInfoByHandleId(),
            contactSync.displayInfoByMatchKey(),
        )

    internal fun handleShortName(handle: Handle): String =
        handleShortName(
            handle,
            contactSync.displayInfoByHandleId(),
            contactSync.displayInfoByMatchKey(),
        )

    private fun isHandleAddress(name: String, handle: Handle): Boolean {
        val keys = ContactSync.addressMatchKeys(name)
        if (keys.isEmpty()) return false
        return ContactSync.addressMatchKeys(handle.address).any(keys::contains) ||
            ContactSync.addressMatchKeys(handle.formattedAddress.orEmpty()).any(keys::contains)
    }

    /**
     * Port of `Message.buildGroupEventText` for rename / participant / photo
     * events (itemType 1–3).
     */
    internal fun groupEventText(message: Message): String =
        groupEventText(
            message,
            contactSync.displayInfoByHandleId(),
            contactSync.displayInfoByMatchKey(),
        )

    private fun groupEventText(
        message: Message,
        contactInfo: Map<Long, HandleDisplayInfo>,
        addressInfo: Map<String, HandleDisplayInfo>,
    ): String {
        val name = when {
            message.isFromMe -> "You"
            message.handleRelation.target != null ->
                handleShortName(message.handleRelation.target, contactInfo, addressInfo)
            else -> "Unknown"
        }
        val other = message.otherHandle?.let { rowId ->
            handleBox.query().equal(Handle_.originalROWID, rowId).build().use { it.findFirst() }
        }?.let { handleShortName(it, contactInfo, addressInfo) } ?: "someone"

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

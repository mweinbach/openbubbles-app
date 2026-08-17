package app.openbubbles.nativeapp.data

import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * In-memory backing store for the fake repositories. Single source of truth so
 * sending, paging, and mark-read all stay coherent across screens.
 */
internal object FakeChatData {

    /** Blue-ish palette for avatar circles (ARGB longs). */
    private val AvatarColors = longArrayOf(
        0xFF0A84FF,
        0xFF34C759,
        0xFFAF52DE,
        0xFFFF9F0A,
        0xFF5AC8FA,
        0xFFFF6482,
        0xFF5E5CE6,
        0xFFFF453A,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val nextMessageId = AtomicLong(1_000)

    /** Full history per chat, ascending by id (= ascending in time). */
    private val history = ConcurrentHashMap<Long, List<MessageItem>>()

    /** Currently materialized window per chat (what `messages()` exposes). */
    private val _windows = MutableStateFlow<Map<Long, List<MessageItem>>>(emptyMap())

    private val _chats = MutableStateFlow(seedChats())
    val chats: StateFlow<List<ChatListItem>> = _chats.asStateFlow()

    init {
        seedMessages()
    }

    fun chatsFlow(): Flow<List<ChatListItem>> = chats.onStart { delay(600) }

    fun messagesFlow(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> {
        _windows.update { windows ->
            if (windows.containsKey(chatId)) {
                windows
            } else {
                val base = history[chatId].orEmpty()
                    .let { list -> if (before != null) list.filter { it.id < before } else list }
                    .takeLast(limit)
                windows + (chatId to base)
            }
        }
        return _windows
            .map { windows -> windows[chatId].orEmpty() }
            .onStart { delay(450) }
            .distinctUntilChanged()
    }

    fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> {
        val current = _windows.value[chatId].orEmpty()
        val cutoff = before ?: current.firstOrNull()?.id ?: return emptyList()
        val older = history[chatId].orEmpty().filter { it.id < cutoff }.takeLast(count)
        if (older.isNotEmpty()) {
            _windows.update { windows -> windows + (chatId to older + current) }
        }
        return older
    }

    fun markRead(chatId: Long) {
        _chats.update { chats -> chats.map { if (it.id == chatId) it.copy(unread = 0) else it } }
    }

    fun setPinned(chatId: Long, pinned: Boolean) {
        _chats.update { chats -> chats.map { if (it.id == chatId) it.copy(pinned = pinned) else it } }
    }

    fun setMuted(chatId: Long, muted: Boolean) {
        _chats.update { chats -> chats.map { if (it.id == chatId) it.copy(muted = muted) else it } }
    }

    fun setArchived(chatId: Long, archived: Boolean) {
        _chats.update { chats ->
            chats.map {
                if (it.id == chatId) it.copy(archived = archived, pinned = if (archived) false else it.pinned)
                else it
            }
        }
    }

    fun delete(chatId: Long) {
        _chats.update { chats -> chats.filterNot { it.id == chatId } }
        history.remove(chatId)
        _windows.update { it - chatId }
    }

    fun send(chatId: Long, text: String): Long {
        val message = MessageItem(
            id = nextMessageId.incrementAndGet(),
            text = text,
            isFromMe = true,
            date = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            isGroupEvent = false,
            reactionEmoji = null,
        )
        append(chatId, message)
        // Keep the chat list in sync with the outgoing message.
        _chats.update { chats ->
            chats.map {
                if (it.id == chatId) it.copy(date = message.date, snippet = text) else it
            }
        }
        scope.launch {
            delay(150)
            updateMessage(chatId, message.id) { it.copy(status = MessageStatus.SENT) }
            delay(900)
            updateMessage(chatId, message.id) { it.copy(status = MessageStatus.DELIVERED) }
            delay(1_800)
            updateMessage(chatId, message.id) { it.copy(status = MessageStatus.READ) }
        }
        return message.id
    }

    fun react(chatId: Long, messageGuid: String, emoji: String?) {
        updateMessageByGuid(chatId, messageGuid) { it.copy(reactionEmoji = emoji) }
    }

    fun edit(chatId: Long, messageGuid: String, text: String) {
        updateMessageByGuid(chatId, messageGuid) { it.copy(text = text, edited = true) }
    }

    fun unsend(chatId: Long, messageGuid: String) {
        updateMessageByGuid(chatId, messageGuid) { it.copy(text = "", unsent = true) }
    }

    /** Fake attachment send: optimistic bubble that settles like a text send. */
    fun sendAttachments(chatId: Long, attachments: List<OutgoingAttachment>, caption: String?): Long {
        require(attachments.isNotEmpty()) { "attachment send requires at least one attachment" }
        val metas = attachments.mapIndexed { index, attachment ->
            AttachmentMeta(
                guid = "outgoing-${nextMessageId.incrementAndGet()}",
                mime = attachment.mime,
                name = attachment.name,
                sizeBytes = attachment.sizeBytes,
                isImage = attachment.mime.startsWith("image/", ignoreCase = true),
                downloaded = true,
                // The caption text occupies part 0, attachments follow.
                partIndex = (index + 1).toLong(),
            )
        }
        val message = MessageItem(
            id = nextMessageId.incrementAndGet(),
            text = caption.orEmpty(),
            isFromMe = true,
            date = System.currentTimeMillis(),
            status = MessageStatus.SENDING,
            isGroupEvent = false,
            reactionEmoji = null,
            attachmentMeta = metas.first(),
            attachmentMetas = metas,
        )
        append(chatId, message)
        _chats.update { chats ->
            chats.map {
                if (it.id == chatId) {
                    val names = attachments.joinToString { it.name ?: "Attachment" }
                    it.copy(date = message.date, snippet = "📎 $names")
                } else it
            }
        }
        scope.launch {
            delay(150)
            updateMessage(chatId, message.id) { it.copy(status = MessageStatus.SENT) }
            delay(900)
            updateMessage(chatId, message.id) { it.copy(status = MessageStatus.DELIVERED) }
        }
        return message.id
    }

    private fun append(chatId: Long, message: MessageItem) {
        history[chatId] = history[chatId].orEmpty() + message
        _windows.update { windows -> windows + (chatId to windows[chatId].orEmpty() + message) }
    }

    private fun updateMessage(chatId: Long, messageId: Long, transform: (MessageItem) -> MessageItem) {
        history[chatId] = history[chatId].orEmpty().map { if (it.id == messageId) transform(it) else it }
        _windows.update { windows ->
            windows + (chatId to windows[chatId].orEmpty().map { if (it.id == messageId) transform(it) else it })
        }
    }

    private fun updateMessageByGuid(chatId: Long, guid: String, transform: (MessageItem) -> MessageItem) {
        history[chatId] = history[chatId].orEmpty().map { if (it.guid == guid) transform(it) else it }
        _windows.update { windows ->
            windows + (chatId to windows[chatId].orEmpty().map { if (it.guid == guid) transform(it) else it })
        }
    }

    // ------------------------------------------------------------------ seeds

    private fun now() = System.currentTimeMillis()
    private fun minutesAgo(minutes: Long) = now() - minutes * 60_000L
    private fun hoursAgo(hours: Long) = now() - hours * 3_600_000L
    private fun daysAgo(days: Long) = now() - days * 86_400_000L

    private fun message(
        id: Long,
        date: Long,
        text: String,
        fromMe: Boolean,
        status: MessageStatus = MessageStatus.READ,
        isGroupEvent: Boolean = false,
        reaction: String? = null,
        attachmentMeta: AttachmentMeta? = null,
        edited: Boolean = false,
        unsent: Boolean = false,
        senderAddress: String? = null,
    ) = MessageItem(
        id = id, text = text, isFromMe = fromMe, date = date, status = status,
        isGroupEvent = isGroupEvent, reactionEmoji = reaction,
        attachmentMeta = attachmentMeta, edited = edited, unsent = unsent,
        senderAddress = senderAddress,
    )

    private fun seedChats(): List<ChatListItem> {
        fun chat(index: Int, id: Long, title: String, snippet: String?, date: Long, unread: Int, pinned: Boolean) =
            ChatListItem(
                id, title, snippet, date, unread, pinned, AvatarColors[index % AvatarColors.size],
                isGroup = id == 1L || id == 3L || id == 8L,
            )

        return listOf(
            chat(0, 1, "Family", "Emma: dessert's on me 🍰", minutesAgo(25), unread = 3, pinned = true),
            chat(1, 2, "Alex Chen", "sounds good — see you at the trailhead", minutesAgo(52), unread = 0, pinned = true),
            chat(2, 3, "Design Team", "Maya: pushed the new mocks to Figma", minutesAgo(18), unread = 12, pinned = false),
            chat(3, 4, "Jordan Rivera", "haha no way 😂😂", hoursAgo(5), unread = 0, pinned = false),
            chat(4, 5, "Sam Whitfield", "can you send the file when you get a sec", hoursAgo(2), unread = 1, pinned = false),
            chat(5, 6, "Priya Patel", "omw, 10 min out", hoursAgo(28), unread = 0, pinned = false),
            chat(6, 7, "Uncle Ray", "👍", daysAgo(6), unread = 0, pinned = false),
            chat(7, 8, "Roommates", "Kai: whose turn is it for trash 🗑️", daysAgo(13), unread = 2, pinned = false),
        )
    }

    private fun seedMessages() {
        history[1] = fillerHistory(firstId = 10, count = 90, newestMinutesAgo = 2_900, stepMinutes = 45) + familyHistory()
        history[2] = smallConversation(200, minutesAgo(52), 6,
            "hey! still on for the hike saturday?" to false,
            "yes! early start?" to true,
            "8am trailhead, i'll drive" to false,
            "perfect. bringing the dog?" to true,
            "obviously 🐕" to false,
            "sounds good — see you at the trailhead" to true,
        )
        history[3] = smallConversation(300, minutesAgo(18), 8,
            "standup moved to 10:30" to false,
            "got it" to true,
            "new icon set from brand is in Figma" to false,
            "oh nice, reviewing after lunch" to true,
            "i left comments on the spacing" to false,
            "thanks! will take a look" to true,
            "pushed the new mocks to Figma" to false,
            "🎉" to true,
        )
        history[4] = smallConversation(400, hoursAgo(5), 6,
            "you watch the game last night?" to false,
            "only the fourth quarter" to true,
            "that shot at the buzzer 😳" to false,
            "unreal" to true,
            "rematch at my place this weekend" to false,
            "haha no way 😂😂 i'm in" to true,
        )
        history[5] = smallConversation(500, hoursAgo(2), 5,
            "hey, quick favor" to false,
            "what's up" to true,
            "can you send the file when you get a sec" to false,
            "the quarterly one?" to true,
            "yes that one" to false,
        )
        history[6] = smallConversation(600, hoursAgo(28), 5,
            "landing at 6, can you grab me?" to false,
            "yep — terminal 2?" to true,
            "correct" to false,
            "omw, 10 min out" to true,
            "just curbside, red car" to true,
        )
        history[7] = smallConversation(700, daysAgo(6), 4,
            "thanks again for the trailer last weekend" to false,
            "anytime! how'd the move go" to true,
            "done except the garage lol" to false,
            "👍" to false,
        )
        history[8] = smallConversation(800, daysAgo(13), 6,
            "whose turn is it for trash 🗑️" to false,
            "pretty sure it's yours" to true,
            "it is not" to false,
            "check the fridge list" to true,
            "the list says KAI" to false,
            "kai your turn!!" to true,
        )
    }

    /** The flagship seeded conversation: group events, reactions, all statuses. */
    private fun familyHistory(): List<MessageItem> = listOf(
        message(100, minutesAgo(2_880), "You named the conversation “Family”", fromMe = false, isGroupEvent = true),
        message(101, minutesAgo(2_875), "Anyone free for a call tomorrow?", fromMe = false),
        message(102, minutesAgo(2_870), "I can do after 5", fromMe = true),
        message(103, minutesAgo(1_500), "Mom added Dad", fromMe = false, isGroupEvent = true),
        message(104, minutesAgo(1_495), "Hi all 👋", fromMe = false),
        message(105, minutesAgo(1_490), "hey dad", fromMe = true),
        message(106, minutesAgo(1_485), "Dinner Saturday, 7pm — the usual place.", fromMe = false, reaction = "❤️"),
        message(107, minutesAgo(1_480), "works for me", fromMe = true),
        message(108, minutesAgo(240), "I'm bringing the dog 🐶", fromMe = false),
        message(109, minutesAgo(235), "of course you are", fromMe = true, reaction = "😂"),
        message(110, minutesAgo(230), "can someone grab me at the airport Friday?", fromMe = true, status = MessageStatus.FAILED),
        message(111, minutesAgo(225), "I can — what time does it land?", fromMe = false),
        message(112, minutesAgo(40), "Dinner at 7 on Saturday — everyone in?", fromMe = false),
        message(113, minutesAgo(35), "👍", fromMe = false),
        message(114, minutesAgo(30), "count me in", fromMe = true, status = MessageStatus.DELIVERED),
        message(115, minutesAgo(28), "", fromMe = false,
            attachmentMeta = FakeAttachmentProvider.byGuid("demo-image-1"),
            senderAddress = "emma@icloud.com"),
        message(116, minutesAgo(26), "", fromMe = false,
            attachmentMeta = FakeAttachmentProvider.byGuid("demo-video-1"),
            senderAddress = "dad@icloud.com"),
        message(117, minutesAgo(24), "", fromMe = false,
            attachmentMeta = FakeAttachmentProvider.byGuid("demo-file-1"),
            senderAddress = "mom@icloud.com"),
        message(118, minutesAgo(22), "meet at 6:30 instead", fromMe = true,
            edited = true, status = MessageStatus.DELIVERED),
        message(119, minutesAgo(20), "", fromMe = true, unsent = true),
        message(120, minutesAgo(18), "", fromMe = false, unsent = true,
            senderAddress = "emma@icloud.com"),
        message(121, minutesAgo(15), "dessert's on me 🍰", fromMe = false),
    )

    /** Generic older filler so paging (`loadMore`) has data to serve. */
    private fun fillerHistory(firstId: Long, count: Int, newestMinutesAgo: Long, stepMinutes: Long): List<MessageItem> {
        val texts = listOf(
            "hey, how's it going?",
            "did you see the game last night?",
            "i'll be home late tonight",
            "thanks for the heads up!",
            "can we move it to thursday?",
            "just landed ✈️",
            "ok good to know",
            "sending it over now",
            "no worries at all",
            "that's hilarious",
            "let's talk later",
            "sounds like a plan 👍",
        )
        return (0 until count).map { i ->
            message(
                id = firstId + i,
                date = minutesAgo(newestMinutesAgo + (count - 1L - i) * stepMinutes),
                text = texts[i % texts.size],
                fromMe = i % 2 == 0,
            )
        }
    }

    /** Each line is `text to isFromMe`, spaced [stepMinutes] apart, newest first. */
    private fun smallConversation(
        firstId: Long,
        firstDate: Long,
        stepMinutes: Long,
        vararg lines: Pair<String, Boolean>,
    ): List<MessageItem> = lines.mapIndexed { i, (text, fromMe) ->
        message(firstId + i, firstDate - i * stepMinutes * 60_000L, text, fromMe)
    }
}

/** Fake [ChatListRepository] backed by [FakeChatData]. */
class FakeChatListRepository : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> = FakeChatData.chatsFlow()
    override fun markRead(id: Long) = FakeChatData.markRead(id)
    override fun setPinned(id: Long, pinned: Boolean) = FakeChatData.setPinned(id, pinned)
    override fun setMuted(id: Long, muted: Boolean) = FakeChatData.setMuted(id, muted)
    override fun setArchived(id: Long, archived: Boolean) = FakeChatData.setArchived(id, archived)
    override fun delete(id: Long) = FakeChatData.delete(id)
}

/** Fake [MessageListRepository] backed by [FakeChatData]. */
class FakeMessageListRepository : MessageListRepository {
    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> =
        FakeChatData.messagesFlow(chatId, limit, before)

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> =
        FakeChatData.loadMore(chatId, before, count)
}

/** Fake [Sender] that appends optimistically and evolves the status over time. */
object FakeSender : Sender {
    override suspend fun send(chatId: Long, text: String): OutgoingTextSend =
        OutgoingTextSend(FakeChatData.send(chatId, text))
}

object FakeMessageActions : MessageActions {
    override suspend fun react(
        chatId: Long,
        messageGuid: String,
        messageText: String,
        messagePart: Long,
        reactionIndex: Int,
        emoji: String?,
        enable: Boolean,
    ) {
        val display = emoji ?: listOf("❤️", "👍", "👎", "😂", "‼️", "❓").getOrNull(reactionIndex)
        FakeChatData.react(chatId, messageGuid, display.takeIf { enable })
    }

    override suspend fun edit(chatId: Long, messageGuid: String, newText: String) =
        FakeChatData.edit(chatId, messageGuid, newText)

    override suspend fun unsend(chatId: Long, messageGuid: String) =
        FakeChatData.unsend(chatId, messageGuid)
}

object FakeFaceTimeCaller : FaceTimeCaller {
    override suspend fun start(chatId: Long): FaceTimeLaunch =
        error("FaceTime requires an active Apple push connection")
}

/** Canned search results for previews and screenshot tests. */
object FakeSearchRepository : SearchRepository {
    override suspend fun searchMessages(query: String, limit: Int): List<MessageItem> = listOf(
        MessageItem(
            id = 9_201, text = "grabbing coffee now, want anything?",
            isFromMe = true, date = 1_759_700_000_000, status = MessageStatus.DELIVERED,
            isGroupEvent = false, reactionEmoji = null, guid = "fake-search-1", chatId = 2,
        ),
        MessageItem(
            id = 9_202, text = "coffee sounds perfect — see you at the trailhead",
            isFromMe = false, date = 1_759_690_000_000, status = MessageStatus.READ,
            isGroupEvent = false, reactionEmoji = null, guid = "fake-search-2",
            senderAddress = "alex@icloud.com", chatId = 2,
        ),
    )

    override suspend fun searchLinks(query: String, limit: Int): List<MessageItem> = listOf(
        MessageItem(
            id = 9_203, text = "https://www.nps.gov/yose/index.htm",
            isFromMe = false, date = 1_759_680_000_000, status = MessageStatus.READ,
            isGroupEvent = false, reactionEmoji = null, guid = "fake-search-3",
            senderAddress = "alex@icloud.com", chatId = 2,
            richLink = RichLinkPreview(
                url = "https://www.nps.gov/yose/index.htm",
                displayHost = "nps.gov",
                title = "Yosemite National Park",
                summary = "Plan the route, check conditions, and get ready for Saturday's hike.",
                imageBytes = null, imageMime = null, iconBytes = null, iconMime = null,
            ),
        ),
    )

    override suspend fun contacts(): List<app.openbubbles.core.contacts.RawContact> = listOf(
        app.openbubbles.core.contacts.RawContact(
            id = "fake-1", displayName = "Alex Chen", firstName = "Alex", lastName = "Chen",
            avatarPath = null, addresses = listOf("alex@icloud.com"),
        ),
        app.openbubbles.core.contacts.RawContact(
            id = "fake-2", displayName = "Mark Linsangan", firstName = "Mark", lastName = "Linsangan",
            avatarPath = null, addresses = listOf("+17033092799"),
        ),
    )
}

/** Fake [AttachmentSender] (no real upload; bubble settles like a text send). */
object FakeAttachmentSender : AttachmentSender {
    override suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
    ): OutgoingAttachmentSend =
        OutgoingAttachmentSend(FakeChatData.sendAttachments(chatId, attachments, caption))
}

/** Fake [TypingRepository]: never any typing activity. */
object FakeTypingRepository : TypingRepository {
    private val empty = kotlinx.coroutines.flow.flowOf(emptyList<TypingEntry>())
    override fun typing(): Flow<List<TypingEntry>> = empty
}

/** Fake [AttachmentProvider]: metadata only, no local files (download chip demo). */
object FakeAttachmentProvider : AttachmentProvider {
    private val known = listOf(
        AttachmentMeta(
            guid = "demo-image-1", mime = "image/jpeg", name = "trailhead.jpg",
            sizeBytes = 2_411_520L, isImage = true, downloaded = false,
        ),
        AttachmentMeta(
            guid = "demo-video-1", mime = "video/quicktime", name = "dog.mov",
            sizeBytes = 18_874_368L, isImage = false, downloaded = false,
        ),
        AttachmentMeta(
            guid = "demo-file-1", mime = "application/pdf", name = "itinerary.pdf",
            sizeBytes = 412_676L, isImage = false, downloaded = true,
        ),
    )

    override fun byGuid(guid: String): AttachmentMeta? = known.firstOrNull { it.guid == guid }
    override fun localFile(guid: String): File? = null
}

/** Fake [ChatInfoRepository]: static participants for the seeded group chat. */
object FakeChatInfoRepository : ChatInfoRepository {
    override fun participantAddresses(chatId: Long): List<String> =
        if (chatId == 1L) listOf("mom@icloud.com", "dad@icloud.com", "emma@icloud.com") else emptyList()

    override fun sharedContent(chatId: Long, limit: Int): List<SharedContentPreview> = emptyList()
}

object FakeChatInfoActions : ChatInfoActions {
    override suspend fun rename(chatId: Long, name: String) = Unit
    override suspend fun addParticipant(chatId: Long, address: String) = Unit
    override suspend fun removeParticipant(chatId: Long, address: String) = Unit
    override suspend fun setGroupIcon(chatId: Long, file: File) = Unit
    override suspend fun removeGroupIcon(chatId: Long) = Unit
    override suspend fun leave(chatId: Long) = Unit
}

/** Composition root. Real core-backed bindings; fakes only as fallback. */
object AppGraph {
    val chats: ChatListRepository get() = CoreGraph.chats
    val messages: MessageListRepository get() = CoreGraph.messages
    val sender: Sender get() = CoreGraph.sender
    val readReceipts: ReadReceiptSender get() = CoreGraph.readReceipts
    val messageActions: MessageActions get() = CoreGraph.messageActions
    val faceTimeCaller: FaceTimeCaller get() = CoreGraph.faceTimeCaller
    val attachmentSender: AttachmentSender get() = CoreGraph.attachmentSender
    val stickerSender: StickerSender get() = CoreGraph.stickerSender
    val typing: TypingRepository get() = CoreGraph.typing
    val attachments: AttachmentProvider get() = CoreGraph.attachments
    val chatInfo: ChatInfoRepository get() = CoreGraph.chatInfo
    val search: SearchRepository get() = CoreGraph.search
    val chatInfoActions: ChatInfoActions get() = CoreGraph.chatInfoActions
    val chatBackgroundActions: ChatBackgroundActions get() = CoreGraph.chatBackgroundActions

    /** Fire-and-forget attachment download (no-op on the fake path). */
    fun requestAttachmentDownload(guid: String) = CoreGraph.requestAttachmentDownload(guid)

    /** Attachment cache maintenance for the settings screen. */
    fun attachmentsCacheBytes(): Long = CoreGraph.attachmentsCacheBytes()
    fun clearAttachmentCache(): Long = CoreGraph.clearAttachmentCache()
}

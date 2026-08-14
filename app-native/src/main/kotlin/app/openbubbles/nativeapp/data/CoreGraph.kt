package app.openbubbles.nativeapp.data

import android.content.Context
import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.repo.ChatRepo
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Chat
import app.openbubbles.db.Db
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.nativeapp.NativeMainActivity
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UConversation
import uniffi.rust_lib_bluebubbles.UPushMessage
import uniffi.rust_lib_bluebubbles.readQueuedJournal
import uniffi.rust_lib_bluebubbles.markJournalAttempt
import java.util.UUID
import kotlin.math.abs

/**
 * Live composition root binding the UI contracts to :core (ObjectBox) and,
 * when the push service is up, the Rust send path. Falls back to the fake
 * repositories if the store cannot open (should not happen; empty DB is a
 * valid, boring state).
 */
object CoreGraph {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val store: BoxStore? by lazy {
        runCatching {
            Db.build(NativeMainActivity.appContext!!.getExternalFilesDir(null)
                ?: NativeMainActivity.appContext!!.filesDir)
        }.getOrNull()
    }

    private val chatRepo: ChatRepo? by lazy { store?.let(::ChatRepo) }
    private val messageRepo: MessageRepo? by lazy { store?.let { MessageRepo(it) } }
    val ingestor: MessageIngestor? by lazy { store?.let { MessageIngestor(it, scope) } }

    val chats: ChatListRepository by lazy {
        chatRepo?.let { repo -> CoreChatListRepository(repo) } ?: FakeChatListRepository()
    }
    val messages: MessageListRepository by lazy {
        messageRepo?.let { repo -> CoreMessageListRepository(repo) } ?: FakeMessageListRepository()
    }
    val sender: Sender by lazy {
        if (store != null) CoreSender else FakeSender
    }

    fun startQueueDrainer() {
        val ing = ingestor ?: return
        scope.launch {
            while (true) {
                val state = PushStateHolder.state
                val handles = PushStateHolder.myHandles
                if (state == null || handles.isEmpty()) {
                    delay(5_000)
                    continue
                }
                try {
                    val entry = runInterruptible(Dispatchers.IO) { readQueuedJournal() }
                    if (entry == null) { delay(2_000); continue }
                    ing.ingest(entry.message, handles)
                    runInterruptible(Dispatchers.IO) { markJournalAttempt(entry.id, true) }
                } catch (t: Throwable) {
                    delay(5_000)
                }
            }
        }
    }
}

/** Set by the push service once the Rust state is live. */
object PushStateHolder {
    private val _state = MutableStateFlow<NativePushState?>(null)
    val stateFlow = _state.asStateFlow()
    val state: NativePushState? get() = _state.value

    private val _myHandles = MutableStateFlow<Set<String>>(emptySet())
    val myHandlesFlow = _myHandles.asStateFlow()
    val myHandles: Set<String> get() = _myHandles.value

    fun install(state: NativePushState, handles: Set<String>) {
        _state.value = state
        _myHandles.value = handles
        CoreGraph.startQueueDrainer()
    }
}

// ---------------------------------------------------------------------------
// Adapters: core DTOs -> UI contracts
// ---------------------------------------------------------------------------

private fun avatarColorFor(seed: String): Long {
    val palette = longArrayOf(
        0xFF7C4FDF, 0xFF4C8BF5, 0xFF00897B, 0xFFD81B60, 0xFFF4511E,
        0xFF6D4C41, 0xFF3949AB, 0xFF43A047, 0xFF8D6E63, 0xFFC0CA33,
    )
    return palette[abs(seed.hashCode()) % palette.size]
}

private fun coreChatToUi(item: app.openbubbles.core.model.ChatListItem) = ChatListItem(
    id = item.id,
    title = item.title,
    snippet = item.snippet,
    date = item.date?.time ?: 0L,
    unread = item.unreadCount,
    pinned = item.pinned,
    avatarColor = avatarColorFor(item.guid),
)

private val TAPBACK_EMOJI = mapOf(
    "love" to "❤️", "like" to "👍", "dislike" to "👎", "laugh" to "😂",
    "emphasize" to "‼️", "question" to "❓",
)

private fun coreMessageToUi(item: app.openbubbles.core.model.MessageItem) = MessageItem(
    id = item.id,
    text = when (item.kind) {
        app.openbubbles.core.model.MessageKind.GROUP_EVENT -> item.groupEventText ?: item.text
        else -> item.text
    },
    isFromMe = item.isFromMe,
    date = item.date?.time ?: 0L,
    status = MessageStatus.valueOf(item.status.name),
    isGroupEvent = item.kind == app.openbubbles.core.model.MessageKind.GROUP_EVENT,
    reactionEmoji = item.reactionEmoji
        ?: item.reactionType?.removePrefix("-")?.let { TAPBACK_EMOJI[it] },
)

private class CoreChatListRepository(
    private val repo: ChatRepo,
) : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> =
        repo.observeChats().flowOn(Dispatchers.IO).map { list -> list.map(::coreChatToUi) }

    override fun markRead(id: Long) = repo.markRead(id)
}

private class CoreMessageListRepository(
    private val repo: MessageRepo,
) : MessageListRepository {
    // Growable newest-first window so loadMore widens the reactive page,
    // matching the Room-style contract the UI was built against.
    private val window = MutableStateFlow(50)

    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> =
        window.flatMapLatest { size ->
            repo.observeMessages(chatId, size.coerceAtLeast(limit))
                .flowOn(Dispatchers.IO)
                .map { page -> page.map(::coreMessageToUi) }
        }

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> {
        window.value = window.value + count
        return repo.messages(chatId, limit = window.value).map(::coreMessageToUi)
    }
}

/**
 * Dart send-path semantics: stage optimistically under a temp guid, swap to
 * the Rust staging guid when the send is accepted, then ingest the echo so
 * receipts flow through the normal intake path.
 */
private object CoreSender : Sender {
    override suspend fun send(chatId: Long, text: String) {
        val graph = CoreGraph
        val store = graph.store ?: error("store unavailable")
        val repo = graph.messages as? CoreMessageListRepository
            ?: error("core message repo unavailable")
        val ing = graph.ingestor ?: error("ingestor unavailable")

        val chatBox = store.boxFor(Chat::class.java)
        val messageBox = store.boxFor(Message::class.java)
        val chat = chatBox.get(chatId) ?: error("no chat $chatId")

        val myHandle = PushStateHolder.myHandles.firstOrNull()
            ?: chat.usingHandle
            ?: "unknown-sender"

        val tempGuid = MessageIngestor.tempGuid()
        graphMessageStage(store, chat.guid, myHandle, text, tempGuid)

        val pushState = PushStateHolder.state
        if (pushState == null) {
            // No live push state (not logged in): leave the bubble SENDING;
            // the queue/SendConfirm path will resolve it once connected.
            return
        }

        try {
            val inst = runInterruptible(Dispatchers.IO) {
                pushState.sendText(
                    UConversation(
                        participants = chat.handles.map { it.address }.distinct(),
                        cvName = chat.displayName,
                        senderGuid = null,
                        afterGuid = null,
                    ),
                    myHandle,
                    text,
                    null, null, null, null,
                )
            }
            // Promote the staged row to the Rust staging guid so the echo and
            // SendConfirm receipts find it (same swap Dart performs).
            store.runInTx {
                val staged = messageBox.query()
                    .equal(Message_.guid, tempGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
                staged?.apply {
                    guid = inst.id
                    stagingGuid = inst.id
                    messageBox.put(this)
                }
            }
            ing.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
        } catch (t: Throwable) {
            store.runInTx {
                val staged = messageBox.query()
                    .equal(Message_.guid, tempGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
                staged?.apply {
                    sendingServiceId = null
                    error = 1
                    errorMessage = t.message?.take(200)
                    messageBox.put(this)
                }
            }
        }
    }

    private suspend fun graphMessageStage(
        store: BoxStore,
        chatGuid: String,
        sender: String,
        text: String,
        tempGuid: String,
    ): app.openbubbles.db.Message {
        // Uses the core repo's staging helper bound to the same store.
        val mRepo = CoreGraphStageHolder.messageRepo(store)
        return mRepo.stageOutgoingMessage(chatGuid, sender, text, tempGuid)
    }
}

private object CoreGraphStageHolder {
    private val repos = java.util.concurrent.ConcurrentHashMap<BoxStore, MessageRepo>()
    fun messageRepo(store: BoxStore): MessageRepo =
        repos.computeIfAbsent(store) { MessageRepo(it) }
}

/** Unused today; reserved for the login flow (M1.e) to name new sessions. */
@Suppress("unused")
private fun newStagingGuid(): String = UUID.randomUUID().toString().uppercase()

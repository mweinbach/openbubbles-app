package app.openbubbles.nativeapp.data

import android.content.Context
import app.openbubbles.core.attachment.AttachmentDownloader
import app.openbubbles.core.attachment.AttachmentManager
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.repo.ChatRepo
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.Db
import app.openbubbles.db.Handle
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
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
import java.io.File
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
            // Match the Flutter app's store location (path_provider's
            // getApplicationDocumentsDirectory = /data/data/<pkg>/app_flutter)
            // so the in-place upgrade at cutover opens the existing store.
            // See tools/CUTOVER.md before changing this.
            val ctx = NativeMainActivity.appContext ?: return@lazy null
            Db.build(File(ctx.dataDir, "app_flutter"))
        }.getOrNull()
    }

    private val chatRepo: ChatRepo? by lazy { store?.let(::ChatRepo) }
    private val messageRepo: MessageRepo? by lazy { store?.let { MessageRepo(it) } }
    val ingestor: MessageIngestor? by lazy { store?.let { MessageIngestor(it, scope) } }

    /**
     * Attachment payload resolver. Downloads go through the live push state:
     * the attachment row's metadata["rustpush"] XML restores to a UAttachment
     * which Rust transfers to the manager-chosen destination path.
     */
    val attachmentManager: AttachmentManager? by lazy {
        val st = store ?: return@lazy null
        val root = File(
            NativeMainActivity.appContext?.dataDir ?: return@lazy null,
            "app_flutter",
        )
        runCatching {
            AttachmentManager(
                store = st,
                rootDir = root,
                downloader = AttachmentDownloader { attachmentGuid, destPath, onProgress ->
                    val pushState = PushStateHolder.state
                        ?: return@AttachmentDownloader Result.failure(IllegalStateException("not connected"))
                    val attachmentBox = st.boxFor(app.openbubbles.db.Attachment::class.java)
                    val xml = attachmentBox.query()
                        .equal(
                            app.openbubbles.db.Attachment_.guid,
                            attachmentGuid,
                            io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE,
                        )
                        .build().use { it.findFirst() }
                        ?.metadata?.get("rustpush") as? String
                        ?: return@AttachmentDownloader Result.failure(
                            IllegalStateException("no rustpush metadata for $attachmentGuid"))
                    runCatching {
                        val uatt = uniffi.rust_lib_bluebubbles.restoreAttachment(xml)
                        pushState.downloadAttachment(
                            uatt,
                            destPath,
                            object : uniffi.rust_lib_bluebubbles.UProgressCallback {
                                override fun onProgress(done: kotlin.ULong, total: kotlin.ULong) {
                                    onProgress(done.toLong(), total.toLong())
                                }
                            },
                        )
                        Result.success(Unit)
                    }
                },
            )
        }.getOrNull()
    }

    /** UI-facing file seam; replaceable for tests / future managers. */
    @Volatile
    var attachmentFiles: AttachmentFileManager? = null

    init {
        // Contact-name hook for the UI (unsent rows, participants, chat titles).
        UiContacts.contactNames = { address -> CoreContacts.displayInfo(address) }
        attachmentFiles = attachmentManager?.let { manager ->
            AttachmentFileManager { attachment -> manager.localFile(attachment) }
        }
    }

    val chats: ChatListRepository by lazy {
        chatRepo?.let { repo -> CoreChatListRepository(repo, store) } ?: FakeChatListRepository()
    }
    val messages: MessageListRepository by lazy {
        messageRepo?.let { repo -> CoreMessageListRepository(repo, store) } ?: FakeMessageListRepository()
    }
    val sender: Sender by lazy {
        if (store != null) CoreSender else FakeSender
    }
    val attachments: AttachmentProvider by lazy {
        store?.let { st -> CoreAttachmentProvider(st, { attachmentFiles }) } ?: FakeAttachmentProvider
    }
    /** Leave a group chat via the Rust group ops. */
    fun leaveChat(chatId: Long): Result<Unit> = CoreGroupOps.leaveChat(chatId)

    /** Upsert device contacts + invalidate the handle→contact index. */
    fun syncContacts(raw: List<app.openbubbles.core.contacts.RawContact>) =
        CoreContacts.syncFromDevice(raw)

    /**
     * Sign out: deregister from iMessage (best effort), tear down the Rust
     * state, stop the push service, and clear the holders — the sign-in
     * banner reappears on the chat list.
     */
    fun signOut(context: android.content.Context) {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { PushStateHolder.state?.teardown(true) }
        }
        PushStateHolder.clear()
        runCatching {
            context.stopService(
                android.content.Intent(context, app.openbubbles.nativeapp.service.NativePushService::class.java))
        }
    }

    /** Attachment send path (staging + Rust upload + echo ingest). */
    val attachmentSender: AttachmentSender by lazy {
        if (store != null) CoreAttachmentSender else FakeAttachmentSender
    }

    /** Live typing indicators translated from the ingestor's chat guids. */
    val typing: TypingRepository by lazy {
        val ing = ingestor
        val st = store
        if (ing != null && st != null) CoreTypingRepository(ing, st) else FakeTypingRepository
    }

    /** Bytes used by the attachments cache directory (0 when unavailable). */
    fun attachmentsCacheBytes(): Long {
        val disk = attachmentStore() ?: return 0L
        return dirSize(disk.attachmentsDir)
    }

    /**
     * Deletes every cached attachment payload and resets `isDownloaded` so
     * bubbles re-offer the download chip. Returns the bytes freed.
     */
    fun clearAttachmentCache(): Long {
        val disk = attachmentStore() ?: return 0L
        val st = store ?: return 0L
        val dir = disk.attachmentsDir
        val freed = dirSize(dir)
        dir.deleteRecursively()
        runCatching {
            val box = st.boxFor(Attachment::class.java)
            box.query()
                .equal(Attachment_.isDownloaded, true)
                .build().use { it.find() }
                .forEach { row ->
                    row.isDownloaded = false
                    box.put(row)
                }
        }
        return freed
    }

    private fun attachmentStore(): AttachmentStore? {
        val st = store ?: return null
        val root = NativeMainActivity.appContext?.getExternalFilesDir(null)
            ?: NativeMainActivity.appContext?.filesDir
            ?: return null
        return AttachmentStore(st, root)
    }

    private fun dirSize(dir: File): Long =
        if (!dir.isDirectory) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    val chatInfo: ChatInfoRepository by lazy {
        store?.let { st -> CoreChatInfoRepository(st) } ?: FakeChatInfoRepository
    }

    /**
     * Fire-and-forget download for the bubble's download chip. Progress
     * surfacing lands with the rust transfer binding; completion persists
     * `isDownloaded`, which re-emits the message flow and flips the bubble.
     */
    fun requestAttachmentDownload(guid: String) {
        val manager = attachmentManager ?: return
        val st = store ?: return
        scope.launch(Dispatchers.IO) {
            val attachment = runCatching {
                st.boxFor(Attachment::class.java)
                    .query()
                    .equal(Attachment_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
            }.getOrNull() ?: return@launch
            runCatching { manager.download(attachment).collect { /* terminal is enough here */ } }
        }
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

    fun clear() {
        _state.value = null
        _myHandles.value = emptySet()
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
    senderAddress = item.senderAddress,
)

/** True when the mime/uti pair clearly describes an image. */
internal fun isImageAttachment(mime: String?, uti: String?): Boolean {
    if (mime != null && mime.startsWith("image/", ignoreCase = true)) return true
    if (uti == null) return false
    return uti.equals("public.image", ignoreCase = true) ||
        uti.startsWith("public.image.", ignoreCase = true) ||
        uti.endsWith(".heic", ignoreCase = true) ||
        uti.endsWith(".heif", ignoreCase = true)
}

internal fun attachmentToMeta(attachment: Attachment) = AttachmentMeta(
    guid = attachment.guid,
    mime = attachment.mimeType,
    name = attachment.transferName,
    sizeBytes = attachment.totalBytes,
    isImage = isImageAttachment(attachment.mimeType, attachment.uti),
    downloaded = attachment.isDownloaded,
)

/** Non-empty retracted-part array inside a dbMessageSummaryInfo JSON blob. */
private val RETRACTED_PARTS = Regex(
    "\"(?:retractedParts|rp)\"\\s*:\\s*\\[\\s*\\d",
)

/**
 * (edited, unsent) for a db message. Unsent requires an edit summary with
 * retracted parts and no remaining text (fully retracted message — same
 * visible result as the Flutter `isFullyUnsent` heuristic).
 */
private fun editedFlags(entity: Message): Pair<Boolean, Boolean> {
    if (entity.dateEdited == null) return false to false
    val summary = entity.dbMessageSummaryInfo ?: return true to false
    val hasRetracted = RETRACTED_PARTS.containsMatchIn(summary)
    if (!hasRetracted) return true to false
    return if (entity.text.isNullOrBlank()) false to true else true to false
}

/**
 * Fills the M2 display fields (attachment metadata, edited/unsent flags) the
 * core MessageItem does not carry yet, using one batched entity read.
 */
private fun enrichWithEntityDetails(
    items: List<MessageItem>,
    store: BoxStore?,
): List<MessageItem> {
    if (store == null || items.isEmpty()) return items
    return runCatching {
        val entities = store.boxFor(Message::class.java).get(items.map { it.id })
        val byId = HashMap<Long, Message>(entities.size)
        entities.forEach { byId[it.id] = it }
        items.map { item ->
            val entity = byId[item.id] ?: return@map item
            val (edited, unsent) = editedFlags(entity)
            val attachment = runCatching { entity.dbAttachments.firstOrNull() }.getOrNull()
            item.copy(
                attachmentMeta = attachment?.let(::attachmentToMeta),
                edited = edited,
                unsent = unsent,
                uploadProgress = attachment?.guid?.let { UploadProgressBoard.current[it] },
                expressiveSendStyleId = entity.expressiveSendStyleId,
            )
        }
    }.getOrDefault(items)
}

/**
 * Bridge from raw handle addresses (what the UI contracts carry) to core's
 * [ContactSync.displayInfoFor], with an in-memory handle index (rebuilt at
 * most every 30s after a miss so newly synced handles resolve too).
 */
private object CoreContacts {
    private val sync: ContactSync? by lazy { CoreGraph.store?.let(::ContactSync) }

    /** Upsert device contacts (called after READ_CONTACTS is granted). */
    fun syncFromDevice(raw: List<app.openbubbles.core.contacts.RawContact>) {
        runCatching { sync?.upsertContacts(raw) }
        handleIndex = null // force rebuild so fresh linkages resolve
    }

    @Volatile
    private var handleIndex: Map<String, Handle>? = null

    @Volatile
    private var indexBuiltAt: Long = 0L

    @Synchronized
    private fun rebuildIndex(store: BoxStore): Map<String, Handle> {
        val index = runCatching {
            val map = HashMap<String, Handle>()
            store.boxFor(Handle::class.java).all.forEach { handle ->
                listOfNotNull(handle.address, handle.formattedAddress).forEach { address ->
                    if (address.contains('@')) {
                        map.putIfAbsent(ContactSync.normalizeEmail(address), handle)
                    } else {
                        ContactSync.phoneNumberVariants(address).forEach { map.putIfAbsent(it, handle) }
                    }
                }
            }
            map
        }.getOrDefault(emptyMap())
        handleIndex = index
        indexBuiltAt = System.currentTimeMillis()
        return index
    }

    private fun handleFor(address: String): Handle? {
        val store = CoreGraph.store ?: return null
        val key = if (address.contains('@')) {
            ContactSync.normalizeEmail(address)
        } else {
            ContactSync.normalizePhoneNumber(address)
        }
        handleIndex?.get(key)?.let { return it }
        // Miss: maybe a fresh handle. Rebuild at most every 30 seconds.
        if (handleIndex == null || System.currentTimeMillis() - indexBuiltAt > 30_000L) {
            return rebuildIndex(store)[key]
        }
        return null
    }

    /** (name, avatarPath) for a handle address, or null when unknown. */
    fun displayInfo(address: String): Pair<String?, String?>? {
        val contactSync = sync ?: return null
        val handle = handleFor(address) ?: return null
        return runCatching {
            val info = contactSync.displayInfoFor(handle)
            info.name to info.avatar
        }.getOrNull()
    }

    /** Chat-list title for DMs whose single participant has a contact. */
    fun chatTitle(item: ChatListItem, store: BoxStore): ChatListItem {
        val contactSync = sync ?: return item
        val chat = runCatching { store.boxFor(Chat::class.java).get(item.id) }.getOrNull()
            ?: return item
        if (chat.displayName != null || chat.handles.size != 1) return item
        val handle = chat.handles.firstOrNull() ?: return item
        // DMs keep the participant address so the UI can resolve a photo avatar.
        val withAvatar = item.copy(avatarAddress = handle.formattedAddress ?: handle.address)
        if (runCatching { handle.contactsV2.isEmpty() }.getOrDefault(true)) return withAvatar
        val name = runCatching { contactSync.displayInfoFor(handle).name }.getOrNull()
            ?: return withAvatar
        if (name.isBlank() || name == withAvatar.title) return withAvatar
        return withAvatar.copy(title = name)
    }
}

private class CoreChatListRepository(
    private val repo: ChatRepo,
    private val store: BoxStore?,
) : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> =
        repo.observeChats()
            .map { list ->
                list.map(::coreChatToUi).map { item ->
                    if (store == null) item else CoreContacts.chatTitle(item, store)
                }
            }
            .flowOn(Dispatchers.IO)

    override fun markRead(id: Long) = repo.markRead(id)
}

private class CoreMessageListRepository(
    private val repo: MessageRepo,
    private val store: BoxStore?,
) : MessageListRepository {
    // Growable newest-first window so loadMore widens the reactive page,
    // matching the Room-style contract the UI was built against.
    private val window = MutableStateFlow(50)

    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> =
        window.flatMapLatest { size ->
            // Combined with the upload board so progress ticks re-emit the page.
            combine(
                repo.observeMessages(chatId, size.coerceAtLeast(limit)),
                UploadProgressBoard.progress,
            ) { page, _ ->
                enrichWithEntityDetails(page.map(::coreMessageToUi), store)
            }.flowOn(Dispatchers.IO)
        }

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> {
        window.value = window.value + count
        return enrichWithEntityDetails(repo.messages(chatId, limit = window.value).map(::coreMessageToUi), store)
    }
}

/** ObjectBox-backed attachment lookups for the viewer route. */
private class CoreAttachmentProvider(
    private val store: BoxStore,
    private val fileManager: () -> AttachmentFileManager?,
) : AttachmentProvider {

    private fun attachmentByGuid(guid: String): Attachment? = runCatching {
        store.boxFor(Attachment::class.java)
            .query()
            .equal(Attachment_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
    }.getOrNull()

    override fun byGuid(guid: String): AttachmentMeta? =
        attachmentByGuid(guid)?.let(::attachmentToMeta)

    override fun localFile(guid: String): File? {
        val attachment = attachmentByGuid(guid) ?: return null
        return runCatching { fileManager()?.localFile(attachment) }.getOrNull()
    }
}

/** Participant addresses for the group-info screen. */
private class CoreChatInfoRepository(
    private val store: BoxStore,
) : ChatInfoRepository {
    override fun participantAddresses(chatId: Long): List<String> = runCatching {
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: return emptyList()
        chat.handles.map { it.formattedAddress ?: it.address }
    }.getOrDefault(emptyList())
}

/**
 * Dart send-path semantics: stage optimistically under a temp guid, swap to
 * the Rust staging guid when the send is accepted, then ingest the echo so
 * receipts flow through the normal intake path.
 */
private object CoreSender : Sender {
    override suspend fun send(chatId: Long, text: String) = sendWithEffect(chatId, text, null)

    override suspend fun sendWithEffect(chatId: Long, text: String, effectId: String?) {
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
        graphMessageStage(store, chat.guid, myHandle, text, tempGuid).let { staged ->
            if (effectId != null) {
                // Persist the effect on the staged row so the bubble (and the
                // screen-effect trigger) sees it before the echo lands.
                staged.expressiveSendStyleId = effectId
                messageBox.put(staged)
            }
        }

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
                    // replyGuid, replyPart, effect, subject
                    null, null, effectId, null,
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
                    if (effectId != null) expressiveSendStyleId = effectId
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

// ---------------------------------------------------------------------------
// Group operations (leave chat) — wired to the Rust group ops from M2.a
// ---------------------------------------------------------------------------

internal object CoreGroupOps {
    fun leaveChat(chatId: Long): Result<Unit> {
        val st = CoreGraph.store ?: return Result.failure(IllegalStateException("store unavailable"))
        val pushState = PushStateHolder.state
            ?: return Result.failure(IllegalStateException("not connected"))
        val myHandle = PushStateHolder.myHandles.firstOrNull()
            ?: return Result.failure(IllegalStateException("no handles"))
        return runCatching {
            val chat = st.boxFor(Chat::class.java).get(chatId)
                ?: error("no chat $chatId")
            val conversation = uniffi.rust_lib_bluebubbles.UConversation(
                participants = chat.handles.map { it.address }.distinct(),
                cvName = chat.displayName,
                senderGuid = null,
                afterGuid = null,
            )
            pushState.leaveChat(
                conversation,
                myHandle,
                ((chat.groupVersion ?: -1L) + 1L).toULong(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Attachment sending (M2 polish) — additive zone
// ---------------------------------------------------------------------------

/**
 * Ephemeral upload progress keyed by staged attachment guid
 * (`"<staging-guid>_att0"`). The message flow combines with [progress] so
 * ticks re-emit the page and bubbles can render an "Uploading…" row.
 */
internal object UploadProgressBoard {
    private val _progress = MutableStateFlow<Map<String, Pair<Long, Long>>>(emptyMap())
    val progress: StateFlow<Map<String, Pair<Long, Long>>> = _progress.asStateFlow()
    val current: Map<String, Pair<Long, Long>> get() = _progress.value

    fun update(guid: String, value: Pair<Long, Long>) {
        _progress.value = _progress.value + (guid to value)
    }

    fun clear(guid: String) {
        _progress.value = _progress.value - guid
    }
}

/**
 * Attachment send path mirroring [CoreSender]'s staging/promotion/echo
 * semantics: stage optimistically under a temp guid with a placeholder
 * attachment row (payload moved into the canonical store layout so the
 * bubble previews immediately), upload through the Rust sendAttachment
 * binding with progress surfaced via [UploadProgressBoard], promote the row
 * to the Rust staging guid, then ingest the echo.
 */
internal object CoreAttachmentSender : AttachmentSender {
    override suspend fun send(chatId: Long, attachment: OutgoingAttachment, caption: String?) {
        val graph = CoreGraph
        val store = graph.store ?: error("store unavailable")
        val ing = graph.ingestor ?: error("ingestor unavailable")

        val chatBox = store.boxFor(Chat::class.java)
        val messageBox = store.boxFor(Message::class.java)
        val attachmentBox = store.boxFor(Attachment::class.java)
        val chat = chatBox.get(chatId) ?: error("no chat $chatId")

        val myHandle = PushStateHolder.myHandles.firstOrNull()
            ?: chat.usingHandle
            ?: "unknown-sender"

        val tempGuid = MessageIngestor.tempGuid()
        val attachmentGuid = "${tempGuid}_att0"

        // 1. Stage the outgoing row (caption rides as the text part).
        val staged = CoreGraphStageHolder.messageRepo(store)
            .stageOutgoingMessage(chat.guid, myHandle, caption.orEmpty(), tempGuid)

        // 2. Placeholder attachment metadata + payload in the canonical
        //    layout so the bubble renders (and image-previews) right away.
        val root = NativeMainActivity.appContext?.getExternalFilesDir(null)
            ?: NativeMainActivity.appContext?.filesDir
            ?: error("no files dir")
        val disk = AttachmentStore(store, root)
        val displayName = attachment.name ?: "attachment"
        val payload = File(disk.directoryFor(attachmentGuid), disk.sanitizeFileName(displayName))
        payload.parentFile?.mkdirs()
        attachment.file.copyTo(payload, overwrite = true)
        runCatching { attachment.file.delete() } // cache copy no longer needed

        store.runInTx {
            attachmentBox.put(
                Attachment().apply {
                    guid = attachmentGuid
                    isOutgoing = true
                    mimeType = attachment.mime
                    uti = attachment.uti
                    transferName = displayName
                    totalBytes = payload.length()
                    isDownloaded = false
                    message.target = staged
                },
            )
            staged.hasAttachments = true
            messageBox.put(staged)
        }
        UploadProgressBoard.update(attachmentGuid, 0L to payload.length())

        val pushState = PushStateHolder.state
        if (pushState == null) {
            // Not connected: leave the bubble SENDING (same as CoreSender);
            // the queue/SendConfirm path resolves it once connected.
            return
        }

        try {
            val inst = runInterruptible(Dispatchers.IO) {
                pushState.sendAttachment(
                    UConversation(
                        participants = chat.handles.map { it.address }.distinct(),
                        cvName = chat.displayName,
                        senderGuid = null,
                        afterGuid = null,
                    ),
                    myHandle,
                    payload.absolutePath,
                    caption?.takeIf { it.isNotBlank() },
                    attachment.mime,
                    attachment.uti,
                    displayName,
                    null, null, null, null, false,
                    object : uniffi.rust_lib_bluebubbles.UProgressCallback {
                        override fun onProgress(done: ULong, total: ULong) {
                            UploadProgressBoard.update(
                                attachmentGuid,
                                done.toLong() to total.toLong(),
                            )
                        }
                    },
                )
            }
            // Promote to the Rust staging guid so the echo and SendConfirm
            // receipts find the row (same swap Dart performs).
            store.runInTx {
                messageBox.query()
                    .equal(Message_.guid, tempGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
                    ?.apply {
                        guid = inst.id
                        stagingGuid = inst.id
                        messageBox.put(this)
                    }
            }
            ing.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
        } catch (t: Throwable) {
            store.runInTx {
                messageBox.query()
                    .equal(Message_.guid, tempGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
                    ?.apply {
                        sendingServiceId = null
                        error = 1
                        errorMessage = t.message?.take(200)
                        messageBox.put(this)
                    }
            }
        } finally {
            UploadProgressBoard.clear(attachmentGuid)
        }
    }
}

/** Translates the ingestor's typing list (chat guids) into chat ids. */
private class CoreTypingRepository(
    private val ingestor: MessageIngestor,
    private val store: BoxStore,
) : TypingRepository {
    override fun typing(): Flow<List<TypingEntry>> = ingestor.typing.map { list ->
        list.mapNotNull { indicator ->
            val chat = runCatching {
                store.boxFor(Chat::class.java)
                    .query()
                    .equal(Chat_.guid, indicator.chatGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
            }.getOrNull() ?: return@mapNotNull null
            TypingEntry(chatId = chat.id, senderAddress = indicator.senderAddress)
        }.distinctBy { it.chatId to it.senderAddress }
    }.flowOn(Dispatchers.IO)
}

package app.openbubbles.nativeapp.data

import android.content.Context
import app.openbubbles.core.attachment.AttachmentDownloader
import app.openbubbles.core.attachment.AttachmentManager
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.backup.BackupManager
import app.openbubbles.core.backup.StoreGate
import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.model.MessageMapper
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
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
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
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UConversation
import uniffi.rust_lib_bluebubbles.UIndexedPart
import uniffi.rust_lib_bluebubbles.UPart
import uniffi.rust_lib_bluebubbles.UPushMessage
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
            val ctx = AppContext.current ?: return@lazy null
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
            AppContext.current?.dataDir ?: return@lazy null,
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
                    val attachment = attachmentBox.query()
                        .equal(
                            app.openbubbles.db.Attachment_.guid,
                            attachmentGuid,
                            io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE,
                        )
                        .build().use { it.findFirst() }
                        ?: return@AttachmentDownloader Result.failure(
                            IllegalStateException("unknown attachment $attachmentGuid"))
                    val cloudRecordId = attachment.metadata?.get("cloud") as? String
                    if (cloudRecordId != null) {
                        return@AttachmentDownloader runCatching {
                            pushState.downloadCloudAttachment(cloudRecordId, destPath)
                            Unit
                        }
                    }
                    val xml = attachment.metadata?.get("rustpush") as? String
                        ?: return@AttachmentDownloader Result.failure(
                            IllegalStateException("no download metadata for $attachmentGuid"))
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
                        Unit
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
        chatRepo?.let(::CoreChatListRepository) ?: FakeChatListRepository()
    }
    val messages: MessageListRepository by lazy {
        messageRepo?.let { repo -> CoreMessageListRepository(repo, store) } ?: FakeMessageListRepository()
    }
    val sender: Sender by lazy {
        if (store != null) CoreSender else FakeSender
    }
    val readReceipts: ReadReceiptSender by lazy {
        if (store != null) CoreReadReceiptSender else ReadReceiptSender { chatId, _ ->
            chats.markRead(chatId)
        }
    }
    val messageActions: MessageActions by lazy {
        if (store != null) CoreMessageActions else FakeMessageActions
    }
    val attachments: AttachmentProvider by lazy {
        store?.let { st -> CoreAttachmentProvider(st, { attachmentFiles }) } ?: FakeAttachmentProvider
    }
    /**
     * Read-only chat id lookup by guid (notification deep links resolve the
     * tapped chat before navigating). Null when unknown or store unavailable.
     */
    fun chatIdForGuid(guid: String): Long? = runCatching {
        store?.boxFor(Chat::class.java)
            ?.query()
            ?.equal(Chat_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            ?.build()
            ?.use { it.findFirst()?.id }
    }.getOrNull()

    /**
     * Get-or-create a chat for the new-conversation UI. Returns the chat id
     * (existing row reused when the participant set matches).
     */
    fun findOrCreateChat(addresses: List<String>, sms: Boolean): Long? = runCatching {
        val repo = chatRepo ?: return@runCatching null
        repo.findOrCreateByAddresses(addresses, if (sms) "SMS" else "iMessage").id
    }.getOrNull()

    /**
     * Battery-saver poll: one incremental CloudKit sync, notifying chats
     * that gained unread messages. The service then tears itself down; the
     * persistent APNs loop never starts.
     */
    fun pollOnce(
        context: android.content.Context,
        state: NativePushState,
        onNewUnread: (chatId: Long, title: String, body: String) -> Unit,
    ) {
        val st = store ?: return
        val chatBox = st.boxFor(Chat::class.java)
        val allowNotifications = CloudSyncWiring.hasCompletedHistorySync(context)
        val before: Map<Long, Boolean> = chatBox.query()
            .equal(Chat_.hasUnreadMessage, true)
            .build().use { q -> q.find().associate { it.id to true } }

        CloudSyncWiring.onStateInstalled(context, state, autoSync = false)
        try {
            val manager = CloudSyncWiring.manager
            val summary = kotlinx.coroutines.runBlocking {
                manager?.sync(app.openbubbles.core.sync.SyncMode.INCREMENTAL)
            }
            if (summary?.error == null && summary?.cancelled == false) {
                relinkContacts()
                CloudSyncWiring.markHistorySyncComplete(context)
            }

            if (allowNotifications) {
                chatBox.query()
                    .equal(Chat_.hasUnreadMessage, true)
                    .build().use { q ->
                        q.find().forEach { chat ->
                            if (!before.containsKey(chat.id)) {
                                val latest = chat.dbLatestMessage.target
                                onNewUnread(
                                    chat.id,
                                    chat.displayName ?: chat.guid,
                                    latest?.text?.takeIf { it.isNotBlank() } ?: "New message",
                                )
                            }
                        }
                    }
            }
        } finally {
            CloudSyncWiring.clear()
        }
    }

    /** Upsert device contacts + invalidate the handle→contact index. */
    fun syncContacts(raw: List<app.openbubbles.core.contacts.RawContact>) =
        CoreContacts.syncFromDevice(raw)

    /** Apply CardDAV contact tombstones + invalidate cached name lookups. */
    fun removeContacts(nativeContactIds: Collection<String>): Int =
        CoreContacts.remove(nativeContactIds)

    /** Re-match stored contacts after CloudKit creates additional handles. */
    fun relinkContacts(): app.openbubbles.core.contacts.ContactRelinkResult? =
        CoreContacts.relink()

    /**
     * (display name, avatar path) for a handle address, or null when unknown.
     *
     * The blocking twin of [UiContacts.contactNames], which is a suspend type
     * and therefore unusable from non-composable, non-suspend callers such as
     * the notification builder. Resolves against the same handle→contact index,
     * so device and iCloud/CardDAV contacts both land here. Touches the store,
     * so call it off the main thread.
     */
    fun contactDisplayInfo(address: String): Pair<String?, String?>? =
        CoreContacts.displayInfo(address)

    /**
     * Sign out: deregister from iMessage (best effort), tear down the Rust
     * state, stop the push service, and clear the holders — the sign-in
     * banner reappears on the chat list.
     */
    fun signOut(context: android.content.Context) {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { PushStateHolder.state?.teardown(true) }
        }
        PushStateHolder.clear(resetError = true)
        runCatching {
            context.stopService(
                android.content.Intent(context, app.openbubbles.nativeapp.service.NativePushService::class.java))
        }
    }

    /** Attachment send path (staging + Rust upload + echo ingest). */
    val attachmentSender: AttachmentSender by lazy {
        if (store != null) CoreAttachmentSender else FakeAttachmentSender
    }

    val stickerSender: StickerSender by lazy {
        if (store != null) CoreStickerSender else StickerSender { _, _, _, _, _, _ -> Unit }
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
        val root = File(
            AppContext.current?.dataDir ?: return null,
            "app_flutter",
        )
        return AttachmentStore(st, root)
    }

    private fun dirSize(dir: File): Long =
        if (!dir.isDirectory) 0L else dir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    val chatInfo: ChatInfoRepository by lazy {
        store?.let { st -> CoreChatInfoRepository(st) } ?: FakeChatInfoRepository
    }
    val chatInfoActions: ChatInfoActions by lazy {
        if (store != null) CoreChatInfoActions else FakeChatInfoActions
    }

    val chatBackgroundActions: ChatBackgroundActions by lazy {
        if (store != null) CoreChatBackgroundActions else object : ChatBackgroundActions {
            override suspend fun setLocalBackground(chatId: Long, file: File) = Unit
            override suspend fun clearLocalBackground(chatId: Long) = Unit
        }
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

    // ---------------------------------------------------------------------------
    // Backup / restore — additive zone
    // ---------------------------------------------------------------------------

    /**
     * Backup composition root over the Flutter-era data root
     * (`<dataDir>/app_flutter`: `objectbox/` + `attachments/`); null when the
     * store or app context is unavailable.
     */
    val backupManager: BackupManager? by lazy {
        val st = store ?: return@lazy null
        val ctx = AppContext.current ?: return@lazy null
        if (st.isClosed) return@lazy null
        val version = runCatching {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName
        }.getOrNull()
        BackupManager(
            rootDir = File(ctx.dataDir, "app_flutter"),
            store = { store },
            storeGate = BackupStoreGate,
            appVersion = version,
        )
    }

    /**
     * One-shot export of the database + attachments into [stream] (a SAF
     * output stream on Android). Does not close [stream]. Run on IO.
     */
    fun backupTo(
        stream: java.io.OutputStream,
        progress: (String) -> Unit = {},
    ): Result<BackupManager.BackupInfo> =
        backupManager?.snapshot(stream, progress)
            ?: Result.failure(IllegalStateException("backup unavailable — store not open"))

    /**
     * One-shot restore from [stream] (a SAF-picked zip). Run on IO. REPLACES
     * the current database + attachments on success.
     *
     * RESTART REQUIRED: this process keeps the old (open) store handles, and
     * CoreGraph's lazy singletons cannot be rebuilt in place — callers must
     * restart the process after a successful restore (the settings screen
     * does `Runtime.getRuntime().exit(0)` after surfacing the result).
     */
    fun restoreFrom(stream: java.io.InputStream): Result<BackupManager.BackupInfo> {
        val manager = backupManager
            ?: return Result.failure(IllegalStateException("backup unavailable — store not open"))
        val ctx = AppContext.current
            ?: return Result.failure(IllegalStateException("no app context"))
        return manager.restore(stream, File(ctx.dataDir, "app_flutter"))
    }
}

/**
 * Pass-through [StoreGate] with a process-wide mutex: serializes
 * backup/restore against each other and against anything else that takes
 * [writeMutex]. Caveat: writers that do not take the mutex (the ingestor's
 * background loop, attachment downloads) can still land mid-copy, so an
 * export may miss the very newest messages — [BackupManager] additionally
 * runs an empty write-tx barrier before copying `data.mdb`, and exports are
 * safest while the app is idle. Single-process by design; restore is followed
 * by a process restart, so no write can race the swap.
 */
internal object BackupStoreGate : StoreGate {
    private val writeMutex = Any()

    override fun <T> withStorePaused(block: () -> T): T =
        synchronized(writeMutex) { block() }
}

/** Set by the push service once the Rust state is live. */
object PushStateHolder {
    private val _state = MutableStateFlow<NativePushState?>(null)
    val stateFlow = _state.asStateFlow()
    val state: NativePushState? get() = _state.value

    private val _myHandles = MutableStateFlow<Set<String>>(emptySet())
    val myHandlesFlow = _myHandles.asStateFlow()
    val myHandles: Set<String> get() = _myHandles.value

    private val _lastError = MutableStateFlow<String?>(null)
    val lastErrorFlow = _lastError.asStateFlow()
    val lastError: String? get() = _lastError.value

    fun install(state: NativePushState, handles: Set<String>) {
        _state.value = state
        _myHandles.value = handles
        _lastError.value = null
        AppContext.current?.let { CloudSyncWiring.onStateInstalled(it, state) }
    }

    fun reportError(message: String) {
        _lastError.value = message
    }

    fun clear(resetError: Boolean = false) {
        _state.value = null
        _myHandles.value = emptySet()
        if (resetError) _lastError.value = null
        CloudSyncWiring.clear()
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
    isSms = item.isSms,
    muted = item.muted,
    archived = item.archived,
    avatarAddress = item.avatarAddress,
    avatarPath = item.avatarPath,
    isGroup = item.isGroup,
    customBackgroundPath = item.customBackgroundPath,
    transcriptBackgroundPath = item.transcriptBackgroundPath,
    transcriptBackgroundVersion = item.transcriptBackgroundVersion,
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
    guid = item.guid,
    replyToGuid = item.threadOriginatorGuid,
    replyToPart = item.threadOriginatorPart,
    stickers = item.stickers.map { sticker ->
        StickerPlacement(
            reactionGuid = sticker.reactionGuid,
            attachmentGuid = sticker.attachmentGuid,
            targetPart = sticker.targetPart,
            messageWidth = sticker.messageWidth,
            normalizedX = sticker.normalizedX,
            normalizedY = sticker.normalizedY,
            rotation = sticker.rotation,
            scale = sticker.scale,
            effectType = sticker.effectType,
            downloaded = sticker.downloaded,
        )
    },
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
    partIndex = attachment.guid?.substringAfterLast('_')?.toLongOrNull() ?: 0L,
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
 * Fills the display fields (attachment metadata, edited/unsent flags) the
 * core MessageItem does not carry yet, using one batched entity read.
 */
private fun enrichWithEntityDetails(
    items: List<MessageItem>,
    store: BoxStore?,
): List<MessageItem> {
    if (store == null || items.isEmpty()) return items
    return runCatching {
        val messageBox = store.boxFor(Message::class.java)
        val entities = messageBox.get(items.map { it.id })
        val byId = HashMap<Long, Message>(entities.size)
        entities.forEach { byId[it.id] = it }
        val replyTargets = entities.asSequence()
            .mapNotNull { it.threadOriginatorGuid }
            .distinct()
            .associateWith { guid ->
                messageBox.query()
                    .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
            }
        items.map { item ->
            val entity = byId[item.id] ?: return@map item
            val (edited, unsent) = editedFlags(entity)
            val attachments = runCatching {
                entity.dbAttachments.map(::attachmentToMeta)
            }.getOrDefault(emptyList())
            val firstAttachment = attachments.firstOrNull()
            item.copy(
                attachmentMeta = firstAttachment,
                attachmentMetas = attachments,
                edited = edited,
                unsent = unsent,
                uploadProgress = firstAttachment?.guid?.let { UploadProgressBoard.current[it] },
                expressiveSendStyleId = entity.expressiveSendStyleId,
                replyPreviewText = entity.threadOriginatorGuid?.let { guid ->
                    val target = replyTargets[guid]
                    val part = entity.threadOriginatorPart?.toLongOrNull() ?: 0L
                    val attachment = target?.dbAttachments?.firstOrNull { row ->
                        row.guid?.substringAfterLast('_')?.toLongOrNull() == part
                    }
                    attachment?.transferName
                        ?: target?.text?.trim()?.takeIf { it.isNotEmpty() }
                        ?: if (target?.hasAttachments == true) "Attachment" else null
                },
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
        sync?.upsertContacts(raw)
        handleIndex = null // force rebuild so fresh linkages resolve
        displayInfoIndex = null
    }

    fun remove(nativeContactIds: Collection<String>): Int {
        val removed = sync?.removeContacts(nativeContactIds) ?: 0
        if (removed > 0) {
            handleIndex = null
            displayInfoIndex = null
        }
        return removed
    }

    fun relink(): app.openbubbles.core.contacts.ContactRelinkResult? {
        val result = sync?.relinkContacts() ?: return null
        // History may have added handles even when every existing relation
        // was already correct, so always rebuild the address lookup too.
        handleIndex = null
        displayInfoIndex = null
        return result
    }

    @Volatile
    private var handleIndex: Map<String, Handle>? = null

    @Volatile
    private var indexBuiltAt: Long = 0L

    @Volatile
    private var displayInfoIndex: Map<Long, app.openbubbles.core.contacts.HandleDisplayInfo>? = null

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
        var infoByHandle = displayInfoIndex
        if (infoByHandle == null) {
            synchronized(this) {
                infoByHandle = displayInfoIndex
                if (infoByHandle == null) {
                    infoByHandle = contactSync.displayInfoByHandleId()
                    displayInfoIndex = infoByHandle
                }
            }
        }
        return infoByHandle?.get(handle.id)?.let { info -> info.name to info.avatar }
    }
}

private class CoreChatListRepository(
    private val repo: ChatRepo,
) : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> =
        repo.observeChats()
            .map { list -> list.map(::coreChatToUi) }
            .flowOn(Dispatchers.IO)

    override fun markRead(id: Long) = repo.markRead(id)

    override fun setPinned(id: Long, pinned: Boolean) = repo.setPinned(id, pinned)

    override fun setMuted(id: Long, muted: Boolean) = repo.setMuted(id, muted)

    override fun setMutedUntil(id: Long, untilEpochMs: Long) = repo.setMutedUntil(id, untilEpochMs)

    override fun setArchived(id: Long, archived: Boolean) = repo.setArchived(id, archived)

    override fun delete(id: Long) {
        val recordId = repo.softDelete(id) ?: return
        AppContext.current?.let { CloudSyncWiring.queueChatDelete(it, recordId) }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
internal class CoreMessageListRepository(
    private val repo: MessageRepo,
    private val store: BoxStore?,
) : MessageListRepository {
    private class PagingWindow(initialLimit: Int) {
        val size = MutableStateFlow(initialLimit)

        @Volatile
        var newestId: Long? = null
    }

    /** Independent bounded window per open conversation. */
    private val windows = ConcurrentHashMap<Long, PagingWindow>()

    private fun window(chatId: Long, initialLimit: Int): PagingWindow =
        windows.computeIfAbsent(chatId) { PagingWindow(initialLimit) }

    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> {
        val paging = window(chatId, limit)
        return paging.size.flatMapLatest { size ->
            // Combined with the upload board so progress ticks re-emit the page.
            combine(
                repo.observeMessages(chatId, size.coerceAtLeast(limit)),
                UploadProgressBoard.progress,
            ) { page, _ ->
                val previousNewest = paging.newestId
                if (page.isNotEmpty()) {
                    paging.newestId = page.first().id
                    val newlyPrepended = previousNewest?.let { previous ->
                        page.indexOfFirst { it.id == previous }
                    } ?: 0
                    if (newlyPrepended > 0) {
                        // Keep already-loaded older rows when new messages land.
                        paging.size.value += newlyPrepended
                    }
                }
                enrichWithEntityDetails(page.map(::coreMessageToUi), store).asReversed()
            }.flowOn(Dispatchers.IO)
        }
    }

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> {
        val cursor = before ?: return emptyList()
        val older = repo.messagesBefore(chatId, beforeId = cursor, limit = count)
        if (older.isNotEmpty()) {
            window(chatId, count).size.value += older.size
        }
        return enrichWithEntityDetails(older.map(::coreMessageToUi), store).asReversed()
    }

    override fun thread(chatId: Long, rootGuid: String, part: Long): List<MessageItem> =
        enrichWithEntityDetails(
            repo.threadMessages(chatId, rootGuid, part).map(::coreMessageToUi),
            store,
        )

    override fun release(chatId: Long) {
        windows.remove(chatId)
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
        sendInternal(chatId, text, effectId, null)
    }

    override suspend fun sendReply(chatId: Long, text: String, replyGuid: String, replyPart: Long) {
        sendInternal(chatId, text, null, replyGuid, replyPart)
    }

    private suspend fun sendInternal(
        chatId: Long,
        text: String,
        effectId: String?,
        replyGuid: String?,
        replyPart: Long = 0L,
    ) {
        val graph = CoreGraph
        val store = graph.store ?: error("store unavailable")
        val ing = graph.ingestor ?: error("ingestor unavailable")

        val chatBox = store.boxFor(Chat::class.java)
        val messageBox = store.boxFor(Message::class.java)
        val chat = chatBox.get(chatId) ?: error("no chat $chatId")

        val pushState = PushStateHolder.state
        val myHandle = sendingHandle(chat)
            ?: if (pushState == null) chat.usingHandle else null
            ?: error("no registered sending handle")
        val conversation = sendConversation(store, chat, myHandle)

        val tempGuid = MessageIngestor.tempGuid()
        graphMessageStage(store, chat.guid, myHandle, text, tempGuid).let { staged ->
            if (effectId != null) {
                // Persist the effect on the staged row so the bubble (and the
                // screen-effect trigger) sees it before the echo lands.
                staged.expressiveSendStyleId = effectId
                messageBox.put(staged)
            }
            if (replyGuid != null) {
                staged.threadOriginatorGuid = replyGuid
                staged.threadOriginatorPart = replyPart.toString()
                messageBox.put(staged)
            }
        }

        if (pushState == null) {
            CoreGraphStageHolder.messageRepo(store)
                .failOutgoing(tempGuid, "Not connected to Apple push")
            return
        }

        var failureLookupGuid = tempGuid
        try {
            val inst = runInterruptible(Dispatchers.IO) {
                pushState.sendText(
                    conversation,
                    myHandle,
                    text,
                    // replyGuid, replyPart, effect, subject
                    replyGuid, replyGuid?.let { replyPart.toString() }, effectId, null,
                )
            }
            failureLookupGuid = inst.id
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
            CoreGraphStageHolder.messageRepo(store)
                .failOutgoing(failureLookupGuid, t.message ?: t.javaClass.simpleName)
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

/** Local unread-state update plus the legacy iMessage read-receipt routing. */
private object CoreReadReceiptSender : ReadReceiptSender {
    override suspend fun markRead(chatId: Long, messageGuid: String?) {
        val store = CoreGraph.store ?: return
        ChatRepo(store).markRead(chatId)

        val chat = store.boxFor(Chat::class.java).get(chatId) ?: return
        val state = PushStateHolder.state ?: return
        val sender = sendingHandle(chat) ?: return
        val receiptGuid = messageGuid?.takeUnless {
            it.contains("temp") || it.contains("error")
        } ?: store.boxFor(Message::class.java)
                .query()
                .equal(Message_.chatId, chatId)
                .isNull(Message_.dateDeleted)
                .orderDesc(Message_.dateCreated)
                .build().use { query ->
                    query.find(0, 10).firstNotNullOfOrNull { message ->
                        (message.stagingGuid ?: message.guid)?.takeUnless {
                            it.contains("temp") || it.contains("error")
                        }
                    }
                }
                ?: return
        val globalReceipts = AppContext.current
            ?.let { MessagingPrefs(it).sendReadReceipts }
            ?: false
        val notifyOthers = chat.autoSendReadReceipts || globalReceipts
        val conversation = readReceiptConversation(chat, sender, receiptGuid, notifyOthers)
        runCatching {
            runInterruptible(Dispatchers.IO) {
                state.sendRead(conversation, sender, receiptGuid)
            }
        }.onFailure { error ->
            PushStateHolder.reportError(
                "Conversation was marked read locally, but the Apple receipt failed: " +
                    (error.message ?: error.javaClass.simpleName),
            )
        }
    }
}

/** Rust-backed tapback, edit, and undo-send operations with local echoes. */
private object CoreMessageActions : MessageActions {
    override suspend fun react(
        chatId: Long,
        messageGuid: String,
        messageText: String,
        reactionIndex: Int,
        emoji: String?,
        enable: Boolean,
    ) {
        require(reactionIndex in 0..6) { "invalid reaction" }
        val (state, conversation, sender, ingestor) = actionContext(chatId)
        val inst = runInterruptible(Dispatchers.IO) {
            state.sendReaction(
                conversation,
                sender,
                messageGuid,
                0uL,
                reactionIndex.toULong(),
                emoji,
                messageText,
                enable,
            )
        }
        ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun edit(chatId: Long, messageGuid: String, newText: String) {
        require(newText.isNotBlank()) { "message cannot be empty" }
        val (state, conversation, sender, ingestor) = actionContext(chatId)
        val inst = runInterruptible(Dispatchers.IO) {
            state.editMessage(
                conversation,
                sender,
                messageGuid,
                0uL,
                listOf(UIndexedPart(UPart.Text(newText, ""), null, null)),
            )
        }
        ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun unsend(chatId: Long, messageGuid: String) {
        val (state, conversation, sender, ingestor) = actionContext(chatId)
        val inst = runInterruptible(Dispatchers.IO) {
            state.unsendMessage(conversation, sender, messageGuid, 0uL)
        }
        ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    private fun actionContext(chatId: Long): MessageActionContext {
        val store = CoreGraph.store ?: error("store unavailable")
        val state = PushStateHolder.state ?: error("not connected to Apple push")
        val ingestor = CoreGraph.ingestor ?: error("ingestor unavailable")
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
        check(chat.isRpSms != true) { "iMessage actions are unavailable for SMS" }
        val sender = sendingHandle(chat) ?: error("no registered sending handle")
        return MessageActionContext(state, sendConversation(store, chat, sender), sender, ingestor)
    }
}

private data class MessageActionContext(
    val state: NativePushState,
    val conversation: UConversation,
    val sender: String,
    val ingestor: MessageIngestor,
)

private object CoreGraphStageHolder {
    private val repos = java.util.concurrent.ConcurrentHashMap<BoxStore, MessageRepo>()
    fun messageRepo(store: BoxStore): MessageRepo =
        repos.computeIfAbsent(store) { MessageRepo(it) }
}

/** Prefer the sender explicitly associated with this chat (legacy ensureHandle). */
internal fun sendingHandle(chat: Chat, handles: Set<String> = PushStateHolder.myHandles): String? {
    val preferred = chat.usingHandle
    if (preferred != null) {
        handles.firstOrNull { candidate ->
            candidate == preferred ||
                MessageMapper.normalizeAddress(candidate) == MessageMapper.normalizeAddress(preferred)
        }?.let { return it }
    }
    return handles.firstOrNull()
}

/**
 * Legacy `Chat.getConversationData`: retain the stable chat identity and the
 * latest confirmed message anchor on every send. Without these fields Rust
 * creates a new sender guid, which can split group conversations.
 */
internal fun sendConversation(chat: Chat, afterGuid: String?, sender: String? = null): UConversation = UConversation(
    participants = buildList {
        addAll(chat.handles.map { MessageMapper.toRustHandle(it.address) })
        sender?.let { add(MessageMapper.toRustHandle(MessageMapper.normalizeAddress(it))) }
    }.distinct(),
    cvName = chat.apnTitle ?: chat.displayName,
    senderGuid = chat.guid,
    afterGuid = afterGuid ?: chat.guid,
)

/**
 * Public direct-chat receipts reach the peer and our devices. Private direct
 * receipts, group receipts, and carrier-chat receipts target only our
 * registered handle, matching the legacy client.
 */
internal fun readReceiptConversation(
    chat: Chat,
    sender: String,
    messageGuid: String,
    notifyOthers: Boolean,
): UConversation {
    val conversation = sendConversation(chat, messageGuid, sender)
    return if (!notifyOthers || conversation.participants.size > 2) {
        conversation.copy(participants = listOf(sender))
    } else {
        conversation
    }
}

private fun sendConversation(store: BoxStore, chat: Chat, sender: String? = null): UConversation {
    val anchor = store.boxFor(Message::class.java)
        .query()
        .equal(Message_.chatId, chat.id)
        .orderDesc(Message_.dateCreated)
        .build().use { it.find(0, 10) }
        .firstNotNullOfOrNull { message ->
            (message.stagingGuid ?: message.guid)?.takeUnless {
                it.contains("temp") || it.contains("error")
            }
        }
    return sendConversation(chat, anchor, sender)
}

/** Unused today; reserved for the login flow (M1.e) to name new sessions. */
@Suppress("unused")
private fun newStagingGuid(): String = UUID.randomUUID().toString().uppercase()

/** Group details mutations with immediate echo ingestion. */
private object CoreChatInfoActions : ChatInfoActions {
    override suspend fun rename(chatId: Long, name: String) {
        require(name.isNotBlank()) { "conversation name cannot be empty" }
        val context = context(chatId)
        val inst = runInterruptible(Dispatchers.IO) {
            context.state.renameChat(context.conversation, context.sender, name.trim())
        }
        context.ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun addParticipant(chatId: Long, address: String) {
        require(address.isNotBlank()) { "participant address cannot be empty" }
        val context = context(chatId)
        val participant = MessageMapper.toRustHandle(MessageMapper.normalizeAddress(address.trim()))
        require(participant !in context.conversation.participants) { "participant is already in this group" }
        changeParticipants(context, (context.conversation.participants + participant).distinct())
    }

    override suspend fun removeParticipant(chatId: Long, address: String) {
        val context = context(chatId)
        val normalized = MessageMapper.normalizeAddress(address)
        val participants = context.conversation.participants.filterNot {
            MessageMapper.normalizeAddress(it) == normalized
        }
        require(participants.size >= 2) { "a group needs at least two participants" }
        changeParticipants(context, participants)
    }

    override suspend fun setGroupIcon(chatId: Long, file: File) {
        require(file.isFile) { "group photo is unavailable" }
        val context = context(chatId)
        val version = nextGroupVersion(context.chat)
        val inst = runInterruptible(Dispatchers.IO) {
            context.state.setGroupIcon(
                context.conversation,
                context.sender,
                file.absolutePath,
                version,
                null,
            )
        }
        context.chat.customAvatarPath = file.absolutePath
        context.chat.photoAttachmentGuid = inst.id
        context.chat.groupVersion = version.toLong()
        CoreGraph.store?.boxFor(Chat::class.java)?.put(context.chat)
        context.ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun removeGroupIcon(chatId: Long) {
        val context = context(chatId)
        val version = nextGroupVersion(context.chat)
        val inst = runInterruptible(Dispatchers.IO) {
            context.state.removeGroupIcon(context.conversation, context.sender, version)
        }
        context.chat.customAvatarPath?.let { runCatching { File(it).delete() } }
        context.chat.customAvatarPath = null
        context.chat.photoAttachmentGuid = null
        context.chat.groupVersion = version.toLong()
        CoreGraph.store?.boxFor(Chat::class.java)?.put(context.chat)
        context.ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun leave(chatId: Long) {
        val context = context(chatId)
        val inst = runInterruptible(Dispatchers.IO) {
            context.state.leaveChat(
                context.conversation,
                context.sender,
                nextGroupVersion(context.chat),
            )
        }
        context.ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    private suspend fun changeParticipants(context: GroupActionContext, participants: List<String>) {
        val version = nextGroupVersion(context.chat)
        val inst = runInterruptible(Dispatchers.IO) {
            context.state.changeParticipants(
                context.conversation,
                context.sender,
                participants,
                version,
            )
        }
        context.ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    private fun context(chatId: Long): GroupActionContext {
        val store = CoreGraph.store ?: error("store unavailable")
        val state = PushStateHolder.state ?: error("not connected to Apple push")
        val ingestor = CoreGraph.ingestor ?: error("ingestor unavailable")
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
        check(chat.isRpSms != true) { "group changes are unavailable for SMS" }
        val sender = sendingHandle(chat) ?: error("no registered sending handle")
        return GroupActionContext(chat, state, sendConversation(store, chat, sender), sender, ingestor)
    }

    private fun nextGroupVersion(chat: Chat): ULong = ((chat.groupVersion ?: -1L) + 1L).toULong()
}

private data class GroupActionContext(
    val chat: Chat,
    val state: NativePushState,
    val conversation: UConversation,
    val sender: String,
    val ingestor: MessageIngestor,
)

/** Device-local background storage; synced Apple backgrounds are handled by push intake. */
private object CoreChatBackgroundActions : ChatBackgroundActions {
    override suspend fun setLocalBackground(chatId: Long, file: File) = withContext(Dispatchers.IO) {
        require(file.isFile) { "background image is unavailable" }
        val store = CoreGraph.store ?: error("store unavailable")
        val context = AppContext.current ?: error("app context unavailable")
        val chatBox = store.boxFor(Chat::class.java)
        val chat = chatBox.get(chatId) ?: error("no chat $chatId")
        val directory = File(context.filesDir, "chat_backgrounds").apply { mkdirs() }
        val extension = file.extension.takeIf { it.length in 2..5 } ?: "jpg"
        val destination = File(directory, "local-$chatId-${UUID.randomUUID()}.$extension")
        file.copyTo(destination, overwrite = true)
        deleteOwnedBackground(chat.customBackgroundPath, directory, destination)
        chat.customBackgroundPath = destination.absolutePath
        chatBox.put(chat)
        Unit
    }

    override suspend fun clearLocalBackground(chatId: Long) = withContext(Dispatchers.IO) {
        val store = CoreGraph.store ?: error("store unavailable")
        val context = AppContext.current ?: error("app context unavailable")
        val chatBox = store.boxFor(Chat::class.java)
        val chat = chatBox.get(chatId) ?: error("no chat $chatId")
        val directory = File(context.filesDir, "chat_backgrounds")
        deleteOwnedBackground(chat.customBackgroundPath, directory, null)
        chat.customBackgroundPath = null
        chatBox.put(chat)
        Unit
    }

    private fun deleteOwnedBackground(path: String?, directory: File, except: File?) {
        val candidate = path?.let(::File)?.canonicalFile ?: return
        val root = directory.canonicalFile.toPath()
        if (candidate.toPath().startsWith(root) && candidate != except) {
            runCatching { candidate.delete() }
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

        val pushState = PushStateHolder.state
        val myHandle = sendingHandle(chat)
            ?: if (pushState == null) chat.usingHandle else null
            ?: error("no registered sending handle")
        val conversation = sendConversation(store, chat, myHandle)

        val tempGuid = MessageIngestor.tempGuid()
        val attachmentGuid = "${tempGuid}_att0"

        // 1. Stage the outgoing row (caption rides as the text part).
        val staged = CoreGraphStageHolder.messageRepo(store)
            .stageOutgoingMessage(chat.guid, myHandle, caption.orEmpty(), tempGuid)

        // 2. Placeholder attachment metadata + payload in the canonical
        //    layout so the bubble renders (and image-previews) right away.
        val root = File(
            AppContext.current?.dataDir ?: error("no files dir"),
            "app_flutter",
        )
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
                    // The payload is already in the canonical local store;
                    // outgoing video/file bubbles must never offer to
                    // download their own just-uploaded file.
                    isDownloaded = true
                    message.target = staged
                },
            )
            staged.hasAttachments = true
            messageBox.put(staged)
        }
        UploadProgressBoard.update(attachmentGuid, 0L to payload.length())

        if (pushState == null) {
            CoreGraphStageHolder.messageRepo(store)
                .failOutgoing(tempGuid, "Not connected to Apple push")
            UploadProgressBoard.clear(attachmentGuid)
            return
        }

        var failureLookupGuid = tempGuid
        try {
            val inst = runInterruptible(Dispatchers.IO) {
                pushState.sendAttachment(
                    conversation,
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
            failureLookupGuid = inst.id
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
            val normal = inst.message as? uniffi.rust_lib_bluebubbles.UMessage.Normal
            val realAttachmentGuid = normal?.let {
                MessageMapper.mapParts(it.parts, inst.id, isOutgoing = true).second.firstOrNull()?.guid
            }
            if (realAttachmentGuid != null) {
                // Database promotion happens during ingest; move the local
                // payload to the same real guid so the confirmed bubble keeps
                // rendering without a redundant network download.
                disk.promoteLocalDirectory(attachmentGuid, realAttachmentGuid)
            }
        } catch (t: Throwable) {
            CoreGraphStageHolder.messageRepo(store)
                .failOutgoing(failureLookupGuid, t.message ?: t.javaClass.simpleName)
        } finally {
            UploadProgressBoard.clear(attachmentGuid)
        }
    }
}

/** Sticker upload/send path backed by the positional Rust reaction API. */
private object CoreStickerSender : StickerSender {
    override suspend fun send(
        chatId: Long,
        targetGuid: String,
        targetPart: Long,
        targetText: String,
        sticker: OutgoingAttachment,
        transform: StickerTransform,
    ) {
        val store = CoreGraph.store ?: error("store unavailable")
        val ingestor = CoreGraph.ingestor ?: error("ingestor unavailable")
        val state = PushStateHolder.state ?: error("not connected to Apple push")
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
        val sender = sendingHandle(chat) ?: error("no registered sending handle")
        val conversation = sendConversation(store, chat, sender)
        val displayName = sticker.name ?: "sticker.png"

        val inst = runInterruptible(Dispatchers.IO) {
            state.sendSticker(
                conversation,
                sender,
                targetGuid,
                targetPart.toULong(),
                targetText,
                sticker.file.absolutePath,
                sticker.mime,
                sticker.uti,
                displayName,
                transform.messageWidth,
                transform.normalizedX,
                transform.normalizedY,
                transform.rotation,
                transform.scale,
                transform.effectType,
                null,
            )
        }
        ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)

        // The uploaded sticker is already local. Put a copy in the canonical
        // attachment directory so the overlay renders immediately.
        val attachmentGuid = "${inst.id}_0"
        val attachmentBox = store.boxFor(Attachment::class.java)
        val row = attachmentBox.query()
            .equal(Attachment_.guid, attachmentGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
        if (row != null) {
            val root = File(AppContext.current?.dataDir ?: error("no files dir"), "app_flutter")
            val disk = AttachmentStore(store, root)
            val destination = disk.pathFor(row)
            destination.parentFile?.mkdirs()
            sticker.file.copyTo(destination, overwrite = true)
            disk.markDownloaded(attachmentGuid, destination.length())
        }
        runCatching { sticker.file.delete() }
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

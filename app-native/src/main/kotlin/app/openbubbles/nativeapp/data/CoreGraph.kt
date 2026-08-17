package app.openbubbles.nativeapp.data

import android.content.Context
import android.util.Log
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
import app.openbubbles.core.send.buildSendConversation
import app.openbubbles.core.send.selectSendingHandle
import app.openbubbles.nativeapp.service.Notifications
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UConversation
import uniffi.rust_lib_bluebubbles.UIndexedPart
import uniffi.rust_lib_bluebubbles.UPart
import uniffi.rust_lib_bluebubbles.UPushMessage
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
/**
 * Live composition root binding the UI contracts to :core (ObjectBox) and,
 * when the push service is up, the Rust send path. Falls back to the fake
 * repositories if the store cannot open (should not happen; empty DB is a
 * valid, boring state).
 */
object CoreGraph {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var restoreRestartRequired = false

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

    private val chatRepo: ChatRepo? by lazy {
        store?.let { ChatRepo(it) { PushStateHolder.myHandles } }
    }
    private val messageRepo: MessageRepo? by lazy {
        val chats = chatRepo ?: return@lazy null
        store?.let { MessageRepo(it, chats) }
    }
    val ingestor: MessageIngestor? by lazy {
        val st = store ?: return@lazy null
        val root = AppContext.current?.dataDir?.let { File(it, "app_flutter") }
        MessageIngestor(
            st,
            scope,
            root?.let { AttachmentStore(st, it) },
            transcriptBackgroundHandler = transcriptBackgroundStore,
        )
    }

    /**
     * Applies Apple chat backgrounds arriving over live push. History sync
     * builds its own copy (CloudSyncWiring); both coordinate through the
     * store's process-wide write mutex and the same chat_backgrounds
     * directory, so a background set on another Apple device lands from
     * whichever path sees it first.
     */
    private val transcriptBackgroundStore: TranscriptBackgroundStore? by lazy {
        val context = AppContext.current ?: return@lazy null
        store ?: return@lazy null
        TranscriptBackgroundStore(context.applicationContext) { PushStateHolder.state }
    }

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
    val faceTimeCaller: FaceTimeCaller by lazy {
        if (store != null) CoreFaceTimeCaller else FakeFaceTimeCaller
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
        onNewUnread: (chatId: Long, body: String) -> Unit,
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
        CoreContacts.syncFromDevice(raw).also { UiContacts.notifyAvatarsChanged() }

    /** Apply CardDAV contact tombstones + invalidate cached name lookups. */
    fun removeContacts(nativeContactIds: Collection<String>): Int =
        CoreContacts.remove(nativeContactIds).also { UiContacts.notifyAvatarsChanged() }

    /** Re-match stored contacts after CloudKit creates additional handles. */
    fun relinkContacts(): app.openbubbles.core.contacts.ContactRelinkResult? =
        CoreContacts.relink()?.also { UiContacts.notifyAvatarsChanged() }

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

    fun relatedDirectChatIds(chatId: Long): List<Long> =
        chatRepo?.relatedDirectChatIds(chatId).orEmpty().ifEmpty { listOf(chatId) }

    internal fun messageNotificationIdentity(
        chat: Chat,
        senderAddress: String? = null,
        myHandles: Set<String> = PushStateHolder.myHandles,
    ): MessageNotificationIdentity = resolveMessageNotificationIdentity(
        chat = chat,
        senderAddress = senderAddress,
        myHandles = myHandles,
        contactNameFor = { address -> contactDisplayInfo(address)?.first },
    )

    fun preferredContacts(includeNativeContacts: Boolean = true): List<app.openbubbles.core.contacts.RawContact> =
        CoreContacts.preferredContacts(includeNativeContacts)

    /**
     * Sign out: deregister from iMessage (best effort), tear down the Rust
     * state, stop the push service, and clear the holders — the sign-in
     * banner reappears on the chat list.
     */
    suspend fun signOut(context: android.content.Context): Result<Unit> {
        Log.i("CoreGraph", "Apple account sign-out requested")
        val teardown = withContext(Dispatchers.IO) {
            runCatching { PushStateHolder.state?.teardown(true) }.map { Unit }
        }
        teardown.onFailure { error ->
            Log.e("CoreGraph", "Apple account sign-out teardown failed", error)
        }
        PushStateHolder.clear(resetError = true)
        runCatching {
            context.stopService(
                android.content.Intent(context, app.openbubbles.nativeapp.service.NativePushService::class.java))
        }.onFailure { error ->
            Log.e("CoreGraph", "Apple push service stop failed during sign-out", error)
        }
        Log.i("CoreGraph", "Apple account sign-out finished")
        return teardown
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
        val repo = chatRepo
        val st = store
        if (repo != null && st != null) {
            CoreChatInfoRepository(repo, st)
        } else {
            FakeChatInfoRepository
        }
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
            appStateSnapshot = { CloudSyncWiring.backupState(ctx) },
            appStateRestore = { CloudSyncWiring.restoreBackupState(ctx, it) },
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
        restoreRestartRequired = false
        val manager = backupManager
            ?: return Result.failure(IllegalStateException("backup unavailable — store not open"))
        val ctx = AppContext.current
            ?: return Result.failure(IllegalStateException("no app context"))
        return manager.restore(stream, File(ctx.dataDir, "app_flutter")) {
            restoreRestartRequired = true
            ctx.stopService(
                android.content.Intent(
                    ctx,
                    app.openbubbles.nativeapp.service.NativePushService::class.java,
                ),
            )
            try {
                PushStateHolder.state?.stopLoop()
            } finally {
                PushStateHolder.clear(resetError = true)
            }
            store?.close()
        }
    }

    fun restoreRequiresRestart(): Boolean = restoreRestartRequired
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

private fun coreChatToUi(item: app.openbubbles.core.model.ChatListItem) = ChatListItem(
    id = item.id,
    title = item.title,
    snippet = item.snippet,
    date = item.date?.time ?: 0L,
    unread = item.unreadCount,
    pinned = item.pinned,
    // Seed DMs by the contact address so the color matches the contact picker
    // and chat-info surfaces; groups fall back to the chat guid.
    avatarColor = app.openbubbles.nativeapp.ui.common.avatarColorFor(item.avatarAddress ?: item.guid),
    isSms = item.isSms,
    muted = item.muted,
    archived = item.archived,
    avatarAddress = item.avatarAddress,
    avatarPath = item.avatarPath,
    isGroup = item.isGroup,
    customBackgroundPath = item.customBackgroundPath,
    transcriptBackgroundPath = item.transcriptBackgroundPath,
    transcriptBackgroundVersion = item.transcriptBackgroundVersion,
    memberChatIds = item.memberChatIds,
    preferredChatId = item.preferredChatId,
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
    replyToPartLocator = item.threadOriginatorLocator,
    replyPartLocators = item.replyPartLocators,
    richLink = parseRichLinkPreview(item.richLinkMetadataJson, item.text),
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
    chatId = item.chatId,
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
    partIndex = (attachment.metadata?.get("messagePart") as? Number)?.toLong()
        ?: attachment.guid?.substringAfterLast('_')?.toLongOrNull()
        ?: 0L,
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
                    val part = app.openbubbles.core.model.MessageMapper
                        .replyPartIndex(entity.threadOriginatorPart) ?: 0L
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

    fun preferredContacts(includeNativeContacts: Boolean): List<app.openbubbles.core.contacts.RawContact> =
        sync?.preferredContacts(includeNativeContacts).orEmpty()

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
        val handle = handleFor(address)
        if (handle != null) {
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
            infoByHandle?.get(handle.id)?.let { info -> return info.name to info.avatar }
        }
        return contactSync.displayInfoForAddress(address)?.let { info -> info.name to info.avatar }
    }
}

private class CoreChatListRepository(
    private val repo: ChatRepo,
) : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> =
        repo.observeChats()
            .map { list -> list.map(::coreChatToUi) }
            .flowOn(Dispatchers.IO)

    private fun memberIds(id: Long): List<Long> =
        repo.relatedDirectChatIds(id).ifEmpty { listOf(id) }

    override fun markRead(id: Long) {
        memberIds(id).forEach(repo::markRead)
        AppContext.current?.let { Notifications.cancelForChat(it, id) }
    }

    override fun setPinned(id: Long, pinned: Boolean) =
        memberIds(id).forEach { repo.setPinned(it, pinned) }

    override fun setMuted(id: Long, muted: Boolean) =
        memberIds(id).forEach { repo.setMuted(it, muted) }

    override fun setMutedUntil(id: Long, untilEpochMs: Long) =
        memberIds(id).forEach { repo.setMutedUntil(it, untilEpochMs) }

    override fun setArchived(id: Long, archived: Boolean) =
        memberIds(id).forEach { repo.setArchived(it, archived) }

    override fun delete(id: Long) {
        memberIds(id).forEach { memberId ->
            val recordId = repo.softDelete(memberId) ?: return@forEach
            AppContext.current?.let { CloudSyncWiring.queueChatDelete(it, recordId) }
        }
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

    private class Snapshot(
        val items: List<MessageItem>,
        val newestId: Long?,
        val limit: Int,
        val warmedAt: Long = System.currentTimeMillis(),
        @Volatile var stale: Boolean = false,
    )

    /** Independent bounded window per open conversation. */
    private val windows = ConcurrentHashMap<Long, PagingWindow>()
    private val snapshots = ConcurrentHashMap<Long, Snapshot>()
    private val retained = ConcurrentHashMap.newKeySet<Long>()
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val warmLimiter = Semaphore(3)

    @Volatile
    private var desired: Set<Long> = emptySet()

    private fun window(chatId: Long, initialLimit: Int): PagingWindow =
        windows.computeIfAbsent(chatId) { PagingWindow(initialLimit) }

    private fun lockFor(chatId: Long): Mutex = locks.getOrPut(chatId) { Mutex() }

    override fun cached(chatId: Long): List<MessageItem> = snapshots[chatId]?.items.orEmpty()

    override suspend fun prefetch(
        chatIds: Collection<Long>,
        limit: Int,
    ) {
        val wanted = chatIds.toSet()
        desired = wanted
        val keep = wanted + retained
        snapshots.keys.filter { it !in keep }.forEach { snapshots.remove(it) }
        windows.keys.filter { it !in keep }.forEach { windows.remove(it) }
        locks.keys.filter { it !in keep }.forEach { locks.remove(it) }
        if (wanted.isEmpty()) return
        coroutineScope {
            wanted.map { chatId ->
                async { warmLimiter.withPermit { warm(chatId, limit) } }
            }.awaitAll()
        }
    }

    override suspend fun prime(chatId: Long, limit: Int) {
        retained.add(chatId)
        warmLimiter.withPermit { warm(chatId, limit) }
    }

    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> {
        retained.add(chatId)
        val paging = window(chatId, limit)
        if (paging.size.value < limit) paging.size.value = limit
        return paging.size.flatMapLatest { size ->
            val requested = size.coerceAtLeast(limit)
            // Combined with the upload board so progress ticks re-emit the page.
            combine(
                repo.observeMessages(chatId, requested),
                UploadProgressBoard.progress,
            ) { page, _ ->
                pageToUi(chatId, paging, page)
            }
                .onStart {
                    val cached = snapshots[chatId]?.items
                    if (!cached.isNullOrEmpty() && cached.size >= requested) {
                        emit(cached)
                    } else {
                        emit(pageToUi(chatId, paging, repo.messages(chatId, requested)))
                    }
                }
                .flowOn(Dispatchers.IO)
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
        retained.remove(chatId)
        val keep = snapshots[chatId]?.items.orEmpty().takeLast(TRANSCRIPT_OPEN_LIMIT)
        if (keep.isEmpty() || chatId !in desired) {
            snapshots.remove(chatId)
            windows.remove(chatId)
            locks.remove(chatId)
            return
        }
        rememberSnapshot(chatId, keep)
        windows[chatId]?.let { paging ->
            paging.size.value = keep.size.coerceAtLeast(TRANSCRIPT_PREFETCH_LIMIT)
            paging.newestId = keep.lastOrNull()?.id
        }
    }

    private fun pageToUi(
        chatId: Long,
        paging: PagingWindow,
        page: List<app.openbubbles.core.model.MessageItem>,
    ): List<MessageItem> {
        val previousNewest = paging.newestId
        if (page.isNotEmpty()) {
            paging.newestId = page.first().id
            val newlyPrepended = previousNewest?.let { previous ->
                page.indexOfFirst { it.id == previous }
            } ?: 0
            if (newlyPrepended > 0) {
                paging.size.value += newlyPrepended
            }
        }
        val ui = enrichWithEntityDetails(page.map(::coreMessageToUi), store).asReversed()
        rememberSnapshot(chatId, ui)
        return ui
    }

    private suspend fun warm(chatId: Long, limit: Int) {
        lockFor(chatId).withLock {
            val existing = snapshots[chatId]
            val now = System.currentTimeMillis()
            if (existing != null &&
                !existing.stale &&
                existing.limit >= limit &&
                now - existing.warmedAt < 750L
            ) {
                return
            }
            val ui = withContext(Dispatchers.IO) {
                val page = repo.messages(chatId, limit)
                enrichWithEntityDetails(page.map(::coreMessageToUi), store).asReversed()
            }
            rememberSnapshot(chatId, ui)
            window(chatId, limit).apply {
                if (size.value < ui.size) size.value = ui.size
                newestId = ui.lastOrNull()?.id
            }
        }
    }

    private fun rememberSnapshot(chatId: Long, items: List<MessageItem>) {
        snapshots[chatId] = Snapshot(
            items = items,
            newestId = items.lastOrNull()?.id,
            limit = items.size,
            warmedAt = System.currentTimeMillis(),
            stale = false,
        )
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

/** Participant addresses and shared media for the conversation-details screen. */
private class CoreChatInfoRepository(
    private val repo: ChatRepo,
    private val store: BoxStore,
) : ChatInfoRepository {
    override fun participantAddresses(chatId: Long): List<String> = runCatching {
        repo.participantAddresses(chatId)
    }.getOrDefault(emptyList())

    override fun sharedContent(chatId: Long, limit: Int): List<SharedContentPreview> = runCatching {
        val ids = repo.relatedDirectChatIds(chatId).ifEmpty { listOf(chatId) }
            .distinct()
            .toLongArray()
        val messages = store.boxFor(Message::class.java).query()
            .`in`(Message_.chatId, ids)
            .isNull(Message_.dateDeleted)
            .equal(Message_.hasAttachments, true)
            .orderDesc(Message_.dateCreated)
            .build()
            .use { it.find(0, (limit * 3L).coerceAtLeast(limit.toLong())) }
        val out = ArrayList<SharedContentPreview>(limit)
        for (message in messages) {
            message.dbAttachments.forEach { attachment ->
                if (out.size >= limit) return@runCatching out
                val guid = attachment.guid ?: return@forEach
                val mime = attachment.mimeType.orEmpty()
                out += SharedContentPreview(
                    id = guid,
                    label = attachment.transferName?.takeIf { it.isNotBlank() }
                        ?: mime.substringAfter('/', mime).ifBlank { "Attachment" },
                    attachmentGuid = guid,
                    url = attachment.webUrl?.takeIf { it.isNotBlank() },
                    isImage = mime.startsWith("image/", ignoreCase = true),
                )
            }
        }
        out
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

    override suspend fun sendReply(
        chatId: Long,
        text: String,
        replyGuid: String,
        replyPartLocator: String,
    ) {
        sendInternal(chatId, text, null, replyGuid, replyPartLocator)
    }

    private suspend fun sendInternal(
        chatId: Long,
        text: String,
        effectId: String?,
        replyGuid: String?,
        replyPartLocator: String? = null,
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

        val stage = stageOutgoingText(store, chat.guid, myHandle, text)
        val tempGuid = stage.tempGuid
        stage.message.let { staged ->
            if (effectId != null) {
                // Persist the effect on the staged row so the bubble (and the
                // screen-effect trigger) sees it before the echo lands.
                staged.expressiveSendStyleId = effectId
                messageBox.put(staged)
            }
            if (replyGuid != null) {
                staged.threadOriginatorGuid = replyGuid
                staged.threadOriginatorPart = replyPartLocator
                messageBox.put(staged)
            }
        }

        if (pushState == null) {
            failOutgoingText(store, tempGuid, "Not connected to Apple push")
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
                    replyGuid, replyPartLocator, effectId, null,
                )
            }
            failureLookupGuid = inst.id
            // Promote the staged row to the Rust staging guid so the echo and
            // SendConfirm receipts find it (same swap Dart performs).
            promoteOutgoingText(store, tempGuid, inst.id)
            ing.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
        } catch (t: Throwable) {
            failOutgoingText(store, failureLookupGuid, t.message ?: t.javaClass.simpleName)
        }
    }
}

/** Local unread-state update plus the legacy iMessage read-receipt routing. */
private object CoreReadReceiptSender : ReadReceiptSender {
    override suspend fun markRead(chatId: Long, messageGuid: String?) {
        AppContext.current?.let { Notifications.cancelForChat(it, chatId) }
        val store = CoreGraph.store ?: return
        val chatRepo = ChatRepo(store)
        val relatedChatIds = chatRepo.relatedDirectChatIds(chatId).ifEmpty { listOf(chatId) }
        relatedChatIds.forEach(chatRepo::markRead)
        relatedChatIds.forEach { id ->
            val chat = store.boxFor(Chat::class.java).get(id) ?: return@forEach
            if (chat.isRpSms != true) return@forEach
            val context = AppContext.current ?: return@forEach
            val threadId = chat.telephonyId
                ?: app.openbubbles.nativeapp.sms.TelephonySmsStore.threadId(
                    context,
                    chat.handles.map { it.address },
                )
            if (threadId != null) {
                app.openbubbles.nativeapp.sms.TelephonySmsStore.markThreadRead(context, threadId)
                if (chat.telephonyId == null) {
                    chat.telephonyId = threadId
                    store.boxFor(Chat::class.java).put(chat)
                }
            }
        }

        val explicitMessage = messageGuid?.let { guid ->
            val messageBox = store.boxFor(Message::class.java)
            messageBox.query()
                .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
                ?: messageBox.query()
                    .equal(Message_.stagingGuid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() }
        }
        val receiptChatIds = when {
            explicitMessage != null -> listOf(explicitMessage.chat.targetId)
            messageGuid != null -> listOf(chatId)
            else -> relatedChatIds
        }
        receiptChatIds.forEach { receiptChatId ->
            sendReadReceipt(store, receiptChatId, messageGuid)
        }
    }

    private suspend fun sendReadReceipt(store: BoxStore, chatId: Long, messageGuid: String?) {
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: return
        if (!shouldSendAppleReadReceipt(chat)) return
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
        try {
            runInterruptible(Dispatchers.IO) {
                state.sendRead(conversation, sender, receiptGuid)
            }
        } catch (error: Throwable) {
            val failureMessage = appleReadReceiptFailureMessage(error) ?: throw error
            PushStateHolder.reportError(failureMessage)
        }
    }
}

internal fun appleReadReceiptFailureMessage(error: Throwable): String? =
    if (error is CancellationException) {
        null
    } else {
        "Conversation was marked read locally, but the Apple receipt failed: " +
            (error.message ?: error.javaClass.simpleName)
    }

internal fun shouldSendAppleReadReceipt(chat: Chat): Boolean = chat.isRpSms != true

/** Rust-backed tapback, edit, and undo-send operations with local echoes. */
private object CoreMessageActions : MessageActions {
    override suspend fun react(
        chatId: Long,
        messageGuid: String,
        messageText: String,
        messagePart: Long,
        reactionIndex: Int,
        emoji: String?,
        enable: Boolean,
    ) {
        require(reactionIndex in 0..6) { "invalid reaction" }
        require(reactionIndex != 6 || !emoji.isNullOrBlank()) { "custom reaction requires an emoji" }
        val (state, conversation, sender, ingestor) = actionContext(chatId)
        val inst = runInterruptible(Dispatchers.IO) {
            state.sendReaction(
                conversation,
                sender,
                messageGuid,
                messagePart.toULong(),
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

private object CoreFaceTimeCaller : FaceTimeCaller {
    override suspend fun start(chatId: Long): FaceTimeLaunch {
        val store = CoreGraph.store ?: error("store unavailable")
        val state = PushStateHolder.state ?: error("not connected to Apple push")
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
        check(chat.isRpSms != true) { "FaceTime is unavailable for SMS conversations" }
        val sender = sendingHandle(chat) ?: error("no registered FaceTime handle")
        val normalizedSender = MessageMapper.normalizeAddress(sender)
        val participantAddresses = chat.handles
            .map { MessageMapper.normalizeAddress(it.address) }
            .filterNot { it == normalizedSender }
            .distinct()
        require(participantAddresses.isNotEmpty()) { "This conversation has no FaceTime participants" }
        val participants = participantAddresses.map(MessageMapper::toRustHandle)
        val callUuid = UUID.randomUUID().toString().uppercase()
        val link = runInterruptible(Dispatchers.IO) {
            state.startFacetimeCall(callUuid, sender, participants)
        }
        val participantNames = participantAddresses.map { address ->
            CoreGraph.contactDisplayInfo(address)?.first?.takeIf(String::isNotBlank) ?: address
        }
        val description = chat.displayName?.takeIf(String::isNotBlank)
            ?: chat.title?.takeIf(String::isNotBlank)
            ?: participantNames.joinToString(" & ")
        return FaceTimeLaunch(
            link = link,
            displayName = normalizedSender,
            description = description,
            callUuid = callUuid,
        )
    }
}

private object CoreGraphStageHolder {
    private val repos = java.util.concurrent.ConcurrentHashMap<BoxStore, MessageRepo>()
    fun messageRepo(store: BoxStore): MessageRepo =
        repos.computeIfAbsent(store) { MessageRepo(it) }
}

/** Prefer the chat sender, then the user's default, then registration order. */
internal fun sendingHandle(
    chat: Chat,
    handles: Set<String> = PushStateHolder.myHandles,
    defaultHandle: String? = AppContext.current?.let { MessagingPrefs(it).defaultSendingHandle },
): String? = selectSendingHandle(chat, handles, defaultHandle)

/**
 * Legacy `Chat.getConversationData`: retain the stable chat identity and the
 * latest confirmed message anchor when one exists. A first message must not
 * use the chat id as `afterGuid` because it is not a message identifier.
 */
internal fun sendConversation(chat: Chat, afterGuid: String?, sender: String? = null): UConversation =
    buildSendConversation(chat, afterGuid, sender)

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
 * semantics: stage optimistically under a temp guid with placeholder
 * attachment rows (payloads moved into the canonical store layout so the
 * bubbles preview immediately), upload everything through the Rust
 * sendAttachments binding as the parts of one message with progress surfaced
 * via [UploadProgressBoard], promote the row to the Rust staging guid, then
 * ingest the echo.
 */
internal object CoreAttachmentSender : AttachmentSender {
    override suspend fun send(chatId: Long, attachments: List<OutgoingAttachment>, caption: String?) {
        if (attachments.isEmpty()) return
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

        // 1. Stage the outgoing row (caption rides as the text part).
        val staged = CoreGraphStageHolder.messageRepo(store)
            .stageOutgoingMessage(chat.guid, myHandle, caption.orEmpty(), tempGuid)

        // 2. Placeholder attachment metadata + payloads in the canonical
        //    layout so the bubbles render (and image-preview) right away.
        val root = File(
            AppContext.current?.dataDir ?: error("no files dir"),
            "app_flutter",
        )
        val disk = AttachmentStore(store, root)
        val stagedGuids = ArrayList<String>(attachments.size)
        val payloads = ArrayList<File>(attachments.size)
        attachments.forEachIndexed { index, attachment ->
            val attachmentGuid = "${tempGuid}_att$index"
            val displayName = attachment.name ?: "attachment"
            val payload = File(disk.directoryFor(attachmentGuid), disk.sanitizeFileName(displayName))
            payload.parentFile?.mkdirs()
            attachment.file.copyTo(payload, overwrite = true)
            runCatching { attachment.file.delete() } // cache copy no longer needed
            stagedGuids += attachmentGuid
            payloads += payload
        }

        store.runInTx {
            attachments.forEachIndexed { index, attachment ->
                attachmentBox.put(
                    Attachment().apply {
                        guid = stagedGuids[index]
                        isOutgoing = true
                        mimeType = attachment.mime
                        uti = attachment.uti
                        transferName = attachment.name ?: "attachment"
                        totalBytes = payloads[index].length()
                        // The payload is already in the canonical local store;
                        // outgoing video/file bubbles must never offer to
                        // download their own just-uploaded file.
                        isDownloaded = true
                        message.target = staged
                    },
                )
            }
            staged.hasAttachments = true
            messageBox.put(staged)
        }
        // The transcript row surfaces the first attachment's entry, so the
        // whole batch (not just the first upload) reports through it.
        val progressGuid = stagedGuids.first()
        val grandTotal = payloads.sumOf { it.length() }
        UploadProgressBoard.update(progressGuid, 0L to grandTotal)

        if (pushState == null) {
            CoreGraphStageHolder.messageRepo(store)
                .failOutgoing(tempGuid, "Not connected to Apple push")
            UploadProgressBoard.clear(progressGuid)
            return
        }

        var failureLookupGuid = tempGuid
        try {
            val inst = runInterruptible(Dispatchers.IO) {
                // Rust reports per-file counters that restart at zero for each
                // upload; fold them into one cumulative (done, total) pair.
                var completedBefore = 0L
                var fileIndex = 0
                var lastDone = 0L
                var lastTotal = 0L
                pushState.sendAttachments(
                    conversation,
                    myHandle,
                    payloads.map { it.absolutePath },
                    caption?.takeIf { it.isNotBlank() },
                    attachments.map { it.mime },
                    attachments.map { it.uti },
                    attachments.map { it.name },
                    null, null, null, null, false,
                    object : uniffi.rust_lib_bluebubbles.UProgressCallback {
                        override fun onProgress(done: ULong, total: ULong) {
                            val doneLong = done.toLong()
                            val totalLong = total.toLong()
                            if (doneLong < lastDone) {
                                // Counters restarted: the previous file is
                                // finished, credit its full size.
                                completedBefore += lastTotal
                                fileIndex = (fileIndex + 1).coerceAtMost(attachments.size - 1)
                            }
                            lastDone = doneLong
                            lastTotal = totalLong
                            UploadProgressBoard.update(
                                progressGuid,
                                (completedBefore + doneLong).coerceAtMost(grandTotal) to grandTotal,
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
            val realAttachmentGuids = normal?.let {
                MessageMapper.mapParts(it.parts, inst.id, isOutgoing = true).second.map { it.guid }
            }.orEmpty()
            if (realAttachmentGuids.size == stagedGuids.size) {
                // Database promotion happens during ingest; move each local
                // payload to its real guid (parts stay in send order) so the
                // confirmed bubble keeps rendering without a redundant
                // network download.
                stagedGuids.zip(realAttachmentGuids).forEach { (local, real) ->
                    disk.promoteLocalDirectory(local, real)
                }
            }
        } catch (t: Throwable) {
            CoreGraphStageHolder.messageRepo(store)
                .failOutgoing(failureLookupGuid, t.message ?: t.javaClass.simpleName)
        } finally {
            UploadProgressBoard.clear(progressGuid)
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

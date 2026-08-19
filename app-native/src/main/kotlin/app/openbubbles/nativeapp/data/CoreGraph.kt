package app.openbubbles.nativeapp.data

import android.content.Context
import android.util.Log
import app.openbubbles.core.attachment.AttachmentDownloader
import app.openbubbles.core.attachment.AttachmentManager
import app.openbubbles.core.attachment.AttachmentMedia
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.backup.BackupManager
import app.openbubbles.core.backup.StoreGate
import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.intake.IncomingProfile
import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.intake.ProfileMessageKind
import app.openbubbles.core.intake.ProfileUpdatePort
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
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
import uniffi.rust_lib_bluebubbles.UProgressCallback
import uniffi.rust_lib_bluebubbles.UPushMessage
import uniffi.rust_lib_bluebubbles.URegisterState
import uniffi.rust_lib_bluebubbles.UReportMessage
import uniffi.rust_lib_bluebubbles.USendAttachmentsRequest
import uniffi.rust_lib_bluebubbles.parseCallPoster
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
/**
 * Live composition root binding the UI contracts to :core (ObjectBox) and,
 * when the push service is up, the Rust send path. Falls back to the fake
 * repositories if the store cannot open (should not happen; empty DB is a
 * valid, boring state).
 */
object CoreGraph {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    internal fun launchBackground(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    @Volatile
    private var restoreRestartRequired = false

    /**
     * True from the moment a restore reaches its point of no return — live
     * services stop and the ObjectBox store closes — until the process
     * exits. Flipped BEFORE any other shutdown state mutates so the root UI
     * (which collects it) swaps nav content for a blocking overlay in the
     * same recomposition pass that observes those shutdown writes; nav
     * entries then never re-run queries against the closed store.
     */
    private val _restoreShutdownStarted = MutableStateFlow(false)
    val restoreShutdownStarted: StateFlow<Boolean> = _restoreShutdownStarted.asStateFlow()

    /** Outcome text the shutdown overlay shows before the process exits. */
    private val _restoreShutdownNotice = MutableStateFlow<String?>(null)
    val restoreShutdownNotice: StateFlow<String?> = _restoreShutdownNotice.asStateFlow()

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
            profileUpdatePort = NativeProfileUpdatePort,
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
        TranscriptBackgroundStore(context.applicationContext) { PushStateHolder.state }.also { store ->
            launchBackground { store.migrateLegacyPosters() }
        }
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

    /** Upsert explicit contact deltas (for example complete CardDAV cards). */
    fun syncContacts(raw: List<app.openbubbles.core.contacts.RawContact>): Boolean =
        CoreContacts.upsert(raw).also { persisted ->
            if (persisted) UiContacts.notifyAvatarsChanged()
        }

    /** Reconcile one complete, successful Android ContactsProvider snapshot. */
    fun syncDeviceContacts(
        snapshot: app.openbubbles.core.contacts.DeviceContactSnapshot,
    ): app.openbubbles.core.contacts.DeviceContactReconcileResult? =
        CoreContacts.syncFromDevice(snapshot).also { UiContacts.notifyAvatarsChanged() }

    /** Apply CardDAV contact tombstones + invalidate cached name lookups. */
    fun removeContacts(nativeContactIds: Collection<String>): Int =
        CoreContacts.remove(nativeContactIds).also { UiContacts.notifyAvatarsChanged() }

    /** Re-match stored contacts after CloudKit creates additional handles. */
    fun relinkContacts(): app.openbubbles.core.contacts.ContactRelinkResult? =
        CoreContacts.relink()?.also { UiContacts.notifyAvatarsChanged() }

    /** Full-fidelity iCloud rows for the device-contacts mirror. */
    fun icloudContacts(): List<app.openbubbles.core.contacts.RawContact> =
        CoreContacts.icloud()

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

    /**
     * Contact relinks rewrite chat-handle relations without changing any row
     * count, which is the one write shape the related-chats cache probe
     * cannot see synchronously; callers that just relinked invalidate here
     * so the very next grouping read is correct.
     */
    fun invalidateRelatedChats() {
        chatRepo?.invalidateRelatedChats()
    }

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

    /**
     * Repair a lost iCloud Keychain: stop push, wipe only the iCloud
     * service state (keychain, CloudKit, passwords, Find My, FaceTime,
     * shared streams — the Apple session, IDS registration, and hardware
     * identity are kept), and let the signed-out gate route the user
     * through sign-in, which refetches delegates and recreates the files.
     */
    suspend fun repairICloudServices(context: android.content.Context): Result<Unit> {
        Log.i("CoreGraph", "iCloud service repair requested")
        PushStateHolder.clear(resetError = true)
        runCatching {
            context.stopService(
                android.content.Intent(context, app.openbubbles.nativeapp.service.NativePushService::class.java))
        }.onFailure { error ->
            Log.e("CoreGraph", "push service stop failed during iCloud repair", error)
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                // Let the service's teardown finish so a final trust sync
                // cannot re-materialize the files being deleted.
                kotlinx.coroutines.delay(1_500)
                uniffi.rust_lib_bluebubbles.repairIcloudServices(context.filesDir.absolutePath)
            }.map { }
        }.onSuccess {
            // The login screen consumes this and auto-runs the sessioned
            // re-auth instead of asking for a password (see RepairFlow).
            RepairFlow.requestSessionRepair()
        }.also { Log.i("CoreGraph", "iCloud service repair finished: $it") }
    }

    /** Attachment send path (staging + Rust upload + echo ingest). */
    val attachmentSender: AttachmentSender by lazy {
        if (store != null) CoreAttachmentSender else FakeAttachmentSender
    }

    val stickerSender: StickerSender by lazy {
        if (store != null) CoreStickerSender else StickerSender { _, _, _, _, sticker, _ ->
            OutgoingStickerSend(sticker.file.absolutePath)
        }
    }

    /** Live typing indicators translated from the ingestor's chat guids. */
    val typing: TypingRepository by lazy {
        val ing = ingestor
        val st = store
        if (ing != null && st != null) CoreTypingRepository(ing, st) else FakeTypingRepository
    }

    fun isChatBlocked(chatId: Long): Boolean =
        store?.let { ChatRepo(it).isBlocked(chatId) } == true

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
    val search: SearchRepository by lazy {
        messageRepo?.let(::CoreSearchRepository) ?: FakeSearchRepository
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
            val box = st.boxFor(Attachment::class.java)
            val pairGuids = listOfNotNull(
                attachment.guid,
                attachment.metadata?.get("livePhotoMotionGuid") as? String,
                attachment.metadata?.get("livePhotoStillGuid") as? String,
            ).distinct()
            pairGuids.forEach { pairGuid ->
                val pair = if (pairGuid == attachment.guid) {
                    attachment
                } else {
                    box.query()
                        .equal(Attachment_.guid, pairGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .build().use { it.findFirst() }
                } ?: return@forEach
                runCatching { manager.download(pair).collect { /* terminal is enough here */ } }
            }
        }
    }

    /**
     * Fire-and-forget auto-download of a chat's incoming media attachments
     * (images, videos, audio) up to the Settings → Messaging ceiling
     * ([MessagingPrefs.autoDownloadMaxBytes]; 0 disables, negative means
     * unlimited). Runs when a conversation opens and after a live push
     * ingest, so photos and voice memos are on disk before their bubbles
     * need them; larger payloads keep waiting for the download chip.
     */
    fun autoDownloadForChat(chatId: Long) {
        val manager = attachmentManager ?: return
        val context = AppContext.current ?: return
        val maxBytes = MessagingPrefs(context).autoDownloadMaxBytes
        if (maxBytes == 0L) return
        scope.launch(Dispatchers.IO) {
            manager.pendingFor(chatId)
                .asSequence()
                .filter { !it.isOutgoing }
                .filter { attachment ->
                    isAutoDownloadEligible(
                        mime = attachment.mimeType,
                        totalBytes = knownAutoDownloadSize(attachment.totalBytes),
                        hasTransferMetadata = attachment.metadata?.containsKey("rustpush") == true ||
                            attachment.metadata?.containsKey("cloud") == true,
                        maxBytes = maxBytes,
                        uti = attachment.uti,
                        name = attachment.transferName,
                    )
                }
                .forEach { attachment ->
                    launch {
                        runCatching {
                            manager.download(attachment).collect { /* terminal is enough here */ }
                        }
                    }
                }
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
     * restart the process after a successful restore ([runRestore] does
     * `Runtime.getRuntime().exit(0)` after surfacing the result).
     */
    fun restoreFrom(stream: java.io.InputStream): Result<BackupManager.BackupInfo> {
        restoreRestartRequired = false
        val manager = backupManager
            ?: return Result.failure(IllegalStateException("backup unavailable — store not open"))
        val ctx = AppContext.current
            ?: return Result.failure(IllegalStateException("no app context"))
        return manager.restore(stream, File(ctx.dataDir, "app_flutter")) {
            // Flip the UI gate FIRST: the calls below write UI-observed state
            // (PushStateHolder.clear) whose recomposition previously queried
            // the store mid-shutdown and crashed ("Store is closed"). With
            // the gate up, that same recomposition disposes the nav entries
            // instead of re-running their queries.
            _restoreShutdownStarted.value = true
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

    /**
     * Runs the full restore + restart flow on CoreGraph's process scope —
     * deliberately NOT the caller's composition scope: once the swap boundary
     * flips [restoreShutdownStarted] the root UI disposes the settings
     * screen, which would cancel a composition-scoped coroutine mid-swap and
     * strand the process with a closed store and no exit.
     *
     * [onStage]/[onError] update the settings UI while it is still composed
     * (staging, validation, pre-swap failures). Post-swap outcomes surface in
     * [restoreShutdownNotice] because the settings screen is gone by then.
     */
    fun runRestore(
        context: Context,
        uri: android.net.Uri,
        onStage: (String?) -> Unit,
        onError: (String) -> Unit,
    ) {
        scope.launch {
            onStage("Restoring…")
            val result = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        restoreFrom(input)
                    } ?: Result.failure(IllegalStateException("cannot open backup file"))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // SAF can throw (provider died, permission revoked) where
                    // it used to return null; surface as a normal failure.
                    Result.failure(e)
                }
            }
            if (result.isSuccess || restoreRequiresRestart()) {
                // CoreGraph's lazy singletons (and the open store) cannot be
                // rebuilt in place, so the process restarts to load the
                // restored data. A failure after the pre-swap shutdown also
                // needs a restart because the live store is already closed.
                _restoreShutdownNotice.value = result.fold(
                    onSuccess = { "Restore complete — restarting…" },
                    onFailure = { it.message ?: "restore failed after shutdown — restarting" },
                )
                onStage(null)
                delay(2_500)
                Runtime.getRuntime().exit(0)
            } else {
                onStage(null)
                onError(result.exceptionOrNull()?.message ?: "restore failed")
            }
        }
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

    private val _registrationState = MutableStateFlow<URegisterState?>(null)
    val registrationStateFlow = _registrationState.asStateFlow()
    val registrationState: URegisterState? get() = _registrationState.value

    fun install(
        state: NativePushState,
        handles: Set<String>,
        registration: URegisterState,
    ) {
        _state.value = state
        _myHandles.value = handles
        updateRegistration(registration)
        AppContext.current?.let { CloudSyncWiring.onStateInstalled(it, state) }
    }

    fun updateRegistration(registration: URegisterState) {
        _registrationState.value = registration
        if (registration is URegisterState.Registered) _lastError.value = null
    }

    fun clearError() {
        _lastError.value = null
    }

    fun reportError(message: String) {
        _lastError.value = message
    }

    fun clear(resetError: Boolean = false) {
        _state.value = null
        _myHandles.value = emptySet()
        if (resetError) _lastError.value = null
        _registrationState.value = null
        CloudSyncWiring.clear()
    }
}

private object NativeProfileUpdatePort : ProfileUpdatePort {
    override suspend fun receive(
        senderAddress: String,
        profileJson: String,
        kind: ProfileMessageKind,
    ): IncomingProfile? {
        if (kind == ProfileMessageKind.SharingUpdate) return null
        val record = PushStateHolder.state?.fetchProfile(profileJson) ?: return null
        val image = record.poster?.let { poster ->
            runCatching { parseCallPoster(poster).lowResImage() }.getOrNull()
        }?.takeIf(ByteArray::isNotEmpty) ?: record.image?.takeIf(ByteArray::isNotEmpty)
        val posterPath = image?.let { bytes ->
            val context = AppContext.current ?: return@let null
            val directory = File(context.filesDir, "shared_profiles").apply { mkdirs() }
            val destination = File(directory, "${senderAddress.hashCode().toUInt()}.img")
            val temporary = File(directory, "${destination.name}.tmp")
            temporary.writeBytes(bytes)
            if (!temporary.renameTo(destination)) {
                destination.writeBytes(bytes)
                temporary.delete()
            }
            destination.absolutePath
        }
        return IncomingProfile(
            displayName = record.name.ifBlank {
                listOf(record.first, record.last).filter(String::isNotBlank).joinToString(" ")
            }.ifBlank { null },
            posterPath = posterPath,
        )
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
    notifsSilenced = item.notifsSilenced,
    archived = item.archived,
    avatarAddress = item.avatarAddress,
    avatarPath = item.avatarPath,
    isGroup = item.isGroup,
    customBackgroundPath = item.customBackgroundPath,
    transcriptBackgroundPath = item.transcriptBackgroundPath,
    transcriptBackgroundVersion = item.transcriptBackgroundVersion,
    memberChatIds = item.memberChatIds,
    preferredChatId = item.preferredChatId,
    senderOverride = item.senderOverride,
    receivedOnHandle = item.receivedOnHandle,
    dateDeleted = item.dateDeleted?.time,
    lockChatName = item.lockChatName,
    lockChatIcon = item.lockChatIcon,
    autoSendReadReceipts = item.autoSendReadReceipts,
    autoSendTypingIndicators = item.autoSendTypingIndicators,
    blocked = item.blocked,
    guid = item.guid,
)

private val TAPBACK_EMOJI = mapOf(
    "love" to "❤️", "like" to "👍", "dislike" to "👎", "laugh" to "😂",
    "emphasize" to "‼️", "question" to "❓",
)

/** Transcript + contact search backed by the local store; links parse lazily in the mapper. */
private class CoreSearchRepository(
    private val repo: MessageRepo,
) : SearchRepository {
    override suspend fun searchMessages(query: String, limit: Int): List<MessageItem> =
        withContext(Dispatchers.IO) {
            repo.searchText(query, limit).map(::coreMessageToUi)
        }

    override suspend fun searchLinks(query: String, limit: Int): List<MessageItem> =
        withContext(Dispatchers.IO) {
            repo.searchLinks(query, limit).map(::coreMessageToUi)
        }

    override suspend fun contacts(): List<app.openbubbles.core.contacts.RawContact> =
        withContext(Dispatchers.IO) { CoreGraph.preferredContacts() }
}

private fun coreMessageToUi(item: app.openbubbles.core.model.MessageItem) = MessageItem(
    id = item.id,
    text = when (item.kind) {
        app.openbubbles.core.model.MessageKind.GROUP_EVENT -> item.groupEventText ?: item.text
        else -> item.text
    },
    subject = item.subject,
    isFromMe = item.isFromMe,
    date = item.date?.time ?: 0L,
    dateDelivered = item.dateDelivered?.time,
    dateRead = item.dateRead?.time,
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
    interactivePayload = item.interactivePayload,
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
    isBookmarked = item.isBookmarked,
    hasBeenForwarded = item.hasBeenForwarded,
    dateDeleted = item.dateDeleted?.time,
    errorCode = item.errorCode,
    errorMessage = item.errorMessage,
    partCount = item.partCount,
)

/** True when the mime/uti/name triple clearly describes an image. */
internal fun isImageAttachment(mime: String?, uti: String?, name: String? = null): Boolean =
    AttachmentMedia.isImage(mime, uti, name)

internal fun attachmentToMeta(attachment: Attachment) = AttachmentMeta(
    guid = attachment.guid,
    mime = attachment.mimeType,
    name = attachment.transferName,
    sizeBytes = attachment.totalBytes,
    isImage = isImageAttachment(attachment.mimeType, attachment.uti, attachment.transferName),
    downloaded = attachment.isDownloaded,
    partIndex = (attachment.metadata?.get("messagePart") as? Number)?.toLong()
        ?: attachment.guid?.substringAfterLast('_')?.toLongOrNull()
        ?: 0L,
    uti = attachment.uti,
    livePhotoMotionGuid = attachment.metadata?.get("livePhotoMotionGuid") as? String,
    isLivePhotoMotion = attachment.metadata?.get("livePhotoMotion") == true,
)

internal fun visibleAttachmentMetas(attachments: List<Attachment>): List<AttachmentMeta> {
    val metas = attachments.map(::attachmentToMeta)
    val hiddenMotionGuids = metas.filter(AttachmentMeta::isLivePhotoMotion).mapTo(mutableSetOf(), AttachmentMeta::guid)
    val inferredMotionByStill = metas.asSequence()
        .filter { it.isImage && it.livePhotoMotionGuid == null && isHeicName(it.name) }
        .mapNotNull { still ->
            val stem = livePhotoStem(still.name) ?: return@mapNotNull null
            val motion = metas.firstOrNull { candidate ->
                candidate.guid != still.guid && candidate.isVideo && livePhotoStem(candidate.name) == stem
            } ?: return@mapNotNull null
            hiddenMotionGuids += motion.guid
            still.guid to motion.guid
        }
        .toMap()
    return metas.mapNotNull { meta ->
        if (meta.guid in hiddenMotionGuids) null
        else inferredMotionByStill[meta.guid]?.let { meta.copy(livePhotoMotionGuid = it) } ?: meta
    }
}

private fun isHeicName(name: String?): Boolean {
    val lower = name.orEmpty().lowercase()
    return lower.endsWith(".heic") || lower.endsWith(".heif")
}

private fun livePhotoStem(name: String?): String? =
    name?.substringBeforeLast('.')?.lowercase()?.takeIf { it.isNotBlank() }

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
        val replyGuids = entities.asSequence()
            .mapNotNull { it.threadOriginatorGuid }
            .distinct()
            .toList()
        val replyTargets: Map<String, Message> = if (replyGuids.isEmpty()) {
            emptyMap()
        } else {
            messageBox.query(
                Message_.guid.oneOf(replyGuids.toTypedArray(), QueryBuilder.StringOrder.CASE_SENSITIVE),
            ).build().use { it.find() }.associateBy { it.guid }
        }
        items.map { item ->
            val entity = byId[item.id] ?: return@map item
            val (edited, unsent) = editedFlags(entity)
            val attachments = runCatching {
                visibleAttachmentMetas(entity.dbAttachments)
            }.getOrDefault(emptyList())
            val firstAttachment = attachments.firstOrNull()
            item.copy(
                attachmentMeta = firstAttachment,
                attachmentMetas = attachments,
                edited = edited,
                unsent = unsent,
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

    fun upsert(raw: List<app.openbubbles.core.contacts.RawContact>): Boolean {
        val contactSync = sync ?: return false
        contactSync.upsertContacts(raw)
        invalidateIndexes()
        return true
    }

    /** Reconcile device contacts after a complete successful provider read. */
    fun syncFromDevice(
        snapshot: app.openbubbles.core.contacts.DeviceContactSnapshot,
    ): app.openbubbles.core.contacts.DeviceContactReconcileResult? {
        val result = sync?.reconcileDeviceSnapshot(snapshot) ?: return null
        invalidateIndexes()
        return result
    }

    private fun invalidateIndexes() {
        handleIndex = null // force rebuild so fresh linkages resolve
        displayInfoIndex = null
        CoreGraph.invalidateRelatedChats()
    }

    fun remove(nativeContactIds: Collection<String>): Int {
        val removed = sync?.removeContacts(nativeContactIds) ?: 0
        if (removed > 0) {
            handleIndex = null
            displayInfoIndex = null
            CoreGraph.invalidateRelatedChats()
        }
        return removed
    }

    fun relink(): app.openbubbles.core.contacts.ContactRelinkResult? {
        val result = sync?.relinkContacts() ?: return null
        // History may have added handles even when every existing relation
        // was already correct, so always rebuild the address lookup too.
        handleIndex = null
        displayInfoIndex = null
        CoreGraph.invalidateRelatedChats()
        return result
    }

    fun preferredContacts(includeNativeContacts: Boolean): List<app.openbubbles.core.contacts.RawContact> =
        sync?.preferredContacts(includeNativeContacts).orEmpty()

    fun icloud(): List<app.openbubbles.core.contacts.RawContact> =
        sync?.icloudContacts().orEmpty()

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

    override fun setSenderOverride(id: Long, handle: String?) =
        memberIds(id).forEach { repo.setSenderOverride(it, handle) }

    override fun setLockChatName(id: Long, locked: Boolean) =
        memberIds(id).forEach { repo.setLockChatName(it, locked) }

    override fun setLockChatIcon(id: Long, locked: Boolean) =
        memberIds(id).forEach { repo.setLockChatIcon(it, locked) }

    override fun setAutoSendReadReceipts(id: Long, enabled: Boolean) {
        memberIds(id).forEach { memberId ->
            repo.setAutoSendReadReceipts(memberId, enabled)
            AppContext.current?.let { MessagingPrefs(it).setChatReadReceiptOverride(memberId, enabled) }
        }
    }

    override fun setAutoSendTypingIndicators(id: Long, enabled: Boolean) {
        memberIds(id).forEach { memberId ->
            repo.setAutoSendTypingIndicators(memberId, enabled)
            AppContext.current?.let { MessagingPrefs(it).setChatTypingOverride(memberId, enabled) }
        }
    }

    override fun setCustomAvatar(id: Long, file: File?) {
        memberIds(id).forEach { memberId ->
            val context = AppContext.current ?: return@forEach
            val directory = File(context.filesDir, "chat_avatars").apply { mkdirs() }
            val destination = file?.let { source ->
                val extension = source.extension.takeIf { it.length in 2..5 } ?: "jpg"
                File(directory, "chat-$memberId-${UUID.randomUUID()}.$extension").also { target ->
                    source.copyTo(target, overwrite = true)
                }
            }
            repo.setCustomAvatarPath(memberId, destination?.absolutePath)
            if (destination != null) repo.setLockChatIcon(memberId, true)
        }
        UiContacts.notifyAvatarsChanged()
    }

    override fun setBlocked(id: Long, blocked: Boolean, archive: Boolean) =
        memberIds(id).forEach { repo.setBlocked(it, blocked, archive) }

    override fun clearTranscript(id: Long) {
        CoreGraph.store?.let { MessageRepo(it, repo).clearTranscript(id) }
    }

    override fun recentlyDeleted(): List<ChatListItem> {
        // Read synchronously in composition (settings row count). During a
        // restore's shutdown window the store is closed; an empty list keeps
        // any stray recomposition that slips past the shutdown overlay from
        // crashing the process. runCatching also covers the microscopic
        // check-then-act race with store.close() itself.
        val st = CoreGraph.store ?: return emptyList()
        if (st.isClosed) return emptyList()
        return runCatching { repo.recentlyDeleted().map(::coreChatToUi) }
            .getOrElse { emptyList() }
    }

    override fun recentlyDeletedCount(): Int {
        val st = CoreGraph.store ?: return 0
        if (st.isClosed) return 0
        return runCatching { repo.recentlyDeletedCount().toInt() }.getOrDefault(0)
    }

    override fun restoreDeleted(id: Long) = repo.restoreDeleted(id)

    override fun permanentlyDelete(id: Long) = repo.permanentlyDelete(id)

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
    private val warmLoader: suspend (Long, Int) -> List<MessageItem> = { chatId, limit ->
        val page = repo.messages(chatId, limit)
        enrichWithEntityDetails(page.map(::coreMessageToUi), store).asReversed()
    },
) : MessageListRepository {
    private class PagingWindow(initialLimit: Int) {
        val size = MutableStateFlow(initialLimit)

        @Volatile
        var newestId: Long? = null
    }

    private class Snapshot(
        val items: List<MessageItem>,
        val newestId: Long?,
        val requestedLimit: Int,
        val changeGeneration: Long,
        @Volatile var stale: Boolean = false,
    )

    /** Independent bounded window per open conversation. */
    private val windows = ConcurrentHashMap<Long, PagingWindow>()
    private val snapshots = ConcurrentHashMap<Long, Snapshot>()
    private val retained = ConcurrentHashMap.newKeySet<Long>()
    private val locks = ConcurrentHashMap<Long, Mutex>()
    private val warmLimiter = Semaphore(3)
    private val cacheScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val invalidationReady = CompletableDeferred<Unit>()
    private val changeGeneration = AtomicLong(0L)
    private val prefetchGeneration = AtomicLong(0L)

    @Volatile
    private var desired: Set<Long> = emptySet()

    init {
        cacheScope.launch {
            var initialized = false
            repo.observeTranscriptChanges().collect {
                if (!initialized) {
                    initialized = true
                    invalidationReady.complete(Unit)
                } else {
                    changeGeneration.incrementAndGet()
                    snapshots.values.forEach { it.stale = true }
                }
            }
        }
    }

    private fun window(chatId: Long, initialLimit: Int): PagingWindow =
        windows.computeIfAbsent(chatId) { PagingWindow(initialLimit) }

    private fun lockFor(chatId: Long): Mutex = locks.getOrPut(chatId) { Mutex() }

    override fun cached(chatId: Long): List<MessageItem> =
        snapshots[chatId]?.takeUnless { it.stale }?.items.orEmpty()

    override suspend fun prefetch(
        chatIds: Collection<Long>,
        limit: Int,
    ) {
        val wanted = chatIds.toSet()
        val generation = prefetchGeneration.incrementAndGet()
        desired = wanted
        val keep = wanted + retained
        snapshots.keys.filter { it !in keep }.forEach { snapshots.remove(it) }
        windows.keys.filter { it !in keep }.forEach { windows.remove(it) }
        if (wanted.isEmpty()) return
        coroutineScope {
            wanted.map { chatId ->
                async { warmLimiter.withPermit { warm(chatId, limit, generation) } }
            }.awaitAll()
        }
    }

    override suspend fun prime(chatId: Long, limit: Int) {
        retained.add(chatId)
        warm(chatId, limit, null)
    }

    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> {
        retained.add(chatId)
        val paging = window(chatId, limit)
        if (paging.size.value < limit) paging.size.value = limit
        return paging.size.flatMapLatest { size ->
            val requested = size.coerceAtLeast(limit)
            val pages = repo.observeMessages(chatId, requested)
                .map { page -> pageToUi(chatId, paging, page, requested) }
                .onStart {
                    snapshots[chatId]
                        ?.takeUnless { it.stale }
                        ?.let { emit(it.items) }
                }
                .flowOn(Dispatchers.IO)
            // Final UI stage: identical content is an identical frame.
            combine(pages, UploadProgressBoard.progress, ::applyUploadProgress)
                .distinctUntilChanged()
        }
    }

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> {
        val cursor = before ?: return emptyList()
        val older = repo.messagesBefore(chatId, beforeId = cursor, limit = count)
        val olderUi = enrichWithEntityDetails(older.map(::coreMessageToUi), store).asReversed()
        if (older.isNotEmpty()) {
            val paging = window(chatId, count)
            paging.size.value += older.size
            snapshots[chatId]?.takeUnless { it.stale }?.let { current ->
                rememberSnapshot(
                    chatId = chatId,
                    items = (olderUi + current.items).distinctBy { it.id },
                    requestedLimit = paging.size.value,
                    generation = current.changeGeneration,
                )
            }
        }
        return olderUi
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
            return
        }
        val previous = snapshots[chatId]
        rememberSnapshot(
            chatId = chatId,
            items = keep,
            requestedLimit = previous?.requestedLimit ?: TRANSCRIPT_OPEN_LIMIT,
            generation = previous?.changeGeneration ?: changeGeneration.get(),
        )
        windows[chatId]?.let { paging ->
            paging.size.value = keep.size.coerceAtLeast(TRANSCRIPT_PREFETCH_LIMIT)
            paging.newestId = keep.lastOrNull()?.id
        }
    }

    override fun bookmarked(chatId: Long): List<MessageItem> =
        repo.bookmarked(chatId).map(::coreMessageToUi)

    override fun recentlyDeleted(chatId: Long?): List<MessageItem> =
        repo.recentlyDeleted(chatId).map(::coreMessageToUi)

    override fun setBookmarked(messageIds: Collection<Long>, bookmarked: Boolean) =
        repo.setBookmarked(messageIds, bookmarked)

    override fun markForwarded(messageIds: Collection<Long>) =
        repo.markForwarded(messageIds)

    override fun deleteLocal(messageIds: Collection<Long>) =
        repo.deleteLocal(messageIds)

    override fun cancelOutgoing(messageId: Long): Boolean =
        repo.cancelOutgoing(messageId)

    override fun restoreDeleted(messageIds: Collection<Long>) =
        repo.restoreDeleted(messageIds)

    private fun pageToUi(
        chatId: Long,
        paging: PagingWindow,
        page: List<app.openbubbles.core.model.MessageItem>,
        requestedLimit: Int,
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
        rememberSnapshot(chatId, ui, requestedLimit, changeGeneration.get())
        return ui
    }

    private suspend fun warm(chatId: Long, limit: Int, prefetch: Long?) {
        invalidationReady.await()
        lockFor(chatId).withLock {
            val existing = snapshots[chatId]
            if (existing != null &&
                !existing.stale &&
                existing.requestedLimit >= limit
            ) {
                return
            }

            var generation: Long
            var ui: List<MessageItem>
            do {
                generation = changeGeneration.get()
                ui = withContext(Dispatchers.IO) { warmLoader(chatId, limit) }
            } while (generation != changeGeneration.get())

            if (prefetch != null && chatId !in retained &&
                (prefetch != prefetchGeneration.get() || chatId !in desired)
            ) {
                return
            }

            val snapshot = rememberSnapshot(chatId, ui, limit, generation)
            window(chatId, limit).apply {
                if (size.value < snapshot.items.size) size.value = snapshot.items.size
                newestId = snapshot.newestId
            }
        }
    }

    private fun rememberSnapshot(
        chatId: Long,
        items: List<MessageItem>,
        requestedLimit: Int,
        generation: Long,
    ): Snapshot {
        val replacement = Snapshot(
            items = items,
            newestId = items.lastOrNull()?.id,
            requestedLimit = requestedLimit,
            changeGeneration = generation,
            stale = false,
        )
        var selected = replacement
        snapshots.compute(chatId) { _, current ->
            if (current != null && !current.stale &&
                current.changeGeneration >= generation &&
                current.requestedLimit > requestedLimit
            ) {
                selected = current
                current
            } else {
                replacement
            }
        }
        return selected
    }

    internal fun close() {
        cacheScope.cancel()
    }
}

internal fun applyUploadProgress(
    items: List<MessageItem>,
    progress: Map<String, Pair<Long, Long>>,
): List<MessageItem> {
    // MMCS fires a progress tick per chunk; most ticks change nothing in a
    // visible list. Returning the same instance lets the downstream
    // distinctUntilChanged drop the frame instead of recomposing it.
    var changed = false
    val mapped = items.map { item ->
        val current = item.attachmentMeta?.guid?.let(progress::get)
        if (item.uploadProgress == current) {
            item
        } else {
            changed = true
            item.copy(uploadProgress = current)
        }
    }
    return if (changed) mapped else items
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
                    isImage = AttachmentMedia.isImage(
                        mime,
                        attachment.uti,
                        attachment.transferName,
                    ),
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
    private val sendLocks = ConcurrentHashMap<Long, Mutex>()

    override suspend fun send(chatId: Long, text: String): OutgoingTextSend =
        sendWithEffect(chatId, text, null)

    override suspend fun send(
        chatId: Long,
        text: String,
        subject: String?,
        mentions: List<OutgoingMention>,
    ): OutgoingTextSend = sendInternal(
        chatId = chatId,
        text = text,
        effectId = null,
        replyGuid = null,
        subject = subject,
        mentions = mentions,
    )

    override suspend fun sendWithEffect(
        chatId: Long,
        text: String,
        effectId: String?,
    ): OutgoingTextSend = sendInternal(chatId, text, effectId, null)

    override suspend fun sendWithEffect(
        chatId: Long,
        text: String,
        effectId: String?,
        subject: String?,
        mentions: List<OutgoingMention>,
    ): OutgoingTextSend = sendInternal(
        chatId = chatId,
        text = text,
        effectId = effectId,
        replyGuid = null,
        subject = subject,
        mentions = mentions,
    )

    override suspend fun sendReply(
        chatId: Long,
        text: String,
        replyGuid: String,
        replyPartLocator: String,
    ): OutgoingTextSend = sendInternal(chatId, text, null, replyGuid, replyPartLocator)

    override suspend fun sendReply(
        chatId: Long,
        text: String,
        replyGuid: String,
        replyPartLocator: String,
        subject: String?,
        mentions: List<OutgoingMention>,
    ): OutgoingTextSend = sendInternal(
        chatId = chatId,
        text = text,
        effectId = null,
        replyGuid = replyGuid,
        replyPartLocator = replyPartLocator,
        subject = subject,
        mentions = mentions,
    )

    private suspend fun sendInternal(
        chatId: Long,
        text: String,
        effectId: String?,
        replyGuid: String?,
        replyPartLocator: String? = null,
        subject: String? = null,
        mentions: List<OutgoingMention> = emptyList(),
    ): OutgoingTextSend {
        val graph = CoreGraph
        val store = graph.store ?: error("store unavailable")
        val ing = graph.ingestor ?: error("ingestor unavailable")
        val pushState = PushStateHolder.state
        val (stage, myHandle) = withContext(Dispatchers.IO) {
            val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
            val handle = sendingHandle(chat)
                ?: (if (pushState == null) chat.usingHandle else null)
                ?: error("no registered sending handle")
            stageOutgoingText(
                store = store,
                chatGuid = chat.guid,
                sender = handle,
                text = text,
                effectId = effectId,
                replyGuid = replyGuid,
                replyPartLocator = replyPartLocator,
                subject = subject,
                attributedBody = outgoingAttributedBody(text, mentions),
            ) to handle
        }
        val tempGuid = stage.tempGuid
        val accepted = OutgoingTextSend(stage.message.id)

        if (pushState == null) {
            withContext(Dispatchers.IO) {
                failOutgoingText(store, tempGuid, "Not connected to Apple push")
            }
            return accepted
        }

        graph.launchBackground {
            sendLocks.computeIfAbsent(chatId) { Mutex() }.withLock {
                var failureLookupGuid = tempGuid
                try {
                    val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
                    val conversation = sendConversation(store, chat, myHandle)
                    maybeShareProfile(pushState, chat, conversation, myHandle)
                    val inst = if (mentions.isEmpty()) {
                        pushState.sendText(
                            conversation,
                            myHandle,
                            text,
                            replyGuid,
                            replyPartLocator,
                            effectId,
                            subject,
                        )
                    } else {
                        pushState.sendParts(
                            conversation,
                            myHandle,
                            outgoingMessageParts(text, mentions),
                            replyGuid,
                            replyPartLocator,
                            effectId,
                            subject,
                        )
                    }
                    failureLookupGuid = inst.id
                    // Promote the staged row to the Rust staging guid so the echo and
                    // SendConfirm receipts find it (same swap Dart performs).
                    promoteOutgoingText(store, tempGuid, inst.id)
                    ing.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
                } catch (failure: Throwable) {
                    failOutgoingText(
                        store,
                        failureLookupGuid,
                        failure.message ?: failure.javaClass.simpleName,
                    )
                }
            }
        }
        return accepted
    }
}

private suspend fun maybeShareProfile(
    state: NativePushState,
    chat: Chat,
    conversation: UConversation,
    sender: String,
) {
    if (chat.isRpSms == true || chat.handles.size != 1) return
    val context = AppContext.current ?: return
    val prefs = ProfilePrefs(context)
    if (!prefs.nameAndPhotoSharing || !prefs.shareAutomatically) return
    val address = MessageMapper.normalizeAddress(chat.handles.single().address)
    if (prefs.wasSharedWith(address)) return
    val json = prefs.shareProfileJson ?: return
    state.sendProfile(conversation, sender, json)
    prefs.markSharedWith(address)
}

internal fun outgoingMessageParts(
    text: String,
    mentions: List<OutgoingMention>,
): List<UIndexedPart> {
    val valid = mentions
        .filter { it.start >= 0 && it.end <= text.length && it.start < it.end }
        .sortedBy { it.start }
        .fold(mutableListOf<OutgoingMention>()) { accepted, mention ->
            if (accepted.lastOrNull()?.end?.let { it > mention.start } != true) accepted += mention
            accepted
        }
    if (valid.isEmpty()) return listOf(UIndexedPart(UPart.Text(text, ""), 0uL, null))
    val parts = mutableListOf<UIndexedPart>()
    var cursor = 0
    valid.forEach { mention ->
        if (mention.start > cursor) {
            parts += UIndexedPart(UPart.Text(text.substring(cursor, mention.start), ""), 0uL, null)
        }
        parts += UIndexedPart(
            UPart.Mention(mention.handle, mention.displayText.removePrefix("@")),
            0uL,
            null,
        )
        cursor = mention.end
    }
    if (cursor < text.length) parts += UIndexedPart(UPart.Text(text.substring(cursor), ""), 0uL, null)
    return parts
}

internal fun outgoingAttributedBody(text: String, mentions: List<OutgoingMention>): String? =
    outgoingMessageParts(text, mentions)
        .takeIf { mentions.isNotEmpty() }
        ?.let(app.openbubbles.core.model.MessageMapper::encodeReplyPartLocators)


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
        // Sibling phone/email threads share local unread state, but Apple
        // only needs one receipt. Sending one per protocol chat repeats the
        // same IDS lookup and is how a history update turns into a rate limit.
        appleReadReceiptChatIds(
            requestedChatId = chatId,
            explicitMessageChatId = explicitMessage?.chat?.targetId,
        ).forEach { receiptChatId ->
            sendReadReceipt(store, receiptChatId, messageGuid)
        }
    }

    private suspend fun sendReadReceipt(store: BoxStore, chatId: Long, messageGuid: String?) {
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: return
        if (!shouldSendAppleReadReceipt(chat)) return
        // Local unread is already cleared. Do not start another IDS lookup
        // while CloudKit is rewriting transcripts — opening many chats or
        // tapping notification mark-as-read during backfill is the same
        // class of storm as the open-transcript observer.
        if (!shouldSendAppleReadReceiptNow(CloudSyncWiring.syncing.value)) return
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
            state.sendRead(conversation, sender, receiptGuid)
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

internal fun shouldSendAppleReadReceiptNow(historySyncActive: Boolean): Boolean = !historySyncActive

/**
 * Local unread is cleared on every related protocol chat. The Apple
 * receipt goes to one chat: the message's own thread when known, otherwise
 * the conversation the user opened.
 */
internal fun appleReadReceiptChatIds(
    requestedChatId: Long,
    explicitMessageChatId: Long?,
): List<Long> = listOf(explicitMessageChatId ?: requestedChatId)

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
        val inst = state.sendReaction(
            conversation,
            sender,
            messageGuid,
            messagePart.toULong(),
            reactionIndex.toULong(),
            emoji,
            messageText,
            enable,
        )
        ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun edit(chatId: Long, messageGuid: String, newText: String) {
        require(newText.isNotBlank()) { "message cannot be empty" }
        val (state, conversation, sender, ingestor) = actionContext(chatId)
        val inst = state.editMessage(
            conversation,
            sender,
            messageGuid,
            0uL,
            listOf(UIndexedPart(UPart.Text(newText, ""), null, null)),
        )
        ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun unsend(chatId: Long, messageGuid: String) {
        val (state, conversation, sender, ingestor) = actionContext(chatId)
        val inst = state.unsendMessage(conversation, sender, messageGuid, 0uL)
        ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun setBookmarked(messageIds: Collection<Long>, bookmarked: Boolean) {
        withContext(Dispatchers.IO) {
            CoreGraph.store?.let { MessageRepo(it).setBookmarked(messageIds, bookmarked) }
        }
    }

    override suspend fun deleteLocal(messageIds: Collection<Long>) {
        withContext(Dispatchers.IO) {
            CoreGraph.store?.let { MessageRepo(it).deleteLocal(messageIds) }
        }
    }

    override suspend fun cancelOutgoing(messageId: Long): Boolean =
        withContext(Dispatchers.IO) {
            CoreGraph.store?.let { MessageRepo(it).cancelOutgoing(messageId) } == true
        }

    override suspend fun markForwarded(messageIds: Collection<Long>) {
        withContext(Dispatchers.IO) {
            CoreGraph.store?.let { MessageRepo(it).markForwarded(messageIds) }
        }
    }

    override suspend fun blockSender(chatId: Long, archive: Boolean) {
        withContext(Dispatchers.IO) {
            CoreGraph.store?.let { ChatRepo(it).setBlocked(chatId, blocked = true, archive = archive) }
        }
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
            participants = participantAddresses,
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
        val inst = context.state.renameChat(context.conversation, context.sender, name.trim())
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
        val inst = context.state.setGroupIcon(
            context.conversation,
            context.sender,
            file.absolutePath,
            version,
            null,
        )
        context.chat.customAvatarPath = file.absolutePath
        context.chat.photoAttachmentGuid = inst.id
        context.chat.groupVersion = version.toLong()
        CoreGraph.store?.boxFor(Chat::class.java)?.put(context.chat)
        context.ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun removeGroupIcon(chatId: Long) {
        val context = context(chatId)
        val version = nextGroupVersion(context.chat)
        val inst = context.state.removeGroupIcon(context.conversation, context.sender, version)
        context.chat.customAvatarPath?.let { runCatching { File(it).delete() } }
        context.chat.customAvatarPath = null
        context.chat.photoAttachmentGuid = null
        context.chat.groupVersion = version.toLong()
        CoreGraph.store?.boxFor(Chat::class.java)?.put(context.chat)
        context.ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun leave(chatId: Long) {
        val context = context(chatId)
        val inst = context.state.leaveChat(
            context.conversation,
            context.sender,
            nextGroupVersion(context.chat),
        )
        context.ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
    }

    override suspend fun reportJunk(chatId: Long) {
        val store = CoreGraph.store ?: error("store unavailable")
        val state = PushStateHolder.state ?: error("not connected to Apple push")
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
        check(chat.isRpSms != true && chat.handles.size == 1) {
            "Report Junk is only available for direct iMessage chats"
        }
        val ownHandle = sendingHandle(chat) ?: error("no registered sending handle")
        val reports = store.boxFor(Message::class.java).query()
            .equal(Message_.chatId, chatId)
            .isNull(Message_.dateDeleted)
            .orderDesc(Message_.dateCreated)
            .build().use { it.find(0, 5) }
            .filterNot { it.isFromMe }
            .mapNotNull { message ->
                val sender = message.handleRelation.target?.address ?: return@mapNotNull null
                UReportMessage(
                    guid = message.guid,
                    sender = MessageMapper.toRustHandle(sender),
                    conversationSize = chat.handles.size.toUInt(),
                    parts = listOf(UIndexedPart(UPart.Text(message.text.orEmpty(), ""), null, null)),
                    timeOfMessage = (message.dateCreated?.time ?: 0L) / 1_000.0,
                )
            }
        runInterruptible(Dispatchers.IO) { state.reportSpam(ownHandle, reports) }
        store.runInTx {
            chat.handles.forEach { handle ->
                handle.blocked = true
                store.boxFor(Handle::class.java).put(handle)
            }
            chat.isArchived = true
            chat.hasUnreadMessage = false
            store.boxFor(Chat::class.java).put(chat)
        }
    }

    private suspend fun changeParticipants(context: GroupActionContext, participants: List<String>) {
        val version = nextGroupVersion(context.chat)
        val inst = context.state.changeParticipants(
            context.conversation,
            context.sender,
            participants,
            version,
        )
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
 * Keep attachment-send progress on the coarse Kotlin "Uploading" state.
 *
 * UniFFI progress is a synchronous foreign callback from Rust's transfer
 * executor. It is not needed for correctness, and retaining it would keep the
 * field-crash path alive even though the send itself remains suspending.
 */
internal fun attachmentSendProgressCallback(): UProgressCallback? = null

/**
 * Attachment send path mirroring [CoreSender]'s staging/promotion/echo
 * semantics: stage optimistically under a temp guid with placeholder
 * attachment rows (payloads moved into the canonical store layout so the
 * bubbles preview immediately), upload everything through the Rust
 * sendAttachments binding as the parts of one message, surface a coarse
 * indeterminate upload state, promote the row to the Rust staging guid, then
 * ingest the echo.
 */
internal object CoreAttachmentSender : AttachmentSender {
    override suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
    ): OutgoingAttachmentSend {
        return send(chatId, attachments, caption, null)
    }

    override suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
        subject: String?,
    ): OutgoingAttachmentSend {
        require(attachments.isNotEmpty()) { "attachment send requires at least one attachment" }
        val graph = CoreGraph
        val store = graph.store ?: error("store unavailable")
        val ing = graph.ingestor ?: error("ingestor unavailable")
        val pushState = PushStateHolder.state
        val prepared = withContext(Dispatchers.IO) {
            val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
            val myHandle = sendingHandle(chat)
                ?: (if (pushState == null) chat.usingHandle else null)
                ?: error("no registered sending handle")
            val tempGuid = MessageIngestor.tempGuid()
            val root = File(
                AppContext.current?.dataDir ?: error("no files dir"),
                "app_flutter",
            )
            val disk = AttachmentStore(store, root)
            val stagedGuids = ArrayList<String>(attachments.size)
            val payloads = ArrayList<File>(attachments.size)
            try {
                attachments.forEachIndexed { index, attachment ->
                    val attachmentGuid = "${tempGuid}_att$index"
                    val displayName = attachment.name ?: "attachment"
                    val payload = File(
                        disk.directoryFor(attachmentGuid),
                        disk.sanitizeFileName(displayName),
                    )
                    moveOutgoingAttachment(attachment.file, payload)
                    stagedGuids += attachmentGuid
                    payloads += payload
                }
                val message = CoreGraphStageHolder.messageRepo(store)
                    .stageOutgoingMessageWithAttachments(
                        chatGuid = chat.guid,
                        sender = myHandle,
                        text = caption.orEmpty(),
                        stagingGuid = tempGuid,
                        attachments = attachments.mapIndexed { index, attachment ->
                            MessageRepo.OutgoingAttachmentStage(
                                guid = stagedGuids[index],
                                mimeType = attachment.mime,
                                uti = attachment.uti,
                                transferName = attachment.name ?: "attachment",
                                totalBytes = payloads[index].length(),
                            )
                        },
                        subject = subject,
                    )
                PreparedAttachmentSend(
                    messageId = message.id,
                    tempGuid = tempGuid,
                    myHandle = myHandle,
                    conversation = sendConversation(store, chat, myHandle),
                    disk = disk,
                    stagedGuids = stagedGuids,
                    payloads = payloads,
                )
            } catch (failure: Throwable) {
                stagedGuids.forEach { disk.directoryFor(it).deleteRecursively() }
                throw failure
            }
        }
        // The transcript row surfaces the first attachment's entry, so the
        // whole batch (not just the first upload) reports through it.
        val progressGuid = prepared.stagedGuids.first()
        UploadProgressBoard.update(progressGuid, 0L to 0L)

        if (pushState == null) {
            CoreGraphStageHolder.messageRepo(store)
                .failOutgoing(prepared.tempGuid, "Not connected to Apple push")
            UploadProgressBoard.clear(progressGuid)
            return OutgoingAttachmentSend(prepared.messageId)
        }

        graph.launchBackground {
            var failureLookupGuid = prepared.tempGuid
            try {
                val inst = pushState.sendAttachments(
                    USendAttachmentsRequest(
                        conversation = prepared.conversation,
                        sender = prepared.myHandle,
                        filePaths = prepared.payloads.map { it.absolutePath },
                        text = caption?.takeIf { it.isNotBlank() },
                        mimes = attachments.map { it.mime },
                        utis = attachments.map { it.uti },
                        names = attachments.map { it.name },
                        replyGuid = null,
                        replyPart = null,
                        effect = null,
                        subject = subject,
                        voice = false,
                    ),
                    attachmentSendProgressCallback(),
                )
                failureLookupGuid = inst.id
                val normal = inst.message as? uniffi.rust_lib_bluebubbles.UMessage.Normal
                val realAttachmentGuids = normal?.let {
                    MessageMapper.mapParts(it.parts, inst.id, isOutgoing = true).second.map { item -> item.guid }
                }.orEmpty()
                if (realAttachmentGuids.size == prepared.stagedGuids.size) {
                    // Move payloads before ingest so the bubble does not lose
                    // its local preview when the guid swaps to the Rust id.
                    prepared.stagedGuids.zip(realAttachmentGuids).forEach { (local, real) ->
                        prepared.disk.promoteLocalDirectory(local, real)
                    }
                }
                // Promote to the Rust staging guid so the echo and SendConfirm
                // receipts find the row (same swap Dart performs).
                val messageBox = store.boxFor(Message::class.java)
                store.runInTx {
                    messageBox.query()
                        .equal(
                            Message_.guid,
                            prepared.tempGuid,
                            QueryBuilder.StringOrder.CASE_SENSITIVE,
                        )
                        .build().use { it.findFirst() }
                        ?.apply {
                            guid = inst.id
                            stagingGuid = inst.id
                            messageBox.put(this)
                        }
                }
                ing.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
            } catch (failure: Throwable) {
                val marked = CoreGraphStageHolder.messageRepo(store)
                    .failOutgoing(failureLookupGuid, failure.message ?: failure.javaClass.simpleName)
                if (marked == null) {
                    android.util.Log.w(
                        "CoreAttachmentSender",
                        "attachment send failed but no staged row for $failureLookupGuid",
                        failure,
                    )
                }
            } finally {
                UploadProgressBoard.clear(progressGuid)
            }
        }
        return OutgoingAttachmentSend(prepared.messageId)
    }
}

private data class PreparedAttachmentSend(
    val messageId: Long,
    val tempGuid: String,
    val myHandle: String,
    val conversation: UConversation,
    val disk: AttachmentStore,
    val stagedGuids: List<String>,
    val payloads: List<File>,
)

/** Sticker upload/send path backed by the positional Rust reaction API. */
private object CoreStickerSender : StickerSender {
    override suspend fun send(
        chatId: Long,
        targetGuid: String,
        targetPart: Long,
        targetText: String,
        sticker: OutgoingAttachment,
        transform: StickerTransform,
    ): OutgoingStickerSend {
        val store = CoreGraph.store ?: error("store unavailable")
        val ingestor = CoreGraph.ingestor ?: error("ingestor unavailable")
        val state = PushStateHolder.state ?: error("not connected to Apple push")
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
        val sender = sendingHandle(chat) ?: error("no registered sending handle")
        val conversation = sendConversation(store, chat, sender)
        val displayName = sticker.name ?: "sticker.png"

        val inst = state.sendSticker(
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
        return OutgoingStickerSend(attachmentGuid)
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

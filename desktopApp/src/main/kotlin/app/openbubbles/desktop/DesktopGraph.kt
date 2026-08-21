package app.openbubbles.desktop

import app.openbubbles.core.attachment.AttachmentDownloader
import app.openbubbles.core.attachment.AttachmentManager
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.repo.ChatRepo
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.core.send.buildSendConversation
import app.openbubbles.core.send.selectSendingHandle
import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Chat
import app.openbubbles.db.Db
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import uniffi.rust_lib_bluebubbles.FileInfo
import uniffi.rust_lib_bluebubbles.HandleWifiNetworksCallback
import uniffi.rust_lib_bluebubbles.KotlinFilePackager
import uniffi.rust_lib_bluebubbles.MsgReceiver
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.PackagedFile
import uniffi.rust_lib_bluebubbles.UPushMessage
import uniffi.rust_lib_bluebubbles.completeMessage
import uniffi.rust_lib_bluebubbles.hasSavedUsers
import uniffi.rust_lib_bluebubbles.initNative
import uniffi.rust_lib_bluebubbles.markJournalAttempt
import uniffi.rust_lib_bluebubbles.ptrToMessage
import uniffi.rust_lib_bluebubbles.readQueuedJournal
import uniffi.rust_lib_bluebubbles.restoreAttachment
import uniffi.rust_lib_bluebubbles.start
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Desktop composition root — the counterpart of the Android app's
 * `CoreGraph` + `NativePushService`, minus the Android Service: the Rust
 * loop lives in an app-scoped daemon coroutine, and the UI binds :core's
 * ObjectBox repositories directly.
 *
 * Rust state on desktop is deliberately simpler than Android: the Rust
 * `SharedPushState::restore` (what `initNative` calls) initializes a
 * pure-software keystore on non-Android targets, so `setupKeystore` is
 * never called here.
 */
object DesktopGraph {

    /** Per-user app state dir (`~/.openbubbles-natives`), mirroring mobile app dirs. */
    val dataDir: File = File(System.getProperty("user.home"), ".openbubbles-natives").apply {
        mkdirs()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val store: BoxStore? by lazy {
        runCatching { Db.build(dataDir) }.getOrNull()
    }

    val chatRepo: ChatRepo? by lazy { store?.let(::ChatRepo) }
    val messageRepo: MessageRepo? by lazy { store?.let { MessageRepo(it) } }
    val ingestor: MessageIngestor? by lazy {
        val st = store ?: return@lazy null
        MessageIngestor(st, scope, AttachmentStore(st, dataDir))
    }

    /** Attachment downloads ride the live push state (rust `downloadAttachment`). */
    val attachmentManager: AttachmentManager? by lazy {
        val st = store ?: return@lazy null
        val pushState = { PushStateHolder.state }
        runCatching {
            AttachmentManager(
                store = st,
                rootDir = dataDir,
                downloader = rustDownloader(st, pushState),
            )
        }.getOrNull()
    }

    /** Whether a previous login registered IDS users (`id.plist`) — auto-boot check. */
    fun hasSavedUsers(): Boolean = runCatching {
        uniffiEnsureInitialized()
        hasSavedUsers(dataDir.absolutePath)
    }.getOrDefault(false)

    // ------------------------------------------------------------------
    // Rust daemon (NativePushService adapted to a plain coroutine app scope)
    // ------------------------------------------------------------------

    @Volatile
    private var booted = false

    private val runtimeLock = Any()
    private val initGeneration = AtomicInteger(0)
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    @Volatile
    private var activeState: NativePushState? = null

    fun ensureRuntimeStarted() {
        uniffiEnsureInitialized()
        if (booted) return
        synchronized(runtimeLock) {
            if (!booted) {
                start(dataDir.absolutePath, DesktopFilePackager, NoopWifiCallback)
                booted = true
            }
        }
    }

    /**
     * Boot the Rust core and restore the live push state. Safe to re-issue
     * (e.g. after a fresh login wrote `id.plist`, or a manual retry from the
     * chat list): `start` runs once per process, `initNative` re-restores.
     */
    fun startDaemon() {
        val generation = initGeneration.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = null
        scope.launch {
            stopActiveState()
            PushStateHolder.clear()
            try {
                runInterruptible(Dispatchers.IO) {
                    ensureRuntimeStarted()
                    initNative(dataDir.absolutePath, null, DesktopReceiver(generation))
                }
            } catch (error: Throwable) {
                handleNativeFailure(
                    generation,
                    "Apple push initialization failed: ${error.message ?: error.javaClass.simpleName}",
                )
            }
        }
    }

    /** Callbacks from Rust — arrived on Rust threads; keep them light. */
    private class DesktopReceiver(
        private val generation: Int,
    ) : MsgReceiver {
        override fun receievedMsg(msg: ULong, retry: ULong) {
            val ing = ingestor ?: return
            scope.launch {
                try {
                    val handles = PushStateHolder.myHandles
                    val decoded = runInterruptible(Dispatchers.IO) { ptrToMessage(msg) }
                    when (decoded) {
                        null -> Unit
                        UPushMessage.ProcessQueue -> startQueueDrainer()
                        else -> ing.ingest(decoded, handles)
                    }
                    runInterruptible(Dispatchers.IO) { completeMessage(msg) }
                } catch (error: Throwable) {
                    // Leave the entry queued; Rust re-emits with backoff.
                    PushStateHolder.reportError(
                        "Incoming message failed on attempt ${retry + 1uL}: " +
                            (error.message ?: error.javaClass.simpleName),
                    )
                }
            }
        }

        override fun nativeReady(state: NativePushState?) {
            scope.launch {
                if (generation != initGeneration.get()) {
                    runCatching { state?.stopLoop() }
                    return@launch
                }
                val live = state ?: run {
                    activeState = null
                    PushStateHolder.clear()
                    return@launch
                }
                val handles = runCatching {
                    runInterruptible(Dispatchers.IO) { live.getHandles().toSet() }
                }.getOrElse { error ->
                    runCatching { live.stopLoop() }
                    handleNativeFailure(
                        generation,
                        "Apple push handle restore failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                    return@launch
                }
                if (generation != initGeneration.get()) {
                    runCatching { live.stopLoop() }
                    return@launch
                }
                activeState?.takeIf { it !== live }?.let { old -> runCatching { old.stopLoop() } }
                activeState = live
                reconnectAttempt = 0
                PushStateHolder.install(live, handles)
                runCatching {
                    runInterruptible(Dispatchers.IO) { live.startLoop(this@DesktopReceiver) }
                }.onFailure { error ->
                    handleNativeFailure(
                        generation,
                        "Apple push loop failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
        }

        override fun nativeError(reason: String) {
            scope.launch {
                handleNativeFailure(generation, "Apple push restore failed: $reason")
            }
        }

        override fun twofaEvent(success: Boolean) {
            // Interactive 2FA is owned by the login flow (ULoginSession).
        }

        override fun finish() {
            scope.launch {
                if (generation == initGeneration.get()) {
                    handleNativeFailure(generation, "Apple push connection ended")
                }
            }
        }
    }

    private fun stopActiveState() {
        val state = activeState
        activeState = null
        runCatching { state?.stopLoop() }
    }

    private fun handleNativeFailure(generation: Int, reason: String) {
        if (generation != initGeneration.get()) return
        stopActiveState()
        PushStateHolder.clear()
        PushStateHolder.reportError(reason)
        scheduleReconnect(generation)
    }

    private fun scheduleReconnect(generation: Int) {
        if (generation != initGeneration.get()) return
        reconnectJob?.cancel()
        val delayMs = (2_000L shl reconnectAttempt.coerceIn(0, 6)).coerceAtMost(120_000L)
        reconnectAttempt++
        reconnectJob = scope.launch {
            delay(delayMs)
            if (generation == initGeneration.get()) startDaemon()
        }
    }

    /** Set once the Rust state is live (desktop PushStateHolder). */
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
            startQueueDrainer()
        }

        fun clear() {
            _state.value = null
            _myHandles.value = emptySet()
        }

        fun reportError(message: String) {
            _lastError.value = message
        }
    }

    /** Journal drain loop guard — one loop per process, not per install. */
    @Volatile
    private var drainerStarted = false

    /**
     * Journal drain loop, same shape as the Android CoreGraph drainer:
     * read one queued journal entry, ingest it, then mark the attempt.
     */
    private fun startQueueDrainer() {
        val ing = ingestor ?: return
        if (drainerStarted) return
        drainerStarted = true
        scope.launch {
            while (true) {
                val state = PushStateHolder.state
                val handles = PushStateHolder.myHandles
                if (state == null || handles.isEmpty()) {
                    delay(5_000)
                    continue
                }
                val entryResult = runCatching {
                    runInterruptible(Dispatchers.IO) { readQueuedJournal() }
                }
                if (entryResult.isFailure) {
                    delay(5_000)
                    continue
                }
                val entry = entryResult.getOrNull()
                if (entry == null) {
                    delay(2_000)
                    continue
                }
                try {
                    ing.ingest(entry.message, handles)
                    runInterruptible(Dispatchers.IO) { markJournalAttempt(entry.id, true) }
                } catch (error: Throwable) {
                    runCatching {
                        runInterruptible(Dispatchers.IO) { markJournalAttempt(entry.id, false) }
                    }
                    PushStateHolder.reportError(
                        "Journal message ${entry.id} failed: ${error.message ?: error.javaClass.simpleName}",
                    )
                    delay(desktopJournalRetryDelayMs(entry.attempts.toInt()))
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Sending (CoreSender semantics: stage -> send -> promote -> echo)
    // ------------------------------------------------------------------

    /**
     * Dart send-path semantics: stage optimistically under a temp guid, swap
     * to the Rust guid when the send is accepted, then ingest the echo so
     * receipts flow through the normal intake path.
     */
    suspend fun send(chatId: Long, text: String) {
        val st = store ?: error("store unavailable")
        val repo = messageRepo ?: error("message repo unavailable")
        val ing = ingestor ?: error("ingestor unavailable")

        val chatBox = st.boxFor(Chat::class.java)
        val messageBox = st.boxFor(Message::class.java)
        val chat = chatBox.get(chatId) ?: error("no chat $chatId")
        require(chat.isRpSms != true) { "Carrier SMS is only supported on Android" }

        val myHandle = selectSendingHandle(chat, PushStateHolder.myHandles)
            ?: error("no registered sending address")

        val tempGuid = MessageIngestor.tempGuid()
        repo.stageOutgoingMessage(chat.guid, myHandle, text, tempGuid)

        val pushState = PushStateHolder.state
        if (pushState == null) {
            repo.failOutgoing(tempGuid, "Not connected to Apple push")
            error("not connected to Apple push")
        }

        try {
            val afterGuid = chat.dbLatestMessage.target?.let { it.stagingGuid ?: it.guid }
            val inst = pushState.sendText(
                buildSendConversation(chat, afterGuid, myHandle),
                myHandle,
                text,
                null, null, null, null,
            )
            // Promote the staged row to the Rust guid so the echo and receipts
            // find it (the swap Dart performs).
            st.runInTx {
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
            repo.failOutgoing(tempGuid, t.message ?: t.javaClass.simpleName)
            throw t
        }
    }

    // ------------------------------------------------------------------
    // Attachments
    // ------------------------------------------------------------------

    /** Fire-and-forget download for the bubble's download chip. */
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
            runCatching { manager.download(attachment).collect { /* terminal is enough */ } }
        }
    }

    /** Local payload file for an attachment row, when present on disk. */
    fun localAttachmentFile(attachment: Attachment): File? =
        runCatching { attachmentManager?.localFile(attachment) }.getOrNull()

    /** Build the rust-backed downloader closure shared by the manager. */
    private fun rustDownloader(
        st: BoxStore,
        pushState: () -> NativePushState?,
    ): AttachmentDownloader = AttachmentDownloader { attachmentGuid, destPath, maxBytes, onProgress ->
        val state = pushState()
            ?: return@AttachmentDownloader Result.failure(IllegalStateException("not connected"))
        val xml = st.boxFor(Attachment::class.java)
            .query()
            .equal(Attachment_.guid, attachmentGuid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
            ?.metadata?.get("rustpush") as? String
            ?: return@AttachmentDownloader Result.failure(
                IllegalStateException("no rustpush metadata for $attachmentGuid"))
        runCatching {
            val uatt = restoreAttachment(xml)
            state.downloadAttachment(
                uatt,
                destPath,
                maxBytes?.takeIf { it > 0L }?.toULong(),
                object : uniffi.rust_lib_bluebubbles.UProgressCallback {
                    override fun onProgress(done: ULong, total: ULong) {
                        onProgress(done.toLong(), total.toLong())
                    }
                },
            )
            Result.success(Unit)
        }
    }
}

internal fun desktopJournalRetryDelayMs(attempt: Int): Long = when (val safeAttempt = attempt.coerceAtLeast(0)) {
    0 -> 2_000L
    1 -> 10_000L
    else -> (30_000L shl (safeAttempt - 2).coerceAtMost(3)).coerceAtMost(240_000L)
}

/** Minimal [KotlinFilePackager]: files exist on disk; no gallery scanning. */
private object DesktopFilePackager : KotlinFilePackager {
    override fun getFile(path: String): PackagedFile = try {
        val file = File(path)
        if (!file.exists()) {
            PackagedFile.Failure("not found: $path")
        } else {
            PackagedFile.Info(FileInfo(duration = null, width = 0u, height = 0u, thumbnail = null))
        }
    } catch (t: Throwable) {
        PackagedFile.Failure(t.message ?: "packaging failed")
    }

    override fun scanFiles(paths: List<String>) {
        // Media scanning is Android-only; nothing to do on desktop.
    }
}

private object NoopWifiCallback : HandleWifiNetworksCallback {
    override fun handleWifiNetworks(networks: Map<String, String>, userApprove: Boolean) {
        // Wi-Fi suggestions UI is out of scope on desktop.
    }
}

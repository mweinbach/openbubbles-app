package app.openbubbles.nativeapp.data

import android.annotation.SuppressLint
import android.content.Context
import android.util.Base64
import androidx.core.content.edit
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.sync.CloudSyncManager
import app.openbubbles.core.sync.CloudSyncStateStore
import app.openbubbles.core.sync.SyncMode
import app.openbubbles.core.sync.SyncSummary
import app.openbubbles.core.sync.UniffiCloudSyncPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.NativePushState
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * App-side CloudKit sync wiring: cursors + pending deletes in
 * SharedPreferences (mirroring the Dart app's prefs-based sync state), a
 * manager per live push state, and automatic incremental sync on connect.
 */
private const val PREFS = "cloud_sync"
private const val KEY_CHAT_CURSOR = "chatSyncToken"
private const val KEY_MESSAGE_CURSOR = "messageSyncToken"
private const val KEY_ATTACHMENT_CURSOR = "attachmentSyncToken"
private const val KEY_CHAT_DELETES = "chatDeletionIds"
private const val KEY_MESSAGE_DELETES = "messageDeletionIds"
private const val KEY_ATTACHMENT_DELETES = "attachmentDeletionIds"
private const val KEY_HISTORY_SYNC_COMPLETE = "historySyncComplete"
private const val KEY_WALLPAPER_BACKFILL = "wallpaperBackfillV1"
private const val KEY_AUTO_SYNC_ATTEMPT = "autoSyncAttemptMs"

/**
 * The push state is re-installed on every reconnect (process restarts, socket
 * loss, activity-resume revivals), and each auto sync costs several CloudKit
 * round trips even when no records changed. Throttle connect-triggered syncs
 * to one attempt per window; manual syncs, battery-saver polls, and resets
 * bypass the throttle.
 */
internal const val AUTO_SYNC_MIN_INTERVAL_MS = 15 * 60 * 1000L

internal fun shouldAutoSyncOnConnect(
    lastAttemptMs: Long,
    nowMs: Long,
    minIntervalMs: Long = AUTO_SYNC_MIN_INTERVAL_MS,
): Boolean {
    if (lastAttemptMs <= 0L) return true
    val elapsedMs = nowMs - lastAttemptMs
    // A wall clock that moved backwards must not suppress syncs indefinitely.
    return elapsedMs < 0L || elapsedMs >= minIntervalMs
}

object CloudSyncWiring {

    private val managerRef = AtomicReference<CloudSyncManager?>(null)
    private val syncCoordinator = HistorySyncCoordinator(
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        sync = { mode -> managerRef.get()?.sync(mode) },
    )

    val manager: CloudSyncManager? get() = managerRef.get()
    val syncing: StateFlow<Boolean> = syncCoordinator.running
    val lastSummary: StateFlow<SyncSummary?> = syncCoordinator.lastSummary

    fun onStateInstalled(context: Context, state: NativePushState, autoSync: Boolean = true) {
        val store = CoreGraph.store ?: return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stateStore = PrefsCloudSyncStateStore(prefs)
        val limitPreferences = HistorySyncPreferences(context.applicationContext)
        val port = HistoryLimitedCloudSyncPort(
            delegate = UniffiCloudSyncPort(state),
            store = store,
            window = { limitPreferences.window },
        )
        val transcriptBackgroundStore = TranscriptBackgroundStore(context.applicationContext) { state }
        managerRef.set(
            CloudSyncManager(
                store,
                port,
                stateStore,
                AttachmentStore(store, File(context.dataDir, "app_flutter")),
                transcriptBackgroundStore,
            ),
        )

        // Auto incremental sync on connect (Dart parity: startup + daily),
        // throttled so reconnect churn never becomes CloudKit churn. Poll
        // mode (battery saver) drives its own single sync instead. The armed
        // first-run backfill owns the single-flight slot behind the lock
        // screen, so connecting must not start a competing incremental pass.
        if (InitialHistoryDownload.isPending(context)) {
            // The service can restore the Apple session before any activity is
            // composed. Resume the durable, cursor-backed initial download in
            // that process too so its notification lock cannot strand users.
            startInitialHistorySync(context.applicationContext)
        } else if (autoSync &&
            !InitialHistoryDownload.isPostSignInOnboardingActive(context)
        ) {
            val now = System.currentTimeMillis()
            if (shouldAutoSyncOnConnect(prefs.getLong(KEY_AUTO_SYNC_ATTEMPT, 0L), now)) {
                // Record the attempt regardless of outcome: a failing sync
                // must retry on the next window, not on every reconnect.
                prefs.edit { putLong(KEY_AUTO_SYNC_ATTEMPT, now) }
                startHistorySync(context, SyncMode.INCREMENTAL)
            }
        }
    }

    /**
     * The one-time first-run backfill armed at the end of onboarding. Runs in
     * the sync scope so the durable pending flag clears (and the user gets
     * their "messages are ready" notification) even if the activity is gone.
     */
    fun startInitialHistorySync(context: Context): Boolean {
        val app = context.applicationContext
        // PushStateHolder is published immediately before onStateInstalled
        // constructs this manager. Do not let that brief gap consume the
        // coordinator's single-flight request with a null sync result.
        if (managerRef.get() == null) return false
        return syncCoordinator.start(
            mode = InitialHistoryDownload.syncMode(app),
            onStarting = { InitialHistoryDownload.markStarted(app) },
            afterSuccessfulSync = {
                CoreGraph.relinkContacts()
                markHistorySyncComplete(app)
                InitialHistoryDownload.finish(app)
            },
        )
    }

    /**
     * Starts a single process-owned sync. The foreground push service keeps
     * this process alive when the app leaves Settings or moves to background;
     * persisted page cursors resume safely after an actual process death.
     */
    fun startHistorySync(context: Context, mode: SyncMode): Boolean =
        syncCoordinator.start(mode) {
            // CardDAV commonly finishes before initial history; bind newly
            // created handles before exposing the completed chat list.
            CoreGraph.relinkContacts()
            markHistorySyncComplete(context.applicationContext)
        }

    fun cancelHistorySync() {
        managerRef.get()?.cancel()
    }

    fun resetHistorySync(context: Context): Boolean {
        cancelHistorySync()
        val cleared = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().clear().commit()
        val state = PushStateHolder.state ?: return cleared
        onStateInstalled(context.applicationContext, state, autoSync = false)
        return cleared && startHistorySync(context.applicationContext, SyncMode.FULL)
    }

    /**
     * A poll may alert only after one complete CloudKit pass. Without this
     * durable gate, a fresh install interprets every historical unread chat
     * as a newly arrived message and floods the notification shade.
     */
    fun hasCompletedHistorySync(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_HISTORY_SYNC_COMPLETE, false)

    @SuppressLint("UseKtx") // commit() boolean is checked; KTX edit() returns Unit.
    fun markHistorySyncComplete(context: Context) {
        check(
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_HISTORY_SYNC_COMPLETE, true).commit(),
        ) { "failed to persist history sync completion" }
    }

    fun backupState(context: Context): ByteArray {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return CloudSyncBackupCodec.encode(snapshot(prefs))
    }

    fun restoreBackupState(context: Context, encoded: ByteArray?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previous = snapshot(prefs)
        val restored = encoded?.let(CloudSyncBackupCodec::decode) ?: CloudSyncBackupState()
        if (!writeSnapshot(prefs, restored)) {
            writeSnapshot(prefs, previous)
            error("failed to restore history sync state")
        }
    }

    fun clear() {
        managerRef.getAndSet(null)?.cancel()
    }

    /** Queue a local chat deletion so the next sync flushes it before pulling. */
    @SuppressLint("UseKtx") // commit() boolean is checked; KTX edit() returns Unit.
    fun queueChatDelete(context: Context, recordId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getStringSet(KEY_CHAT_DELETES, emptySet()).orEmpty().toMutableSet()
        pending += recordId
        check(prefs.edit().putStringSet(KEY_CHAT_DELETES, pending).commit()) {
            "failed to persist pending chat deletion"
        }
    }

    private fun snapshot(prefs: android.content.SharedPreferences): CloudSyncBackupState {
        val stateStore = PrefsCloudSyncStateStore(prefs)
        return CloudSyncBackupState(
            chatCursor = stateStore.chatCursor(),
            messageCursor = stateStore.messageCursor(),
            attachmentCursor = stateStore.attachmentCursor(),
            pendingChatDeletes = stateStore.pendingChatDeletes().sorted(),
            pendingMessageDeletes = stateStore.pendingMessageDeletes().sorted(),
            pendingAttachmentDeletes = stateStore.pendingAttachmentDeletes().sorted(),
            historySyncComplete = prefs.getBoolean(KEY_HISTORY_SYNC_COMPLETE, false),
        )
    }

    @SuppressLint("UseKtx") // Incremental editor; commit() boolean is returned to the caller.
    private fun writeSnapshot(
        prefs: android.content.SharedPreferences,
        state: CloudSyncBackupState,
    ): Boolean {
        val editor = prefs.edit().clear()
        state.chatCursor?.let { editor.putString(KEY_CHAT_CURSOR, encodeCursor(it)) }
        state.messageCursor?.let { editor.putString(KEY_MESSAGE_CURSOR, encodeCursor(it)) }
        state.attachmentCursor?.let { editor.putString(KEY_ATTACHMENT_CURSOR, encodeCursor(it)) }
        editor.putStringSet(KEY_CHAT_DELETES, state.pendingChatDeletes.toSet())
        editor.putStringSet(KEY_MESSAGE_DELETES, state.pendingMessageDeletes.toSet())
        editor.putStringSet(KEY_ATTACHMENT_DELETES, state.pendingAttachmentDeletes.toSet())
        editor.putBoolean(KEY_HISTORY_SYNC_COMPLETE, state.historySyncComplete)
        return editor.commit()
    }
}

internal class HistorySyncCoordinator(
    private val scope: CoroutineScope,
    private val sync: suspend (SyncMode) -> SyncSummary?,
) {
    private val lock = Any()
    private var activeJob: Job? = null
    private val _running = MutableStateFlow(false)
    private val _lastSummary = MutableStateFlow<SyncSummary?>(null)

    val running: StateFlow<Boolean> = _running.asStateFlow()
    val lastSummary: StateFlow<SyncSummary?> = _lastSummary.asStateFlow()

    fun start(
        mode: SyncMode,
        onStarting: () -> Unit = {},
        afterSuccessfulSync: suspend () -> Unit,
    ): Boolean =
        synchronized(lock) {
            if (activeJob?.isActive == true) return@synchronized false
            onStarting()
            _lastSummary.value = null
            _running.value = true

            lateinit var launched: Job
            launched = scope.launch(start = CoroutineStart.LAZY) {
                try {
                    val summary = sync(mode)
                    _lastSummary.value = if (
                        summary != null && summary.error == null && !summary.cancelled
                    ) {
                        runCatching { afterSuccessfulSync() }
                            .fold(
                                onSuccess = { summary },
                                onFailure = { error ->
                                    summary.copy(
                                        error = error.message ?: error.javaClass.simpleName,
                                    )
                                },
                            )
                    } else {
                        summary
                    }
                } finally {
                    synchronized(lock) {
                        if (activeJob === launched) {
                            activeJob = null
                            _running.value = false
                        }
                    }
                }
            }
            activeJob = launched
            launched.start()
            true
        }
}

private class PrefsCloudSyncStateStore(
    private val prefs: android.content.SharedPreferences,
) : CloudSyncStateStore {

    override fun chatCursor(): ByteArray? = decode(prefs.getString(KEY_CHAT_CURSOR, null))
    override fun messageCursor(): ByteArray? = decode(prefs.getString(KEY_MESSAGE_CURSOR, null))
    override fun attachmentCursor(): ByteArray? = decode(prefs.getString(KEY_ATTACHMENT_CURSOR, null))

    override fun saveChatCursor(cursor: ByteArray?) {
        persistCursor(KEY_CHAT_CURSOR, cursor)
    }

    override fun saveMessageCursor(cursor: ByteArray?) {
        persistCursor(KEY_MESSAGE_CURSOR, cursor)
    }

    override fun saveAttachmentCursor(cursor: ByteArray?) {
        persistCursor(KEY_ATTACHMENT_CURSOR, cursor)
    }

    override fun pendingChatDeletes(): List<String> =
        prefs.getStringSet(KEY_CHAT_DELETES, emptySet())?.toList().orEmpty()

    override fun pendingMessageDeletes(): List<String> =
        prefs.getStringSet(KEY_MESSAGE_DELETES, emptySet())?.toList().orEmpty()

    override fun pendingAttachmentDeletes(): List<String> =
        prefs.getStringSet(KEY_ATTACHMENT_DELETES, emptySet())?.toList().orEmpty()

    override fun savePendingChatDeletes(ids: List<String>) {
        persistDeletes(KEY_CHAT_DELETES, ids)
    }

    override fun savePendingMessageDeletes(ids: List<String>) {
        persistDeletes(KEY_MESSAGE_DELETES, ids)
    }

    override fun savePendingAttachmentDeletes(ids: List<String>) {
        persistDeletes(KEY_ATTACHMENT_DELETES, ids)
    }

    override fun wallpaperBackfillDone(): Boolean =
        prefs.getBoolean(KEY_WALLPAPER_BACKFILL, false)

    @SuppressLint("UseKtx") // commit() boolean is checked; KTX edit() returns Unit.
    override fun saveWallpaperBackfillDone(done: Boolean) {
        check(prefs.edit().putBoolean(KEY_WALLPAPER_BACKFILL, done).commit()) {
            "failed to persist wallpaper backfill flag"
        }
    }

    @SuppressLint("UseKtx") // Incremental editor; commit() boolean is checked.
    private fun persistCursor(key: String, cursor: ByteArray?) {
        val editor = prefs.edit()
        if (cursor == null) editor.remove(key) else editor.putString(key, encodeCursor(cursor))
        check(editor.commit()) { "failed to persist CloudKit cursor" }
    }

    @SuppressLint("UseKtx") // commit() boolean is checked; KTX edit() returns Unit.
    private fun persistDeletes(key: String, ids: List<String>) {
        check(prefs.edit().putStringSet(key, ids.toSet()).commit()) {
            "failed to persist pending CloudKit deletions"
        }
    }

    private fun decode(s: String?): ByteArray? =
        s?.let { runCatching { Base64.decode(it, Base64.NO_WRAP) }.getOrNull() }
}

private fun encodeCursor(bytes: ByteArray): String =
    Base64.encodeToString(bytes, Base64.NO_WRAP)

internal data class CloudSyncBackupState(
    val chatCursor: ByteArray? = null,
    val messageCursor: ByteArray? = null,
    val attachmentCursor: ByteArray? = null,
    val pendingChatDeletes: List<String> = emptyList(),
    val pendingMessageDeletes: List<String> = emptyList(),
    val pendingAttachmentDeletes: List<String> = emptyList(),
    val historySyncComplete: Boolean = false,
)

internal object CloudSyncBackupCodec {
    private const val MAGIC = 0x4f425343
    private const val VERSION = 1
    private const val MAX_STATE_BYTES = 512 * 1024
    private const val MAX_CURSOR_BYTES = 256 * 1024
    private const val MAX_DELETE_IDS = 100_000

    fun encode(state: CloudSyncBackupState): ByteArray {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(MAGIC)
            output.writeInt(VERSION)
            output.writeNullableBytes(state.chatCursor)
            output.writeNullableBytes(state.messageCursor)
            output.writeNullableBytes(state.attachmentCursor)
            output.writeStrings(state.pendingChatDeletes)
            output.writeStrings(state.pendingMessageDeletes)
            output.writeStrings(state.pendingAttachmentDeletes)
            output.writeBoolean(state.historySyncComplete)
        }
        return bytes.toByteArray().also {
            require(it.size <= MAX_STATE_BYTES) { "history sync state is too large to back up" }
        }
    }

    fun decode(bytes: ByteArray): CloudSyncBackupState {
        require(bytes.size <= MAX_STATE_BYTES) { "history sync backup state is too large" }
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                require(input.readInt() == MAGIC) { "invalid history sync backup state" }
                require(input.readInt() == VERSION) { "unsupported history sync backup state version" }
                val state = CloudSyncBackupState(
                    chatCursor = input.readNullableBytes(),
                    messageCursor = input.readNullableBytes(),
                    attachmentCursor = input.readNullableBytes(),
                    pendingChatDeletes = input.readStrings(),
                    pendingMessageDeletes = input.readStrings(),
                    pendingAttachmentDeletes = input.readStrings(),
                    historySyncComplete = input.readBoolean(),
                )
                require(input.read() == -1) { "trailing history sync backup data" }
                state
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("invalid history sync backup state", error)
        }
    }

    private fun DataOutputStream.writeNullableBytes(bytes: ByteArray?) {
        if (bytes == null) {
            writeInt(-1)
            return
        }
        require(bytes.size <= MAX_CURSOR_BYTES) { "CloudKit cursor is too large" }
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readNullableBytes(): ByteArray? {
        val size = readInt()
        if (size == -1) return null
        require(size in 0..MAX_CURSOR_BYTES) { "invalid CloudKit cursor length" }
        return ByteArray(size).also { readFully(it) }
    }

    private fun DataOutputStream.writeStrings(values: List<String>) {
        require(values.size <= MAX_DELETE_IDS) { "too many pending CloudKit deletions" }
        writeInt(values.size)
        values.forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_STATE_BYTES) { "CloudKit record id is too large" }
            writeInt(bytes.size)
            write(bytes)
        }
    }

    private fun DataInputStream.readStrings(): List<String> {
        val count = readInt()
        require(count in 0..MAX_DELETE_IDS) { "invalid pending deletion count" }
        return List(count) {
            val size = readInt()
            require(size in 0..MAX_STATE_BYTES) { "invalid CloudKit record id length" }
            ByteArray(size).also { readFully(it) }.toString(Charsets.UTF_8)
        }
    }
}

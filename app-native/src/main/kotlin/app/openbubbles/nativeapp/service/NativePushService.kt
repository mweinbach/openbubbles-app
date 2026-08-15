package app.openbubbles.nativeapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import app.openbubbles.core.model.isGroupConversation
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.ICloudContactSync
import app.openbubbles.nativeapp.data.NotifPrefs
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uniffi.rust_lib_bluebubbles.MsgReceiver
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UPushMessage
import uniffi.rust_lib_bluebubbles.completeMessage
import uniffi.rust_lib_bluebubbles.initNative
import uniffi.rust_lib_bluebubbles.markJournalAttempt
import uniffi.rust_lib_bluebubbles.parsePoster
import uniffi.rust_lib_bluebubbles.ptrToMessage
import uniffi.rust_lib_bluebubbles.readQueuedJournal
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized
import java.util.concurrent.atomic.AtomicInteger
import java.io.File

/**
 * Foreground service owning the live Rust push state for the native app —
 * the counterpart of the Flutter app's APNService, minus Dart:
 *
 *   setupKeystore (Android Keystore) -> initNative (SharedPushState::restore)
 *   -> nativeReady -> startLoop -> receievedMsg -> :core ingest -> completeMessage
 *
 * Messages arrive as pointers into Rust's QUEUED_MESSAGES; on ingest failure
 * the entry is NOT completed so Rust re-emits it (30s, max 5 retries).
 */
class NativePushService : Service(), MsgReceiver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pollMode = false

    private var bootStarted = false

    /** Invalidates late callbacks when a post-login reload supersedes restore. */
    private val initGeneration = AtomicInteger(0)

    /** Journal consumption belongs to the service lifecycle, never a global polling loop. */
    private val journalMutex = Mutex()

    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onCreate runs before Android delivers the start intent. Configure the
        // service mode first, then boot Rust, so a fast nativeReady callback can
        // never mistake a one-shot poll for the persistent APNs loop.
        if (shouldInitializePush(bootStarted, intent?.action)) {
            pollMode = isPollStart(intent?.action)
            bootStarted = true
            bootRust()
        }
        return restartModeFor(pollMode)
    }

    // -- Rust lifecycle -----------------------------------------------------

    private fun bootRust() {
        val generation = initGeneration.incrementAndGet()
        scope.launch {
            val dir = filesDir.absolutePath
            try {
                runInterruptible(Dispatchers.IO) {
                    uniffiEnsureInitialized()
                    app.openbubbles.nativeapp.data.RustBoot.ensureStarted(this@NativePushService, dir)
                    initNative(dir, null, InitReceiver(generation))
                }
            } catch (error: Throwable) {
                Log.e(TAG, "native initialization failed", error)
                PushStateHolder.reportError(
                    "Apple push initialization failed: ${error.message ?: error.javaClass.simpleName}",
                )
                scheduleReconnect(generation, "initialization failed")
            }
        }
    }

    /**
     * `initNative` invokes its callback on Rust's Tokio worker. Calling a
     * synchronous UniFFI method such as `getHandles` from that callback would
     * recursively call `RUNTIME.block_on` and panic. This generation-tagged
     * receiver immediately hands completion back to the service's IO scope.
     */
    private inner class InitReceiver(
        private val generation: Int,
    ) : MsgReceiver {
        override fun receievedMsg(msg: ULong, retry: ULong) {
            this@NativePushService.receievedMsg(msg, retry)
        }

        override fun nativeReady(state: NativePushState?) {
            handleNativeReady(generation, state)
        }

        override fun twofaEvent(success: Boolean) {
            this@NativePushService.twofaEvent(success)
        }

        override fun finish() {
            handleFinish(generation)
        }
    }

    // -- MsgReceiver (called on Rust threads — keep them light) -------------

    override fun receievedMsg(msg: kotlin.ULong, retry: kotlin.ULong) {
        scope.launch {
            try {
                val handles = PushStateHolder.myHandles
                val decoded = runInterruptible(Dispatchers.IO) { ptrToMessage(msg.toString()) }
                when (decoded) {
                    null -> Unit
                    UPushMessage.ProcessQueue -> drainMessageJournal(handles, allowNotifications = true)
                    else -> ingestAndNotify(decoded, handles, allowNotifications = true)
                }
                runInterruptible(Dispatchers.IO) { completeMessage(msg.toString()) }
            } catch (error: Throwable) {
                // Leave the entry queued; Rust re-emits with backoff.
                Log.e(TAG, "incoming pointer $msg failed on attempt $retry", error)
                PushStateHolder.reportError(
                    "Incoming message failed on attempt ${retry + 1uL}: " +
                        (error.message ?: error.javaClass.simpleName),
                )
            }
        }
    }

    /**
     * Rust journals iMessages before emitting [UPushMessage.ProcessQueue]. Drain
     * that journal immediately and serially. Retries only exist after an actual
     * failure, so an idle connection has no periodic coroutine wakeups.
     */
    private suspend fun drainMessageJournal(
        handles: Set<String>,
        allowNotifications: Boolean,
    ) = journalMutex.withLock {
        while (true) {
            val entry = runInterruptible(Dispatchers.IO) { readQueuedJournal() } ?: return@withLock
            try {
                ingestAndNotify(entry.message, handles, allowNotifications)
                runInterruptible(Dispatchers.IO) { markJournalAttempt(entry.id, true) }
            } catch (error: Throwable) {
                Log.e(TAG, "journal message ${entry.id} failed on attempt ${entry.attempts.toUInt() + 1u}", error)
                runInterruptible(Dispatchers.IO) { markJournalAttempt(entry.id, false) }
                if (entry.attempts.toUInt() < MAX_JOURNAL_RETRIES - 1u) {
                    delay(journalRetryDelayMs(entry.attempts.toInt()))
                }
            }
        }
    }

    private suspend fun ingestAndNotify(
        decoded: UPushMessage,
        handles: Set<String>,
        allowNotifications: Boolean,
    ) {
        if (FaceTimeDispatch.onPushMessage(this, decoded)) return
        val ingestor = CoreGraph.ingestor
            ?: error("message store unavailable")
        val result = ingestor.ingestWithResult(decoded, handles)
        val chat = result.chat
        syncGroupIcon(decoded, chat)
        syncStickerAttachments(decoded)
        syncTranscriptBackground(decoded, chat)
        if (allowNotifications && result.isNewIncomingMessage) {
            notifyIncoming(decoded, chat)
        }
    }

    /** Sticker bodies are ordinary MMCS attachments; fetch them immediately so overlays render. */
    private fun syncStickerAttachments(decoded: UPushMessage) {
        val inst = (decoded as? UPushMessage.IMessage)?.inst ?: return
        val reaction = inst.message as? UMessage.React ?: return
        reaction.parts.forEachIndexed { fallbackIndex, indexed ->
            val part = indexed.part as? uniffi.rust_lib_bluebubbles.UPart.Attachment
                ?: return@forEachIndexed
            if (part.iris || part.mime == "application/smil") return@forEachIndexed
            val partIndex = indexed.idx?.toLong() ?: fallbackIndex.toLong()
            CoreGraph.requestAttachmentDownload("${inst.id}_$partIndex")
        }
    }

    /** Apply Apple's shared transcript wallpaper without producing a message notification. */
    private suspend fun syncTranscriptBackground(
        decoded: UPushMessage,
        chat: app.openbubbles.db.Chat?,
    ) {
        val inst = (decoded as? UPushMessage.IMessage)?.inst ?: return
        val background = inst.message as? UMessage.SetTranscriptBackground ?: return
        val box = CoreGraph.store?.boxFor(app.openbubbles.db.Chat::class.java) ?: return
        val target = chat ?: background.chatId?.let { guid ->
            box.query()
                .equal(
                    app.openbubbles.db.Chat_.guid,
                    guid,
                    io.objectbox.query.QueryBuilder.StringOrder.CASE_SENSITIVE,
                )
                .build().use { it.findFirst() }
        } ?: return
        val incomingVersion = background.version.toLong()
        if ((target.transcriptBackgroundVersion ?: Long.MIN_VALUE) >= incomingVersion) return

        val directory = File(filesDir, "chat_backgrounds").apply { mkdirs() }
        if (background.remove) {
            deleteOwnedBackground(target.transcriptPosterPath, directory, null)
            target.transcriptPosterPath = null
            target.transcriptBackgroundVersion = incomingVersion
            box.put(target)
            return
        }

        val mmcsXml = background.mmcsXml ?: error("transcript background has no MMCS payload")
        val state = PushStateHolder.state ?: error("push state unavailable for transcript background")
        val payload = File.createTempFile("transcript-background-", ".zip", cacheDir)
        try {
            runInterruptible(Dispatchers.IO) {
                state.downloadMmcs(mmcsXml, payload.absolutePath, null)
            }
            val image = runInterruptible(Dispatchers.IO) {
                parsePoster(payload.readBytes()).use { poster -> poster.watch().backgroundImage }
            }
            check(image.isNotEmpty()) { "transcript background did not contain an image" }
            val destination = File(directory, "shared-${target.id}-$incomingVersion.img")
            runInterruptible(Dispatchers.IO) { destination.writeBytes(image) }
            deleteOwnedBackground(target.transcriptPosterPath, directory, destination)
            target.transcriptPosterPath = destination.absolutePath
            target.transcriptBackgroundVersion = incomingVersion
            box.put(target)
        } finally {
            runCatching { payload.delete() }
        }
    }

    private fun deleteOwnedBackground(path: String?, directory: File, except: File?) {
        val candidate = path?.let(::File)?.canonicalFile ?: return
        val root = directory.canonicalFile.toPath()
        if (candidate.toPath().startsWith(root) && candidate != except?.canonicalFile) {
            runCatching { candidate.delete() }
        }
    }

    /** Download and persist group-photo changes carried by an incoming event. */
    private suspend fun syncGroupIcon(decoded: UPushMessage, chat: app.openbubbles.db.Chat?) {
        val inst = (decoded as? UPushMessage.IMessage)?.inst ?: return
        val icon = inst.message as? UMessage.IconChange ?: return
        val target = chat ?: return
        val box = CoreGraph.store?.boxFor(app.openbubbles.db.Chat::class.java) ?: return
        val version = GROUP_VERSION.find(icon.json)?.groupValues?.getOrNull(1)?.toLongOrNull()
        val iconXml = icon.iconXml
        if (iconXml == null) {
            target.customAvatarPath?.let { runCatching { File(it).delete() } }
            target.customAvatarPath = null
            target.photoAttachmentGuid = null
            if (version != null) target.groupVersion = version
            box.put(target)
            return
        }

        val state = PushStateHolder.state ?: return
        val iconDir = File(filesDir, "group_icons").apply { mkdirs() }
        val destination = File(iconDir, "${target.id}-${inst.id.hashCode()}.png")
        runInterruptible(Dispatchers.IO) {
            state.downloadMmcs(iconXml, destination.absolutePath, null)
        }
        target.customAvatarPath?.takeIf { it != destination.absolutePath }?.let {
            runCatching { File(it).delete() }
        }
        target.customAvatarPath = destination.absolutePath
        target.photoAttachmentGuid = inst.id
        if (version != null) target.groupVersion = version
        box.put(target)
    }

    override fun nativeReady(state: NativePushState?) {
        handleNativeReady(initGeneration.get(), state)
    }

    private fun handleNativeReady(generation: Int, state: NativePushState?) {
        scope.launch {
            if (generation != initGeneration.get()) return@launch
            val live = state ?: run {
                Log.i(TAG, "no registered account to restore; stopping push service")
                stopUnavailableService()
                return@launch
            }

            // This coroutine runs on Dispatchers.IO, never Rust's Tokio worker.
            var handlesError: Throwable? = null
            val handles = runCatching { live.getHandles().toSet() }
                .onFailure {
                    Log.e(TAG, "failed to load registered handles", it)
                    handlesError = it
                }
                .getOrDefault(emptySet())
            if (generation != initGeneration.get()) return@launch

            if (pollMode) {
                runPollOnce(live)
                return@launch
            }
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0
            PushStateHolder.install(live, handles)
            handlesError?.let {
                PushStateHolder.reportError(
                    "Registered handles unavailable: ${it.message ?: it.javaClass.simpleName}",
                )
            }
            updateStatus(CONNECTED_STATUS)
            // Contact sync is independent of Messages-in-iCloud history and
            // uses the same self-hosted Apple session. Keep it off the APNs
            // owner coroutine so a slow CardDAV collection never delays the
            // live receive loop.
            scope.launch {
                ICloudContactSync.sync(applicationContext, live)
            }
            // Recover durable messages left by a process death before starting
            // the live loop. They may overlap history sync or already-rendered
            // rows, so this startup catch-up is deliberately silent. New
            // ProcessQueue events use the same drain with notifications enabled.
            drainMessageJournal(handles, allowNotifications = false)
            runCatching { live.startLoop(InitReceiver(generation)) }
                .onFailure {
                    Log.e(TAG, "failed to start Apple push loop", it)
                    PushStateHolder.reportError(
                        "Apple push loop failed: ${it.message ?: it.javaClass.simpleName}",
                    )
                    updateStatus(DISCONNECTED_STATUS)
                    scheduleReconnect(generation, "loop start failed")
                }
        }
    }

    private fun stopUnavailableService() {
        PushStateHolder.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Battery-saver cycle: restore state WITHOUT the persistent loop, run
     * one incremental CloudKit sync, notify chats that gained unread
     * messages, then exit until the next WorkManager tick.
     */
    private fun runPollOnce(live: NativePushState) {
        scope.launch {
            try {
                val appCtx = applicationContext
                app.openbubbles.nativeapp.data.CoreGraph.pollOnce(
                    appCtx, live,
                    onNewUnread = { chatId, title, body ->
                        notifyPollResult(chatId, title, body)
                    },
                )
            } catch (error: Throwable) {
                // next WorkManager tick retries
                Log.e(TAG, "battery-saver poll failed", error)
                PushStateHolder.reportError(
                    "Background message check failed: ${error.message ?: error.javaClass.simpleName}",
                )
            } finally {
                stopSelf()
            }
        }
    }

    private fun notifyPollResult(chatId: Long, title: String, body: String) {
        val chat = CoreGraph.store?.boxFor(app.openbubbles.db.Chat::class.java)?.get(chatId) ?: return
        val guid = chat.guid ?: return
        Notifications.postIncoming(
            context = this,
            chatId = chatId,
            chatGuid = guid,
            title = title,
            text = body,
            isGroup = chat.isGroupConversation(),
        )
    }

    override fun twofaEvent(success: Boolean) {
        // Login flow (ULoginSession) owns interactive 2FA in the native app.
    }

    override fun finish() {
        handleFinish(initGeneration.get())
    }

    private fun handleFinish(generation: Int) {
        scope.launch {
            if (generation != initGeneration.get()) {
                Log.i(TAG, "ignoring stale Apple push loop completion")
                return@launch
            }
            Log.w(TAG, "Apple push loop ended")
            PushStateHolder.clear()
            PushStateHolder.reportError("Apple push disconnected; reconnecting automatically")
            updateStatus(DISCONNECTED_STATUS)
            scheduleReconnect(generation, "loop ended")
        }
    }

    private fun scheduleReconnect(generation: Int, reason: String) {
        if (pollMode || generation != initGeneration.get()) return
        reconnectJob?.cancel()
        val attempt = reconnectAttempt++
        val delayMs = reconnectDelayMs(attempt)
        Log.w(TAG, "$reason; reconnecting in ${delayMs}ms")
        reconnectJob = scope.launch {
            delay(delayMs)
            if (!pollMode && generation == initGeneration.get()) {
                updateStatus(CONNECTING_STATUS)
                bootRust()
            }
        }
    }

    // -- Notifications -------------------------------------------------------

    private fun notifyIncoming(decoded: UPushMessage, chat: app.openbubbles.db.Chat?) {
        val inst = (decoded as? UPushMessage.IMessage)?.inst ?: return
        if (inst.sender == null || inst.sender in PushStateHolder.myHandles) return
        if (inst.message is UMessage.React && !NotifPrefs(this).notifyReactions) return
        val body = notificationPreview(inst) ?: return
        val target = chat ?: return
        if (app.openbubbles.core.model.ChatMute.shouldMute(
                target,
                senderAddress = inst.sender,
                messageText = body,
            )
        ) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == false
        ) return

        val isGroup = target.isGroupConversation()

        // Prefer the linked contact's name over the raw handle. Without this the
        // shade shows phone numbers and email addresses even when the contact is
        // known, because formattedAddress is still just the address.
        fun displayNameFor(address: String?): String? =
            address?.let { CoreGraph.contactDisplayInfo(it)?.first }?.takeIf { it.isNotBlank() }

        fun app.openbubbles.db.Handle.contactOrAddress(): String =
            displayNameFor(address) ?: formattedAddress ?: address

        val rawSender = inst.sender?.removePrefix("tel:")?.removePrefix("mailto:")
        val senderName = target.handles
            .firstOrNull { handle -> handle.address == rawSender }
            ?.contactOrAddress()
            ?: displayNameFor(rawSender)
            ?: rawSender
        val chatTitle = if (isGroup) {
            target.displayName
                ?: target.apnTitle
                ?: target.title
                ?: target.handles.joinToString(", ") { it.contactOrAddress() }
                    .ifBlank { "Group" }
        } else {
            target.displayName
                ?: target.handles.firstOrNull()?.contactOrAddress()
                ?: senderName
                ?: "Message"
        }

        Notifications.postIncoming(
            context = this,
            chatId = target.id,
            chatGuid = target.guid ?: return,
            title = chatTitle,
            text = body,
            isGroup = isGroup,
            senderName = senderName,
            messageGuid = inst.id,
        )
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Connection status", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun startForegroundCompat() {
        val notification = statusNotification(CONNECTING_STATUS)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(STATUS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(STATUS_NOTIFICATION_ID, notification)
        }
    }

    private fun updateStatus(text: String) {
        getSystemService(NotificationManager::class.java)
            ?.notify(STATUS_NOTIFICATION_ID, statusNotification(text))
    }

    private fun statusNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(app.openbubbles.nativeapp.R.drawable.ic_stat_message)
            .setContentTitle("OpenBubbles")
            .setContentText(text)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        initGeneration.incrementAndGet()
        reconnectJob?.cancel()
        PushStateHolder.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private val GROUP_VERSION = Regex("\"(?:group_version|groupVersion)\"\\s*:\\s*(\\d+)")
        private const val CHANNEL_STATUS = "push-status"
        private const val CHANNEL_MESSAGES = "messages"
        private const val STATUS_NOTIFICATION_ID = 1001
        private const val CONNECTING_STATUS = "Connecting to Apple push"
        private const val CONNECTED_STATUS = "Connected to Apple push"
        private const val DISCONNECTED_STATUS = "Apple push disconnected"
        private const val MAX_JOURNAL_RETRIES = 3u
        internal const val ACTION_RELOAD = "app.openbubbles.nativeapp.action.RELOAD_PUSH"
        private const val TAG = "NativePushService"

        fun start(context: Context): Boolean {
            return start(context, action = null)
        }

        /** Re-run persisted-state restoration after IDS registration. */
        fun reloadAfterLogin(context: Context): Boolean {
            return start(context, action = ACTION_RELOAD)
        }

        private fun start(context: Context, action: String?): Boolean {
            return try {
                context.startForegroundService(
                    Intent(context, NativePushService::class.java).setAction(action),
                )
                true
            } catch (error: SecurityException) {
                Log.w("NativePushService", "foreground start denied", error)
                false
            } catch (error: RuntimeException) {
                // The exception class was added in API 31 while our minSdk is
                // 26, so identify it without introducing an older-device class
                // loading dependency.
                if (error.javaClass.name ==
                    "android.app.ForegroundServiceStartNotAllowedException"
                ) {
                    Log.w("NativePushService", "foreground start deferred by Android", error)
                    false
                } else {
                    throw error
                }
            }
        }
    }
}

internal fun isPollStart(action: String?): Boolean =
    action == BatterySaver.ACTION_POLL_ONCE

internal fun isReloadStart(action: String?): Boolean =
    action == NativePushService.ACTION_RELOAD

internal fun shouldInitializePush(bootStarted: Boolean, action: String?): Boolean =
    !bootStarted || isReloadStart(action)

internal fun restartModeFor(pollMode: Boolean): Int =
    if (pollMode) Service.START_NOT_STICKY else Service.START_STICKY

internal fun journalRetryDelayMs(attempt: Int): Long = when (attempt.coerceAtLeast(0)) {
    0 -> 2_000L
    else -> 10_000L
}

internal fun reconnectDelayMs(attempt: Int): Long {
    val bounded = attempt.coerceIn(0, 6)
    return (2_000L shl bounded).coerceAtMost(120_000L)
}

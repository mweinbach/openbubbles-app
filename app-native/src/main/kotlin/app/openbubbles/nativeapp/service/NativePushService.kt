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
import app.openbubbles.core.model.ChatMute
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.core.sync.TranscriptBackgroundUpdate
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.ICloudContactSync
import app.openbubbles.nativeapp.data.LiveMessageArrival
import app.openbubbles.nativeapp.data.LiveMessageArrivals
import app.openbubbles.nativeapp.data.NotifPrefs
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.TranscriptBackgroundStore
import app.openbubbles.nativeapp.facetime.FaceTimeNotifications
import io.objectbox.query.QueryBuilder
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
import uniffi.rust_lib_bluebubbles.URegisterState
import uniffi.rust_lib_bluebubbles.completeMessage
import uniffi.rust_lib_bluebubbles.initNative
import uniffi.rust_lib_bluebubbles.markJournalAttempt
import uniffi.rust_lib_bluebubbles.ptrToMessage
import uniffi.rust_lib_bluebubbles.readQueuedJournal
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

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
    /** Keeps recovery semantics even when persisting Rust's retry counter fails. Guarded by [journalMutex]. */
    private val journalFailures = mutableSetOf<ULong>()

    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    @Volatile
    private var activeState: NativePushState? = null

    private val transcriptBackgroundStore by lazy {
        TranscriptBackgroundStore(applicationContext) {
            activeState ?: PushStateHolder.state
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        Notifications.collapseActiveConversationNotifications(this)
        startForegroundCompat()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // onCreate runs before Android delivers the start intent. Configure the
        // service mode first, then boot Rust, so a fast nativeReady callback can
        // never mistake a one-shot poll for the persistent APNs loop.
        if (shouldInitializePush(bootStarted, intent?.action)) {
            if (isReloadStart(intent?.action)) stopActiveState()
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
                Log.e(TAG, "native initialization failed (${diagnosticKind(error)})")
                PushStateHolder.reportError(
                    "Apple push initialization failed (${diagnosticKind(error)})",
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

        override fun nativeError(reason: String) {
            handleNativeError(generation, reason)
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
                val decoded = runInterruptible(Dispatchers.IO) { ptrToMessage(msg) }
                when (decoded) {
                    null -> Unit
                    UPushMessage.ProcessQueue ->
                        drainMessageJournal(handles, IncomingNotificationSource.LIVE)
                    UPushMessage.RegistrationState -> handleRegistrationState()
                    else -> ingestAndNotify(decoded, handles, IncomingNotificationSource.LIVE)
                }
                runInterruptible(Dispatchers.IO) { completeMessage(msg) }
            } catch (error: Throwable) {
                // Leave the entry queued; Rust re-emits with backoff.
                Log.e(TAG, "incoming message failed on attempt ${retry + 1uL} (${diagnosticKind(error)})")
                PushStateHolder.reportError(
                    "Incoming message failed on attempt ${retry + 1uL} (${diagnosticKind(error)})",
                )
            }
        }
    }

    private fun handleRegistrationState() {
        val state = activeState ?: return
        val registration = runCatching { state.getRegstate() }
            .onFailure { error -> Log.w(TAG, "failed to read IDS registration state (${diagnosticKind(error)})") }
            .getOrNull() ?: return
        PushStateHolder.updateRegistration(registration)
        when (registration) {
            is URegisterState.Failed -> {
                if (registrationRequiresSignIn(registration)) {
                    markAccountSignInRequired(registration)
                } else {
                    PushStateHolder.reportError("Apple messaging registration failed")
                    updateStatus(REGISTRATION_FAILED_STATUS)
                }
            }
            URegisterState.Registering -> updateStatus(REGISTERING_STATUS)
            is URegisterState.Registered -> {
                PushStateHolder.clearError()
                updateStatus(CONNECTED_STATUS)
            }
        }
    }

    private fun markAccountSignInRequired(registration: URegisterState.Failed) {
        // A rejected IDS renewal does not necessarily invalidate the current
        // APNs session. Keep it alive so already-addressed messages can still
        // be received and the durable journal can drain while the user signs
        // in again. The status and UI must still make the degraded account
        // state explicit instead of calling this fully connected.
        Log.w(TAG, "Apple account reauthentication required; keeping current push session active")
        PushStateHolder.updateRegistration(registration)
        PushStateHolder.reportError(
            if (registration.error.startsWith(ACCOUNT_TWO_FACTOR_REQUIRED_PREFIX)) {
                "Apple ID verification required"
            } else {
                "Apple ID sign-in required"
            },
        )
        updateStatus(
            if (registration.error.startsWith(ACCOUNT_TWO_FACTOR_REQUIRED_PREFIX)) {
                ACCOUNT_TWO_FACTOR_REQUIRED_STATUS
            } else {
                ACCOUNT_REAUTH_REQUIRED_STATUS
            },
        )
    }

    /**
     * Rust journals iMessages before emitting [UPushMessage.ProcessQueue]. Drain
     * that journal immediately and serially. Retries only exist after an actual
     * failure, so an idle connection has no periodic coroutine wakeups.
     */
    private suspend fun drainMessageJournal(
        handles: Set<String>,
        drainSource: IncomingNotificationSource,
    ) = journalMutex.withLock {
        while (true) {
            val entry = runInterruptible(Dispatchers.IO) { readQueuedJournal() } ?: return@withLock
            try {
                // Fresh live entries preserve duplicate suppression. A prior
                // failed attempt, or any startup drain, uses recovery semantics
                // so persistence-before-notify can still alert on retry.
                val entrySource = journalEntryNotificationSource(
                    drainSource = drainSource,
                    priorAttempts = entry.attempts.toInt(),
                    failedInThisProcess = entry.id in journalFailures,
                )
                ingestAndNotify(entry.message, handles, entrySource)
            } catch (error: Throwable) {
                journalFailures += entry.id
                Log.e(TAG, "journal message failed on attempt ${entry.attempts.toUInt() + 1u} (${diagnosticKind(error)})")
                runCatching {
                    runInterruptible(Dispatchers.IO) { markJournalAttempt(entry.id, false) }
                }.onFailure { persistenceError ->
                    Log.e(TAG, "failed to persist journal retry (${diagnosticKind(persistenceError)})")
                }
                delay(journalRetryDelayMs(entry.attempts.toInt()))
                continue
            }
            try {
                runInterruptible(Dispatchers.IO) { markJournalAttempt(entry.id, true) }
                journalFailures -= entry.id
            } catch (error: Throwable) {
                Log.e(TAG, "failed to persist journal completion (${diagnosticKind(error)})")
                delay(journalRetryDelayMs(entry.attempts.toInt()))
            }
        }
    }

    private suspend fun ingestAndNotify(
        decoded: UPushMessage,
        handles: Set<String>,
        notificationSource: IncomingNotificationSource,
    ) {
        if (FaceTimeDispatch.onPushMessage(this, decoded)) return
        val ingestor = CoreGraph.ingestor
            ?: error("message store unavailable")
        val result = ingestor.ingestWithResult(decoded, handles)
        val chat = result.chat
        syncGroupIcon(decoded, chat)
        syncStickerAttachments(decoded)
        syncTranscriptBackground(decoded, chat)
        if (result.isNewIncomingMessage) {
            val liveMessage = (decoded as? UPushMessage.IMessage)?.inst
            val normal = liveMessage?.message as? UMessage.Normal
            if (liveMessage != null && normal != null && chat != null) {
                LiveMessageArrivals.publish(
                    LiveMessageArrival(
                        messageGuid = liveMessage.id,
                        chatId = chat.id,
                        threadRootGuid = normal.replyGuid,
                        threadPart = MessageMapper.replyPartIndex(normal.replyPart),
                    ),
                )
            }
            // Size-capped media auto-download (Settings → Messaging); the
            // bubble's download chip remains for anything over the ceiling.
            chat?.let { CoreGraph.autoDownloadForChat(it.id) }
        }
        // The caller completes the Rust pointer/journal entry only after this
        // method returns, so every delivery reaches an explicit notification
        // disposition after persistence.
        notifyIncoming(
            decoded = decoded,
            chat = chat,
            handles = handles,
            source = notificationSource,
            newlyIngested = result.isNewIncomingMessage,
        )
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
        if (inst.verificationFailed) return
        val background = inst.message as? UMessage.SetTranscriptBackground ?: return
        val version = background.version.takeIf { it <= Long.MAX_VALUE.toULong() }?.toLong() ?: return
        // The ingestor already resolved the chat (cid handle / rust guid /
        // sender fallback); a null means we do not know the conversation yet
        // and the history sync that imports it applies the wallpaper instead.
        val target = chat ?: return
        // The wallpaper is decoration: a payload Apple's variants defeat
        // (or a dropped MMCS object) must never fail the journal entry it
        // rides on, or every later message wedges behind the retry loop.
        runCatching {
            transcriptBackgroundStore.apply(
                TranscriptBackgroundUpdate(
                    chatId = target.id,
                    version = version,
                    remove = background.remove,
                    mmcsXml = background.mmcsXml,
                ),
            )
        }.onFailure { error ->
            Log.w(TAG, "failed to apply transcript background (${diagnosticKind(error)})")
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
        state.downloadMmcs(iconXml, destination.absolutePath, null)
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

    override fun nativeError(reason: String) {
        handleNativeError(initGeneration.get(), reason)
    }

    private fun handleNativeError(generation: Int, _reason: String) {
        scope.launch {
            if (generation != initGeneration.get()) return@launch
            activeState = null
            PushStateHolder.clear()
            PushStateHolder.reportError("Apple push restore failed")
            updateStatus(DISCONNECTED_STATUS)
            scheduleReconnect(generation, "native restore failed")
        }
    }

    private fun handleNativeReady(generation: Int, state: NativePushState?) {
        scope.launch {
            if (generation != initGeneration.get()) {
                state?.let(::stopState)
                return@launch
            }
            val live = state ?: run {
                Log.i(TAG, "no registered account to restore; stopping push service")
                stopUnavailableService()
                return@launch
            }
            activeState = live

            // This coroutine runs on Dispatchers.IO, never Rust's Tokio worker.
            val handles = runCatching { live.getHandles().toSet() }
                .getOrElse { error ->
                    Log.e(TAG, "failed to load registered handles (${diagnosticKind(error)})")
                    activeState = null
                    stopState(live)
                    PushStateHolder.clear()
                    PushStateHolder.reportError(
                        "Registered handles unavailable (${diagnosticKind(error)})",
                    )
                    updateStatus(DISCONNECTED_STATUS)
                    scheduleReconnect(generation, "registered handles unavailable")
                    return@launch
                }
            val registration = runCatching { live.getRegstate() }
                .onFailure { Log.e(TAG, "failed to read restored IDS registration state (${diagnosticKind(it)})") }
                .getOrElse { error ->
                    activeState = null
                    stopState(live)
                    PushStateHolder.clear()
                    PushStateHolder.reportError(
                        "Apple registration status unavailable (${diagnosticKind(error)})",
                    )
                    updateStatus(DISCONNECTED_STATUS)
                    scheduleReconnect(generation, "registration status unavailable")
                    return@launch
                }
            if (generation != initGeneration.get()) {
                stopState(live)
                return@launch
            }

            if (pollMode) {
                runPollOnce(live, generation, handles)
                return@launch
            }
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0
            PushStateHolder.install(live, handles, registration)
            if (registration is URegisterState.Failed && registrationRequiresSignIn(registration)) {
                markAccountSignInRequired(registration)
            } else {
                updateStatus(
                    when (registration) {
                        is URegisterState.Registered -> CONNECTED_STATUS
                        URegisterState.Registering -> REGISTERING_STATUS
                        is URegisterState.Failed -> REGISTRATION_FAILED_STATUS
                    },
                )
            }
            // Contact sync is independent of Messages-in-iCloud history and
            // uses the same self-hosted Apple session. Keep it off the APNs
            // owner coroutine so a slow CardDAV collection never delays the
            // live receive loop.
            scope.launch {
                ICloudContactSync.sync(applicationContext, live)
            }
            // Recover durable messages left by a process death before starting
            // the live loop. A replay alerts only while its persisted row is
            // still unread and no matching notification is active. Rust does
            // not persist a separate "notification posted" disposition, so a
            // process death in the tiny interval after notify() but before the
            // platform exposes the active notification remains a bounded race.
            drainMessageJournal(handles, IncomingNotificationSource.JOURNAL_RECOVERY)
            runCatching { live.startLoop(InitReceiver(generation)) }
                .onFailure {
                    Log.e(TAG, "failed to start Apple push loop (${diagnosticKind(it)})")
                    PushStateHolder.reportError(
                        "Apple push loop failed (${diagnosticKind(it)})",
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
    private fun runPollOnce(live: NativePushState, generation: Int, handles: Set<String>) {
        scope.launch {
            try {
                val appCtx = applicationContext
                app.openbubbles.nativeapp.data.CoreGraph.pollOnce(
                    appCtx, live,
                    onNewUnread = { chatId, body ->
                        notifyPollResult(chatId, body, handles)
                    },
                )
            } catch (error: Throwable) {
                // next WorkManager tick retries
                Log.e(TAG, "battery-saver poll failed (${diagnosticKind(error)})")
                PushStateHolder.reportError(
                    "Background message check failed (${diagnosticKind(error)})",
                )
            } finally {
                if (shouldStopAfterPoll(generation, initGeneration.get(), pollMode)) {
                    stopSelf()
                }
            }
        }
    }

    private fun notifyPollResult(chatId: Long, body: String, handles: Set<String>) {
        val chat = CoreGraph.store?.boxFor(app.openbubbles.db.Chat::class.java)?.get(chatId) ?: return
        val guid = chat.guid ?: return
        val latest = chat.dbLatestMessage.target
        val identity = CoreGraph.messageNotificationIdentity(
            chat = chat,
            senderAddress = latest?.handleRelation?.target?.address,
            myHandles = handles,
        )
        Notifications.postIncoming(
            context = this,
            chatId = chatId,
            chatGuid = guid,
            title = identity.title,
            text = body,
            isGroup = identity.isGroup,
            senderName = identity.senderName,
            messageGuid = latest?.guid,
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
            activeState = null
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

    private fun notifyIncoming(
        decoded: UPushMessage,
        chat: app.openbubbles.db.Chat?,
        handles: Set<String>,
        source: IncomingNotificationSource,
        newlyIngested: Boolean,
    ): IncomingNotificationDisposition {
        val inst = (decoded as? UPushMessage.IMessage)?.inst
        val sender = inst?.sender
        val isMessage = inst?.message is UMessage.Normal || inst?.message is UMessage.React
        val reactionAllowed = inst?.message !is UMessage.React || NotifPrefs(this).notifyReactions
        val body = inst?.takeIf { isMessage && reactionAllowed }?.let(Notifications::previewForIncoming)
        val persistedMessage = inst?.id?.let(::messageByGuid)
        val target = persistedMessage?.chat?.target ?: chat
        val persisted = persistedMessage != null &&
            target != null &&
            persistedMessage.chat.targetId == target.id &&
            !target.guid.isNullOrBlank()
        val eligibleIncoming = isMessage &&
            reactionAllowed &&
            body != null &&
            sender != null &&
            sender !in handles &&
            !inst.verificationFailed &&
            persistedMessage?.isFromMe != true &&
            persistedMessage?.verificationFailed != true
        val lastReadAtMs = target?.lastReadMessageGuid
            ?.let(::messageByGuid)
            ?.dateCreated
            ?.time
        val unread = persistedMessage != null &&
            target != null &&
            isPersistedIncomingMessageUnread(
                chatHasUnreadMessage = target.hasUnreadMessage,
                chatDeleted = target.dateDeleted != null,
                messageFromMe = persistedMessage.isFromMe,
                messageDeleted = persistedMessage.dateDeleted != null,
                messageCreatedAtMs = persistedMessage.dateCreated?.time,
                lastReadAtMs = lastReadAtMs,
            )
        val runtimeState = if (persisted) {
            Notifications.runtimeStateForIncoming(this, target.id, inst.id)
        } else {
            IncomingNotificationRuntimeState(
                notificationsEnabled = false,
                activeMatchingNotification = false,
            )
        }
        val disposition = incomingNotificationDisposition(
            IncomingNotificationFacts(
                source = source,
                newlyIngested = newlyIngested,
                eligibleIncoming = eligibleIncoming,
                persisted = persisted,
                unread = unread,
                conversationVisible = target?.let { Notifications.isConversationVisible(it.id) } == true,
                muted = target != null &&
                    body != null &&
                    ChatMute.shouldMute(target, senderAddress = sender, messageText = body),
                blocked = target?.let { CoreGraph.isChatBlocked(it.id) } == true,
                notificationsEnabled = runtimeState.notificationsEnabled,
                activeMatchingNotification = runtimeState.activeMatchingNotification,
            ),
        )
        if (disposition == IncomingNotificationDisposition.NOT_PERSISTED) {
            error("incoming message ${inst?.id ?: "<unknown>"} was not persisted with a stable chat")
        }
        if (disposition != IncomingNotificationDisposition.POST) return disposition

        val notificationChat = checkNotNull(target)
        val notificationBody = checkNotNull(body)
        val notificationInst = checkNotNull(inst)
        val identity = CoreGraph.messageNotificationIdentity(
            chat = notificationChat,
            senderAddress = sender,
            myHandles = handles,
        )
        Notifications.postIncoming(
            context = this,
            chatId = notificationChat.id,
            chatGuid = checkNotNull(notificationChat.guid),
            title = identity.title,
            text = notificationBody,
            isGroup = identity.isGroup,
            senderName = identity.senderName,
            messageGuid = notificationInst.id,
        )
        return disposition
    }

    private fun messageByGuid(guid: String): Message? {
        val box = CoreGraph.store?.boxFor(Message::class.java) ?: return null
        return box.query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build()
            .use { it.findFirst() }
    }

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        // IMPORTANCE_MIN keeps the ongoing status notification out of the status bar and
        // silent. The rest only takes effect on a first install: the platform treats an
        // already-created channel as user-owned and ignores everything but name and
        // description on re-creation.
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STATUS, "Connection status", NotificationManager.IMPORTANCE_MIN).apply {
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_MESSAGES, "Messages", NotificationManager.IMPORTANCE_HIGH)
        )
        FaceTimeNotifications.ensureIncomingChannel(this)
        FaceTimeNotifications.ensureMissedChannel(this)
        FaceTimeNotifications.ensureInCallChannel(this)
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

    /**
     * The ongoing "we are connected" notification. A foreground service must post one, so
     * the goal is the least obtrusive form the platform allows: the status is the title and
     * there is no content text, which collapses it to a single line, and dropping the
     * timestamp shortens the header. Deferring publication lets a reconnect that finishes
     * within ten seconds never render at all. Silence comes from the channel's
     * IMPORTANCE_MIN; the builder has no say in it above API 26.
     */
    private fun statusNotification(text: String): Notification =
        Notification.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(app.openbubbles.nativeapp.R.drawable.ic_stat_message)
            .setContentTitle(text)
            .setOngoing(true)
            .setShowWhen(false)
            .setLocalOnly(true)
            .apply {
                if (Build.VERSION.SDK_INT >= 31) {
                    setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED)
                }
            }
            .build()

    override fun onDestroy() {
        initGeneration.incrementAndGet()
        reconnectJob?.cancel()
        stopActiveState()
        PushStateHolder.clear()
        scope.cancel()
        super.onDestroy()
    }

    private fun stopActiveState() {
        activeState?.let(::stopState)
        activeState = null
    }

    private fun stopState(state: NativePushState) {
        runCatching { state.stopLoop() }
            .onFailure { Log.w(TAG, "failed to stop Apple push state (${diagnosticKind(it)})") }
    }

    companion object {
        private val GROUP_VERSION = Regex("\"(?:group_version|groupVersion)\"\\s*:\\s*(\\d+)")
        private const val CHANNEL_STATUS = "push-status"
        private const val CHANNEL_MESSAGES = "messages"
        private const val STATUS_NOTIFICATION_ID = 1001
        private const val CONNECTING_STATUS = "Connecting to Apple push"
        private const val CONNECTED_STATUS = "Connected to Apple push"
        private const val REGISTERING_STATUS = "Registering iMessage with Apple"
        private const val REGISTRATION_FAILED_STATUS = "Apple messaging registration failed"
        private const val ACCOUNT_TWO_FACTOR_REQUIRED_STATUS = "Apple ID verification required"
        private const val ACCOUNT_REAUTH_REQUIRED_STATUS = "Apple ID sign-in required"
        private const val DISCONNECTED_STATUS = "Apple push disconnected"
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
                Log.w("NativePushService", "foreground start denied")
                false
            } catch (error: RuntimeException) {
                // The exception class was added in API 31 while our minSdk is
                // 26, so identify it without introducing an older-device class
                // loading dependency.
                if (error.javaClass.name ==
                    "android.app.ForegroundServiceStartNotAllowedException"
                ) {
                    Log.w("NativePushService", "foreground start deferred by Android")
                    false
                } else {
                    throw error
                }
            }
        }
    }
}

private fun diagnosticKind(error: Throwable): String =
    error.javaClass.simpleName.ifBlank { "NativeError" }

internal fun isPollStart(action: String?): Boolean =
    action == BatterySaver.ACTION_POLL_ONCE

internal fun isReloadStart(action: String?): Boolean =
    action == NativePushService.ACTION_RELOAD

internal fun shouldInitializePush(bootStarted: Boolean, action: String?): Boolean =
    !bootStarted || isReloadStart(action)

internal fun restartModeFor(pollMode: Boolean): Int =
    if (pollMode) Service.START_NOT_STICKY else Service.START_STICKY

internal fun shouldStopAfterPoll(
    pollGeneration: Int,
    currentGeneration: Int,
    pollMode: Boolean,
): Boolean = pollMode && pollGeneration == currentGeneration

internal fun journalRetryDelayMs(attempt: Int): Long = when (val safeAttempt = attempt.coerceAtLeast(0)) {
    0 -> 2_000L
    1 -> 10_000L
    else -> (30_000L shl (safeAttempt - 2).coerceAtMost(3)).coerceAtMost(240_000L)
}

internal fun reconnectDelayMs(attempt: Int): Long {
    val bounded = attempt.coerceIn(0, 6)
    return (2_000L shl bounded).coerceAtMost(120_000L)
}

internal const val ACCOUNT_TWO_FACTOR_REQUIRED_PREFIX = "Apple ID verification required."

internal fun registrationRequiresSignIn(state: URegisterState): Boolean =
    state is URegisterState.Failed &&
        state.retryWait == null &&
        (
            state.error.startsWith("Apple ID session expired.") ||
                state.error.startsWith(ACCOUNT_TWO_FACTOR_REQUIRED_PREFIX)
            )

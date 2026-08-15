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
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import uniffi.rust_lib_bluebubbles.MsgReceiver
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UPushMessage
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.completeMessage
import uniffi.rust_lib_bluebubbles.initNative
import uniffi.rust_lib_bluebubbles.ptrToMessage
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized
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
                if (generation == initGeneration.get()) stopUnavailableService()
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
            this@NativePushService.finish()
        }
    }

    // -- MsgReceiver (called on Rust threads — keep them light) -------------

    override fun receievedMsg(msg: kotlin.ULong, retry: kotlin.ULong) {
        val ingestor = CoreGraph.ingestor ?: return
        scope.launch {
            try {
                val handles = PushStateHolder.myHandles
                val decoded = runInterruptible(Dispatchers.IO) { ptrToMessage(msg.toString()) }
                if (decoded != null) {
                    if (FaceTimeDispatch.onPushMessage(this@NativePushService, decoded)) {
                        runInterruptible(Dispatchers.IO) { completeMessage(msg.toString()) }
                        return@launch
                    }
                    val chat = ingestor.ingest(decoded, handles)
                    notifyIncoming(decoded, chat)
                }
                runInterruptible(Dispatchers.IO) { completeMessage(msg.toString()) }
            } catch (_: Throwable) {
                // Leave the entry queued; Rust re-emits with backoff.
            }
        }
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
            val handles = runCatching { live.getHandles().toSet() }
                .onFailure { Log.e(TAG, "failed to load registered handles", it) }
                .getOrDefault(emptySet())
            if (generation != initGeneration.get()) return@launch

            if (pollMode) {
                runPollOnce(live)
                return@launch
            }
            PushStateHolder.install(live, handles)
            updateStatus(CONNECTED_STATUS)
            runCatching { live.startLoop(this@NativePushService) }
                .onFailure { Log.e(TAG, "failed to start Apple push loop", it) }
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
            } catch (_: Throwable) {
                // next WorkManager tick retries
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
            isGroup = chat.handles.size > 2,
        )
    }

    override fun twofaEvent(success: Boolean) {
        // Login flow (ULoginSession) owns interactive 2FA in the native app.
    }

    override fun finish() {
        // Rust loop ended (state torn down); service stays for a future
        // re-init (e.g. after re-login).
    }

    // -- Notifications -------------------------------------------------------

    private fun notifyIncoming(decoded: UPushMessage, chat: app.openbubbles.db.Chat?) {
        val inst = (decoded as? UPushMessage.IMessage)?.inst ?: return
        if (inst.sender == null || inst.sender in PushStateHolder.myHandles) return
        val body = plainText(inst) ?: return
        val target = chat ?: return
        // Per-chat mute (Flutter parity: muteType set -> no notification).
        // Time-based auto-unmute (muteArgs) lands with chat settings UI.
        if (!target.muteType.isNullOrEmpty()) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == false
        ) return

        Notifications.postIncoming(
            context = this,
            chatId = target.id,
            chatGuid = target.guid ?: return,
            title = if (target.handles.size > 2) {
                inst.sender ?: "Message"
            } else {
                target.displayName ?: inst.sender ?: "Message"
            },
            text = body,
            isGroup = target.handles.size > 2,
        )
    }

    private fun plainText(inst: UMessageInst): String? {
        val normal = (inst.message as? UMessage.Normal) ?: return null
        return normal.parts.joinToString("") { indexed ->
            (indexed.part as? uniffi.rust_lib_bluebubbles.UPart.Text)?.text ?: " "
        }.trim().ifEmpty { null }
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
        PushStateHolder.clear()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_STATUS = "push-status"
        private const val CHANNEL_MESSAGES = "messages"
        private const val STATUS_NOTIFICATION_ID = 1001
        private const val CONNECTING_STATUS = "Connecting to Apple push"
        private const val CONNECTED_STATUS = "Connected to Apple push"
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

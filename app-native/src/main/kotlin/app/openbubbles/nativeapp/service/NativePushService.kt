package app.openbubbles.nativeapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import com.bluebubbles.messaging.services.rustpush.AndroidNativeKeystore
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
import uniffi.rust_lib_bluebubbles.isLocked
import uniffi.rust_lib_bluebubbles.ptrToMessage
import uniffi.rust_lib_bluebubbles.start
import uniffi.rust_lib_bluebubbles.setupKeystore
import uniffi.rust_lib_bluebubbles.uniffiEnsureInitialized

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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForegroundCompat()
        bootRust()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // -- Rust lifecycle -----------------------------------------------------

    private fun bootRust() {
        scope.launch {
            val dir = filesDir.absolutePath
            runInterruptible(Dispatchers.IO) {
                uniffiEnsureInitialized()
                if (!booted) {
                    start(dir, SimpleFilePackager(), NoopWifiCallback())
                    setupKeystore(dir, AndroidNativeKeystore(this@NativePushService))
                    booted = true
                }
                initNative(dir, null, this@NativePushService)
            }
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
                    ingestor.ingest(decoded, handles)
                    notifyIncoming(decoded)
                }
                runInterruptible(Dispatchers.IO) { completeMessage(msg.toString()) }
            } catch (_: Throwable) {
                // Leave the entry queued; Rust re-emits with backoff.
            }
        }
    }

    override fun nativeReady(state: NativePushState?) {
        val live = state ?: return
        val handles = runCatching { live.getHandles().toSet() }.getOrDefault(emptySet())
        PushStateHolder.install(live, handles)
        runCatching { live.startLoop(this@NativePushService) }
    }

    override fun twofaEvent(success: Boolean) {
        // Login flow (ULoginSession) owns interactive 2FA in the native app.
    }

    override fun finish() {
        // Rust loop ended (state torn down); service stays for a future
        // re-init (e.g. after re-login).
    }

    // -- Notifications -------------------------------------------------------

    private fun notifyIncoming(decoded: UPushMessage) {
        val inst = (decoded as? UPushMessage.IMessage)?.inst ?: return
        if (inst.sender == null || inst.sender in PushStateHolder.myHandles) return
        val body = plainText(inst) ?: return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 33 &&
            nm.areNotificationsEnabled().not()
        ) return

        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, NativeMainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CHANNEL_MESSAGES)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(inst.sender)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        nm.notify(inst.sender.hashCode(), notification)
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
        val notification = Notification.Builder(this, CHANNEL_STATUS)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle("OpenBubbles")
            .setContentText("Connected to Apple push")
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(STATUS_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(STATUS_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_STATUS = "push-status"
        private const val CHANNEL_MESSAGES = "messages"
        private const val STATUS_NOTIFICATION_ID = 1001

        @Volatile
        private var booted = false

        fun start(context: Context) {
            context.startForegroundService(Intent(context, NativePushService::class.java))
        }
    }
}

private class NoopWifiCallback : uniffi.rust_lib_bluebubbles.HandleWifiNetworksCallback {
    override fun handleWifiNetworks(networks: Map<String, String>, userApprove: Boolean) {
        // Wi-Fi suggestions UI is out of MVP scope.
    }
}

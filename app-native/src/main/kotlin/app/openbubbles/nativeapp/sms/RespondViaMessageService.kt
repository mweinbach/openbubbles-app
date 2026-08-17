package app.openbubbles.nativeapp.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import app.openbubbles.nativeapp.data.CoreGraph
import kotlinx.coroutines.launch

/** Handles Android's lock-screen/call-screen "respond via message" contract. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destination = intent?.data?.schemeSpecificPart?.substringBefore('?')
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (destination.isNullOrBlank() || text.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        SmsIngest.seedAppContext(this)
        SmsBridge.scope.launch {
            try {
                val handle = SmsPushBuilder.toRustAddress(destination)
                val chatId = CoreGraph.findOrCreateChat(listOf(handle), sms = true)
                if (chatId != null) {
                    SmsBridge.sender.send(chatId, text)
                } else {
                    Log.w(TAG, "Respond-via-message could not open an SMS conversation")
                }
            } catch (error: Throwable) {
                Log.w(TAG, "Respond-via-message send failed", error)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private companion object {
        private const val TAG = "RespondViaMessage"
    }
}

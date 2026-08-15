package app.openbubbles.nativeapp.sms

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log

/** Handles Android's lock-screen/call-screen "respond via message" contract. */
class RespondViaMessageService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destination = intent?.data?.schemeSpecificPart?.substringBefore('?')
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        if (!destination.isNullOrBlank() && !text.isNullOrBlank() && SmsPermissions.canSendSms(this)) {
            runCatching {
                val manager = if (Build.VERSION.SDK_INT >= 31) {
                    getSystemService(SmsManager::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
                manager.sendTextMessage(destination, null, text, null, null)
            }.onFailure { Log.w(TAG, "Respond-via-message send failed", it) }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    private companion object {
        private const val TAG = "RespondViaMessage"
    }
}

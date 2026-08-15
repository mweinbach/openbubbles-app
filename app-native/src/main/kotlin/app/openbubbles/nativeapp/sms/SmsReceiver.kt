package app.openbubbles.nativeapp.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives regular (non-data) SMS broadcasts from the platform and ingests
 * them into the shared store as `service=sms` messages — the on-device twin
 * of a relayed-SMS iMessage push. This is NOT the Apple-OTP path (data SMS /
 * `DATA_SMS_RECEIVED` stays with the login flow; different action).
 *
 * Flow: SMS_RECEIVED → [Telephony.Sms.Intents.getMessagesFromIntent] (the
 * platform already reassembled the multipart PDUs of one message) →
 * [SmsPushBuilder.buildIncomingSms] → [SmsIngest.ingestIncoming] →
 * notification via the existing pipeline. The telephony thread id is resolved
 * best-effort from the provider when READ_SMS is granted.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION
        ) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val sender = messages.firstNotNullOfOrNull { it.originatingAddress } ?: return
        val body = messages.joinToString("") { it.messageBody ?: "" }
        if (body.isEmpty()) return
        val timestamp = messages.firstOrNull { it.timestampMillis > 0L }?.timestampMillis
            ?: System.currentTimeMillis()

        val push = SmsPushBuilder.buildIncomingSms(
            senderAddress = sender,
            body = body,
            timestampMs = timestamp,
            myPhoneHandles = PushStateHolder.myHandles.filter { it.startsWith("tel:") },
        )
        val preview = body.lineSequence().firstOrNull { it.isNotBlank() } ?: body

        val pending = goAsync()
        SmsBridge.scope.launch(Dispatchers.IO) {
            try {
                val threadId = resolveTelephonyThreadId(context, sender, timestamp)
                SmsIngest.ingestIncoming(context, push, preview, threadId)
            } catch (t: Throwable) {
                Log.w(TAG, "SMS ingest failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    /**
     * Best-effort thread-id lookup (needs READ_SMS): newest provider row from
     * this address around the PDU timestamp. Null when unreadable — receive
     * works without it.
     */
    private fun resolveTelephonyThreadId(context: Context, sender: String, timestampMs: Long): Long? {
        if (!SmsPermissions.canReadTelephony(context)) return null
        return runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms.THREAD_ID),
                "${Telephony.Sms.ADDRESS} = ? AND ${Telephony.Sms.DATE} >= ?",
                arrayOf(sender, (timestampMs - 5_000L).toString()),
                "${Telephony.Sms.DATE} DESC",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    private companion object {
        private const val TAG = "SmsReceiver"
    }
}

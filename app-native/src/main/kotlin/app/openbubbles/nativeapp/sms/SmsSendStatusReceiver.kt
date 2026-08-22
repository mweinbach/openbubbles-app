package app.openbubbles.nativeapp.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.openbubbles.db.Message
import app.openbubbles.nativeapp.data.CoreGraph
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Flips the staged outgoing SMS row's status directly in the store, driven by
 * the sent/delivery PendingIntents [SmsManagerSender] attached to the modem
 * send:
 *  - sent OK → [Message.sendingServiceId] cleared (bubble: SENT),
 *  - sent error → error row (bubble: FAILED),
 *  - delivery → [Message.dateDelivered] (bubble: DELIVERED).
 */
class SmsSendStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val guid = intent.getStringExtra(EXTRA_GUID) ?: return
        val transport = intent.getStringExtra(EXTRA_TRANSPORT) ?: "SMS"
        SmsIngest.seedAppContext(context) // cold-started broadcasts have no activity
        val store = CoreGraph.store ?: return

        // goAsync() detaches BroadcastReceiver.mPendingResult; its resultCode
        // getter returns 0 afterward even when Android reported RESULT_OK.
        val callbackResultCode = resultCode
        val pending = goAsync()
        SmsBridge.scope.launch(Dispatchers.IO) {
            try {
                val kind = when (intent.action) {
                    ACTION_SENT -> CarrierCallbackKind.SENT
                    ACTION_DELIVERED -> CarrierCallbackKind.DELIVERED
                    else -> return@launch
                }
                applyCarrierSendStatus(
                    store = store,
                    guid = guid,
                    kind = kind,
                    identity = CarrierCallbackIdentity(
                        recipientIndex = intent.getIntExtra(EXTRA_RECIPIENT_INDEX, 0),
                        partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0),
                    ),
                    successful = callbackResultCode == Activity.RESULT_OK,
                    failureDescription = "$transport ${kind.name.lowercase()} failed (code $callbackResultCode)",
                )
            } catch (t: Throwable) {
                Log.w(TAG, "SMS status update failed", t)
            } finally {
                if (transport == "MMS") {
                    intent.getStringExtra(com.klinker.android.send_message.MmsSentReceiver.EXTRA_FILE_PATH)
                        ?.let { path -> runCatching { File(path).delete() } }
                }
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SENT = "app.openbubbles.nativeapp.sms.SMS_SENT"
        const val ACTION_DELIVERED = "app.openbubbles.nativeapp.sms.SMS_DELIVERED"
        const val EXTRA_GUID = "sms_staged_guid"
        const val EXTRA_PART_INDEX = "sms_part_index"
        const val EXTRA_RECIPIENT_INDEX = "sms_recipient_index"
        const val EXTRA_TRANSPORT = "message_transport"

        private const val TAG = "SmsSendStatusReceiver"
    }
}

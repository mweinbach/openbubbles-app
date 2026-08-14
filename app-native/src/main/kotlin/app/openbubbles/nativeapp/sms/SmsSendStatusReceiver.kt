package app.openbubbles.nativeapp.sms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.nativeapp.data.CoreGraph
import io.objectbox.query.QueryBuilder
import java.util.Date
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
        SmsIngest.seedAppContext(context) // cold-started broadcasts have no activity
        val store = CoreGraph.store ?: return

        val pending = goAsync()
        SmsBridge.scope.launch(Dispatchers.IO) {
            try {
                val box = store.boxFor(Message::class.java)
                val row = box.query()
                    .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
                    .build().use { it.findFirst() } ?: return@launch

                when (intent.action) {
                    ACTION_SENT -> when (resultCode) {
                        Activity.RESULT_OK -> if (row.sendingServiceId != null || row.error != null) {
                            row.sendingServiceId = null
                            row.error = null
                            row.errorMessage = null
                            box.put(row)
                        }
                        else -> {
                            row.error = 1L
                            row.errorMessage = "SMS send failed (code $resultCode)"
                            row.sendingServiceId = null
                            box.put(row)
                        }
                    }
                    ACTION_DELIVERED -> if (row.dateDelivered == null) {
                        row.dateDelivered = Date()
                        box.put(row)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "SMS status update failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SENT = "app.openbubbles.nativeapp.sms.SMS_SENT"
        const val ACTION_DELIVERED = "app.openbubbles.nativeapp.sms.SMS_DELIVERED"
        const val EXTRA_GUID = "sms_staged_guid"
        const val EXTRA_PART_INDEX = "sms_part_index"

        private const val TAG = "SmsSendStatusReceiver"
    }
}

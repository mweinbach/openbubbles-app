package app.openbubbles.nativeapp.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.SmsSender
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * On-device SMS send: stage optimistically (same contract as the iMessage
 * [app.openbubbles.nativeapp.data.Sender] — the bubble appears immediately as
 * SENDING), then dispatch through [SmsManager] with sent/delivery
 * PendingIntents whose broadcasts flip the staged row directly in the store.
 *
 * Local seam note: this class is deliberately behind the [SmsSender] seam.
 * The Rust worker's future `sendSms`/sms-target UniFFI exports (routing the
 * send through the registered Mac like the Flutter app does for relayed SMS)
 * can be added as an alternative implementation; until the generated bindings
 * expose them (grep `sendSms` in rust_lib_bluebubbles.kt), the modem path is
 * the only implementation.
 */
class SmsManagerSender(private val context: Context) : SmsSender {

    override suspend fun send(chatId: Long, text: String) = withContext(Dispatchers.IO) {
        val store = CoreGraph.store ?: error("store unavailable")
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
        val chatGuid = chat.guid ?: error("chat $chatId has no guid")

        val myHandle = PushStateHolder.myHandles.firstOrNull { it.startsWith("tel:") }
            ?: chat.usingHandle
            ?: "tel:unknown"
        val myAddress = myHandle.removePrefix("tel:")

        val destinations = chat.handles.map { it.address }
            .filter { it.isNotBlank() && it != myAddress }
            .distinct()
        if (destinations.isEmpty()) error("chat $chatId has no SMS destination")

        // 1. Stage the outgoing row (temp guid, SENDING, latest-message bump).
        val tempGuid = MessageIngestor.tempGuid()
        MessageRepo(store).stageOutgoingMessage(chatGuid, myHandle, text, tempGuid)

        // 2. Dispatch via the modem; receivers flip the row's status.
        if (!SmsPermissions.canSendSms(context)) {
            fail(store, tempGuid, "SMS permission not granted")
            return@withContext
        }

        try {
            val smsManager = smsManager()
            val parts = smsManager.divideMessage(text)
            for (dest in destinations) {
                if (parts.size == 1) {
                    smsManager.sendTextMessage(
                        dest,
                        null,
                        text,
                        statusPendingIntent(tempGuid, 0, SmsSendStatusReceiver.ACTION_SENT),
                        statusPendingIntent(tempGuid, 0, SmsSendStatusReceiver.ACTION_DELIVERED),
                    )
                } else {
                    val sentIntents = ArrayList<PendingIntent>(parts.size)
                    val deliveredIntents = ArrayList<PendingIntent>(parts.size)
                    for (index in parts.indices) {
                        sentIntents.add(statusPendingIntent(tempGuid, index, SmsSendStatusReceiver.ACTION_SENT))
                        deliveredIntents.add(
                            statusPendingIntent(tempGuid, index, SmsSendStatusReceiver.ACTION_DELIVERED),
                        )
                    }
                    smsManager.sendMultipartTextMessage(dest, null, parts, sentIntents, deliveredIntents)
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "SMS send failed", t)
            fail(store, tempGuid, t.message?.take(200) ?: t.javaClass.simpleName)
        }
    }

    // ------------------------------------------------------------------
    // Internals
    // ------------------------------------------------------------------

    private fun smsManager(): SmsManager =
        if (Build.VERSION.SDK_INT >= 31) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

    private fun statusPendingIntent(guid: String, partIndex: Int, action: String): PendingIntent {
        val intent = Intent(context, SmsSendStatusReceiver::class.java)
            .setAction(action)
            .putExtra(SmsSendStatusReceiver.EXTRA_GUID, guid)
            .putExtra(SmsSendStatusReceiver.EXTRA_PART_INDEX, partIndex)
        // Unique request code per (guid, action, part) so multipart parts do
        // not collapse into one PendingIntent.
        val requestCode = (guid.hashCode() * 31 + action.hashCode() * 7 + partIndex)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun messageByGuid(store: io.objectbox.BoxStore, guid: String): Message? =
        store.boxFor(Message::class.java)
            .query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    private fun fail(store: io.objectbox.BoxStore, guid: String, message: String) {
        runCatching {
            messageByGuid(store, guid)?.apply {
                error = 1L
                errorMessage = message
                sendingServiceId = null
                dateDelivered = null
                store.boxFor(Message::class.java).put(this)
            }
        }.onFailure { Log.w(TAG, "failed to mark SMS row failed", it) }
    }

    private companion object {
        private const val TAG = "SmsManagerSender"
    }
}

package app.openbubbles.nativeapp.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.net.toUri
import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.OutgoingTextSend
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

    override suspend fun send(chatId: Long, text: String): OutgoingTextSend {
        val prepared = withContext(Dispatchers.IO) {
            val store = CoreGraph.store ?: error("store unavailable")
            val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
            val chatGuid = chat.guid ?: error("chat $chatId has no guid")
            check(chat.isRpSms == true) { "chat $chatId is not a SIM conversation" }
            check(SmsPermissions.canSendSms(context)) { "SMS permission not granted" }

            val myHandle = app.openbubbles.nativeapp.data.sendingHandle(chat)
                ?.takeIf { it.startsWith("tel:") }
                ?: PushStateHolder.myHandles.firstOrNull { it.startsWith("tel:") }
                ?: chat.usingHandle
                ?: "tel:unknown"
            val myAddress = myHandle.removePrefix("tel:")
            val destinations = chat.handles.map { it.address }
                .filter { it.isNotBlank() && it != myAddress }
                .distinct()
            check(destinations.isNotEmpty()) { "chat $chatId has no SMS destination" }

            val tempGuid = MessageIngestor.tempGuid()
            val staged = MessageRepo(store).stageOutgoingMessage(chatGuid, myHandle, text, tempGuid)
            PreparedSmsSend(
                store = store,
                chatId = chatId,
                threadId = chat.telephonyId,
                tempGuid = tempGuid,
                messageId = staged.id,
                destinations = destinations,
            )
        }
        SmsBridge.launchOutgoing(prepared.messageId) { dispatch(prepared, text) }
        return OutgoingTextSend(prepared.messageId)
    }

    private fun dispatch(prepared: PreparedSmsSend, text: String) {
        try {
            val smsManager = smsManager()
            val parts = smsManager.divideMessage(text)
            check(parts.isNotEmpty()) { "SMS has no message segments" }
            prepareCarrierSendStatus(
                store = prepared.store,
                guid = prepared.tempGuid,
                recipientCount = prepared.destinations.size,
                partCount = parts.size,
            )
            if (!SmsBridge.beginOutgoingDispatch(prepared.messageId)) return
            for ((recipientIndex, dest) in prepared.destinations.withIndex()) {
                if (parts.size == 1) {
                    smsManager.sendTextMessage(
                        dest,
                        null,
                        text,
                        statusPendingIntent(
                            prepared.tempGuid,
                            recipientIndex,
                            0,
                            SmsSendStatusReceiver.ACTION_SENT,
                        ),
                        statusPendingIntent(
                            prepared.tempGuid,
                            recipientIndex,
                            0,
                            SmsSendStatusReceiver.ACTION_DELIVERED,
                        ),
                    )
                } else {
                    val sentIntents = ArrayList<PendingIntent>(parts.size)
                    val deliveredIntents = ArrayList<PendingIntent>(parts.size)
                    for (index in parts.indices) {
                        sentIntents.add(
                            statusPendingIntent(
                                prepared.tempGuid,
                                recipientIndex,
                                index,
                                SmsSendStatusReceiver.ACTION_SENT,
                            ),
                        )
                        deliveredIntents.add(
                            statusPendingIntent(
                                prepared.tempGuid,
                                recipientIndex,
                                index,
                                SmsSendStatusReceiver.ACTION_DELIVERED,
                            ),
                        )
                    }
                    smsManager.sendMultipartTextMessage(dest, null, parts, sentIntents, deliveredIntents)
                }
            }
            val persisted = TelephonySmsStore.insertSent(
                context = context,
                addresses = prepared.destinations,
                body = text,
                dateMs = System.currentTimeMillis(),
                threadId = prepared.threadId,
            )
            if (persisted.threadId != null && prepared.threadId == null) {
                prepared.store.boxFor(Chat::class.java).get(prepared.chatId)?.let { chat ->
                    if (chat.telephonyId == null) {
                        chat.telephonyId = persisted.threadId
                        prepared.store.boxFor(Chat::class.java).put(chat)
                    }
                }
            }
        } catch (failure: Throwable) {
            Log.w(TAG, "SMS send failed", failure)
            fail(
                prepared.store,
                prepared.tempGuid,
                failure.message?.take(200) ?: failure.javaClass.simpleName,
            )
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

    private fun statusPendingIntent(
        guid: String,
        recipientIndex: Int,
        partIndex: Int,
        action: String,
    ): PendingIntent {
        val intent = Intent(context, SmsSendStatusReceiver::class.java)
            .setAction(action)
            .setData("openbubbles://sms/status/$guid/$recipientIndex/$partIndex".toUri())
            .putExtra(SmsSendStatusReceiver.EXTRA_GUID, guid)
            .putExtra(SmsSendStatusReceiver.EXTRA_RECIPIENT_INDEX, recipientIndex)
            .putExtra(SmsSendStatusReceiver.EXTRA_PART_INDEX, partIndex)
            .putExtra(SmsSendStatusReceiver.EXTRA_TRANSPORT, "SMS")
        // Extras do not participate in PendingIntent identity. Include both
        // recipient and segment in the data URI as well as the request code.
        val requestCode = carrierCallbackRequestCode(guid, action, recipientIndex, partIndex)
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

    private data class PreparedSmsSend(
        val store: io.objectbox.BoxStore,
        val chatId: Long,
        val threadId: Long?,
        val tempGuid: String,
        val messageId: Long,
        val destinations: List<String>,
    )

    private companion object {
        private const val TAG = "SmsManagerSender"
    }
}

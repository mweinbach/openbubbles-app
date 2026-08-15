package app.openbubbles.nativeapp.sms

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Attachment
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.PushStateHolder
import com.klinker.android.send_message.Message as CarrierMessage
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import io.objectbox.query.QueryBuilder
import java.io.File

/** Sends media in a SIM conversation through Android's carrier MMS service. */
class MmsManagerSender(private val context: Context) {

    suspend fun send(chatId: Long, attachment: OutgoingAttachment, caption: String?) {
        val store = CoreGraph.store ?: error("store unavailable")
        val chat = store.boxFor(Chat::class.java).get(chatId) ?: error("no chat $chatId")
        val chatGuid = chat.guid ?: error("chat $chatId has no guid")
        check(chat.isRpSms == true) { "chat $chatId is not a SIM conversation" }
        check(SmsPermissions.canSendSms(context)) { "SMS permission not granted" }

        val myHandle = PushStateHolder.myHandles.firstOrNull { it.startsWith("tel:") }
            ?: chat.usingHandle
            ?: "tel:unknown"
        val myAddress = myHandle.removePrefix("tel:")
        val destinations = chat.handles.map { it.address }
            .filter { it.isNotBlank() && it != myAddress }
            .distinct()
        check(destinations.isNotEmpty()) { "chat $chatId has no MMS destination" }

        val tempGuid = MessageIngestor.tempGuid()
        val attachmentGuid = "${tempGuid}_att0"
        val staged = MessageRepo(store).stageOutgoingMessage(
            chatGuid,
            myHandle,
            caption.orEmpty(),
            tempGuid,
        )

        val disk = AttachmentStore(store, File(context.dataDir, "app_flutter"))
        val displayName = attachment.name ?: "attachment"
        val payload = File(disk.directoryFor(attachmentGuid), disk.sanitizeFileName(displayName))

        try {
            payload.parentFile?.mkdirs()
            attachment.file.copyTo(payload, overwrite = true)
            runCatching { attachment.file.delete() }
            val attachmentBox = store.boxFor(Attachment::class.java)
            store.runInTx {
                attachmentBox.put(
                    Attachment().apply {
                        guid = attachmentGuid
                        isOutgoing = true
                        mimeType = attachment.mime
                        uti = attachment.uti
                        transferName = displayName
                        totalBytes = payload.length()
                        isDownloaded = true
                        message.target = staged
                    },
                )
                staged.hasAttachments = true
                store.boxFor(Message::class.java).put(staged)
            }

            val settings = Settings().apply {
                setUseSystemSending(true)
                setGroup(destinations.size > 1)
                setDeliveryReports(true)
            }
            val statusIntent = Intent(context, SmsSendStatusReceiver::class.java)
                .setAction(SmsSendStatusReceiver.ACTION_SENT)
                .setData(Uri.parse("openbubbles://mms/status/$tempGuid"))
                .putExtra(SmsSendStatusReceiver.EXTRA_GUID, tempGuid)
                .putExtra(SmsSendStatusReceiver.EXTRA_PART_INDEX, 0)
                .putExtra(SmsSendStatusReceiver.EXTRA_TRANSPORT, "MMS")
            val transaction = Transaction(context.applicationContext, settings)
                .setExplicitBroadcastForSentMms(statusIntent)
            val message = CarrierMessage(caption.orEmpty(), destinations.toTypedArray()).apply {
                setFromAddress(myAddress.takeUnless { it == "unknown" })
                setSave(false)
                addMedia(payload.readBytes(), attachment.mime, displayName)
            }
            transaction.sendNewMessage(message, chat.telephonyId ?: Transaction.NO_THREAD_ID)
        } catch (failure: Throwable) {
            Log.w(TAG, "MMS send failed", failure)
            fail(store, tempGuid, failure.message ?: failure.javaClass.simpleName)
            throw failure
        }
    }

    private fun fail(store: io.objectbox.BoxStore, guid: String, reason: String) {
        store.boxFor(Message::class.java)
            .query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
            ?.apply {
                error = 1L
                errorMessage = reason.take(200)
                sendingServiceId = null
                store.boxFor(Message::class.java).put(this)
            }
    }

    private companion object {
        private const val TAG = "MmsManagerSender"
    }
}

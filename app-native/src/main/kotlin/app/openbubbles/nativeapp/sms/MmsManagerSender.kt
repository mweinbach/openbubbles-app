package app.openbubbles.nativeapp.sms

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.net.toUri
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.nativeapp.data.AttachmentSender
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.OutgoingAttachmentSend
import app.openbubbles.nativeapp.data.OutgoingPayloadStage
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.stageOutgoingPayloadBatch
import com.klinker.android.send_message.Message as CarrierMessage
import com.klinker.android.send_message.Settings
import com.klinker.android.send_message.Transaction
import io.objectbox.query.QueryBuilder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sends media in a SIM conversation through Android's carrier MMS service. */
class MmsManagerSender(private val context: Context) : AttachmentSender {

    override suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
    ): OutgoingAttachmentSend {
        require(attachments.isNotEmpty()) { "attachment send requires at least one attachment" }
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
            check(destinations.isNotEmpty()) { "chat $chatId has no MMS destination" }

            val tempGuid = MessageIngestor.tempGuid()
            val disk = AttachmentStore(store, File(context.dataDir, "app_flutter"))
            val media = attachments.mapIndexed { index, attachment ->
                val attachmentGuid = "${tempGuid}_att$index"
                val displayName = attachment.name ?: "attachment"
                val payload = File(disk.directoryFor(attachmentGuid), disk.sanitizeFileName(displayName))
                StagedMedia(attachmentGuid, attachment.mime, attachment.uti, displayName, payload)
            }
            try {
                stageOutgoingPayloadBatch(
                    stages = attachments.mapIndexed { index, attachment ->
                        OutgoingPayloadStage(attachment.file, media[index].payload)
                    },
                    cacheRoot = context.cacheDir,
                ) { payloads ->
                    val message = MessageRepo(store).stageOutgoingMessageWithAttachments(
                        chatGuid = chatGuid,
                        sender = myHandle,
                        text = caption.orEmpty(),
                        stagingGuid = tempGuid,
                        attachments = media.mapIndexed { index, item ->
                            MessageRepo.OutgoingAttachmentStage(
                                guid = item.guid,
                                mimeType = item.mime,
                                uti = item.uti,
                                transferName = item.name,
                                totalBytes = payloads[index].length(),
                            )
                        },
                    )
                    PreparedMmsSend(
                        store = store,
                        chatId = chatId,
                        existingThreadId = chat.telephonyId,
                        tempGuid = tempGuid,
                        messageId = message.id,
                        myAddress = myAddress,
                        destinations = destinations,
                        media = media,
                    )
                }
            } catch (failure: Throwable) {
                media.forEach { disk.directoryFor(it.guid).deleteRecursively() }
                throw failure
            }
        }

        SmsBridge.scope.launch {
            try {
                val threadId = prepared.existingThreadId
                    ?: TelephonySmsStore.threadId(context, prepared.destinations)
                if (threadId != null && prepared.existingThreadId == null) {
                    prepared.store.boxFor(Chat::class.java).get(prepared.chatId)?.let { chat ->
                        chat.telephonyId = threadId
                        prepared.store.boxFor(Chat::class.java).put(chat)
                    }
                }
                val settings = Settings().apply {
                    setUseSystemSending(true)
                    setGroup(prepared.destinations.size > 1)
                    setDeliveryReports(true)
                }
                val statusIntent = Intent(context, SmsSendStatusReceiver::class.java)
                    .setAction(SmsSendStatusReceiver.ACTION_SENT)
                    .setData("openbubbles://mms/status/${prepared.tempGuid}".toUri())
                    .putExtra(SmsSendStatusReceiver.EXTRA_GUID, prepared.tempGuid)
                    .putExtra(SmsSendStatusReceiver.EXTRA_PART_INDEX, 0)
                    .putExtra(SmsSendStatusReceiver.EXTRA_TRANSPORT, "MMS")
                val transaction = Transaction(context.applicationContext, settings)
                    .setExplicitBroadcastForSentMms(statusIntent)
                val message = CarrierMessage(caption.orEmpty(), prepared.destinations.toTypedArray()).apply {
                    setFromAddress(prepared.myAddress.takeUnless { it == "unknown" })
                    setSave(SmsRole.isHeld(context))
                    prepared.media.forEach { item ->
                        addMedia(item.payload.readBytes(), item.mime, item.name)
                    }
                }
                transaction.sendNewMessage(message, threadId ?: Transaction.NO_THREAD_ID)
            } catch (failure: Throwable) {
                Log.w(TAG, "MMS send failed after local staging", failure)
                fail(
                    prepared.store,
                    prepared.tempGuid,
                    failure.message ?: failure.javaClass.simpleName,
                )
            }
        }
        return OutgoingAttachmentSend(prepared.messageId)
    }

    private data class StagedMedia(
        val guid: String,
        val mime: String,
        val uti: String,
        val name: String,
        val payload: File,
    )

    private data class PreparedMmsSend(
        val store: io.objectbox.BoxStore,
        val chatId: Long,
        val existingThreadId: Long?,
        val tempGuid: String,
        val messageId: Long,
        val myAddress: String,
        val destinations: List<String>,
        val media: List<StagedMedia>,
    )

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

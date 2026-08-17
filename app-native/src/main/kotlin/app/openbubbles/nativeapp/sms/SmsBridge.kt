package app.openbubbles.nativeapp.sms

import android.util.Log
import app.openbubbles.db.Chat
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.AttachmentSender
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.OutgoingAttachmentSend
import app.openbubbles.nativeapp.data.OutgoingTextSend
import app.openbubbles.nativeapp.data.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

/**
 * Composition root + routing seam for the on-device SMS subsystem.
 *
 * [routeIfSmsChat] is the single hook the chat UI uses: chats flagged
 * `isRpSms` (SIM-routed conversations, exactly the set the ingestor creates
 * for incoming SMS) send over the modem via [SmsManagerSender] instead of the
 * APNs [app.openbubbles.nativeapp.data.Sender].
 */
object SmsBridge {

    /** Shared IO scope for receiver-driven ingest + status flips. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Provider MMS row ids already ingested this process (bounded). */
    val seenMmsIds: MutableSet<Long> = object : LinkedHashSet<Long>() {
        override fun add(value: Long): Boolean {
            if (size >= 256) iterator().let { if (it.hasNext()) { it.next(); it.remove() } }
            return super.add(value)
        }
    }

    /**
     * SMS send path behind the [SmsSender] seam (modem implementation);
     * resolved per call so a late-arriving app context is picked up.
     */
    val sender: SmsSender
        get() {
            val context = AppContext.current
            return if (context != null && CoreGraph.store != null) SmsManagerSender(context)
            else UnavailableSmsSender
        }

    val attachmentSender: AttachmentSender
        get() {
            val context = AppContext.current
            return if (context != null && CoreGraph.store != null) MmsManagerSender(context)
            else UnavailableMmsSender
        }

    /**
     * Sends [text] over SMS when [chatId] is an isRpSms chat. Returns true
     * when the send was routed (and is therefore consumed), false when the
     * chat should go through the iMessage sender instead.
     */
    suspend fun routeIfSmsChat(chatId: Long, text: String): Boolean = withContext(Dispatchers.IO) {
        val store = CoreGraph.store ?: return@withContext false
        val chat = runCatching { store.boxFor(Chat::class.java).get(chatId) }.getOrNull()
            ?: return@withContext false
        routeSmsTransport(
            isSmsChat = chat.isRpSms == true,
            send = { sender.send(chatId, text) },
            onStagedFailure = { failure ->
                Log.w("SmsBridge", "SMS transport failed after local staging", failure)
            },
        )
    }

    /**
     * MMS twin of [routeIfSmsChat]. Media selected in a SIM conversation is
     * composed and sent by Android's carrier MMS service instead of entering
     * the iMessage/MMCS uploader. All staged attachments ride one MMS.
     */
    suspend fun routeAttachmentsIfSmsChat(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val store = CoreGraph.store ?: return@withContext false
        val chat = runCatching { store.boxFor(Chat::class.java).get(chatId) }.getOrNull()
            ?: return@withContext false
        if (chat.isRpSms != true) return@withContext false
        val context = AppContext.current
            ?: error("MMS sender unavailable - app context missing")
        MmsManagerSender(context).send(chatId, attachments, caption)
        true
    }
}

internal class SmsSendAlreadyStagedException(cause: Throwable) :
    Exception(cause.message ?: cause.javaClass.simpleName, cause)

internal suspend fun routeSmsTransport(
    isSmsChat: Boolean,
    send: suspend () -> Unit,
    onStagedFailure: (Throwable) -> Unit = {},
): Boolean {
    if (!isSmsChat) return false
    try {
        send()
    } catch (failure: SmsSendAlreadyStagedException) {
        onStagedFailure(failure.cause ?: failure)
    }
    return true
}

/** Fallback used before the app context/store exist (nothing can be sent). */
private object UnavailableSmsSender : SmsSender {
    override suspend fun send(chatId: Long, text: String): OutgoingTextSend =
        error("SMS sender unavailable — store not open")
}

private object UnavailableMmsSender : AttachmentSender {
    override suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
    ): OutgoingAttachmentSend = error("MMS sender unavailable — store not open")
}

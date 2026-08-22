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
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
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

    private val carrierDispatchLock = Any()
    private val carrierDispatches = mutableMapOf<Long, CarrierDispatch>()

    private class CarrierDispatch {
        lateinit var job: Job
        var cancelled = false
        var dispatched = false
    }

    /** Carrier work survives Apple sign-out, but remains cancellable until modem dispatch. */
    internal fun launchOutgoing(messageId: Long, block: suspend () -> Unit): Job {
        val dispatch = CarrierDispatch()
        synchronized(carrierDispatchLock) {
            check(!carrierDispatches.containsKey(messageId)) {
                "Carrier message $messageId already has a dispatch owner"
            }
            dispatch.job = scope.launch(start = CoroutineStart.LAZY) { block() }
            carrierDispatches[messageId] = dispatch
            dispatch.job.invokeOnCompletion {
                synchronized(carrierDispatchLock) {
                    if (carrierDispatches[messageId] === dispatch) {
                        carrierDispatches.remove(messageId)
                    }
                }
            }
        }
        dispatch.job.start()
        return dispatch.job
    }

    /** Claims the irreversible modem boundary; cancellation and the claim cannot cross. */
    internal fun beginOutgoingDispatch(messageId: Long): Boolean = synchronized(carrierDispatchLock) {
        val dispatch = carrierDispatches[messageId] ?: return@synchronized false
        if (dispatch.cancelled || dispatch.dispatched) return@synchronized false
        dispatch.dispatched = true
        true
    }

    /** Returns true only when the queued carrier job stopped before any modem call. */
    suspend fun cancelOutgoing(messageId: Long): Boolean {
        val job = synchronized(carrierDispatchLock) {
            val dispatch = carrierDispatches[messageId] ?: return false
            if (dispatch.cancelled || dispatch.dispatched) return false
            dispatch.cancelled = true
            dispatch.job
        }
        job.cancelAndJoin()
        return true
    }

    /** Provider messages become deduplicated only after every payload is durably published. */
    internal val mmsIngestionGate = MmsIngestionGate()

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

package app.openbubbles.nativeapp.sms

import android.content.Context
import app.openbubbles.db.Chat
import app.openbubbles.nativeapp.data.AppContext
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.LiveMessageArrivals
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.service.Notifications
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UPushMessage

/**
 * Shared receive pipeline for the SMS/MMS receivers: ingest the fabricated
 * [UPushMessage] through the core intake (identical to the APNs path), track
 * the telephony thread on the chat row, and post the incoming notification.
 *
 * Cold-start note: receivers can launch the process without any activity, so
 * the app context is seeded here before [CoreGraph]'s lazy store needs it.
 */
internal object SmsIngest {

    /**
     * Ingests one incoming SMS/MMS payload.
     *
     * @param notificationText preview body for the notification.
     * @param telephonyThreadId provider thread id when known (chat row keeps
     *   it in [Chat.telephonyId] for telephony-side correlation).
     * @return the affected chat id, or null when nothing was ingested.
     */
    suspend fun ingestIncoming(
        context: Context,
        push: UPushMessage,
        notificationText: String,
        telephonyThreadId: Long? = null,
    ): Long? {
        seedAppContext(context)

        val ingestor = CoreGraph.ingestor ?: return null
        val store = CoreGraph.store ?: return null

        val result = ingestor.ingestWithResult(push, PushStateHolder.myHandles)
        val chat = result.chat ?: return null

        // Track the telephony thread id on first sight (senders/notifications
        // key off it later; never overwrite an existing binding).
        if (telephonyThreadId != null && chat.telephonyId == null) {
            runCatching {
                chat.telephonyId = telephonyThreadId
                store.boxFor(Chat::class.java).put(chat)
            }
        }

        liveArrivalGuid(push, result.isNewIncomingMessage)?.let(LiveMessageArrivals::publish)
        notifyIncoming(context, push, chat, notificationText)
        return chat.id
    }

    /** Notification semantics mirror NativePushService.notifyIncoming. */
    private fun notifyIncoming(context: Context, push: UPushMessage, chat: Chat, text: String) {
        val inst = (push as? UPushMessage.IMessage)?.inst ?: return
        val sender = inst.sender ?: return
        if (sender in PushStateHolder.myHandles) return
        if (app.openbubbles.core.model.ChatMute.shouldMute(chat, sender, text)) return
        val guid = chat.guid ?: return
        val identity = CoreGraph.messageNotificationIdentity(chat, sender)
        Notifications.postIncoming(
            context = context,
            chatId = chat.id,
            chatGuid = guid,
            title = identity.title,
            text = text,
            isGroup = identity.isGroup,
            senderName = identity.senderName,
            messageGuid = inst.id,
        )
    }

    /**
     * Receivers may cold-start the process (no activity ran yet); CoreGraph's
     * store path goes through this static, so make sure it is populated.
     */
    fun seedAppContext(context: Context) {
        AppContext.initialize(context)
    }
}

/** Mirrors the APNs intake boundary without publishing reactions or redeliveries. */
internal fun liveArrivalGuid(push: UPushMessage, newlyIngested: Boolean): String? {
    if (!newlyIngested) return null
    val inst = (push as? UPushMessage.IMessage)?.inst ?: return null
    return inst.id.takeIf { inst.message is UMessage.Normal }
}

package app.openbubbles.nativeapp.sms

import android.content.Context
import app.openbubbles.core.model.isGroupConversation
import app.openbubbles.db.Chat
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.service.Notifications
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

        val chat = ingestor.ingest(push, PushStateHolder.myHandles) ?: return null

        // Track the telephony thread id on first sight (senders/notifications
        // key off it later; never overwrite an existing binding).
        if (telephonyThreadId != null && chat.telephonyId == null) {
            runCatching {
                chat.telephonyId = telephonyThreadId
                store.boxFor(Chat::class.java).put(chat)
            }
        }

        notifyIncoming(context, push, chat, notificationText)
        return chat.id
    }

    /** Notification semantics mirror NativePushService.notifyIncoming. */
    private fun notifyIncoming(context: Context, push: UPushMessage, chat: Chat, text: String) {
        val inst = (push as? UPushMessage.IMessage)?.inst ?: return
        val sender = inst.sender ?: return
        if (sender in PushStateHolder.myHandles) return
        if (!chat.muteType.isNullOrEmpty()) return
        val guid = chat.guid ?: return
        val isGroup = chat.isGroupConversation()
        Notifications.postIncoming(
            context = context,
            chatId = chat.id,
            chatGuid = guid,
            title = if (isGroup) {
                sender.removePrefix("tel:").removePrefix("mailto:")
            } else {
                chat.displayName
                    ?: chat.handles.firstOrNull()?.formattedAddress
                    ?: sender.removePrefix("tel:").removePrefix("mailto:")
            },
            text = text,
            isGroup = isGroup,
            senderName = sender.removePrefix("tel:").removePrefix("mailto:"),
        )
    }

    /**
     * Receivers may cold-start the process (no activity ran yet); CoreGraph's
     * store path goes through this static, so make sure it is populated.
     */
    fun seedAppContext(context: Context) {
        if (NativeMainActivity.appContext == null) {
            NativeMainActivity.appContext = context.applicationContext
        }
    }
}

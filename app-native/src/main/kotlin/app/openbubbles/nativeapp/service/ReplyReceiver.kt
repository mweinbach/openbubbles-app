package app.openbubbles.nativeapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import app.openbubbles.core.repo.ChatRepo
import app.openbubbles.db.Chat
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.UConversation
import uniffi.rust_lib_bluebubbles.UPushMessage

/**
 * Inline-reply action from a message notification — the counterpart of the
 * Flutter app's InternalIntentReceiver "ReplyChat" branch: extracts the
 * RemoteInput text, sends it through the live Rust push state, ingests the
 * echo so the message lands in the chat, marks the chat read, and updates the
 * notification with the sent reply.
 *
 * sendText is a blocking UniFFI call, and receivers are short-lived, so the
 * work runs on [Dispatchers.IO] under [goAsync].
 */
class ReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val replyText = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(Notifications.KEY_TEXT_REPLY)?.toString()?.trim()
        if (replyText.isNullOrEmpty()) return

        val chatId = intent.getLongExtra(Notifications.EXTRA_CHAT_ID, 0L)
        val chatGuid = intent.getStringExtra(Notifications.EXTRA_CHAT_GUID)
        val notificationId = intent.getIntExtra(Notifications.EXTRA_NOTIFICATION_ID, -1)
        if (chatId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sendReply(context, chatId, chatGuid, notificationId, replyText)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun sendReply(
        context: Context,
        chatId: Long,
        chatGuid: String?,
        notificationId: Int,
        text: String,
    ) {
        val store = CoreGraph.store ?: return
        val pushState = PushStateHolder.state ?: return
        val chat = runCatching { store.boxFor(Chat::class.java).get(chatId) }.getOrNull() ?: return
        val sender = app.openbubbles.nativeapp.data.sendingHandle(chat) ?: return

        val sent = runCatching {
            val inst = pushState.sendText(
                UConversation(
                    participants = chat.handles.map { it.address }.distinct(),
                    cvName = chat.displayName,
                    senderGuid = null,
                    afterGuid = null,
                ),
                sender,
                text,
                // replyGuid, replyPart, effect, subject
                null, null, null, null,
            )
            // Ingest the echo so the sent message (and its receipts) flow
            // through the normal intake path, same as the in-app send path.
            CoreGraph.ingestor?.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
        }.isSuccess

        runCatching { ChatRepo(store).markRead(chatId) }
        Notifications.cancelForChat(context, chatId)
        if (sent) {
            val guid = chatGuid ?: chat.guid
            val title = chat.displayName
                ?: chat.handles.firstOrNull()?.formattedAddress
                ?: "Message"
            Notifications.postReplySent(context, notificationId, chatId, guid, title, text)
        }
    }
}

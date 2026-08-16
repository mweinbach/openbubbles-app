package app.openbubbles.nativeapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import app.openbubbles.db.Chat
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.sendConversation
import app.openbubbles.nativeapp.sms.SmsBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        val messageGuid = intent.getStringExtra(Notifications.EXTRA_MESSAGE_GUID)
        val notificationId = intent.getIntExtra(Notifications.EXTRA_NOTIFICATION_ID, -1)
        if (chatId <= 0L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                sendReply(context, chatId, chatGuid, messageGuid, notificationId, replyText)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun sendReply(
        context: Context,
        chatId: Long,
        chatGuid: String?,
        messageGuid: String?,
        notificationId: Int,
        text: String,
    ) {
        var resolvedGuid = chatGuid
        var title = "Message"
        fun fail(reason: String, error: Throwable? = null) {
            if (error != null) Log.e(TAG, reason, error) else Log.e(TAG, reason)
            PushStateHolder.reportError(reason)
            Notifications.postReplyFailed(
                context = context,
                notificationId = notificationId,
                chatId = chatId,
                chatGuid = resolvedGuid,
                title = title,
            )
        }

        val store = CoreGraph.store ?: run {
            fail("Reply not sent: message store unavailable")
            return
        }
        val chat = runCatching { store.boxFor(Chat::class.java).get(chatId) }.getOrNull() ?: run {
            fail("Reply not sent: conversation unavailable")
            return
        }
        resolvedGuid = chatGuid ?: chat.guid
        title = chat.displayName
            ?: chat.handles.firstOrNull()?.formattedAddress
            ?: "Message"
        val afterGuid = chat.dbLatestMessage.target?.let { it.stagingGuid ?: it.guid }

        if (notificationReplyTransport(chat) == NotificationReplyTransport.SMS) {
            runCatching { SmsBridge.sender.send(chatId, text) }.getOrElse { error ->
                fail("Reply not sent: ${error.message ?: error.javaClass.simpleName}", error)
                return
            }
        } else {
            val pushState = PushStateHolder.state ?: run {
                fail("Reply not sent: Apple push is disconnected")
                return
            }
            val sender = app.openbubbles.nativeapp.data.sendingHandle(chat) ?: run {
                fail("Reply not sent: no registered sending address")
                return
            }
            val inst = runCatching {
                pushState.sendText(
                    sendConversation(chat, afterGuid),
                    sender,
                    text,
                    // replyGuid, replyPart, effect, subject
                    null, null, null, null,
                )
            }.getOrElse { error ->
                fail("Reply not sent: ${error.message ?: error.javaClass.simpleName}", error)
                return
            }

            // The network send succeeded. A local-echo failure should be visible,
            // but must not claim the already-sent reply failed.
            runCatching {
                CoreGraph.ingestor?.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
            }.onFailure { error ->
                Log.e(TAG, "reply sent but local echo ingest failed", error)
                PushStateHolder.reportError("Reply sent, but its local copy could not be saved")
            }
        }

        runCatching { CoreGraph.readReceipts.markRead(chatId, messageGuid ?: afterGuid) }
        Notifications.cancelForChat(context, chatId)
        Notifications.postReplySent(
            context,
            notificationId,
            chatId,
            resolvedGuid ?: "chat-$chatId",
            title,
            text,
        )
    }

    private companion object {
        const val TAG = "ReplyReceiver"
    }
}

internal enum class NotificationReplyTransport { SMS, IMESSAGE }

internal fun notificationReplyTransport(chat: Chat): NotificationReplyTransport =
    if (chat.isRpSms == true) NotificationReplyTransport.SMS else NotificationReplyTransport.IMESSAGE

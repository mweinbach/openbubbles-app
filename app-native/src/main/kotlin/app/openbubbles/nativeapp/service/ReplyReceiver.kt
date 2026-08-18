package app.openbubbles.nativeapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import app.openbubbles.db.Chat
import app.openbubbles.nativeapp.data.CoreGraph
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.failOutgoingText
import app.openbubbles.nativeapp.data.promoteOutgoingText
import app.openbubbles.nativeapp.data.sendConversation
import app.openbubbles.nativeapp.data.stageOutgoingText
import app.openbubbles.nativeapp.sms.SmsBridge
import io.objectbox.BoxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uniffi.rust_lib_bluebubbles.UMessageInst
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
                val failure = runNotificationReplySafely {
                    sendReply(context, chatId, chatGuid, messageGuid, notificationId, replyText)
                }
                failure?.let { error ->
                    reportReplyFailure(
                        context = context,
                        notificationId = notificationId,
                        chatId = chatId,
                        chatGuid = chatGuid,
                        title = "Message",
                        reason = "Reply not sent: ${error.message ?: error.javaClass.simpleName}",
                        error = error,
                    )
                }
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
            reportReplyFailure(
                context = context,
                notificationId = notificationId,
                chatId = chatId,
                chatGuid = resolvedGuid,
                title = title,
                reason = reason,
                error = error,
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
        val identity = CoreGraph.messageNotificationIdentity(chat)
        title = identity.title
        val afterGuid = chat.dbLatestMessage.target?.let { it.stagingGuid ?: it.guid }

        if (notificationReplyTransport(chat) == NotificationReplyTransport.SMS) {
            runCatching { SmsBridge.sender.send(chatId, text) }.getOrElse { error ->
                fail("Reply not sent: ${error.message ?: error.javaClass.simpleName}", error)
                return
            }
        } else {
            val pushState = awaitNotificationReplyState(
                currentState = { PushStateHolder.state },
                startService = { NativePushService.start(context.applicationContext) },
                stateFlow = PushStateHolder.stateFlow,
                timeoutMs = PUSH_RESTORE_TIMEOUT_MS,
            ) ?: run {
                fail(PushStateHolder.lastError?.let { "Reply not sent: $it" }
                    ?: "Reply not sent: Apple push is disconnected")
                return
            }
            val sender = app.openbubbles.nativeapp.data.sendingHandle(chat) ?: run {
                fail("Reply not sent: no registered sending address")
                return
            }
            val persistedChatGuid = chat.guid ?: run {
                fail("Reply not sent: conversation identifier unavailable")
                return
            }
            val result = runCatching {
                sendAppleNotificationReply(
                    store = store,
                    chatGuid = persistedChatGuid,
                    sender = sender,
                    text = text,
                    send = {
                        pushState.sendText(
                            sendConversation(chat, afterGuid, sender),
                            sender,
                            text,
                            // replyGuid, replyPart, effect, subject
                            null, null, null, null,
                        )
                    },
                    ingest = { inst ->
                        val ingestor = CoreGraph.ingestor ?: error("message ingestor unavailable")
                        ingestor.ingest(UPushMessage.IMessage(inst), PushStateHolder.myHandles)
                    },
                )
            }.getOrElse { error ->
                fail("Reply not sent: ${error.message ?: error.javaClass.simpleName}", error)
                return
            }

            result.localEchoError?.let { error ->
                Log.e(TAG, "reply sent but local echo ingest failed", error)
                PushStateHolder.reportError("Reply sent, but its local copy could not be saved")
            }
        }

        runCatching { CoreGraph.readReceipts.markRead(chatId, messageGuid ?: afterGuid) }
        runCatching {
            Notifications.cancelForChat(context, chatId)
            Notifications.postReplySent(
                context,
                notificationId,
                chatId,
                resolvedGuid ?: "chat-$chatId",
                title,
                text,
                isGroup = identity.isGroup,
            )
        }.onFailure { error ->
            Log.e(TAG, "reply sent but notification confirmation failed", error)
        }
    }

    private companion object {
        const val TAG = "ReplyReceiver"
        const val PUSH_RESTORE_TIMEOUT_MS = 8_000L
    }
}

private fun reportReplyFailure(
    context: Context,
    notificationId: Int,
    chatId: Long,
    chatGuid: String?,
    title: String,
    reason: String,
    error: Throwable?,
) {
    if (error != null) Log.e("ReplyReceiver", reason, error) else Log.e("ReplyReceiver", reason)
    runCatching { PushStateHolder.reportError(reason) }
        .onFailure { Log.e("ReplyReceiver", "failed to record notification reply error", it) }
    runCatching {
        Notifications.postReplyFailed(
            context = context,
            notificationId = notificationId,
            chatId = chatId,
            chatGuid = chatGuid,
            title = title,
        )
    }.onFailure { Log.e("ReplyReceiver", "failed to post notification reply error", it) }
}

internal suspend fun runNotificationReplySafely(
    sendReply: suspend () -> Unit,
): Throwable? = try {
    sendReply()
    null
} catch (error: Throwable) {
    error
}

internal suspend fun <T : Any> awaitNotificationReplyState(
    currentState: () -> T?,
    startService: () -> Boolean,
    stateFlow: Flow<T?>,
    timeoutMs: Long,
): T? {
    currentState()?.let { return it }
    if (!startService()) return currentState()
    return withTimeoutOrNull(timeoutMs) {
        stateFlow.filterNotNull().first()
    } ?: currentState()
}

internal enum class NotificationReplyTransport { SMS, IMESSAGE }

internal fun notificationReplyTransport(chat: Chat): NotificationReplyTransport =
    if (chat.isRpSms == true) NotificationReplyTransport.SMS else NotificationReplyTransport.IMESSAGE

internal data class NotificationReplySendResult(
    val localEchoError: Throwable?,
)

internal suspend fun sendAppleNotificationReply(
    store: BoxStore,
    chatGuid: String,
    sender: String,
    text: String,
    send: suspend () -> UMessageInst,
    ingest: suspend (UMessageInst) -> Unit,
): NotificationReplySendResult {
    val stage = stageOutgoingText(store, chatGuid, sender, text)
    val inst = try {
        send()
    } catch (error: Throwable) {
        failOutgoingText(store, stage.tempGuid, error.message ?: error.javaClass.simpleName)
        throw error
    }
    val promotionError = runCatching {
        checkNotNull(promoteOutgoingText(store, stage.tempGuid, inst.id)) {
            "staged notification reply disappeared"
        }
    }.exceptionOrNull()
    val localEchoError = promotionError ?: runCatching { ingest(inst) }.exceptionOrNull()
    return NotificationReplySendResult(localEchoError)
}

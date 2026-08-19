package app.openbubbles.nativeapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.openbubbles.nativeapp.data.CoreGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** One-tap heart/like/etc. from an incoming-message notification. */
class TapbackReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getLongExtra(Notifications.EXTRA_CHAT_ID, 0L)
        val messageGuid = intent.getStringExtra(Notifications.EXTRA_MESSAGE_GUID)
        val reactionIndex = intent.getIntExtra(Notifications.EXTRA_TAPBACK_INDEX, 0)
        if (chatId <= 0L || messageGuid.isNullOrBlank()) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    CoreGraph.messageActions.react(
                        chatId = chatId,
                        messageGuid = messageGuid,
                        messageText = "",
                        messagePart = 0L,
                        reactionIndex = reactionIndex.coerceIn(0, 5),
                        emoji = null,
                        enable = true,
                    )
                }.onFailure { error ->
                    Log.e(TAG, "notification tapback failed", error)
                }
                runCatching { Notifications.cancelForChat(context, chatId) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "TapbackReceiver"
    }
}

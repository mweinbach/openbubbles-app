package app.openbubbles.nativeapp.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.openbubbles.core.repo.ChatRepo
import app.openbubbles.nativeapp.data.CoreGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Mark As Read" action and the swipe-dismiss deleteIntent — the counterpart
 * of the Flutter app's "MarkChatRead" / "DeleteNotification" intents: marks
 * the chat read (hasUnreadMessage=false, last-read pointer) and, when invoked
 * from the action button (rather than a silent swipe), cancels the chat's
 * notifications. DB writes run on [Dispatchers.IO] under [goAsync].
 */
class MarkReadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val chatId = intent.getLongExtra(Notifications.EXTRA_CHAT_ID, 0L)
        if (chatId <= 0L) return
        val cancelNotifications =
            intent.getBooleanExtra(Notifications.EXTRA_CANCEL_NOTIFICATIONS, true)

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                runCatching {
                    CoreGraph.store?.let { ChatRepo(it).markRead(chatId) }
                }
                if (cancelNotifications) Notifications.cancelForChat(context, chatId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

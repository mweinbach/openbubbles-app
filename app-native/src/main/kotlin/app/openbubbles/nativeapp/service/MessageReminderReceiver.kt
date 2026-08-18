package app.openbubbles.nativeapp.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.openbubbles.nativeapp.NativeMainActivity
import app.openbubbles.nativeapp.R
import app.openbubbles.nativeapp.data.MessageItem

object MessageReminders {
    private const val CHANNEL_ID = "message_reminders"
    private const val EXTRA_CHAT_GUID = "reminder_chat_guid"
    private const val EXTRA_TITLE = "reminder_title"
    private const val EXTRA_TEXT = "reminder_text"

    fun schedule(context: Context, chatGuid: String, chatTitle: String, message: MessageItem, atMillis: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val requestCode = (message.id xor atMillis).hashCode()
        val pending = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, MessageReminderReceiver::class.java)
                .putExtra(EXTRA_CHAT_GUID, chatGuid)
                .putExtra(EXTRA_TITLE, chatTitle)
                .putExtra(EXTRA_TEXT, message.text.ifBlank { message.attachmentMeta?.name ?: "Attachment" }),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
    }

    internal fun post(context: Context, chatGuid: String, title: String, text: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Message reminders", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            chatGuid.hashCode(),
            Intent(context, NativeMainActivity::class.java)
                .putExtra(NativeMainActivity.EXTRA_CHAT_GUID, chatGuid),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        manager.notify(
            (chatGuid + text).hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_message)
                .setContentTitle("Reminder: $title")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build(),
        )
    }

    internal fun chatGuid(intent: Intent): String? = intent.getStringExtra(EXTRA_CHAT_GUID)
    internal fun title(intent: Intent): String = intent.getStringExtra(EXTRA_TITLE).orEmpty()
    internal fun text(intent: Intent): String = intent.getStringExtra(EXTRA_TEXT).orEmpty()
}

class MessageReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val guid = MessageReminders.chatGuid(intent) ?: return
        MessageReminders.post(context, guid, MessageReminders.title(intent), MessageReminders.text(intent))
    }
}

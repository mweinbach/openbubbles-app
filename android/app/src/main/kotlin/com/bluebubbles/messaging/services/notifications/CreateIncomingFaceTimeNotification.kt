package com.bluebubbles.messaging.services.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.bluebubbles.messaging.Constants
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.R
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.services.facetime.FaceTimeActivity
import com.bluebubbles.messaging.services.intents.InternalIntentReceiver
import com.bluebubbles.messaging.utils.Utils
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class CreateIncomingFaceTimeNotification: MethodCallHandlerImpl() {
    companion object {
        const val tag = "create-incoming-facetime-notification"

        val avatarCache = mutableMapOf<String, Bitmap?>()
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        // channel details
        val channelId: String = call.argument("channel_id")!!
        val notificationId: Int = call.argument("notification_id")!!
        // call details
        val callUuid: String? = call.argument("call_uuid")!!
        val title: String = call.argument("title")!!
        val link: String? = call.argument("link")!!
        var username: String = call.argument("name")!!

        var poster: String? = call.argument("poster")

        // contact details
        val callerName: String = call.argument("caller")!!
        val callerIcon: ByteArray? = call.argument("caller_avatar")
        val callerBitmap = if ((callerIcon?.size ?: 0) == 0) null else BitmapFactory.decodeByteArray(callerIcon!!, 0, callerIcon.size)
        val callerIconCompat = if ((callerIcon?.size ?: 0) == 0) null else Utils.getAdaptiveIconFromByteArray(callerIcon!!)

        if (callerBitmap != null) {
            avatarCache[callUuid!!] = callerBitmap
        }

        // build the caller object
        val caller = Person.Builder()
            .setName(callerName)
            .setIcon(callerIconCompat)
            .setImportant(true)
            .build()

        // create a bundle for extra info
        val extras = Bundle()
        extras.putString("callUuid", callUuid)

        Log.i("FaceTime", "Creating notification for call $callUuid")

        // intent to open the app
        val openSummaryIntent = PendingIntent.getActivity(
            context,
            notificationId + Constants.pendingIntentOpenFaceTimeOffset,
            Intent(context, FaceTimeActivity::class.java)
                .putExtras(extras)
                .putExtra("answer", false)
                .putExtra("link", link)
                .putExtra("name", username)
                .putExtra("notificationId", notificationId.toString())
                .putExtra("desc", title)
                .putExtra("poster", poster),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for answering and opening the facetime link
        val answerIntent = PendingIntent.getActivity(
            context,
            notificationId + Constants.pendingIntentAnswerFaceTimeOffset,
            Intent(context, FaceTimeActivity::class.java)
                .putExtras(extras)
                .putExtra("answer", true)
                .putExtra("link", link)
                .putExtra("name", username)
                .putExtra("notificationId", notificationId.toString())
                .putExtra("desc", title)
                .putExtra("poster", poster),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for declining the facetime
        val declineIntent = PendingIntent.getBroadcast(
            context,
            notificationId + Constants.pendingIntentDeclineFaceTimeOffset,
            Intent(context, InternalIntentReceiver::class.java)
                .putExtras(extras)
                .putExtra("notificationId", notificationId.toString())
                .setType("DeclineFaceTime"),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_stat_icon)
            .setAutoCancel(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(caller, declineIntent, answerIntent))
            .addExtras(extras)
            .setColor(4888294)
        if (callerBitmap != null) {
            notificationBuilder.setLargeIcon(callerBitmap)
        }
        notificationBuilder.setContentIntent(openSummaryIntent)
        notificationBuilder.setFullScreenIntent(openSummaryIntent, true)
        // clear after 30 seconds in case we didn't get an event from the server
        notificationBuilder.setTimeoutAfter(30000);

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notification = notificationBuilder.build()
        // loop ringtone
        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT

        notificationManager.notify(Constants.newFaceTimeNotificationTag, notificationId, notification)
        result.success(null)
    }
}
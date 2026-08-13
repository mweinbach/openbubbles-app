package com.bluebubbles.messaging.services.notifications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.bluebubbles.messaging.Constants
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.R
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.utils.Utils
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class CreateMissedFaceTimeNotification: MethodCallHandlerImpl() {
    companion object {
        const val tag = "create-missed-facetime-notification"
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

        // create a bundle for extra info
        val extras = Bundle()
        extras.putString("callUuid", callUuid)

        // intent to open the app
        val callBackIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .putExtras(extras)
                .putExtra("desc", title)
                .putExtra("notificationId", notificationId)
                .setAction("com.bluebubbles.messaging.CallBackFT"),
            PendingIntent.FLAG_IMMUTABLE
        )

        val recentCalls = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction("com.bluebubbles.messaging.RecentCalls"),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, channelId)
            .setContentTitle(title)
            .setContentText("Missed FaceTime Call")
            .setSmallIcon(R.mipmap.ic_stat_icon)
            .addAction(R.drawable.accept, "Call Back", callBackIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .addExtras(extras)
            .setColor(4888294)

        notificationBuilder.setContentIntent(recentCalls)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notification = notificationBuilder.build()

        notificationManager.notify(Constants.newFaceTimeNotificationTag, notificationId, notification)
        result.success(null)
    }
}
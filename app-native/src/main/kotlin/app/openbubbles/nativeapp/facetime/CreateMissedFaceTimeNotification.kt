package app.openbubbles.nativeapp.facetime

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import app.openbubbles.nativeapp.facetime.FtConstants

import app.openbubbles.nativeapp.R
import app.openbubbles.nativeapp.facetime.getAdaptiveIconFromByteArray

object CreateMissedFaceTimeNotification {
    const val tag = "create-missed-facetime-notification"

    fun create(
        context: Context,
        notificationId: Int,
        callUuid: String?,
        title: String,
        poster: String?,
        callerName: String,
        callerAvatar: ByteArray?,
    ) {
        val channelId: String = "facetime_missed"

        // create a bundle for extra info
        val extras = Bundle()
        extras.putString("callUuid", callUuid)

        // intent to open the app
        val callBackIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, FaceTimeActivity::class.java)
                .putExtras(extras)
                .putExtra("notificationId", notificationId),
            PendingIntent.FLAG_IMMUTABLE
        )

        val recentCalls = PendingIntent.getActivity(
            context,
            0,
            Intent(context, app.openbubbles.nativeapp.NativeMainActivity::class.java),
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

        notificationManager.notify(FtConstants.NEW_FACE_TIME_NOTIFICATION_TAG, notificationId, notification)
    }
}
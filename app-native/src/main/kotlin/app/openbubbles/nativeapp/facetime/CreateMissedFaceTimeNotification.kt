package app.openbubbles.nativeapp.facetime

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.NotificationCompat
import app.openbubbles.nativeapp.R

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
        val channelId: String = FaceTimeNotifications.ensureMissedChannel(context)

        // create a bundle for extra info
        val extras = Bundle()
        extras.putString("callUuid", callUuid)

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

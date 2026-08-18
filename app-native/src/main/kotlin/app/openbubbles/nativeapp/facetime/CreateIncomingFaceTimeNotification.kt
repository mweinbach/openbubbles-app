package app.openbubbles.nativeapp.facetime

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import app.openbubbles.nativeapp.R

/** Parameter bundle replacing the Flutter method-channel arguments. */
data class FtIncomingCall(
    val notificationId: Int,
    val callUuid: String?,
    val title: String,
    val link: String?,
    val name: String,
    val poster: String?,
    val callerName: String,
    val callerAvatar: ByteArray?,
)

object CreateIncomingFaceTimeNotification {
    const val tag = "create-incoming-facetime-notification"

    val avatarCache = mutableMapOf<String, Bitmap?>()

    fun create(context: Context, call: FtIncomingCall) {
        val channelId: String = FaceTimeNotifications.ensureIncomingChannel(context)
        val notificationId: Int = call.notificationId
        val callUuid: String? = call.callUuid
        val title: String = call.title
        val link: String? = call.link
        var username: String = call.name

        var poster: String? = call.poster

        // contact details
        val callerName: String = call.callerName
        val callerIcon: ByteArray? = call.callerAvatar
        val callerBitmap = if ((callerIcon?.size ?: 0) == 0) null else BitmapFactory.decodeByteArray(callerIcon!!, 0, callerIcon.size)
        val callerIconCompat = if ((callerIcon?.size ?: 0) == 0) null else getAdaptiveIconFromByteArray(callerIcon!!)

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
            notificationId + FtConstants.PENDING_INTENT_OPEN_OFFSET,
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
            notificationId + FtConstants.PENDING_INTENT_ANSWER_OFFSET,
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
            notificationId + FtConstants.PENDING_INTENT_DECLINE_OFFSET,
            Intent(context, FaceTimeActionReceiver::class.java)
                .putExtras(extras)
                .putExtra(FaceTimeActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
                .setAction(FaceTimeActionReceiver.ACTION_DECLINE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        val canFullScreen = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            notificationManager.canUseFullScreenIntent()
        } else {
            true
        }
        if (canPostFullScreenCallIntent(canFullScreen)) {
            notificationBuilder.setFullScreenIntent(openSummaryIntent, true)
        }
        // clear after 30 seconds in case we didn't get an event from the server
        notificationBuilder.setTimeoutAfter(30000)

        val notification = notificationBuilder.build()
        // loop ringtone
        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT

        notificationManager.notify(FtConstants.NEW_FACE_TIME_NOTIFICATION_TAG, notificationId, notification)
    }
}

package app.openbubbles.nativeapp.facetime

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
    /** Raw rust handle (`tel:`/`mailto:`) of the caller for the telecom address. */
    val callerHandle: String? = null,
)

/**
 * The one FaceTimeActivity launch shape for an incoming call, shared by the
 * notification PendingIntents and the telecom answer path.
 */
internal fun faceTimeActivityIntent(context: Context, call: FtIncomingCall, answer: Boolean): Intent =
    Intent(context, FaceTimeActivity::class.java)
        .putExtra("callUuid", call.callUuid)
        .putExtra("answer", answer)
        .putExtra("link", call.link)
        .putExtra("name", call.name)
        .putExtra("notificationId", call.notificationId.toString())
        .putExtra("desc", call.title)
        .putExtra("poster", call.poster)

object CreateIncomingFaceTimeNotification {
    const val tag = "create-incoming-facetime-notification"

    const val CHANNEL_ID = FaceTimeNotifications.CHANNEL_INCOMING

    val avatarCache = mutableMapOf<String, Bitmap?>()

    /**
     * Registers [CHANNEL_ID] before the first ring. Delegates to
     * [FaceTimeNotifications] so the incoming / missed / in-call channels
     * stay one owner.
     */
    fun ensureChannel(context: Context) {
        FaceTimeNotifications.ensureIncomingChannel(context)
    }

    fun create(context: Context, call: FtIncomingCall) {
        val channelId: String = FaceTimeNotifications.ensureIncomingChannel(context)
        val notificationId: Int = call.notificationId
        val callUuid: String? = call.callUuid

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
            faceTimeActivityIntent(context, call, answer = false),
            PendingIntent.FLAG_IMMUTABLE
        )

        // Create intent for answering and opening the facetime link
        val answerIntent = PendingIntent.getActivity(
            context,
            notificationId + FtConstants.PENDING_INTENT_ANSWER_OFFSET,
            faceTimeActivityIntent(context, call, answer = true),
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
        // Always attach the full-screen intent. On Android 14+ a denied
        // USE_FULL_SCREEN_INTENT grant still lets the system keep the
        // heads-up / lock-screen fallback; omitting the intent turns a
        // ringing call into an ordinary notification.
        notificationBuilder.setFullScreenIntent(openSummaryIntent, true)
        val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return
        // clear after 30 seconds in case we didn't get an event from the server
        notificationBuilder.setTimeoutAfter(FACETIME_RING_TIMEOUT_MS)

        val notification = notificationBuilder.build()
        // loop ringtone
        notification.flags = notification.flags or NotificationCompat.FLAG_INSISTENT

        notificationManager.notify(FtConstants.NEW_FACE_TIME_NOTIFICATION_TAG, notificationId, notification)
    }
}

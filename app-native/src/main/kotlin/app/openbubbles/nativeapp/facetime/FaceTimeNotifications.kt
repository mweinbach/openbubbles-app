package app.openbubbles.nativeapp.facetime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build

/** Shared FaceTime notification channel ids and creation. */
internal object FaceTimeNotifications {
    const val CHANNEL_INCOMING = "facetime_incoming"
    const val CHANNEL_MISSED = "facetime_missed"
    const val CHANNEL_IN_CALL = "com.bluebubbles.in_call_channel"

    fun ensureIncomingChannel(context: Context): String {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return CHANNEL_INCOMING
        val channel = NotificationChannel(
            CHANNEL_INCOMING,
            "Incoming FaceTime",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Incoming FaceTime calls"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
        nm.createNotificationChannel(channel)
        return CHANNEL_INCOMING
    }

    fun ensureMissedChannel(context: Context): String {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return CHANNEL_MISSED
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MISSED,
                "Missed FaceTime",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Missed FaceTime calls"
            },
        )
        return CHANNEL_MISSED
    }

    fun ensureInCallChannel(context: Context): String {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return CHANNEL_IN_CALL
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_IN_CALL,
                "In Call",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Shows the state of an in-progress FaceTime call"
            },
        )
        return CHANNEL_IN_CALL
    }
}

/**
 * Camera/microphone FGS types only when those runtime permissions are
 * granted. A zero type is illegal on Android 14+ for a service declared as
 * `camera|microphone`.
 */
internal fun faceTimeForegroundServiceType(
    cameraGranted: Boolean,
    microphoneGranted: Boolean,
): Int {
    var type = 0
    if (cameraGranted) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
    if (microphoneGranted) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    return type
}

/** Full-screen incoming-call intents need the runtime grant on Android 14+. */
internal fun canPostFullScreenCallIntent(
    canUseFullScreenIntent: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = sdkInt < Build.VERSION_CODES.UPSIDE_DOWN_CAKE || canUseFullScreenIntent

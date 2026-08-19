package app.openbubbles.nativeapp.facetime

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi

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
@SuppressLint("InlinedApi") // Service type flags are passed only to the API-29+ startForeground overload.
internal fun faceTimeForegroundServiceType(
    cameraGranted: Boolean,
    microphoneGranted: Boolean,
): Int {
    var type = 0
    if (cameraGranted) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
    if (microphoneGranted) type = type or android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
    return type
}

/**
 * Incoming FaceTime always attaches a full-screen intent. The Android 14+
 * grant is only used to decide whether Settings should send the user to
 * the system full-screen-intent screen.
 */
internal fun shouldOfferFullScreenCallSettings(
    canUseFullScreenIntent: Boolean,
    sdkInt: Int = Build.VERSION.SDK_INT,
): Boolean = sdkInt >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFullScreenIntent

internal fun shouldOfferFullScreenCallSettings(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
    val granted = runCatching {
        context.getSystemService(NotificationManager::class.java)?.canUseFullScreenIntent()
    }.getOrNull() ?: return false
    return shouldOfferFullScreenCallSettings(
        canUseFullScreenIntent = granted,
        sdkInt = Build.VERSION.SDK_INT,
    )
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal fun fullScreenCallSettingsIntent(packageName: String): Intent =
    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(
        Uri.fromParts("package", packageName, null),
    )

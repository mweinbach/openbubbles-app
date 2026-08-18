package app.openbubbles.nativeapp.facetime

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import app.openbubbles.nativeapp.R

class FaceTimeInCallService : Service() {

    override fun onCreate() {
        super.onCreate()
        notifyForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_NOT_STICKY
    }

    private fun notifyForeground() {
        val channelId = FaceTimeNotifications.ensureInCallChannel(this)
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("FaceTime call in progress")
            .setSmallIcon(R.mipmap.ic_stat_icon)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setSilent(true)
            .build()

        val type = faceTimeForegroundServiceType(
            cameraGranted = checkSelfPermission(android.Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
            microphoneGranted = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )

        try {
            // Three-arg startForeground exists on Android 10. Camera and
            // microphone types exist on Android 11. Passing type 0 on
            // Android 14+ throws MissingForegroundServiceTypeException.
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && type == 0 -> {
                    Log.w(TAG, "refusing camera|microphone FGS with no media permissions")
                    stopSelf()
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    startForeground(FOREGROUND_ID, notification, type)
                }
                else -> startForeground(FOREGROUND_ID, notification)
            }
        } catch (e: RuntimeException) {
            Log.w(TAG, "failed to start FaceTime foreground service", e)
            stopSelf()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "FaceTimeInCallService"
        private const val FOREGROUND_ID = 3884786
    }
}

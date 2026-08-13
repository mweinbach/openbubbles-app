package com.bluebubbles.messaging.services.facetime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresApi
import com.bluebubbles.messaging.R

class FaceTimeInCallService: Service() {

    @RequiresApi(Build.VERSION_CODES.O)
    fun createNotificationChannel() {
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(IN_CALL_CHANNEL, "In Call", importance).apply {
            description = "Shows the state of an in-progress FaceTime call"
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    val IN_CALL_CHANNEL = "com.bluebubbles.in_call_channel";
    @RequiresApi(Build.VERSION_CODES.O)
    fun notifyForeground() {
        createNotificationChannel()
        val notification: Notification = Notification.Builder(this, IN_CALL_CHANNEL)
            .setContentTitle("FaceTime call in progress")
            .setSmallIcon(R.mipmap.ic_stat_icon)
            .build()

        var type: Int = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }

        // Notification ID cannot be 0.
        startForeground(3884786, notification, type)
    }

    override fun onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notifyForeground()
        }
        super.onCreate()
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
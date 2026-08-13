package com.bluebubbles.messaging.services.system

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel


/// Open an existing contact page
class ZenModeSetupHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag = "zen-mode-setup"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            result.error("Failed", "Zen Mode requires Android 11 or later", null)
            return
        }
        var notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.isNotificationPolicyAccessGranted) {
            result.success(true)
            return
        }
        val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        context.startActivity(intent)

        val toast = Toast.makeText(context, "Enable Modes Access for OpenBubbles", Toast.LENGTH_LONG)
        toast.show()

        result.success(false)

    }
}
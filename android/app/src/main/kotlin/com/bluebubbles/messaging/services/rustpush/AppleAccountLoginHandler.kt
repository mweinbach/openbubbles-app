package com.bluebubbles.messaging.services.rustpush

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bluebubbles.messaging.Constants
import com.bluebubbles.messaging.R
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.services.notifications.DeleteNotificationHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.UUID

class AppleAccountLoginHandler: MethodCallHandlerImpl() {

    companion object {
        const val tag = "apple-account-login"

        var txnid: String? = null
        var defbtn: String? = null
        var albtn: String? = null
        var sbdy: String? = null
        var title: String? = null
        var body: String? = null

        var activity: AuthCodeActivity? = null
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val myTxnId: String = call.argument("txnid")!!

        // teardown
        if (call.argument<String>("title") == null) {
            if (myTxnId == txnid) {
                if (activity?.done != true) {
                    activity?.finishAndRemoveTask()
                }
                DeleteNotificationHandler().deleteNotification(
                    context,
                    -50 - 3,
                    Constants.appleAccountLoginAttemptTag
                )
            }
            result.success(null)
            return
        }

        txnid = myTxnId;

        title = call.argument("title")!!
        body = call.argument("body")!!
        defbtn = call.argument("defbtn")!!
        albtn = call.argument("albtn")!!
        sbdy = call.argument("sbdy")!!


        val recentCalls = PendingIntent.getActivity(
            context,
            -50 - 4,
            Intent(context, AuthCodeActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
            PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(context, "com.bluebubbles.auth_codes")
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(R.mipmap.ic_stat_icon)
            .setAutoCancel(true)
            .setGroup(UUID.randomUUID().toString())
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setColor(4888294)

        notificationBuilder.setContentIntent(recentCalls)

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val notification = notificationBuilder.build()

        notificationManager.notify(Constants.appleAccountLoginAttemptTag, -50 - 3, notification)
        result.success(null)
    }

}
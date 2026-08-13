package com.bluebubbles.messaging.services.notifications

import android.app.NotificationManager
import android.content.Context
import android.util.Log
import com.bluebubbles.messaging.Constants
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class DeleteNotificationHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag = "delete-notification"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val notificationId: Int = call.argument("notification_id")!!
        val tag: String? = call.argument("tag")
        val success = deleteNotification(context, notificationId, tag)
        if (success) {
            result.success(null)
        } else {
            result.error("500", "Failed to cancel notification!", null)
        }
    }

    fun deleteNotification(context: Context, notificationId: Int, tag: String?): Boolean {
        Log.d(Constants.logTag, "Attempting to delete notification with ID, $notificationId from tag, $tag")
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        try {
            // Get the notification by ID
            val notification = notificationManager.activeNotifications.firstOrNull { it.id == notificationId }

            // If it's null, we can't cancel it.
            // Return true cuz there wasn't technically an issue.
            if (notification == null) {
                Log.d(Constants.logTag, "Notification with ID $notificationId not found!")
                if (tag != null) {
                    // nessesary for insistent full screen intent activities which don't show in activeNotifications
                    notificationManager.cancel(tag, notificationId)
                }
            } else {
                Log.d(Constants.logTag, "Cancelling notification with ID, $notificationId")
                notificationManager.cancel(notification.tag, notificationId)
            }

            val channelTag: String? = notification?.tag ?: tag
            Log.d(Constants.logTag, "Using Channel Tag: $channelTag (Notif: ${notification?.tag}; Param: $tag)")
            if (channelTag != null) {
                // Get all notifications of the same tag/channel, excluding the one we just cancelled
                val leftoverNotifications = notificationManager.activeNotifications.filter { 
                    it.tag == channelTag && it.id != notificationId 
                }

                Log.d(Constants.logTag, "Found ${leftoverNotifications.size} leftover notifications after deleting ID $notificationId")

                // If there are no non-summary notifications left, or only the summary notification (ID = 0) remains,
                // we should cancel the summary notification
                val nonSummaryNotifications = leftoverNotifications.filter { it.id != 0 }
                if (nonSummaryNotifications.isEmpty()) {
                    Log.d(Constants.logTag, "No non-summary notifications remaining, cancelling notification summary")
                    notificationManager.cancel(channelTag, 0)
                }
            }
        } catch (exception: Exception) {
            Log.e(Constants.logTag, "Failed to cancel notification with ID $notificationId!")
            return false
        }
        
        return true
    }
}
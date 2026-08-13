package com.bluebubbles.messaging.services.system

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.Person
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import com.bluebubbles.messaging.Constants
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.utils.Utils
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.UUID

class GetZenMode: MethodCallHandlerImpl() {
    companion object {
        const val tag = "get-zen-mode"

        fun getZenMode(context: Context): String? {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val interruptionFilter = notificationManager.currentInterruptionFilter
            val zenMode = if (interruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                val policy = notificationManager.consolidatedNotificationPolicy
                val messageSenders =
                    if ((policy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES) != 0) {
                        policy.priorityMessageSenders
                    } else {
                        -1
                    }
                val conversationSenders =
                    if ((policy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CONVERSATIONS) != 0) {
                        policy.priorityConversationSenders
                    } else {
                        NotificationManager.Policy.CONVERSATION_SENDERS_NONE
                    }
                if (conversationSenders == NotificationManager.Policy.CONVERSATION_SENDERS_ANYONE
                    || messageSenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY
                ) {
                    null
                } else if (messageSenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED &&
                    conversationSenders == NotificationManager.Policy.CONVERSATION_SENDERS_IMPORTANT
                ) {
                    "starred_priority"
                } else if (messageSenders == NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS) {
                    null // we are all contacts
                } else if (messageSenders == NotificationManager.Policy.PRIORITY_SENDERS_STARRED) {
                    "starred"
                } else if (conversationSenders == NotificationManager.Policy.CONVERSATION_SENDERS_IMPORTANT) {
                    "priority"
                } else {
                    "none"
                }
            } else if (interruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE || interruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALARMS) {
                "none"
            } else {
                null
            }
            return zenMode
        }

    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        result.success(getZenMode(context))
    }
}
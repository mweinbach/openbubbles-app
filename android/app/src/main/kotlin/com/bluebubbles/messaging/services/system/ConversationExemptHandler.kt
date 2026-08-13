package com.bluebubbles.messaging.services.system

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.CancellationSignal
import android.provider.ContactsContract
import androidx.core.content.ContentResolverCompat
import androidx.core.content.ContextCompat
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

/// Open an existing contact page
class ConversationExemptHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag = "is-conversation-exempt"
    }

    fun isContactStarred(context: Context, contactId: Long): Boolean {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(ContactsContract.Contacts.STARRED)
        val selection = "${ContactsContract.Contacts._ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val starred = cursor.getInt(cursor.getColumnIndexOrThrow(ContactsContract.Contacts.STARRED))
                return starred == 1
            }
        }

        return false
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val mode: String = call.argument("mode")!!
        if (mode == "star") {
            result.success(isContactStarred(context, call.argument<Long>("contactId")!!))
        } else {
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val channelId = "com.bluebubbles.new_messages.${call.argument<String>("guid")!!}"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                result.success(notificationManager.getNotificationChannel(channelId)?.isImportantConversation ?: false)
            } else {
                result.success(false)
            }
        }
    }
}
package com.bluebubbles.messaging.services.system

import android.content.Context
import android.net.Uri
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class RecentContactsRequestHandler : MethodCallHandlerImpl() {
    companion object {
        const val tag = "recent-contacts"
    }

    fun getTop6SentContactsLastWeek(context: Context): List<String> {
        val smsUri = Uri.parse("content://sms/sent") // shortcut: only sent messages
        val projection = arrayOf("address", "date")

        // Calculate timestamp for 7 days ago
        val oneWeekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L

        val selection = "date >= ?"
        val selectionArgs = arrayOf(oneWeekAgo.toString())
        val sortOrder = "date DESC" // optional: recent messages first

        val resolver = context.contentResolver
        val cursor = resolver.query(smsUri, projection, selection, selectionArgs, sortOrder) ?: return emptyList()

        val contactCountMap = mutableMapOf<String, Int>()

        cursor.use {
            while (it.moveToNext()) {
                val address = it.getString(it.getColumnIndexOrThrow("address"))
                if (!address.isNullOrEmpty()) {
                    contactCountMap[address] = (contactCountMap[address] ?: 0) + 1
                }
            }
        }

        return contactCountMap.entries
            .sortedByDescending { it.value } // sort by number of messages sent
            .take(6) // top 6 contacts
            .map { it.key }
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        result.success(getTop6SentContactsLastWeek(context))
    }

}
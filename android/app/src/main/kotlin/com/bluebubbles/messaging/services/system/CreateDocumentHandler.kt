package com.bluebubbles.messaging.services.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import com.bluebubbles.messaging.Constants
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.io.FileInputStream

/// Create a new event in the calendar
class CreateDocumentHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag = "create-document"

        var savedResult: MethodChannel.Result? = null
        var savedPath: String? = null
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val path: String = call.argument("path")!!
        val mime: String = call.argument("mime")!!
        val name: String = call.argument("name")!!

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = mime
            putExtra(Intent.EXTRA_TITLE, name)
        }
        savedPath = path
        savedResult = result
        (context as MainActivity).startActivityForResult(intent, Constants.documentSaveRequestCode)
    }
}
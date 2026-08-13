package com.bluebubbles.messaging.services.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class OpenSMSAppHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag = "open-sms-app"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val contactids: String = call.argument("targets")!!
        val body: String? = call.argument("body")
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:${Uri.encode(contactids)}")
            putExtra("sms_body", body)
        }

        context.startActivity(intent)
    }
}
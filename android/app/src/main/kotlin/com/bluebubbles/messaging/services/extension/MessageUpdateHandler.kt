package com.bluebubbles.messaging.services.extension

import android.content.Context
import com.bluebubbles.messaging.IMessageViewHandle
import com.bluebubbles.messaging.ITaskCompleteCallback
import com.bluebubbles.messaging.MadridMessage
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.services.backend_ui_interop.MethodCallHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class MessageUpdateHandler: MethodCallHandlerImpl() {

    companion object {
        const val tag = "message-update-handler"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val message = MadridMessageUtil.fromCall(call)

        if (!MessageViewRegistry.registered.contains(message.session)) {
            result.success(null)
            return
        }

        MadridExtensionConnection.bind(call.argument("appId")!!, context) {
            it.extension?.messageUpdated(message)
            it.unbind(context)
            result.success(null)
        }
    }

}
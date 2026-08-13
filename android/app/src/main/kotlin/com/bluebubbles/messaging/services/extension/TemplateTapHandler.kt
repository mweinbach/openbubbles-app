package com.bluebubbles.messaging.services.extension

import android.content.Context
import android.os.Handler
import com.bluebubbles.messaging.IMessageViewHandle
import com.bluebubbles.messaging.ITaskCompleteCallback
import com.bluebubbles.messaging.MadridMessage
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.services.backend_ui_interop.MethodCallHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class TemplateTapHandler: MethodCallHandlerImpl() {

    companion object {
        const val tag = "extension-template-tap"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val message = MadridMessageUtil.fromCall(call)

        val appId: Int = call.argument("appId")!!
        val userCount: Int = call.argument("userCount")!!
        MessageViewRegistry.registered.add(message.session)
        MadridExtensionConnection.bind(appId, context) {
            var handle = MessageViewHandle(
                context,
                call.argument("session")!!,
                appId,
                call.argument("messageGuid")!!,
            ) {
                it.unbind(context)
            }
            it.extension?.didTapTemplate(message, handle, userCount)
            handle.markDead()
            result.success(null)
        }
    }
}
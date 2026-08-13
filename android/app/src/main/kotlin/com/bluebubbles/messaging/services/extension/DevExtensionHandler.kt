package com.bluebubbles.messaging.services.extension

import android.content.Context
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class DevExtensionHandler: MethodCallHandlerImpl() {

    companion object {
        const val tag = "dev-extension-handler"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val appId: String = call.argument("serviceName")!!

        ExtensionRegistry.registerDevExtension(context, appId)
        result.success(null)
    }
}
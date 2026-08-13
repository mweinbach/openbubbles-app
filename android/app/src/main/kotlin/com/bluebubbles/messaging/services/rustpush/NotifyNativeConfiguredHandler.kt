package com.bluebubbles.messaging.services.rustpush

import android.content.Context
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class NotifyNativeConfiguredHandler: MethodCallHandlerImpl() {

    companion object {
        const val tag = "notify-native-configured"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val client = APNClient(context)
        client.bind { service: APNService ->
            service.configured()
            result.success("")
            client.destroy()
        }
    }

}
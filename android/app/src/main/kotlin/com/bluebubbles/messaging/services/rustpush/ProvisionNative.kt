package com.bluebubbles.messaging.services.rustpush

import android.content.Context
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class ProvisionNative: MethodCallHandlerImpl() {

    companion object {
        const val tag = "provision-native"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val client = APNClient(context)
        val data = call.argument<String>("native")!!
        client.bind { service: APNService ->
            service.kickstartNative(data)
            result.success(null)
            client.destroy()
        }
    }

}
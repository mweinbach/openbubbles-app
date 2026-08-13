package com.bluebubbles.messaging.services.facetime

import android.content.Context
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class FaceTimeGetActiveCallHandler: MethodCallHandlerImpl() {

    companion object {
        const val tag = "get-active-call"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) { 
        result.success(FaceTimeActivity.activeFaceTimeActivity?.callUuid)
    }

}
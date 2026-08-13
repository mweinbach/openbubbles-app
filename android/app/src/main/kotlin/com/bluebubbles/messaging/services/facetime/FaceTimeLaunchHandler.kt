package com.bluebubbles.messaging.services.facetime

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.services.backend_ui_interop.MethodCallHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class FaceTimeLaunchHandler: MethodCallHandlerImpl() {

    companion object {
        const val tag = "launch-facetime"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val i = Intent(context, FaceTimeActivity::class.java)
        i.putExtra("link", call.argument<String>("link"))
        i.putExtra("desc", call.argument<String>("desc"))
        i.putExtra("name", call.argument<String?>("name"))
        i.putExtra("callUuid", call.argument<String?>("callUuid"))
        call.argument<Boolean?>("answer")?.let {
            i.putExtra("answer", it)
        }

        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        context.startActivity(i)

        result.success(null)
    }

}
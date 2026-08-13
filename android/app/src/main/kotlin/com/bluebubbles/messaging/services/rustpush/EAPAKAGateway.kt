package com.bluebubbles.messaging.services.rustpush

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import android.util.Log
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.services.rustpush.eap_aka.EapAkaChallenge
import com.bluebubbles.messaging.services.rustpush.eap_aka.EapAkaResponse
import com.bluebubbles.messaging.services.rustpush.eap_aka.EapAkaResponse.getImsiEap
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import uniffi.rust_lib_bluebubbles.CarrierHandler
import uniffi.rust_lib_bluebubbles.getCarrier
import java.util.Random

class EAPAKAGateway: MethodCallHandlerImpl() {

    companion object {
        const val tag = "eap-aka-gateway"
    }

    // subscriberid works with icc auth, suppress lint
    @SuppressLint("MissingPermission")
    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val realm = "nai.epc"
        val challenge = call.argument<String>("challenge")!!
        val subscription = call.argument<Int>("subscription")!!
        val challenge2 = EapAkaChallenge.parseEapAkaChallenge(challenge)

        result.success(EapAkaResponse.respondToEapAkaChallenge(context, subscription, challenge2, realm).response())
    }

}
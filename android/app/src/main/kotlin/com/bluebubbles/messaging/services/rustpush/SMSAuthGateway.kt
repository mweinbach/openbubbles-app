package com.bluebubbles.messaging.services.rustpush

import android.content.Context
import android.os.Build
import android.os.Handler
import android.telephony.SmsManager
import android.telephony.TelephonyManager
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import uniffi.rust_lib_bluebubbles.CarrierHandler
import uniffi.rust_lib_bluebubbles.getCarrier
import java.util.Random

class SMSAuthGateway: MethodCallHandlerImpl() {

    companion object {
        const val tag = "sms-auth-gateway"

        var waitingResult: HashMap<String, MethodChannel.Result> = HashMap()

        fun processMessage(body: String) {
            synchronized(this) {
                if (body.split("?").first() == "REG-RESP") {
                    val rest = body.split("?").last()
                    val parts = rest.split(";").associateBy({it.split("=").first()}, {it.split("=").last()})
                    waitingResult.remove(parts["r"])?.success("${parts["n"]}|${parts["s"]}")
                }
            }
        }
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val token = call.argument<String>("token")!!
        val subscription = call.argument<Int>("subscription")!!

        var telephonyManager = (context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            telephonyManager = telephonyManager.createForSubscriptionId(subscription)
        }
        val carrierMccMnc = telephonyManager.simOperator

        getCarrier(object : CarrierHandler {
            override fun gotGateway(gateway: String?, error: String?) {
                if (gateway == null) {
                    result.error(error ?: "No error", null, null)
                    return
                }
                val smsManager = SmsManager.getSmsManagerForSubscriptionId(subscription)

                val reqId = Random().nextInt()
                val sms = "REG-REQ?v=3;t=${token};r=${reqId};"

                // Catch the exception if the SMS fails to send
                try {
                    smsManager.sendTextMessage(gateway, null, sms, null, null)
                } catch (e: Exception) {
                    return result.error(e.message ?: "No error", null, null)
                }
                waitingResult[reqId.toString()] = result

                val handler = Handler(context.mainLooper)
                handler.postDelayed({
                    waitingResult.remove(reqId.toString())?.error("Timed out waiting for REG-RESP", null, null)
                }, 30000)
            }
        }, carrierMccMnc)

    }

}
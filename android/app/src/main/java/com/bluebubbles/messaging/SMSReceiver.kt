package com.bluebubbles.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import com.bluebubbles.messaging.services.rustpush.SMSAuthGateway


class SMSReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val extractMessages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        var totalBody = ""
        for (message in extractMessages) {
            Log.i("PDU_RCVRsms", message.messageBody)
            if (message.messageBody.startsWith("~")) continue; // google fi being stupid
            totalBody += message.messageBody
        }
        SMSAuthGateway.processMessage(totalBody)
    }
}
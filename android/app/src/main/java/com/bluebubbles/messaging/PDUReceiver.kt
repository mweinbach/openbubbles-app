package com.bluebubbles.messaging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Base64
import android.util.Log
import com.bluebubbles.messaging.services.rustpush.SMSAuthGateway
import java.nio.charset.StandardCharsets


class PDUReceiver : BroadcastReceiver() {
    private fun sanityCheck(result: String?): Boolean {
        return result != null && result.contains("REG-RESP")
    }

    private fun sanityCheck(message: SmsMessage?): Boolean {
        return message != null && sanityCheck(message.messageBody)
    }

    private fun found(input: String): String {
        val resdata = input.substring(input.indexOf("REG-RESP"))
        Log.w(TAG, "PDU: $resdata")
        return resdata
    }

    private fun parse(pdu: ByteArray): String? {
        // Print base64-encoded PDU
        Log.w(TAG, "PDU: " + String(Base64.encode(pdu, Base64.DEFAULT)))
        Log.d(TAG, "Attempting to process message as 3gpp...")
        try {
            val message = SmsMessage.createFromPdu(pdu, "3gpp")
            if (sanityCheck(message)) {
                return found(message.messageBody)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "The following error occurred while attempting to process PDU as 3gpp:\n$e"
            )
        }
        Log.d(TAG, "Failed to get body using 3gpp parsing, trying 3gpp2...")
        try {
            val message = SmsMessage.createFromPdu(pdu, "3gpp2")
            if (sanityCheck(message)) {
                return found(message.messageBody)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "The following error occurred while attempting to process PDU as 3gpp2:\n$e"
            )
        }
        Log.d(TAG, "Failed to get body using 3gpp2 parsing, manually parsing...")
        try {
            val pduString = String(pdu, 0, pdu.size, StandardCharsets.US_ASCII)
            if (sanityCheck(pduString)) {
                return found(pduString)
            }
        } catch (e: Exception) {
            Log.e(
                TAG,
                "The following error occurred while attempting to process PDU manually:\n$e"
            )
        }
        Log.w(TAG, "PDU could not be deciphered.")
        return null
    }

    override fun onReceive(context: Context, intent: Intent) {
        //Runs whenever a data SMS PDU is received. On some carriers (i.e. AT&T), the REG-RESP message is sent as
        //  a data SMS (PDU) instead of a regular SMS message.
        Log.d(TAG, "Received intent!")
        val bundle = intent.extras
        if (bundle != null) {
            val pdus = bundle["pdus"] as Array<Any>?
            Log.d(TAG, "Got " + pdus!!.size + " PDUs")
            for (o in pdus) {
                parse(o as ByteArray)?.let {
                    SMSAuthGateway.processMessage(it)
                }
                //SMSReceiver.processMessage(SmsMessage.createFromPdu((byte[]) pdus[i]));
            }
        }
    }

    companion object {
        private const val TAG = "PDU_RCVR"
    }
}
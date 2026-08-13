package com.bluebubbles.messaging.services.facetime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class FaceTimeActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        FaceTimeActivity.activeFaceTimeActivity?.endCall()
    }
}
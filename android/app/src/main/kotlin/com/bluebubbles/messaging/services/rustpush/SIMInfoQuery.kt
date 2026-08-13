package com.bluebubbles.messaging.services.rustpush

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.app.ActivityCompat
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.services.backend_ui_interop.MethodCallHandler
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class SIMInfoQuery: MethodCallHandlerImpl() {

    companion object {
        const val tag = "sim-info-query"

        var subscribed: SubscriptionManager.OnSubscriptionsChangedListener? = null
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager

        if ((subscribed != null) != call.argument("subscribe")) {
            if (subscribed != null) {
                subscriptionManager.removeOnSubscriptionsChangedListener(subscribed)
                subscribed = null
            } else {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_PHONE_STATE
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    result.error("No permissions!", null, null)
                    return
                }

                subscribed = object : SubscriptionManager.OnSubscriptionsChangedListener() {
                    override fun onSubscriptionsChanged() {
                        super.onSubscriptionsChanged()

                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.READ_PHONE_STATE
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }

                        val info = subscriptionManager.activeSubscriptionInfoList
                        if (MainActivity.getEngine() != null) {
                            MethodCallHandler.invokeMethod("sim-info", hashMapOf(
                                "info" to (info ?: listOf()).map { i ->
                                    hashMapOf(
                                        "carrier" to i.displayName.toString(),
                                        "subscription" to i.subscriptionId
                                    )
                                }
                            ))
                        }
                    }
                }
                subscriptionManager.addOnSubscriptionsChangedListener(subscribed)
            }
        }

        result.success(null)
    }

}

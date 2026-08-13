package com.bluebubbles.messaging.services.credentials

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import androidx.core.net.toUri

class OpenAutofillProviderSettingsHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag = "open-autofill-provider-settings"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            result.success(null)
            return
        }
        val intent = Intent(Settings.ACTION_CREDENTIAL_PROVIDER)

        intent.setData("package:${context.packageName}".toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent)
        result.success(null)
    }
}
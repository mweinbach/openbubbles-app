package com.bluebubbles.messaging.services.rustpush

import android.content.Context
import android.os.Build
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class KeystoreUnlockHandler: MethodCallHandlerImpl() {

    companion object {
        const val tag = "keystore-unlock"
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            result.success(true)
            return
        }

        val lock = call.argument<Boolean>("lock")!!
        val title = call.argument<String>("title") ?: "Manage iCloud Keychain"

        val client = APNClient(context)
        client.bind { service: APNService ->
            if (lock) {
                service.keystore.lockKeystore()
            } else {
                service.keystore.unlockKeystore(title) { success ->
                    result.success(success)
                }
            }
            client.destroy()
        }
    }
}
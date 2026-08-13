package com.bluebubbles.messaging.services.system

import android.R.attr.mode
import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Parcel
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule_PackageNameFactory.packageName
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper


class ShizukuGrantPermissionHandler : MethodCallHandlerImpl() {
    companion object {
        const val tag = "shizuku-grant-permission"

        var callback: (Int, Int) -> Unit = { a, b -> }

        init {
            Shizuku.addRequestPermissionResultListener { requestCode: Int, grantResult: Int ->
                callback(requestCode, grantResult)
            }
        }
    }



    fun handlePermissionGranted(result: MethodChannel.Result) {
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService(Context.APP_OPS_SERVICE))

        val TRANSACTION_setUidMode = 30 // From AOSP IAppOpsService.java
        val DESCRIPTOR = "com.android.internal.app.IAppOpsService"

        val data = Parcel.obtain()
        val reply = Parcel.obtain()

        try {
            data.writeInterfaceToken(DESCRIPTOR)
            data.writeInt(105) // use icc auth with device identifier
            data.writeInt(android.os.Process.myUid())
            data.writeInt(AppOpsManager.MODE_ALLOWED)


            binder.transact(TRANSACTION_setUidMode, data, reply, 0)
            reply.readException()
        } finally {
            data.recycle()
            reply.recycle()
        }

        result.success(null)
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context 
    ) {

        if (!Shizuku.pingBinder()) {
            result.error("Shizuku is not running! Start it in the Shizuku App.", null, null)
            return;
        }

        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            callback = { request, res ->
                if (res != PackageManager.PERMISSION_GRANTED) {
                    result.error("Permission Denied!", null, null)
                } else {
                    handlePermissionGranted(result)
                }
            }
            Shizuku.requestPermission(0)
        } else {
            handlePermissionGranted(result)
        }
    }

}
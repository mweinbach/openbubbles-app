package com.bluebubbles.messaging.services.system

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.bluebubbles.messaging.Constants
import com.bluebubbles.messaging.MainActivity
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import com.bluebubbles.messaging.services.system.CreateDocumentHandler.Companion
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel

class EnableBTHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag: String = "enable-bt"

        var savedResult: MethodChannel.Result? = null
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectGranted = context.checkSelfPermission(
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val advertiseGranted = context.checkSelfPermission(
                Manifest.permission.BLUETOOTH_ADVERTISE
            ) == PackageManager.PERMISSION_GRANTED

            if (!connectGranted || !advertiseGranted) {
                Log.w("CircleProximitySession", "Permissions not granted")
                result.success(false)
                return
            }
        }

        val adapter = bluetoothManager.adapter
        if (adapter?.isEnabled != true) {
            val request: Boolean = call.argument<Boolean>("request") ?: false
            if (request) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                (context as MainActivity).startActivityForResult(enableBtIntent, Constants.enableBtRequestCode)
            }
            savedResult = result
        } else {
            result.success(adapter.isEnabled)
        }
    }

}
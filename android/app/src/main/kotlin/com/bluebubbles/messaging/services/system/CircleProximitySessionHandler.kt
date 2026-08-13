package com.bluebubbles.messaging.services.system

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import com.bluebubbles.messaging.models.MethodCallHandlerImpl
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.UUID

/// Check if ChromeOS is the current OS
class CircleProximitySessionHandler: MethodCallHandlerImpl() {
    companion object {
        const val tag: String = "circle-proximity-session"

        @SuppressLint("StaticFieldLeak")
        var currentCircleServer: CircleProximityServer? = null
    }

    class CircleProximityServer(val context: Context, val sid: UUID) : BluetoothGattServerCallback() {

        init {
            setup()
        }

        var advertiser: BluetoothLeAdvertiser? = null
        var gattServer: BluetoothGattServer? = null

        fun setup() {
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
                    return
                }
            }

            val adapter = bluetoothManager.adapter
            if (adapter?.isEnabled != true) {
                Log.w("CircleProximitySession", "Bluetooth adapter not enabled")
                return
            }

            gattServer = bluetoothManager.openGattServer(context, this)

            val service = BluetoothGattService(sid, BluetoothGattService.SERVICE_TYPE_PRIMARY)
            gattServer?.addService(service)

            advertiser = adapter.bluetoothLeAdvertiser ?: return

            val settings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)   // fast
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .setConnectable(true)                                            // IMPORTANT: allows centrals to connect
                .build()

            val data = AdvertiseData.Builder()
                .addServiceUuid(ParcelUuid(sid))                     // advertise the service
                .setIncludeTxPowerLevel(false)
                .build()

            advertiser?.startAdvertising(settings, data, object : AdvertiseCallback() { })
            Log.i("CircleProximitySession", "Session started")
        }

        fun destroy() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val connectGranted = context.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED

                val advertiseGranted = context.checkSelfPermission(
                    Manifest.permission.BLUETOOTH_ADVERTISE
                ) == PackageManager.PERMISSION_GRANTED

                if (!connectGranted || !advertiseGranted) {
                    Log.w("CircleProximitySession", "Permissions not granted")
                    return
                }
            }

            advertiser?.stopAdvertising(object : AdvertiseCallback() { })
            gattServer?.close()
            advertiser = null
            gattServer = null
        }
    }

    override fun handleMethodCall(
        call: MethodCall,
        result: MethodChannel.Result,
        context: Context
    ) {
        val sid = call.argument<String?>("sid")?.let { UUID.fromString(it) }

        if (currentCircleServer?.sid != sid) {
            Log.i("CircleProximitySession", "Destroying circle session")
            currentCircleServer?.destroy()
            currentCircleServer = null
        }

        if (sid != null) {
            Log.i("CircleProximitySession", "Starting circle session")
            currentCircleServer = CircleProximityServer(context, sid)
        }
        result.success(null)
    }
}
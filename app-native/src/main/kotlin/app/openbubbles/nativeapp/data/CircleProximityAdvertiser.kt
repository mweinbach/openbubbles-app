package app.openbubbles.nativeapp.data

import android.Manifest
import android.annotation.SuppressLint
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
import androidx.core.content.ContextCompat
import java.util.UUID

internal object CircleProximityPermissions {
    fun requiredForSdk(sdkInt: Int): List<String> = if (sdkInt >= Build.VERSION_CODES.S) {
        listOf(
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        emptyList()
    }
}

internal class CircleProximityAdvertiser(context: Context) {
    private val context = context.applicationContext
    private var advertiser: BluetoothLeAdvertiser? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var gattServer: BluetoothGattServer? = null

    fun missingPermissions(): List<String> = CircleProximityPermissions
        .requiredForSdk(Build.VERSION.SDK_INT)
        .filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    fun start(sessionId: String, onFailure: (String) -> Unit): Result<Unit> {
        stop()
        return runCatching {
            val missing = missingPermissions()
            check(missing.isEmpty()) { "Nearby-device Bluetooth permission is required" }

            val bluetoothManager = context.getSystemService(BluetoothManager::class.java)
                ?: error("Bluetooth is unavailable on this device")
            val adapter = bluetoothManager.adapter
                ?: error("Bluetooth is unavailable on this device")
            check(adapter.isEnabled) { "Turn on Bluetooth to approve from a nearby device" }
            check(adapter.isMultipleAdvertisementSupported) {
                "This device does not support Bluetooth proximity advertising"
            }

            val serviceUuid = UUID.fromString(sessionId)
            val server = bluetoothManager.openGattServer(context, object : BluetoothGattServerCallback() {})
                ?: error("Unable to open the Bluetooth proximity service")
            gattServer = server
            check(
                server.addService(
                    BluetoothGattService(serviceUuid, BluetoothGattService.SERVICE_TYPE_PRIMARY),
                ),
            ) { "Unable to publish the Bluetooth proximity service" }

            val bluetoothAdvertiser = adapter.bluetoothLeAdvertiser
                ?: error("Bluetooth proximity advertising is unavailable")
            val callback = object : AdvertiseCallback() {
                override fun onStartFailure(errorCode: Int) {
                    stop()
                    onFailure(advertiseFailureMessage(errorCode))
                }
            }
            advertiser = bluetoothAdvertiser
            advertiseCallback = callback
            bluetoothAdvertiser.startAdvertising(
                AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                    .setConnectable(true)
                    .build(),
                AdvertiseData.Builder()
                    .addServiceUuid(ParcelUuid(serviceUuid))
                    .setIncludeTxPowerLevel(false)
                    .build(),
                callback,
            )
        }.onFailure { stop() }
    }

    @SuppressLint("MissingPermission")
    fun stop() {
        val callback = advertiseCallback
        if (callback != null && missingPermissions().isEmpty()) {
            runCatching { advertiser?.stopAdvertising(callback) }
        }
        runCatching { gattServer?.close() }
        advertiseCallback = null
        advertiser = null
        gattServer = null
    }
}

private fun advertiseFailureMessage(errorCode: Int): String = when (errorCode) {
    AdvertiseCallback.ADVERTISE_FAILED_ALREADY_STARTED ->
        "Bluetooth proximity advertising is already active"
    AdvertiseCallback.ADVERTISE_FAILED_DATA_TOO_LARGE ->
        "The Bluetooth proximity request was too large"
    AdvertiseCallback.ADVERTISE_FAILED_FEATURE_UNSUPPORTED ->
        "This device does not support Bluetooth proximity advertising"
    AdvertiseCallback.ADVERTISE_FAILED_INTERNAL_ERROR ->
        "Android could not start Bluetooth proximity advertising"
    AdvertiseCallback.ADVERTISE_FAILED_TOO_MANY_ADVERTISERS ->
        "Too many apps are using Bluetooth advertising; close one and try again"
    else -> "Bluetooth proximity advertising failed ($errorCode)"
}

package app.openbubbles.nativeapp.data

import android.Manifest
import android.os.Build
import kotlin.test.Test
import kotlin.test.assertEquals

class CircleProximityAdvertiserTest {
    @Test
    fun bluetoothRuntimePermissionsStartOnAndroid12() {
        assertEquals(emptyList(), CircleProximityPermissions.requiredForSdk(Build.VERSION_CODES.R))
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            CircleProximityPermissions.requiredForSdk(Build.VERSION_CODES.S),
        )
    }
}

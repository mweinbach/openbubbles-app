package app.openbubbles.nativeapp.facetime

import android.content.pm.ServiceInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FaceTimeOriginPolicyTest {

    @Test
    fun `FaceTime WebView requires HTTPS and exact origin`() {
        val origin = assertNotNull(secureWebOrigin("https://facetime.example.test/join/abc"))

        assertTrue(matchesWebOrigin(origin, "https://facetime.example.test/room"))
        assertFalse(matchesWebOrigin(origin, "https://attacker.example.test/room"))
        assertFalse(matchesWebOrigin(origin, "http://facetime.example.test/room"))
        assertNull(secureWebOrigin("javascript:alert(1)"))
    }

    @Test
    fun `FaceTime FGS type is empty without camera or microphone`() {
        assertEquals(0, faceTimeForegroundServiceType(cameraGranted = false, microphoneGranted = false))
    }

    @Test
    fun `FaceTime FGS type includes only granted media permissions`() {
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA,
            faceTimeForegroundServiceType(cameraGranted = true, microphoneGranted = false),
        )
        assertEquals(
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            faceTimeForegroundServiceType(cameraGranted = true, microphoneGranted = true),
        )
    }

    @Test
    fun `FaceTime channel ids stay stable for incoming missed and in-call`() {
        assertEquals("facetime_incoming", FaceTimeNotifications.CHANNEL_INCOMING)
        assertEquals("facetime_missed", FaceTimeNotifications.CHANNEL_MISSED)
        assertEquals("com.bluebubbles.in_call_channel", FaceTimeNotifications.CHANNEL_IN_CALL)
        assertEquals(FaceTimeNotifications.CHANNEL_INCOMING, CreateIncomingFaceTimeNotification.CHANNEL_ID)
    }

    @Test
    fun `full-screen call settings are offered only when Android 14 denied the grant`() {
        assertFalse(shouldOfferFullScreenCallSettings(canUseFullScreenIntent = false, sdkInt = 33))
        assertFalse(shouldOfferFullScreenCallSettings(canUseFullScreenIntent = true, sdkInt = 34))
        assertTrue(shouldOfferFullScreenCallSettings(canUseFullScreenIntent = false, sdkInt = 34))
    }
}

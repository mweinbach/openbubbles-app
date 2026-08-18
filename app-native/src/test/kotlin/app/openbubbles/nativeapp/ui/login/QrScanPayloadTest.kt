package app.openbubbles.nativeapp.ui.login

import androidx.camera.core.CameraSelector
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class QrScanPayloadTest {

    @Test
    fun `empty ML Kit result is ignored`() {
        assertNull(qrScanPayload(null, null))
    }

    @Test
    fun `binary pairing payload is delivered`() {
        val bytes = byteArrayOf(0x4F, 0x41, 0x42, 0x53)
        val payload = qrScanPayload(bytes, null)

        assertEquals(bytes.toList(), payload?.first?.toList())
        assertEquals(null, payload?.second)
    }

    @Test
    fun `text pairing payload is delivered`() {
        val payload = qrScanPayload(null, "https://relay.example/pair")

        assertEquals(null, payload?.first)
        assertEquals("https://relay.example/pair", payload?.second)
    }

    @Test
    fun `QR camera tries the back lens then the front lens then any camera`() {
        val selectors = qrCameraSelectors()

        assertEquals(3, selectors.size)
        assertEquals(CameraSelector.DEFAULT_BACK_CAMERA, selectors[0])
        assertEquals(CameraSelector.DEFAULT_FRONT_CAMERA, selectors[1])
    }
}

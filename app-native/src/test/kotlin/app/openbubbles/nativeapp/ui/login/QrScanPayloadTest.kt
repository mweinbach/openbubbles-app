package app.openbubbles.nativeapp.ui.login

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
}

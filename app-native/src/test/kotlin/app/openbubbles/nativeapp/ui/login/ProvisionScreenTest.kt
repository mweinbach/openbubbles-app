package app.openbubbles.nativeapp.ui.login

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProvisionScreenTest {

    @Test
    fun `relay URL extracts host and activation code`() {
        val result = classifyProvisioningInput(
            bytes = null,
            text = "https://relay.example:8443/share/ABC-123",
        )

        val relay = assertIs<ProvisioningInput.Relay>(result)
        assertEquals("ABC-123", relay.code)
        assertEquals("https://relay.example:8443", relay.host)
    }

    @Test
    fun `plain activation code keeps configured host`() {
        val result = classifyProvisioningInput(
            bytes = null,
            text = "ABC-123",
            currentHost = "https://relay.example",
        )

        val relay = assertIs<ProvisioningInput.Relay>(result)
        assertEquals("ABC-123", relay.code)
        assertEquals("https://relay.example", relay.host)
    }

    @Test
    fun `OABS pairing payload is rejected`() {
        val result = classifyProvisioningInput(
            bytes = "OABS".toByteArray() + byteArrayOf(1) + "private-mac-payload".toByteArray(),
            text = null,
        )

        assertEquals(ProvisioningInput.UnsupportedRaw, result)
    }

    @Test
    fun `large base64 validation payload is rejected`() {
        val encoded = Base64.getEncoder().encodeToString(ByteArray(517) { it.toByte() })

        assertEquals(
            ProvisioningInput.UnsupportedRaw,
            classifyProvisioningInput(bytes = null, text = encoded),
        )
    }

    @Test
    fun `empty scan is invalid`() {
        assertEquals(
            ProvisioningInput.Invalid,
            classifyProvisioningInput(bytes = null, text = "  "),
        )
    }
}

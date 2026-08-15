package app.openbubbles.nativeapp.ui.login

import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ProvisionScreenTest {

    @Test
    fun `binary OABS QR extracts encoded hardware payload`() {
        val encoded = byteArrayOf(9, 8, 7, 6)
        val result = classifyProvisioningInput(
            bytes = "OABS".toByteArray() + byteArrayOf(1) + encoded,
            text = null,
        )

        assertContentEquals(encoded, assertIs<ProvisioningInput.Encoded>(result).payload)
    }

    @Test
    fun `base64 OABS payload is accepted offline`() {
        val encoded = byteArrayOf(5, 4, 3, 2)
        val oabs = "OABS".toByteArray() + byteArrayOf(0) + encoded
        val result = classifyProvisioningInput(
            bytes = null,
            text = Base64.getEncoder().encodeToString(oabs),
        )

        assertContentEquals(encoded, assertIs<ProvisioningInput.Encoded>(result).payload)
    }

    @Test
    fun `raw validation payload is accepted locally`() {
        val validationData = ByteArray(517) { index -> index.toByte() }.also { it[0] = 0x02 }
        val result = classifyProvisioningInput(
            bytes = null,
            text = Base64.getEncoder().encodeToString(validationData),
        )

        assertContentEquals(
            validationData,
            assertIs<ProvisioningInput.ValidationData>(result).payload,
        )
    }

    @Test
    fun `hosted relay URL is not accepted as local hardware`() {
        assertEquals(
            ProvisioningInput.Invalid,
            classifyProvisioningInput(
                bytes = null,
                text = "https://hw.openbubbles.app/ticket/hosted-code",
            ),
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

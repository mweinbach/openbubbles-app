package app.openbubbles.nativeapp.credentials

import com.upokecenter.cbor.CBORObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VaultPasskeyUserTagTest {

    private fun tag(build: CBORObject.() -> Unit): ByteArray =
        CBORObject.NewMap().apply(build).EncodeToBytes()

    @Test
    fun aFullUserTagYieldsBothLabels() {
        val user = vaultPasskeyUserDecoder().decode(
            tag {
                Add("id", byteArrayOf(1, 2, 3))
                Add("name", "ada@example.com")
                Add("displayName", "Ada Lovelace")
            },
        )

        assertEquals("ada@example.com", user?.name)
        assertEquals("Ada Lovelace", user?.displayName)
    }

    @Test
    fun aTagWithOnlyOneLabelKeepsIt() {
        val nameOnly = vaultPasskeyUserDecoder().decode(tag { Add("name", "ada@example.com") })
        assertEquals("ada@example.com", nameOnly?.name)
        assertNull(nameOnly?.displayName)

        val displayOnly = vaultPasskeyUserDecoder().decode(tag { Add("displayName", "Ada") })
        assertNull(displayOnly?.name)
        assertEquals("Ada", displayOnly?.displayName)
    }

    @Test
    fun aMalformedTagDecodesToNothingRatherThanThrowing() {
        // A single unreadable passkey must not fail the whole vault listing.
        assertNull(vaultPasskeyUserDecoder().decode(byteArrayOf(0x7f, 0x7f, 0x7f)))
        assertNull(vaultPasskeyUserDecoder().decode(ByteArray(0)))
    }
}

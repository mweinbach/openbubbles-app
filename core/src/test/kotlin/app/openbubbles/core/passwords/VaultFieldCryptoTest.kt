package app.openbubbles.core.passwords

import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the envelope the Android catalog stores. Android supplies
 * AndroidKeyStore keys; here plain JCE keys prove the format, the
 * authentication failure path, and the blind index off-device.
 */
class VaultFieldCryptoTest {

    private class JceKeys(
        private val data: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
        private val index: SecretKey = KeyGenerator.getInstance("HmacSHA256").generateKey(),
    ) : VaultCatalogKeys {
        override fun dataKey(): SecretKey = data
        override fun indexKey(): SecretKey = index
    }

    private val keys = JceKeys()
    private val crypto = AesGcmVaultFieldCrypto(keys)

    @Test
    fun sealedValuesRoundTrip() {
        listOf("example.com", "ada@example.com", "", "üñïçøde ✅", "a".repeat(4096)).forEach { value ->
            assertEquals(value, crypto.open(crypto.seal(value)))
        }
    }

    @Test
    fun theSameValueSealsDifferentlyEveryTime() {
        val first = crypto.seal("ada@example.com")
        val second = crypto.seal("ada@example.com")
        // A deterministic column would let anyone reading the file count how
        // many sites share an account name.
        assertNotEquals(first, second)
        assertEquals(crypto.open(first), crypto.open(second))
    }

    @Test
    fun aTamperedRowIsRejectedRatherThanDecoded() {
        val raw = Base64.getDecoder().decode(crypto.seal("ada@example.com"))
        // Flip one ciphertext byte just past the 12-byte nonce.
        raw[12] = (raw[12].toInt() xor 0x01).toByte()
        val tampered = Base64.getEncoder().withoutPadding().encodeToString(raw)
        assertFailsWith<VaultCatalogUnreadable> { crypto.open(tampered) }
    }

    @Test
    fun aTamperedNonceIsRejectedToo() {
        val raw = Base64.getDecoder().decode(crypto.seal("ada@example.com"))
        raw[0] = (raw[0].toInt() xor 0x01).toByte()
        val tampered = Base64.getEncoder().withoutPadding().encodeToString(raw)
        assertFailsWith<VaultCatalogUnreadable> { crypto.open(tampered) }
    }

    @Test
    fun aRowFromAnotherKeyIsUnreadable() {
        val sealed = AesGcmVaultFieldCrypto(JceKeys()).seal("ada@example.com")
        assertFailsWith<VaultCatalogUnreadable> { crypto.open(sealed) }
    }

    @Test
    fun malformedAndTruncatedRowsFailClosed() {
        assertFailsWith<VaultCatalogUnreadable> { crypto.open("not base64 at all!") }
        assertFailsWith<VaultCatalogUnreadable> { crypto.open("") }
        assertFailsWith<VaultCatalogUnreadable> { crypto.open("AAAA") }
    }

    @Test
    fun theBlindIndexIsStableKeyedAndDoesNotRevealTheHost() {
        val index = crypto.index("example.com")
        assertEquals(index, crypto.index("example.com"))
        assertNotEquals(index, crypto.index("other.example"))
        assertNotEquals(index, AesGcmVaultFieldCrypto(JceKeys()).index("example.com"))
        assertTrue(index.matches(Regex("[0-9a-f]{64}")))
        assertTrue("example" !in index)
    }

    @Test
    fun nullableColumnsStayNull() {
        assertNull(crypto.sealOrNull(null))
        assertNull(crypto.openOrNull(null))
        assertEquals("ada", crypto.openOrNull(crypto.sealOrNull("ada")))
    }
}

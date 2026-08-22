package app.openbubbles.nativeapp.credentials

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PasskeyAssertionTest {
    @Test
    fun `verified native apps use Android origins without failing HTTPS browser validation`() {
        assertTrue(
            passkeyRequestMatchesSelection(
                site = "bank.example.com",
                rpId = "BANK.EXAMPLE.COM.",
                origin = NATIVE_ORIGIN,
                browserOrigin = false,
            ),
        )
        assertFalse(
            passkeyRequestMatchesSelection(
                site = "bank.example.com",
                rpId = "bank.example.com",
                origin = NATIVE_ORIGIN,
                browserOrigin = true,
            ),
        )
    }

    @Test
    fun `native assertions reject replaced relying parties and invalid app origins`() {
        assertFalse(
            passkeyRequestMatchesSelection(
                "bank.example.com",
                "attacker.example",
                NATIVE_ORIGIN,
                browserOrigin = false,
            ),
        )
        assertFalse(
            passkeyRequestMatchesSelection(
                "bank.example.com",
                "bank.example.com.attacker.example",
                NATIVE_ORIGIN,
                browserOrigin = false,
            ),
        )
        assertFalse(
            passkeyRequestMatchesSelection(
                "bank.example.com",
                "bank.example.com",
                "android:apk-key-hash:spoofed",
                browserOrigin = false,
            ),
        )
        assertFalse(
            passkeyRequestMatchesSelection(
                "bank.example.com",
                "bank.example.com",
                "https://bank.example.com",
                browserOrigin = false,
            ),
        )
    }

    @Test
    fun `signed browsers retain strict HTTPS origin and exact relying party checks`() {
        assertTrue(
            passkeyRequestMatchesSelection(
                "bank.example.com",
                "bank.example.com",
                "https://login.bank.example.com",
                browserOrigin = true,
            ),
        )
        assertFalse(
            passkeyRequestMatchesSelection(
                "bank.example.com",
                "bank.example.com",
                "https://bank.example.com.attacker.example",
                browserOrigin = true,
            ),
        )
        assertFalse(
            passkeyRequestMatchesSelection(
                "bank.example.com",
                "bank.example.com",
                "http://bank.example.com",
                browserOrigin = true,
            ),
        )
        assertFalse(
            passkeyRequestMatchesSelection(
                "bank.example.com",
                "login.bank.example.com",
                "https://login.bank.example.com",
                browserOrigin = true,
            ),
        )
    }

    @Test
    fun `native sign in binds Android client data relying party and verified assertion`() {
        val keys = generateKeyPair()
        val assertion = createPasskeyAssertion(
            rpId = "bank.example.com",
            challenge = "challenge-value",
            origin = NATIVE_ORIGIN,
            packageName = RELYING_PARTY_PACKAGE,
            providedClientDataHash = null,
            privateKey = keys.private,
        )

        val clientData = Json.parseToJsonElement(assertion.clientDataJson.decodeToString()).jsonObject
        assertEquals("webauthn.get", clientData.getValue("type").jsonPrimitive.content)
        assertEquals("challenge-value", clientData.getValue("challenge").jsonPrimitive.content)
        assertEquals(NATIVE_ORIGIN, clientData.getValue("origin").jsonPrimitive.content)
        val signedPackage = clientData.getValue("androidPackageName").jsonPrimitive.content
        assertEquals(RELYING_PARTY_PACKAGE, signedPackage)
        assertNotEquals(PROVIDER_PACKAGE, signedPackage)
        assertContentEquals(sha256(assertion.clientDataJson), assertion.clientDataHash)
        assertContentEquals(
            sha256("bank.example.com".toByteArray()),
            assertion.authenticatorData.copyOfRange(0, 32),
        )
        assertEquals(0x1d, assertion.authenticatorData[32].toInt() and 0xff)
        assertContentEquals(byteArrayOf(0, 0, 0, 0), assertion.authenticatorData.copyOfRange(33, 37))
        assertTrue(signatureVerifies(assertion, keys.public, assertion.clientDataHash))
        assertFalse(signatureVerifies(assertion, keys.public, sha256("different-challenge".toByteArray())))
    }

    @Test
    fun `browser assertion omits Android package and signs the supplied client data hash`() {
        val keys = generateKeyPair()
        val generated = createPasskeyAssertion(
            rpId = "bank.example.com",
            challenge = "browser-challenge",
            origin = "https://login.bank.example.com",
            packageName = "com.android.browser",
            providedClientDataHash = null,
            privateKey = keys.private,
        )
        val browserData = Json.parseToJsonElement(generated.clientDataJson.decodeToString()).jsonObject
        assertEquals("https://login.bank.example.com", browserData.getValue("origin").jsonPrimitive.content)
        assertNull(browserData["androidPackageName"])
        assertTrue(signatureVerifies(generated, keys.public, generated.clientDataHash))

        val suppliedHash = ByteArray(32) { index -> index.toByte() }
        val supplied = createPasskeyAssertion(
            rpId = "bank.example.com",
            challenge = "browser-challenge",
            origin = "https://login.bank.example.com",
            packageName = "com.android.browser",
            providedClientDataHash = suppliedHash,
            privateKey = keys.private,
        )
        assertEquals("{}", supplied.clientDataJson.decodeToString())
        assertContentEquals(suppliedHash, supplied.clientDataHash)
        assertTrue(signatureVerifies(supplied, keys.public, suppliedHash))
        assertFalse(signatureVerifies(supplied, keys.public, sha256(supplied.clientDataJson)))
    }

    @Test
    fun `client data hash override requires privileged browser and exactly one SHA256 digest`() {
        assertTrue(passkeyClientDataHashValid(null, browserOrigin = false))
        assertTrue(passkeyClientDataHashValid(null, browserOrigin = true))
        assertTrue(passkeyClientDataHashValid(ByteArray(32), browserOrigin = true))
        assertFalse(passkeyClientDataHashValid(ByteArray(32), browserOrigin = false))
        assertFalse(passkeyClientDataHashValid(ByteArray(0), browserOrigin = true))
        assertFalse(passkeyClientDataHashValid(ByteArray(31), browserOrigin = true))
        assertFalse(passkeyClientDataHashValid(ByteArray(33), browserOrigin = true))

        assertFailsWith<IllegalArgumentException> {
            createPasskeyAssertion(
                rpId = "bank.example.com",
                challenge = "browser-challenge",
                origin = "https://login.bank.example.com",
                packageName = "com.android.browser",
                providedClientDataHash = ByteArray(31),
                privateKey = generateKeyPair().private,
            )
        }
    }

    private fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun signatureVerifies(
        assertion: PasskeyAssertion,
        publicKey: PublicKey,
        clientDataHash: ByteArray,
    ): Boolean = Signature.getInstance("SHA256withECDSA").run {
        initVerify(publicKey)
        update(assertion.authenticatorData)
        update(clientDataHash)
        verify(assertion.signature)
    }

    private companion object {
        const val NATIVE_ORIGIN = "android:apk-key-hash:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
        const val PROVIDER_PACKAGE = "com.openbubbles.messaging"
        const val RELYING_PARTY_PACKAGE = "com.example.bank"
    }
}

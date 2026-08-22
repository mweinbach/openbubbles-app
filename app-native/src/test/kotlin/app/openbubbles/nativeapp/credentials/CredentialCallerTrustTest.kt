package app.openbubbles.nativeapp.credentials

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CredentialCallerTrustTest {
    @Test
    fun `native app association accepts only an OS verified exact host`() {
        val verified = mapOf("Bank.Example.COM." to VERIFIED)

        assertTrue(isVerifiedDomain("bank.example.com", verified))
        assertFalse(isVerifiedDomain("login.bank.example.com", verified))
        assertFalse(isVerifiedDomain("example.com", verified))
        assertFalse(isVerifiedDomain("bank.example.com.attacker.test", verified))
        assertFalse(isVerifiedDomain("bank.example.com", mapOf("bank.example.com" to SELECTED)))
        assertFalse(isVerifiedDomain("bank.example.com", mapOf("bank.example.com" to NONE)))
        assertFalse(isVerifiedDomain("bank.example.com", emptyMap()))
    }

    @Test
    fun `signed browser origins and verified native apps use separate trust boundaries`() {
        assertTrue(
            credentialDomainAuthorized(
                "bank.example.com",
                "https://login.bank.example.com",
                emptyMap(),
            ),
        )
        assertFalse(
            credentialDomainAuthorized(
                "bank.example.com",
                "https://bank.example.com.attacker.test",
                mapOf("bank.example.com" to VERIFIED),
            ),
        )
        assertTrue(
            credentialDomainAuthorized(
                "bank.example.com",
                null,
                mapOf("bank.example.com" to VERIFIED),
            ),
        )
        assertFalse(
            credentialDomainAuthorized(
                "bank.example.com",
                null,
                mapOf("bank.example.com" to SELECTED),
            ),
        )
        assertFalse(credentialDomainAuthorized("bank.example.com", null, emptyMap()))
    }

    @Test
    fun `credential selection remains bound to its originating app and origin`() {
        assertTrue(
            selectionCallerMatches(
                "com.browser",
                "https://bank.example.com",
                "com.browser",
                "https://bank.example.com",
            ),
        )
        assertFalse(
            selectionCallerMatches(
                "com.browser",
                "https://bank.example.com",
                "com.attacker",
                "https://bank.example.com",
            ),
        )
        assertFalse(
            selectionCallerMatches(
                "com.browser",
                "https://bank.example.com",
                "com.browser",
                "https://attacker.example",
            ),
        )
        assertFalse(selectionCallerMatches("", "https://bank.example.com", "", "https://bank.example.com"))
    }

    @Test
    fun `autofill authentication rejects replaced activities or domains`() {
        assertTrue(
            autofillSelectionMatches(
                "com.bank.app",
                "BANK.EXAMPLE.COM",
                "com.bank.app",
                "bank.example.com",
            ),
        )
        assertFalse(
            autofillSelectionMatches(
                "com.bank.app",
                "bank.example.com",
                "com.attacker",
                "bank.example.com",
            ),
        )
        assertFalse(
            autofillSelectionMatches(
                "com.bank.app",
                "bank.example.com",
                "com.bank.app",
                "attacker.example",
            ),
        )
        assertFalse(autofillSelectionMatches("com.bank.app", "bank.example.com", "com.bank.app", null))
    }

    @Test
    fun `mutable pending intents cannot collide across callers origins or records`() {
        val original = credentialPendingIntentAction(
            "com.browser",
            "https://bank.example.com",
            "bank.example.com",
            "record-1",
            "password",
        )
        assertEquals(
            original,
            credentialPendingIntentAction(
                "com.browser",
                "https://bank.example.com",
                "bank.example.com",
                "record-1",
                "password",
            ),
        )
        assertNotEquals(
            original,
            credentialPendingIntentAction(
                "com.attacker",
                "https://bank.example.com",
                "bank.example.com",
                "record-1",
                "password",
            ),
        )
        assertNotEquals(
            original,
            credentialPendingIntentAction(
                "com.browser",
                "https://attacker.example",
                "attacker.example",
                "record-1",
                "password",
            ),
        )
        assertNotEquals(
            original,
            credentialPendingIntentAction(
                "com.browser",
                "https://bank.example.com",
                "bank.example.com",
                "record-2",
                "password",
            ),
        )
        assertFalse(original.contains("bank.example.com"))
    }

    private companion object {
        const val NONE = 0
        const val SELECTED = 1
        const val VERIFIED = 2
    }
}

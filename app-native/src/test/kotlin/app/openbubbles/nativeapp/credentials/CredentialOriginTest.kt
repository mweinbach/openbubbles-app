package app.openbubbles.nativeapp.credentials

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CredentialOriginTest {

    @Test
    fun `origin host accepts exact RP and real subdomains`() {
        assertTrue(originMatchesRpId("https://example.com", "example.com"))
        assertTrue(originMatchesRpId("https://login.example.com/path", "example.com"))
    }

    @Test
    fun `origin host rejects suffix confusion and unrelated hosts`() {
        assertFalse(originMatchesRpId("https://notexample.com", "example.com"))
        assertFalse(originMatchesRpId("https://example.com.attacker.test", "example.com"))
        assertFalse(originMatchesRpId("not a valid origin", "example.com"))
    }
}

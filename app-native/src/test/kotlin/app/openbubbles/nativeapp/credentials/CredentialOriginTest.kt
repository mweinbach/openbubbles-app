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

    @Test
    fun `privileged allowlist accepts only nonempty cache inside bounded grace`() {
        val now = 100_000L
        assertTrue(privilegedAllowlistCacheUsable(now - 10, 1, now, maxAgeMs = 20))
        assertFalse(privilegedAllowlistCacheUsable(now - 21, 1, now, maxAgeMs = 20))
        assertFalse(privilegedAllowlistCacheUsable(now - 10, 0, now, maxAgeMs = 20))
        assertFalse(privilegedAllowlistCacheUsable(now + 1, 1, now, maxAgeMs = 20))
    }
}

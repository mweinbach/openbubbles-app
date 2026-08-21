package app.openbubbles.core.passwords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VaultSiteKeyTest {
    @Test
    fun apppleSiteAndRequestOriginReduceToTheSameHost() {
        assertEquals("example.com", vaultSiteKey("example.com"))
        assertEquals("example.com", vaultSiteKey("https://example.com/login?next=%2Fhome"))
        assertEquals("example.com", vaultSiteKey("EXAMPLE.COM"))
        assertEquals("example.com", vaultSiteKey("  example.com  "))
        assertEquals("example.com", vaultSiteKey("https://user:pw@example.com:8443/"))
    }

    @Test
    fun trailingDotAndPortDoNotCreateASecondSite() {
        assertEquals("example.com", vaultSiteKey("example.com."))
        assertEquals("example.com", vaultSiteKey("example.com:443"))
        assertEquals(vaultSiteKey("example.com"), vaultSiteKey("https://example.com."))
    }

    @Test
    fun internationalizedHostsUseTheAsciiForm() {
        assertEquals("xn--bcher-kva.example", vaultSiteKey("bücher.example"))
        assertEquals(vaultSiteKey("bücher.example"), vaultSiteKey("https://BÜCHER.example/login"))
    }

    @Test
    fun unusableValuesNeverProduceAKey() {
        assertNull(vaultSiteKey(null))
        assertNull(vaultSiteKey(""))
        assertNull(vaultSiteKey("   "))
        assertNull(vaultSiteKey("."))
        assertNull(vaultSiteKey("/login"))
        assertNull(vaultSiteKey("Home Wi-Fi"))
    }

    @Test
    fun matchingIsExactHostEqualityNotSuffixContainment() {
        assertTrue(vaultSiteMatches("example.com", "https://example.com/login"))
        assertTrue(vaultSiteMatches("Example.com.", "example.com"))
        // A stored parent-domain password must not answer a subdomain request:
        // Apple matches `srvr` exactly and the cache may not be more generous.
        assertFalse(vaultSiteMatches("example.com", "accounts.example.com"))
        assertFalse(vaultSiteMatches("accounts.example.com", "example.com"))
        assertFalse(vaultSiteMatches("notexample.com", "example.com"))
        assertFalse(vaultSiteMatches("example.com", null))
        assertFalse(vaultSiteMatches(null, "example.com"))
    }
}

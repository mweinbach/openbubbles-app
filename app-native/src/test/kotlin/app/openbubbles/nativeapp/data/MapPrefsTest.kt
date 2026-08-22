package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MapPrefsTest {
    @Test
    fun `map imagery starts enabled when no preference was saved`() {
        assertTrue(resolveMapImageryPreference(null))
    }

    @Test
    fun `map imagery keeps an explicitly enabled preference`() {
        assertTrue(resolveMapImageryPreference(true))
    }

    @Test
    fun `map imagery respects an explicit privacy opt out`() {
        assertFalse(resolveMapImageryPreference(false))
    }

    @Test
    fun `google maps stays disabled before explicit consent`() {
        assertFalse(
            resolveGoogleMapsPreference(
                storedPreference = null,
                isConfigured = true,
                playServicesAvailable = true,
            ),
        )
    }

    @Test
    fun `google maps stays disabled after declining consent`() {
        assertFalse(
            resolveGoogleMapsPreference(
                storedPreference = false,
                isConfigured = true,
                playServicesAvailable = true,
            ),
        )
    }

    @Test
    fun `google maps requires a configured api key`() {
        assertFalse(
            resolveGoogleMapsPreference(
                storedPreference = true,
                isConfigured = false,
                playServicesAvailable = true,
            ),
        )
    }

    @Test
    fun `google maps requires working play services`() {
        assertFalse(
            resolveGoogleMapsPreference(
                storedPreference = true,
                isConfigured = true,
                playServicesAvailable = false,
            ),
        )
    }

    @Test
    fun `google maps activates only after explicit compatible consent`() {
        assertTrue(
            resolveGoogleMapsPreference(
                storedPreference = true,
                isConfigured = true,
                playServicesAvailable = true,
            ),
        )
    }
}

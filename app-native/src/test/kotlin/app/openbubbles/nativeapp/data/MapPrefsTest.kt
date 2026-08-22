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
}

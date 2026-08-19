package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeModeTest {

    @Test
    fun `persisted values round-trip`() {
        ThemeMode.entries.forEach { mode ->
            assertEquals(mode, ThemeMode.fromPersistedValue(mode.persistedValue))
        }
    }

    @Test
    fun `unknown or missing persisted value falls back to system`() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPersistedValue(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPersistedValue(""))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPersistedValue("sepia"))
    }

    @Test
    fun `system mode follows the platform setting`() {
        assertTrue(ThemeMode.SYSTEM.resolvesToDark(systemDark = true))
        assertFalse(ThemeMode.SYSTEM.resolvesToDark(systemDark = false))
    }

    @Test
    fun `light and dark ignore the platform setting`() {
        assertFalse(ThemeMode.LIGHT.resolvesToDark(systemDark = true))
        assertFalse(ThemeMode.LIGHT.resolvesToDark(systemDark = false))
        assertTrue(ThemeMode.DARK.resolvesToDark(systemDark = true))
        assertTrue(ThemeMode.DARK.resolvesToDark(systemDark = false))
    }
}

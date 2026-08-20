package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class SharedProfileStorageTest {
    @Test
    fun `java hash collisions cannot share a profile image path`() {
        val first = "Aa@x.com"
        val second = "BB@x.com"
        assertEquals(first.hashCode(), second.hashCode())

        val firstFile = sharedProfileFileName(first)
        val secondFile = sharedProfileFileName(second)

        assertNotEquals(firstFile, secondFile)
        assertTrue(firstFile.matches(Regex("[0-9a-f]{64}\\.img")))
        assertTrue(secondFile.matches(Regex("[0-9a-f]{64}\\.img")))
    }

    @Test
    fun `equivalent email casing shares the same profile image path`() {
        assertEquals(
            sharedProfileFileName(" Friend@Example.COM "),
            sharedProfileFileName("friend@example.com"),
        )
    }
}

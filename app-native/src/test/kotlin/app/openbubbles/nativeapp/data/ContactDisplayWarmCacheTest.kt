package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContactDisplayWarmCacheTest {

    @Test
    fun `seeded entries read back until the generation moves`() {
        var generation = 0
        val cache = ContactDisplayLru(maxEntries = 4) { generation }
        cache.put("tel:+1555", ContactDisplay("Alex", "content://photo/1"))
        assertEquals("Alex", cache.peek("tel:+1555")?.displayName)
        assertEquals("content://photo/1", cache.peek("tel:+1555")?.avatarPath)
        generation = 1
        assertNull(cache.peek("tel:+1555"))
    }

    @Test
    fun `a fresh write after invalidation is served again`() {
        var generation = 0
        val cache = ContactDisplayLru(maxEntries = 4) { generation }
        cache.put("a", ContactDisplay("Old", null))
        generation = 1
        cache.put("a", ContactDisplay("New", null))
        assertEquals("New", cache.peek("a")?.displayName)
    }

    @Test
    fun `eldest entries are evicted beyond the cap`() {
        val cache = ContactDisplayLru(maxEntries = 2) { 0 }
        cache.put("a", ContactDisplay("A", null))
        cache.put("b", ContactDisplay("B", null))
        cache.put("c", ContactDisplay("C", null))
        assertNull(cache.peek("a"))
        assertEquals("B", cache.peek("b")?.displayName)
        assertEquals("C", cache.peek("c")?.displayName)
    }

    @Test
    fun `blank addresses are never stored`() {
        val cache = ContactDisplayLru(maxEntries = 2) { 0 }
        cache.put("", ContactDisplay("Ghost", null))
        assertNull(cache.peek(""))
    }
}

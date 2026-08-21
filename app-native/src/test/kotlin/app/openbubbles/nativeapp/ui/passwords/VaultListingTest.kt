package app.openbubbles.nativeapp.ui.passwords

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun item(id: String, title: String, username: String? = null) =
    VaultItemUi(id = id, category = VaultCategory.Passwords, title = title, username = username)

/** List ordering and the A–Z sections built on top of it. */
class VaultListingTest {
    @Test
    fun `items are sorted by title regardless of the order iCloud returned`() {
        val sorted = filterVaultItems(
            items = listOf(item("1", "netflix.com"), item("2", "Apple"), item("3", "bank.example")),
            category = VaultCategory.Passwords,
            query = "",
        )
        assertEquals(listOf("Apple", "bank.example", "netflix.com"), sorted.map { it.title })
    }

    @Test
    fun `two logins on one site are ordered by username`() {
        val sorted = filterVaultItems(
            items = listOf(
                item("1", "example.com", "zoe@example.com"),
                item("2", "example.com", "adam@example.com"),
            ),
            category = VaultCategory.Passwords,
            query = "",
        )
        assertEquals(listOf("adam@example.com", "zoe@example.com"), sorted.map { it.username })
    }

    @Test
    fun `search matches the site or the username, case-insensitively`() {
        val items = listOf(
            item("1", "example.com", "person@example.com"),
            item("2", "Bank", "someone@else.com"),
        )
        assertEquals(
            listOf("example.com"),
            filterVaultItems(items, VaultCategory.Passwords, "EXAMPLE").map { it.title },
        )
        assertEquals(
            listOf("Bank"),
            filterVaultItems(items, VaultCategory.Passwords, "else.com").map { it.title },
        )
        assertTrue(filterVaultItems(items, VaultCategory.Passwords, "nothing").isEmpty())
    }

    @Test
    fun `only the shown category is listed`() {
        val items = listOf(
            item("1", "example.com"),
            VaultItemUi(id = "2", category = VaultCategory.Wifi, title = "Home network"),
        )
        assertEquals(1, filterVaultItems(items, VaultCategory.Passwords, "").size)
        assertEquals(1, filterVaultItems(items, VaultCategory.Wifi, "").size)
    }

    @Test
    fun `a short list is one unlabelled run rather than a header per row`() {
        val sections = vaultSections((1..5).map { item("$it", "site$it.com") })
        assertEquals(1, sections.size)
        assertEquals("", sections.single().letter)
        assertEquals(5, sections.single().items.size)
    }

    @Test
    fun `a long list gets alphabetical headers`() {
        val items = filterVaultItems(
            items = ('a'..'z').map { letter -> item("$letter", "${letter}site.com") },
            category = VaultCategory.Passwords,
            query = "",
        )
        val sections = vaultSections(items)
        assertEquals(26, sections.size)
        assertEquals("A", sections.first().letter)
        assertEquals("Z", sections.last().letter)
        assertTrue(sections.all { it.items.size == 1 })
    }

    @Test
    fun `anything that does not start with a letter groups under a hash`() {
        val items = filterVaultItems(
            items = listOf(item("1", "1password.com"), item("2", "192.168.1.1"), item("3", "apple.com")) +
                (1..12).map { item("pad$it", "site$it.example") },
            category = VaultCategory.Passwords,
            query = "",
        )
        val sections = vaultSections(items)
        val hash = sections.first { it.letter == "#" }
        assertEquals(listOf("192.168.1.1", "1password.com"), hash.items.map { it.title })
    }

    @Test
    fun `every item survives sectioning exactly once`() {
        val items = filterVaultItems(
            items = (1..40).map { item("$it", "site${it % 7}-$it.com") },
            category = VaultCategory.Passwords,
            query = "",
        )
        val flattened = vaultSections(items).flatMap { it.items }
        assertEquals(items, flattened)
    }
}

/** The suggestion offered by the create form. */
class VaultPasswordGeneratorTest {
    @Test
    fun `a suggested password has every class a site is likely to demand`() {
        val random = Random(20260821)
        repeat(200) {
            val generated = VaultPasswordGenerator.generate(random = random)
            assertEquals(VaultPasswordGenerator.DEFAULT_LENGTH, generated.length)
            assertTrue(VaultPasswordGenerator.isStrong(generated), "weak: $generated")
        }
    }

    @Test
    fun `an unreasonably short request is raised to the floor`() {
        val generated = VaultPasswordGenerator.generate(length = 3, random = Random(1))
        assertEquals(VaultPasswordGenerator.MINIMUM_LENGTH, generated.length)
    }

    @Test
    fun `glyphs that cannot be read aloud unambiguously are excluded`() {
        val random = Random(7)
        val sample = (1..50).joinToString("") { VaultPasswordGenerator.generate(random = random) }
        listOf('l', 'I', 'O', '0', '1').forEach { forbidden ->
            assertFalse(sample.contains(forbidden), "found $forbidden")
        }
    }

    @Test
    fun `suggestions differ from each other`() {
        val random = Random(99)
        val generated = (1..50).map { VaultPasswordGenerator.generate(random = random) }
        assertEquals(generated.size, generated.distinct().size)
    }

    @Test
    fun `strength checking rejects what it should`() {
        assertFalse(VaultPasswordGenerator.isStrong("short1!A"))
        assertFalse(VaultPasswordGenerator.isStrong("alllowercaseonly"))
        assertFalse(VaultPasswordGenerator.isStrong("NoSymbolsHere123"))
        assertTrue(VaultPasswordGenerator.isStrong("Adequate-Passw0rd"))
    }
}

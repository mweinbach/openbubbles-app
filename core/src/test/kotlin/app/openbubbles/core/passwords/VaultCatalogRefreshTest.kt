package app.openbubbles.core.passwords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class VaultCatalogRefreshTest {

    private class FakeListing(
        var inClique: Boolean = true,
        var passwords: List<VaultItemRecord> = emptyList(),
        var passkeys: List<VaultItemRecord> = emptyList(),
        var groups: List<VaultGroupRecord> = emptyList(),
        var invites: List<VaultInviteRecord> = emptyList(),
    ) : VaultListingSource {
        val requestedKinds = mutableListOf<VaultItemKind>()

        override suspend fun inClique(): Boolean = inClique

        override suspend fun items(kind: VaultItemKind): List<VaultItemRecord> {
            requestedKinds += kind
            return when (kind) {
                VaultItemKind.Password -> passwords
                VaultItemKind.Passkey -> passkeys
                else -> emptyList()
            }
        }

        override suspend fun groups(): List<VaultGroupRecord> = groups
        override suspend fun invites(): List<VaultInviteRecord> = invites
    }

    private val password = VaultItemRecord(
        id = "rec-1",
        kind = VaultItemKind.Password,
        site = "example.com",
        title = "example.com",
        username = "ada@example.com",
    )

    @Test
    fun onePassRepublishesEveryKindAndTheGroupRoster() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        val listing = FakeListing(
            passwords = listOf(password),
            groups = listOf(VaultGroupRecord("group-1", "Family", owner = true, memberCount = 1)),
            invites = listOf(VaultInviteRecord("invite-1", "Work", "grace@example.com")),
        )

        refreshVaultCatalog(listing, catalog, nowMs = { 1_234 })

        val cached = catalog.load()
        assertEquals(VaultItemKind.entries.toSet(), cached.syncedKinds.toSet())
        assertTrue(cached.groupsSynced)
        assertEquals(1_234, cached.syncedAtMs)
        assertEquals(listOf("rec-1"), cached.items(VaultItemKind.Password).map { it.id })
        assertEquals(listOf("Family"), cached.groups.map { it.name })
        assertEquals(listOf("Work"), cached.invites.map { it.groupName })
        assertEquals(VaultItemKind.entries.toList(), listing.requestedKinds.toList())
    }

    @Test
    fun losingKeychainAccessClearsTheCatalogInsteadOfFreezingIt() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        refreshVaultCatalog(FakeListing(passwords = listOf(password)), catalog)
        assertFalse(catalog.load().cold)

        // A cached entry the vault can no longer open would appear in the
        // Android picker and then fail at selection.
        refreshVaultCatalog(FakeListing(inClique = false), catalog)

        val cached = catalog.load()
        assertTrue(cached.cold)
        assertEquals(emptyList(), cached.items)
    }

    @Test
    fun aSupersededGenerationCannotRepopulateTheCatalog() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        // Stands in for sign-out advancing the generation while a pass is in
        // flight: every write is dropped, so nothing of the old account lands.
        refreshVaultCatalog(
            source = FakeListing(passwords = listOf(password)),
            catalog = catalog,
            publish = { },
        )

        assertTrue(catalog.load().cold)
        assertEquals(emptyList(), catalog.load().items)
    }

    @Test
    fun aSupersededGenerationAlsoDropsTheClearWrite() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        refreshVaultCatalog(FakeListing(passwords = listOf(password)), catalog)

        // The next account already owns the catalog; a late pass from the old
        // one must not wipe it.
        refreshVaultCatalog(
            source = FakeListing(inClique = false),
            catalog = catalog,
            publish = { },
        )

        assertEquals(listOf("rec-1"), catalog.load().items(VaultItemKind.Password).map { it.id })
    }
}

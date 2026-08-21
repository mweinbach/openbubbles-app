package app.openbubbles.core.passwords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * Semantics every [VaultCatalog] must hold. `InMemoryVaultCatalog` is the
 * reference; the Android SQLite catalog implements the same contract with the
 * same transaction boundaries.
 */
class VaultCatalogContractTest {

    private fun password(id: String, site: String, username: String) = VaultItemRecord(
        id = id,
        kind = VaultItemKind.Password,
        site = site,
        title = site,
        username = username,
    )

    private fun passkey(id: String, site: String, credentialId: String?) = VaultItemRecord(
        id = id,
        kind = VaultItemKind.Passkey,
        site = site,
        title = site,
        username = "ada",
        webauthnCredentialId = credentialId,
    )

    @Test
    fun aFreshCatalogIsColdRatherThanEmpty() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        val cached = catalog.load()
        assertTrue(cached.cold)
        assertEquals(emptyList(), cached.items)

        val snapshot = catalog.credentialsForSite("example.com", setOf(VaultItemKind.Password))
        assertEquals(emptySet(), snapshot.syncedKinds)
    }

    @Test
    fun anEmptyListingMarksTheKindWarm() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        catalog.replaceItems(VaultItemKind.Password, emptyList(), syncedAtMs = 10)

        val cached = catalog.load()
        assertFalse(cached.cold)
        assertEquals(setOf(VaultItemKind.Password), cached.syncedKinds)
        assertEquals(
            setOf(VaultItemKind.Password),
            catalog.credentialsForSite("example.com", setOf(VaultItemKind.Password)).syncedKinds,
        )
    }

    @Test
    fun aListingReplacesTheKindAndLeavesOtherKindsAlone() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        catalog.replaceItems(
            VaultItemKind.Password,
            listOf(password("a", "example.com", "ada@example.com")),
            syncedAtMs = 10,
        )
        catalog.replaceItems(
            VaultItemKind.Passkey,
            listOf(passkey("b", "example.com", "Y3JlZC1pZA")),
            syncedAtMs = 10,
        )
        catalog.replaceItems(
            VaultItemKind.Password,
            listOf(password("c", "other.example", "grace@other.example")),
            syncedAtMs = 20,
        )

        val cached = catalog.load()
        assertEquals(listOf("c"), cached.items(VaultItemKind.Password).map { it.id })
        assertEquals(listOf("b"), cached.items(VaultItemKind.Passkey).map { it.id })
        assertEquals(20, cached.syncedAtMs)
    }

    @Test
    fun siteLookupIsExactAndScopedToTheRequestedKinds() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        catalog.replaceItems(
            VaultItemKind.Password,
            listOf(
                password("a", "Example.com", "ada@example.com"),
                password("b", "accounts.example.com", "ada@example.com"),
            ),
            syncedAtMs = 10,
        )
        catalog.replaceItems(
            VaultItemKind.Passkey,
            listOf(passkey("c", "example.com", "Y3JlZC1pZA")),
            syncedAtMs = 10,
        )

        assertEquals(
            listOf("a"),
            catalog.credentialsForSite("https://example.com/login", setOf(VaultItemKind.Password))
                .items.map { it.id },
        )
        assertEquals(
            listOf("a", "c").sorted(),
            catalog.credentialsForSite(
                "example.com",
                setOf(VaultItemKind.Password, VaultItemKind.Passkey),
            ).items.map { it.id }.sorted(),
        )
        assertEquals(
            emptyList(),
            catalog.credentialsForSite("nope.example", setOf(VaultItemKind.Password)).items,
        )
    }

    @Test
    fun aSiteHydrationReplacesThatSiteWithoutMarkingTheKindWarm() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        catalog.mergeSiteItems(
            "example.com",
            VaultItemKind.Passkey,
            listOf(passkey("a", "example.com", "Y3JlZC1pZA")),
        )

        val snapshot = catalog.credentialsForSite("example.com", setOf(VaultItemKind.Passkey))
        assertEquals(listOf("a"), snapshot.items.map { it.id })
        // Still cold: one site is not a listing, so a later request for another
        // site must not read this as "the vault has nothing".
        assertEquals(emptySet(), snapshot.syncedKinds)
        assertTrue(catalog.load().cold)

        catalog.mergeSiteItems(
            "example.com",
            VaultItemKind.Passkey,
            listOf(passkey("b", "example.com", "b3RoZXI")),
        )
        assertEquals(
            listOf("b"),
            catalog.credentialsForSite("example.com", setOf(VaultItemKind.Passkey)).items.map { it.id },
        )
    }

    @Test
    fun aSiteHydrationLeavesOtherSitesInPlace() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        catalog.replaceItems(
            VaultItemKind.Password,
            listOf(
                password("a", "example.com", "ada@example.com"),
                password("b", "other.example", "grace@other.example"),
            ),
            syncedAtMs = 10,
        )
        catalog.mergeSiteItems(
            "example.com",
            VaultItemKind.Password,
            listOf(password("c", "example.com", "ada+new@example.com")),
        )

        assertEquals(
            listOf("b", "c").sorted(),
            catalog.load().items(VaultItemKind.Password).map { it.id }.sorted(),
        )
    }

    @Test
    fun groupsAndInvitesArePublishedTogether() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        catalog.replaceGroups(
            groups = listOf(
                VaultGroupRecord(
                    id = "group-1",
                    name = "Family",
                    owner = true,
                    memberCount = 2,
                    members = listOf(
                        VaultGroupMemberRecord("Ada", "ada@example.com", joined = true, currentUser = true),
                        VaultGroupMemberRecord(null, "grace@example.com", joined = false, currentUser = false),
                    ),
                ),
            ),
            invites = listOf(VaultInviteRecord("invite-1", "Work", "grace@example.com")),
            syncedAtMs = 30,
        )

        val cached = catalog.load()
        assertTrue(cached.groupsSynced)
        assertFalse(cached.cold)
        assertEquals(listOf("Family"), cached.groups.map { it.name })
        assertEquals(2, cached.groups.single().members.size)
        assertEquals(listOf("Work"), cached.invites.map { it.groupName })
    }

    @Test
    fun signOutLeavesTheCatalogColdAgain() = runBlocking {
        val catalog = InMemoryVaultCatalog()
        catalog.replaceItems(
            VaultItemKind.Password,
            listOf(password("a", "example.com", "ada@example.com")),
            syncedAtMs = 10,
        )
        catalog.replaceGroups(emptyList(), emptyList(), syncedAtMs = 10)

        catalog.clearAccountData()

        val cached = catalog.load()
        assertTrue(cached.cold)
        assertEquals(emptyList(), cached.items)
        assertEquals(emptyList(), cached.groups)
        assertEquals(
            emptyList(),
            catalog.credentialsForSite("example.com", setOf(VaultItemKind.Password)).items,
        )
    }
}

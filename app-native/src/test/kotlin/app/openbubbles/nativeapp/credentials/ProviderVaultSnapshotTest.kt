package app.openbubbles.nativeapp.credentials

import app.openbubbles.core.passwords.InMemoryVaultCatalog
import app.openbubbles.core.passwords.VaultCatalog
import app.openbubbles.core.passwords.VaultItemKind
import app.openbubbles.core.passwords.VaultSiteSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith

class ProviderVaultSnapshotTest {

    @Test
    fun `catalog failure becomes a cold site snapshot`() = runTest {
        val backing = InMemoryVaultCatalog()
        val failing = object : VaultCatalog by backing {
            override suspend fun credentialsForSite(
                site: String,
                kinds: Set<VaultItemKind>,
            ): VaultSiteSnapshot = error("catalog unavailable")
        }

        val snapshot = providerVaultSnapshot(
            failing,
            "https://Example.com/login",
            setOf(VaultItemKind.Password),
        )

        assertEquals("example.com", snapshot.siteKey)
        assertEquals(emptyList(), snapshot.items)
        assertEquals(emptySet(), snapshot.syncedKinds)
    }

    @Test
    fun `catalog cancellation is preserved`() = runTest {
        val backing = InMemoryVaultCatalog()
        val cancelled = object : VaultCatalog by backing {
            override suspend fun credentialsForSite(
                site: String,
                kinds: Set<VaultItemKind>,
            ): VaultSiteSnapshot = throw CancellationException("cancelled")
        }

        assertFailsWith<CancellationException> {
            providerVaultSnapshot(cancelled, "example.com", setOf(VaultItemKind.Password))
        }
    }
}

package app.openbubbles.core.passwords

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class VaultCredentialLookupTest {

    private val password = VaultItemRecord(
        id = "record-password",
        kind = VaultItemKind.Password,
        site = "example.com",
        title = "example.com",
        username = "ada@example.com",
    )

    private val passkey = VaultItemRecord(
        id = "record-passkey",
        kind = VaultItemKind.Passkey,
        site = "example.com",
        title = "example.com",
        username = "ada",
        webauthnCredentialId = "Y3JlZC1pZA",
    )

    private fun snapshot(
        items: List<VaultItemRecord>,
        syncedKinds: Set<VaultItemKind>,
        syncedAtMs: Long = System.currentTimeMillis(),
    ) = VaultSiteSnapshot(
        siteKey = "example.com",
        items = items,
        syncedKinds = syncedKinds,
        syncedAtMs = syncedAtMs,
    )

    private fun request(
        passwords: Boolean = true,
        passkeys: Boolean = false,
        allowed: Set<String>? = null,
    ) = VaultCredentialRequest(
        site = "example.com",
        wantsPasswords = passwords,
        wantsPasskeys = passkeys,
        allowedCredentialIds = allowed,
    )

    @Test
    fun warmCatalogAnswersWithoutTheBackend() {
        val plan = planVaultLookup(
            snapshot(listOf(password), setOf(VaultItemKind.Password)),
            request(),
            backendReady = false,
        )
        assertEquals(VaultLookupPlan.Serve(listOf(password)), plan)
    }

    @Test
    fun warmCatalogWithNothingForTheSiteIsAnHonestEmptyAnswer() {
        val plan = planVaultLookup(
            snapshot(emptyList(), setOf(VaultItemKind.Password)),
            request(),
            backendReady = false,
        )
        assertEquals(VaultLookupPlan.NoCredentials, plan)
    }

    @Test
    fun staleCatalogMissRevalidatesInsteadOfReturningEmpty() {
        val plan = planVaultLookup(
            snapshot(
                items = emptyList(),
                syncedKinds = setOf(VaultItemKind.Password),
                syncedAtMs = 1_000,
            ),
            request(),
            backendReady = false,
            nowMs = 1_000 + VAULT_CATALOG_MISS_MAX_AGE_MS + 1,
        )

        assertEquals(VaultLookupPlan.RequireUnlock, plan)
    }

    @Test
    fun coldCatalogAsksTheBackendWhenItIsAlreadyRunning() {
        val plan = planVaultLookup(
            snapshot(emptyList(), emptySet()),
            request(),
            backendReady = true,
        )
        assertEquals(VaultLookupPlan.ConsultBackend, plan)
    }

    @Test
    fun coldCatalogAndNoBackendOffersUnlockRatherThanAnEmptyPicker() {
        val plan = planVaultLookup(
            snapshot(emptyList(), emptySet()),
            request(),
            backendReady = false,
        )
        assertEquals(VaultLookupPlan.RequireUnlock, plan)
    }

    @Test
    fun aPartiallyWarmCatalogStillConsultsTheBackend() {
        // Passwords were listed, passkeys never were: answering from the cache
        // would hide every passkey for the site.
        val plan = planVaultLookup(
            snapshot(listOf(password), setOf(VaultItemKind.Password)),
            request(passwords = true, passkeys = true),
            backendReady = true,
        )
        assertEquals(VaultLookupPlan.ConsultBackend, plan)
    }

    @Test
    fun allowCredentialsFiltersCachedPasskeys() {
        val plan = planVaultLookup(
            snapshot(listOf(passkey), setOf(VaultItemKind.Passkey)),
            request(passwords = false, passkeys = true, allowed = setOf("Y3JlZC1pZA")),
            backendReady = false,
        )
        assertEquals(VaultLookupPlan.Serve(listOf(passkey)), plan)

        val excluded = planVaultLookup(
            snapshot(listOf(passkey), setOf(VaultItemKind.Passkey)),
            request(passwords = false, passkeys = true, allowed = setOf("c29tZXRoaW5nLWVsc2U")),
            backendReady = false,
        )
        assertEquals(VaultLookupPlan.NoCredentials, excluded)
    }

    @Test
    fun aPasskeyWithNoKnownCredentialIdIsNeverOfferedAgainstAnAllowList() {
        val unprovable = passkey.copy(webauthnCredentialId = null)
        assertEquals(
            VaultLookupPlan.ConsultBackend,
            planVaultLookup(
                snapshot(listOf(unprovable), setOf(VaultItemKind.Passkey)),
                request(passwords = false, passkeys = true, allowed = setOf("Y3JlZC1pZA")),
                backendReady = true,
            ),
        )
        assertEquals(
            VaultLookupPlan.RequireUnlock,
            planVaultLookup(
                snapshot(listOf(unprovable), setOf(VaultItemKind.Passkey)),
                request(passwords = false, passkeys = true, allowed = setOf("Y3JlZC1pZA")),
                backendReady = false,
            ),
        )
    }

    @Test
    fun aDiscoverableRequestAcceptsAPasskeyWithoutAKnownCredentialId() {
        val unprovable = passkey.copy(webauthnCredentialId = null)
        val plan = planVaultLookup(
            snapshot(listOf(unprovable), setOf(VaultItemKind.Passkey)),
            request(passwords = false, passkeys = true, allowed = null),
            backendReady = false,
        )
        assertEquals(VaultLookupPlan.Serve(listOf(unprovable)), plan)
    }

    @Test
    fun servedCredentialsAreLimitedToTheRequestedTypes() {
        val plan = planVaultLookup(
            snapshot(listOf(password, passkey), setOf(VaultItemKind.Password, VaultItemKind.Passkey)),
            request(passwords = true, passkeys = false),
            backendReady = false,
        )
        assertEquals(VaultLookupPlan.Serve(listOf(password)), plan)
    }

    @Test
    fun anUnusableSiteAndAnEmptyRequestBothDeclineWithoutTouchingTheBackend() {
        assertIs<VaultLookupPlan.NoCredentials>(
            planVaultLookup(
                VaultSiteSnapshot(siteKey = null),
                request(),
                backendReady = true,
            ),
        )
        assertIs<VaultLookupPlan.NoCredentials>(
            planVaultLookup(
                snapshot(listOf(password), setOf(VaultItemKind.Password)),
                request(passwords = false, passkeys = false),
                backendReady = true,
            ),
        )
    }
}

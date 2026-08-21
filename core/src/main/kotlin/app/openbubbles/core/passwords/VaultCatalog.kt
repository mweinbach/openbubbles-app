package app.openbubbles.core.passwords

enum class VaultItemKind {
    Password,
    Passkey,
    Code,
    Wifi,
}

/**
 * One vault row as the Kotlin side is allowed to remember it: identity and
 * labels only. A password string, TOTP seed, Wi-Fi key, or passkey private key
 * must never reach this type — those stay in the Rust keychain state and are
 * fetched for the lifetime of a single request.
 */
data class VaultItemRecord(
    /** Apple record id. This is the same id the provider boundary calls `credId`. */
    val id: String,
    val kind: VaultItemKind,
    /** Site exactly as Apple stores it, so a later exact-match backend lookup still resolves. */
    val site: String,
    val title: String,
    val username: String? = null,
    val displayName: String? = null,
    /** base64url WebAuthn credential id; passkeys only, needed for allow-list filtering. */
    val webauthnCredentialId: String? = null,
    val groupId: String? = null,
    val modifiedAtMs: Long? = null,
)

data class VaultGroupMemberRecord(
    val name: String?,
    val handle: String,
    val joined: Boolean,
    val currentUser: Boolean,
)

data class VaultGroupRecord(
    val id: String,
    val name: String,
    val owner: Boolean,
    val memberCount: Int,
    val members: List<VaultGroupMemberRecord> = emptyList(),
)

data class VaultInviteRecord(
    val id: String,
    val groupName: String,
    val inviter: String,
)

/**
 * Everything the catalog knows, restored before any live Apple request runs.
 * [syncedKinds] and [groupsSynced] separate "we looked and there is nothing"
 * from "we have never looked", which is the difference between showing an
 * empty vault and showing a broken one.
 */
data class CachedVault(
    val items: List<VaultItemRecord> = emptyList(),
    val groups: List<VaultGroupRecord> = emptyList(),
    val invites: List<VaultInviteRecord> = emptyList(),
    val syncedKinds: Set<VaultItemKind> = emptySet(),
    val groupsSynced: Boolean = false,
    val syncedAtMs: Long? = null,
) {
    val cold: Boolean get() = syncedKinds.isEmpty() && !groupsSynced

    fun items(kind: VaultItemKind): List<VaultItemRecord> = items.filter { it.kind == kind }
}

/** The catalog rows that matched one site, plus how much of the catalog is warm. */
data class VaultSiteSnapshot(
    val siteKey: String?,
    val items: List<VaultItemRecord> = emptyList(),
    val syncedKinds: Set<VaultItemKind> = emptySet(),
    val syncedAtMs: Long? = null,
)

/**
 * Durable vault metadata. Implementations must publish a kind's rows and its
 * sync marker in one transaction: marking a kind synced before its rows are
 * durable would make a later cold start report an empty vault as complete.
 */
interface VaultCatalog {
    suspend fun load(): CachedVault

    suspend fun replaceItems(kind: VaultItemKind, items: List<VaultItemRecord>, syncedAtMs: Long)

    suspend fun replaceGroups(
        groups: List<VaultGroupRecord>,
        invites: List<VaultInviteRecord>,
        syncedAtMs: Long,
    )

    /**
     * Upserts rows learned from a single site request without disturbing the
     * kind's sync marker. A per-site hydration is not a full listing, so it
     * must not make a cold catalog look warm.
     */
    suspend fun mergeSiteItems(site: String, kind: VaultItemKind, items: List<VaultItemRecord>)

    /** Exact canonical-site lookup used by the Android credential provider. */
    suspend fun credentialsForSite(site: String, kinds: Set<VaultItemKind>): VaultSiteSnapshot

    suspend fun clearAccountData()
}

/** Reference implementation used by desktop, tests, and the catalog contract suite. */
class InMemoryVaultCatalog : VaultCatalog {
    private val lock = Any()
    private var itemsByKind: Map<VaultItemKind, List<VaultItemRecord>> = emptyMap()
    private var syncedKinds: Set<VaultItemKind> = emptySet()
    private var groups: List<VaultGroupRecord> = emptyList()
    private var invites: List<VaultInviteRecord> = emptyList()
    private var groupsSynced: Boolean = false
    private var syncedAtByKind: Map<VaultItemKind, Long> = emptyMap()
    private var groupsSyncedAtMs: Long? = null

    override suspend fun load(): CachedVault = synchronized(lock) {
        CachedVault(
            items = VaultItemKind.entries.flatMap { itemsByKind[it].orEmpty() },
            groups = groups,
            invites = invites,
            syncedKinds = syncedKinds,
            groupsSynced = groupsSynced,
            syncedAtMs = (syncedAtByKind.values + listOfNotNull(groupsSyncedAtMs)).maxOrNull(),
        )
    }

    override suspend fun replaceItems(
        kind: VaultItemKind,
        items: List<VaultItemRecord>,
        syncedAtMs: Long,
    ) = synchronized(lock) {
        itemsByKind = itemsByKind + (kind to items.filter { it.kind == kind })
        syncedKinds = syncedKinds + kind
        syncedAtByKind = syncedAtByKind + (kind to syncedAtMs)
    }

    override suspend fun replaceGroups(
        groups: List<VaultGroupRecord>,
        invites: List<VaultInviteRecord>,
        syncedAtMs: Long,
    ) = synchronized(lock) {
        this.groups = groups
        this.invites = invites
        groupsSynced = true
        groupsSyncedAtMs = syncedAtMs
    }

    override suspend fun mergeSiteItems(
        site: String,
        kind: VaultItemKind,
        items: List<VaultItemRecord>,
    ) = synchronized(lock) {
        val siteKey = vaultSiteKey(site)
        val kept = itemsByKind[kind].orEmpty().filterNot { existing ->
            existing.id in items.map { it.id }.toSet() ||
                (siteKey != null && vaultSiteKey(existing.site) == siteKey)
        }
        itemsByKind = itemsByKind + (kind to kept + items.filter { it.kind == kind })
    }

    override suspend fun credentialsForSite(
        site: String,
        kinds: Set<VaultItemKind>,
    ): VaultSiteSnapshot = synchronized(lock) {
        val siteKey = vaultSiteKey(site)
        VaultSiteSnapshot(
            siteKey = siteKey,
            items = if (siteKey == null) {
                emptyList()
            } else {
                kinds.flatMap { itemsByKind[it].orEmpty() }
                    .filter { vaultSiteKey(it.site) == siteKey }
            },
            syncedKinds = syncedKinds intersect kinds,
            syncedAtMs = kinds.mapNotNull(syncedAtByKind::get).minOrNull(),
        )
    }

    override suspend fun clearAccountData() = synchronized(lock) {
        itemsByKind = emptyMap()
        syncedKinds = emptySet()
        groups = emptyList()
        invites = emptyList()
        groupsSynced = false
        syncedAtByKind = emptyMap()
        groupsSyncedAtMs = null
    }
}

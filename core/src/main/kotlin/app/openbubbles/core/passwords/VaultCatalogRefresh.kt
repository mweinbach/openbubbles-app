package app.openbubbles.core.passwords

/** One vault listing pass, as the catalog needs it. */
interface VaultListingSource {
    suspend fun inClique(): Boolean
    suspend fun items(kind: VaultItemKind): List<VaultItemRecord>
    suspend fun groups(): List<VaultGroupRecord>
    suspend fun invites(): List<VaultInviteRecord>
}

/**
 * Republishes the whole catalog from one listing pass.
 *
 * Losing keychain access clears the catalog rather than freezing it: a cached
 * entry the vault can no longer open would show up in the Android picker and
 * then fail at selection, and the cached site list should not outlive the
 * account's access to it.
 *
 * @param publish wraps every durable write so an account generation that has
 * since been superseded drops its writes instead of applying them.
 */
suspend fun refreshVaultCatalog(
    source: VaultListingSource,
    catalog: VaultCatalog,
    nowMs: () -> Long = System::currentTimeMillis,
    publish: suspend (suspend () -> Unit) -> Unit = { write -> write() },
) {
    if (!source.inClique()) {
        publish { catalog.clearAccountData() }
        return
    }
    VaultItemKind.entries.forEach { kind ->
        val items = source.items(kind)
        publish { catalog.replaceItems(kind, items, nowMs()) }
    }
    val groups = source.groups()
    val invites = source.invites()
    publish { catalog.replaceGroups(groups, invites, nowMs()) }
}

package app.openbubbles.core.passwords

/** One Android credential-provider query, reduced to what the catalog can answer. */
data class VaultCredentialRequest(
    val site: String,
    val wantsPasswords: Boolean,
    val wantsPasskeys: Boolean,
    /** base64url ids from `allowCredentials`; `null` means any discoverable passkey. */
    val allowedCredentialIds: Set<String>? = null,
) {
    val kinds: Set<VaultItemKind> = buildSet {
        if (wantsPasswords) add(VaultItemKind.Password)
        if (wantsPasskeys) add(VaultItemKind.Passkey)
    }
}

/**
 * What the provider should do for one request. Keeping this a value makes the
 * "empty picker" cases testable on the host: the difference between a warm
 * catalog with nothing for this site and a catalog that was never populated is
 * the difference between a correct empty response and a silent failure.
 */
sealed interface VaultLookupPlan {
    /** Answer straight from the catalog. Metadata only; secrets stay in Rust until selection. */
    data class Serve(
        val credentials: List<VaultItemRecord>,
        /** Some requested kinds are still cold, so keep known rows and also offer backend unlock. */
        val offerUnlock: Boolean = false,
    ) : VaultLookupPlan

    /** The catalog cannot answer authoritatively and the Apple backend is reachable. */
    data object ConsultBackend : VaultLookupPlan

    /** Nothing cached and no backend: offer an unlock action instead of an empty picker. */
    data object RequireUnlock : VaultLookupPlan

    /** The catalog is warm and genuinely holds nothing for this site. */
    data object NoCredentials : VaultLookupPlan
}

/** A cached miss is cheap, but it must not hide credentials added on another device indefinitely. */
const val VAULT_CATALOG_MISS_MAX_AGE_MS = 60_000L

/**
 * @param backendReady the live Rust state is already installed, so a backend
 * lookup costs a keychain scan rather than a service cold start.
 */
fun planVaultLookup(
    snapshot: VaultSiteSnapshot,
    request: VaultCredentialRequest,
    backendReady: Boolean,
    nowMs: Long = System.currentTimeMillis(),
): VaultLookupPlan {
    if (request.kinds.isEmpty()) return VaultLookupPlan.NoCredentials
    if (snapshot.siteKey == null) return VaultLookupPlan.NoCredentials

    val matches = snapshot.items.filter { it.kind in request.kinds }
    val allowed = request.allowedCredentialIds
    val usable = matches.filter { item ->
        when {
            item.kind != VaultItemKind.Passkey -> true
            allowed == null -> true
            // Without the WebAuthn credential id we cannot prove the relying
            // party would accept this passkey, so never offer it on a guess.
            item.webauthnCredentialId == null -> false
            else -> item.webauthnCredentialId in allowed
        }
    }

    val droppedUnprovable = matches.any { item ->
        item.kind == VaultItemKind.Passkey && allowed != null && item.webauthnCredentialId == null
    }
    val coldKinds = request.kinds - snapshot.syncedKinds
    if (usable.isNotEmpty()) {
        if ((coldKinds.isNotEmpty() || droppedUnprovable) && backendReady) {
            return VaultLookupPlan.ConsultBackend
        }
        return VaultLookupPlan.Serve(
            credentials = usable,
            offerUnlock = coldKinds.isNotEmpty() || droppedUnprovable,
        )
    }

    if (coldKinds.isNotEmpty()) {
        return if (backendReady) VaultLookupPlan.ConsultBackend else VaultLookupPlan.RequireUnlock
    }

    val staleMiss = snapshot.syncedAtMs?.let { syncedAt ->
        val age = nowMs - syncedAt
        age < 0L || age > VAULT_CATALOG_MISS_MAX_AGE_MS
    } ?: true
    return when {
        backendReady -> VaultLookupPlan.ConsultBackend
        droppedUnprovable -> VaultLookupPlan.RequireUnlock
        staleMiss -> VaultLookupPlan.RequireUnlock
        else -> VaultLookupPlan.NoCredentials
    }
}

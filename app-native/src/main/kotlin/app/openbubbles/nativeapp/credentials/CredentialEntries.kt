package app.openbubbles.nativeapp.credentials

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.credentials.provider.AuthenticationAction
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CredentialEntry
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.PublicKeyCredentialEntry
import app.openbubbles.nativeapp.R
import app.openbubbles.core.passwords.VaultCatalog
import app.openbubbles.core.passwords.VaultCredentialRequest
import app.openbubbles.core.passwords.VaultItemKind
import app.openbubbles.core.passwords.VaultItemRecord
import app.openbubbles.core.passwords.VaultLookupPlan
import app.openbubbles.core.passwords.VaultSiteSnapshot
import app.openbubbles.core.passwords.planVaultLookup
import app.openbubbles.core.passwords.vaultSiteKey
import app.openbubbles.core.passwords.vaultWebauthnCredentialId
import app.openbubbles.nativeapp.data.PushStateHolder
import app.openbubbles.nativeapp.data.passwords.VaultCatalogStore
import app.openbubbles.nativeapp.data.passwords.VaultCatalogSync
import org.json.JSONObject
import uniffi.rust_lib_bluebubbles.NativePushState
import kotlinx.coroutines.CancellationException

/**
 * Turns one Credential Manager query into picker entries.
 *
 * The entries are built from the durable vault catalog whenever it can answer.
 * That is both the fast path — the system binds this service cold and will not
 * wait for a keychain sync — and the safer one: an entry carries a record id
 * and a label, never a password or a private key. The secret is read once, in
 * the selection activity, after the user has chosen.
 */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal object CredentialEntries {

    /** One parsed request, reduced to what both the catalog and the backend need. */
    data class Query(
        val site: String,
        val callingOrigin: String,
        val packageName: String,
        val passwordOption: BeginGetPasswordOption?,
        val passkeyOption: BeginGetPublicKeyCredentialOption?,
        val passkeyRequestJson: String?,
        val clientDataHash: ByteArray?,
        val allowedCredentialIds: Set<String>?,
    ) {
        val vaultRequest = VaultCredentialRequest(
            site = site,
            wantsPasswords = passwordOption != null,
            wantsPasskeys = passkeyOption != null,
            allowedCredentialIds = allowedCredentialIds,
        )
    }

    /** `null` when the request cannot be served safely, which answers with no entries. */
    fun parse(context: Context, request: BeginGetCredentialRequest): Query? {
        val callingAppInfo = request.callingAppInfo ?: return null
        val callingOrigin = CredentialService.appInfoToOrigin(context, callingAppInfo)

        var site = ""
        var passkeyRequestJson: String? = null
        var passkeyOption: BeginGetPublicKeyCredentialOption? = null
        var passwordOption: BeginGetPasswordOption? = null
        var allowedCredentialIds: Set<String>? = null
        var clientDataHash: ByteArray? = null
        for (option in request.beginGetCredentialOptions) {
            when (option) {
                is BeginGetPublicKeyCredentialOption -> {
                    passkeyOption = option
                    passkeyRequestJson = option.requestJson
                    clientDataHash = option.clientDataHash
                    val parsed = JSONObject(option.requestJson)
                    site = parsed.optString("rpId", site)
                    val allowCredentials = parsed.optJSONArray("allowCredentials")
                    if (allowCredentials != null && allowCredentials.length() > 0) {
                        val ids = HashSet<String>()
                        for (index in 0 until allowCredentials.length()) {
                            val item = allowCredentials.optJSONObject(index) ?: continue
                            val id = item.optString("id")
                            if (id.isNotEmpty()) ids.add(id)
                        }
                        allowedCredentialIds = ids
                    }
                }

                is BeginGetPasswordOption -> passwordOption = option
            }
        }
        if (site.isEmpty()) site = canonicalRpHost(callingOrigin).orEmpty()
        if (site.isEmpty()) return null

        // RP ID check: if origin host is present, it must be equal to or a subdomain of rpId.
        if (callingAppInfo.isOriginPopulated() && !originMatchesRpId(callingOrigin, site)) return null

        return Query(
            site = site,
            callingOrigin = callingOrigin,
            packageName = callingAppInfo.packageName,
            passwordOption = passwordOption,
            passkeyOption = passkeyOption,
            passkeyRequestJson = passkeyRequestJson,
            clientDataHash = clientDataHash,
            allowedCredentialIds = allowedCredentialIds,
        )
    }

    /**
     * @param offerUnlock false once the user has already come through the
     * unlock action, so a still-unavailable backend answers with no entries
     * instead of looping the user back into the same action.
     */
    suspend fun respond(
        context: Context,
        query: Query,
        offerUnlock: Boolean = true,
    ): BeginGetCredentialResponse {
        val catalog = VaultCatalogStore.of(context)
        val snapshot = providerVaultSnapshot(catalog, query.site, query.vaultRequest.kinds)
        val state = PushStateHolder.state
        return when (val plan = planVaultLookup(snapshot, query.vaultRequest, state != null)) {
            is VaultLookupPlan.Serve -> {
                state?.let { VaultCatalogSync.refresh(context, it) }
                BeginGetCredentialResponse(
                    credentialEntries = entries(context, query, plan.credentials),
                    authenticationActions = if (plan.offerUnlock && offerUnlock) {
                        listOf(unlockAction(context, query))
                    } else {
                        emptyList()
                    },
                )
            }

            VaultLookupPlan.NoCredentials -> BeginGetCredentialResponse(emptyList())

            // ConsultBackend has a running backend and RequireUnlock does not,
            // but both need an authoritative answer, so both wait for one and
            // fall back to the unlock action if it never arrives.
            VaultLookupPlan.ConsultBackend, VaultLookupPlan.RequireUnlock -> {
                val live = state ?: awaitPushState(context)
                if (live == null) {
                    unlockOrEmpty(context, query, offerUnlock)
                } else {
                    val generation = VaultCatalogSync.captureGeneration()
                    val records = hydrate(catalog, live, query, generation)
                        ?: return BeginGetCredentialResponse(emptyList())
                    VaultCatalogSync.refreshIfCurrent(context, live, generation)
                    BeginGetCredentialResponse(entries(context, query, records))
                }
            }
        }
    }

    private fun unlockOrEmpty(
        context: Context,
        query: Query,
        offerUnlock: Boolean,
    ): BeginGetCredentialResponse {
        if (!offerUnlock) return BeginGetCredentialResponse(emptyList())
        // Nothing cached and no reachable backend. An empty response would look
        // like "OpenBubbles has no credentials"; the documented provider answer
        // for locked credentials is an authentication action the user can take.
        return BeginGetCredentialResponse(
            authenticationActions = listOf(unlockAction(context, query)),
        )
    }

    private fun unlockAction(context: Context, query: Query) = AuthenticationAction(
        context.getString(R.string.credential_unlock_action),
        PendingIntent.getActivity(
            context,
            UNLOCK_REQUEST_CODE,
            Intent(context, CredentialUnlockActivity::class.java)
                .putExtra(CredentialService.EXTRA_SITE, query.site),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        ),
    )

    /**
     * Records the metadata of a live site lookup so the next request for the
     * same site does not need the backend. The secrets that arrived with it are
     * deliberately dropped on the floor.
     */
    private suspend fun hydrate(
        catalog: VaultCatalog,
        state: NativePushState,
        query: Query,
        generation: Long,
    ): List<VaultItemRecord>? {
        val config = state.awaitSiteConfig(query.site)
        val decoder = vaultPasskeyUserDecoder()
        val passwords = config.passwords.map { saved ->
            VaultItemRecord(
                id = saved.credId,
                kind = VaultItemKind.Password,
                site = query.site,
                title = query.site,
                username = saved.username,
            )
        }
        val passkeys = config.passkeys.map { saved ->
            val user = decoder.decode(saved.tag)
            VaultItemRecord(
                id = saved.credId,
                kind = VaultItemKind.Passkey,
                site = query.site,
                title = query.site,
                username = user?.name ?: user?.displayName,
                displayName = user?.displayName ?: user?.name,
                webauthnCredentialId = vaultWebauthnCredentialId(saved.id),
            )
        }
        if (VaultCatalogSync.publishIfCurrent(generation) {
                if (query.vaultRequest.wantsPasswords) {
                    catalog.mergeSiteItems(query.site, VaultItemKind.Password, passwords)
                }
                if (query.vaultRequest.wantsPasskeys) {
                    catalog.mergeSiteItems(query.site, VaultItemKind.Passkey, passkeys)
                }
                true
            } == null
        ) {
            return null
        }

        val allowed = query.allowedCredentialIds
        val offeredPasswords = if (query.vaultRequest.wantsPasswords) passwords else emptyList()
        val offeredPasskeys = if (!query.vaultRequest.wantsPasskeys) {
            emptyList()
        } else {
            passkeys.filter { allowed == null || it.webauthnCredentialId in allowed }
        }
        return offeredPasswords + offeredPasskeys
    }

    private fun entries(
        context: Context,
        query: Query,
        records: List<VaultItemRecord>,
    ): List<CredentialEntry> = records.mapIndexedNotNull { index, record ->
        when (record.kind) {
            VaultItemKind.Password -> query.passwordOption?.let { option ->
                PasswordCredentialEntry.Builder(
                    context,
                    record.username.orEmpty().ifEmpty { record.title },
                    selectionIntent(context, query, record, index),
                    option,
                ).build()
            }

            VaultItemKind.Passkey -> query.passkeyOption?.let { option ->
                val username = record.username ?: record.displayName ?: PASSKEY_FALLBACK_LABEL
                PublicKeyCredentialEntry.Builder(
                    context,
                    username,
                    selectionIntent(context, query, record, PASSKEY_REQUEST_CODE_BASE + index),
                    option,
                )
                    .setDisplayName(record.displayName ?: username)
                    .build()
            }

            // Wi-Fi keys and verification codes are not Credential Manager
            // types; advertising them here would fabricate a credential.
            VaultItemKind.Code, VaultItemKind.Wifi -> null
        }
    }

    private fun selectionIntent(
        context: Context,
        query: Query,
        record: VaultItemRecord,
        requestCode: Int,
    ): PendingIntent {
        val passkey = record.kind == VaultItemKind.Passkey
        val intent = Intent(context, CredentialGetActivity::class.java).apply {
            // The raw Apple site, not the canonical host: the backend still
            // matches it exactly when the selection activity reads the secret.
            putExtra(CredentialService.EXTRA_SITE, record.site)
            putExtra(CredentialService.EXTRA_CRED_ID, record.id)
            putExtra(
                CredentialService.EXTRA_TYPE,
                if (passkey) CredentialService.TYPE_PASSKEY else CredentialService.TYPE_PASSWORD,
            )
            putExtra(CredentialService.EXTRA_ORIGIN, query.callingOrigin)
            putExtra(CredentialService.EXTRA_PACKAGE_NAME, query.packageName)
            if (passkey) {
                query.passkeyRequestJson?.let { putExtra(CredentialService.EXTRA_REQUEST_JSON, it) }
                query.clientDataHash?.let { putExtra(CredentialService.EXTRA_CLIENT_DATA_HASH, it) }
            }
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            // FLAG_MUTABLE so the system can append the final request; never
            // FLAG_ONE_SHOT, because a user may reselect the same entry.
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private const val PASSKEY_FALLBACK_LABEL = "Passkey"
    private const val PASSKEY_REQUEST_CODE_BASE = 10_000
    private const val UNLOCK_REQUEST_CODE = 20_000
}

/** A broken metadata cache is cold, never an authoritative empty-vault answer. */
internal suspend fun providerVaultSnapshot(
    catalog: VaultCatalog,
    site: String,
    kinds: Set<VaultItemKind>,
): VaultSiteSnapshot = try {
    catalog.credentialsForSite(site, kinds)
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Throwable) {
    VaultSiteSnapshot(siteKey = vaultSiteKey(site))
}

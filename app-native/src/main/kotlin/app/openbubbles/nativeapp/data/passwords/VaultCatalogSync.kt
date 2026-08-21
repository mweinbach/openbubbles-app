package app.openbubbles.nativeapp.data.passwords

import android.content.Context
import android.util.Log
import app.openbubbles.core.passwords.VaultGroupMemberRecord
import app.openbubbles.core.passwords.VaultGroupRecord
import app.openbubbles.core.passwords.VaultInviteRecord
import app.openbubbles.core.passwords.VaultItemKind
import app.openbubbles.core.passwords.VaultItemRecord
import app.openbubbles.core.passwords.VaultListingSource
import app.openbubbles.core.passwords.record
import app.openbubbles.core.passwords.refreshVaultCatalog
import app.openbubbles.core.passwords.uniffi
import app.openbubbles.nativeapp.credentials.vaultPasskeyUserDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.NativePushState

/**
 * Republishes the durable catalog from the Rust vault listing, and is its only
 * writer.
 *
 * The Android credential provider is started by the system with no chance to
 * wait for a keychain sync, so the catalog has to be warm before the request
 * arrives. This runs when a connected Apple state is installed, after a create
 * or an Autofill save, and after a Passwords-screen pass.
 *
 * Generation-scoped per [docs/DATA_LIFECYCLE.md]: sign-out advances the
 * generation, cancels and joins the in-flight pass, and only then deletes rows,
 * so a late refresh cannot repopulate the previous account's catalog.
 */
object VaultCatalogSync {
    private const val TAG = "VaultCatalogSync"

    /**
     * A listing decrypts every keychain entry, so an opportunistic pass behind
     * a credential request is rate limited. A pass that follows an actual write
     * is not: the new credential has to become fillable immediately.
     */
    private const val OPPORTUNISTIC_INTERVAL_MS = 60_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var generation: Long = 0
    private var running: Job? = null
    private var lastStartedAtMs: Long = 0

    /** Keeps the catalog warm; skipped when a recent pass already did. */
    fun refresh(context: Context, state: NativePushState) = start(context, state, forced = false)

    /** The vault just changed, so re-read it regardless of the last pass. */
    fun refreshNow(context: Context, state: NativePushState) = start(context, state, forced = true)

    private fun start(context: Context, state: NativePushState, forced: Boolean) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (running?.isActive == true) return
            val now = System.currentTimeMillis()
            if (!forced && now - lastStartedAtMs < OPPORTUNISTIC_INTERVAL_MS) return
            lastStartedAtMs = now
            val startedAt = generation
            running = scope.launch {
                try {
                    refreshVaultCatalog(
                        source = UniffiVaultListingSource(state),
                        catalog = VaultCatalogStore.of(appContext),
                        publish = { write -> if (current() == startedAt) write() },
                    )
                } catch (failure: Throwable) {
                    // A cold catalog is recoverable; never crash a system-bound
                    // provider process over a cache refresh.
                    Log.w(TAG, "vault catalog refresh failed (${failure.javaClass.simpleName})")
                }
            }
        }
    }

    /** Sign-out ordering step: invalidate late writers, then cancel and join. */
    suspend fun cancelAndJoin() {
        val job = synchronized(lock) {
            generation += 1
            lastStartedAtMs = 0
            running.also { running = null }
        }
        job?.cancel()
        job?.join()
    }

    private fun current(): Long = synchronized(lock) { generation }
}

private class UniffiVaultListingSource(private val state: NativePushState) : VaultListingSource {
    private val decoder = vaultPasskeyUserDecoder()

    override suspend fun inClique(): Boolean = state.isInClique()

    override suspend fun items(kind: VaultItemKind): List<VaultItemRecord> =
        state.listPasswords(kind.uniffi()).map { it.record(decoder) }

    override suspend fun groups(): List<VaultGroupRecord> = state.listPasswordGroups().map { group ->
        VaultGroupRecord(
            id = group.id,
            name = group.name,
            owner = group.owner,
            memberCount = group.memberCount.toInt(),
            members = group.members.map { member ->
                VaultGroupMemberRecord(
                    name = member.name,
                    handle = member.handle,
                    joined = member.joined,
                    currentUser = member.currentUser,
                )
            },
        )
    }

    override suspend fun invites(): List<VaultInviteRecord> = state.listPasswordGroupInvites().map {
        VaultInviteRecord(id = it.id, groupName = it.groupName, inviter = it.inviter)
    }
}

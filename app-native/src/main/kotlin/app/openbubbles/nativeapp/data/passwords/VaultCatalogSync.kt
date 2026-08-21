package app.openbubbles.nativeapp.data.passwords

import android.content.Context
import android.util.Log
import app.openbubbles.core.passwords.VaultCatalog
import app.openbubbles.core.passwords.VaultGroupMemberRecord
import app.openbubbles.core.passwords.VaultGroupRecord
import app.openbubbles.core.passwords.VaultInviteRecord
import app.openbubbles.core.passwords.VaultItemKind
import app.openbubbles.core.passwords.record
import app.openbubbles.core.passwords.uniffi
import app.openbubbles.nativeapp.credentials.vaultPasskeyUserDecoder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uniffi.rust_lib_bluebubbles.NativePushState

/**
 * Refreshes the durable catalog from the Rust vault listing.
 *
 * The Android credential provider is started by the system with no chance to
 * wait for a network sync, so the catalog has to be warm before the request
 * arrives. This runs whenever a connected Apple state is installed and after a
 * provider request had to fall back to the backend.
 *
 * Generation-scoped per [docs/DATA_LIFECYCLE.md]: sign-out advances the
 * generation, cancels and joins the in-flight pass, and only then deletes rows,
 * so a late refresh cannot repopulate the previous account's catalog.
 */
object VaultCatalogSync {
    private const val TAG = "VaultCatalogSync"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var generation: Long = 0
    private var running: Job? = null

    /** Starts one refresh unless an identical pass is already in flight. */
    fun refresh(context: Context, state: NativePushState) {
        val appContext = context.applicationContext
        synchronized(lock) {
            if (running?.isActive == true) return
            val startedAt = generation
            running = scope.launch {
                try {
                    runRefresh(VaultCatalogStore.of(appContext), state, startedAt)
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
            running.also { running = null }
        }
        job?.cancel()
        job?.join()
    }

    private suspend fun runRefresh(
        catalog: VaultCatalog,
        state: NativePushState,
        startedAt: Long,
    ) {
        if (!state.isInClique()) return
        val decoder = vaultPasskeyUserDecoder()
        VaultItemKind.entries.forEach { kind ->
            val items = state.listPasswords(kind.uniffi()).map { it.record(decoder) }
            publish(startedAt) { catalog.replaceItems(kind, items, System.currentTimeMillis()) }
        }
        val groups = state.listPasswordGroups().map { group ->
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
        val invites = state.listPasswordGroupInvites().map {
            VaultInviteRecord(id = it.id, groupName = it.groupName, inviter = it.inviter)
        }
        publish(startedAt) { catalog.replaceGroups(groups, invites, System.currentTimeMillis()) }
    }

    /** A write from a superseded account generation is dropped, not applied. */
    private suspend fun publish(startedAt: Long, write: suspend () -> Unit) {
        if (synchronized(lock) { generation } != startedAt) return
        write()
    }
}

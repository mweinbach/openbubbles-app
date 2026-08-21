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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val coordinator = VaultRefreshCoordinator(scope, OPPORTUNISTIC_INTERVAL_MS)

    /** Keeps the catalog warm; skipped when a recent pass already did. */
    fun refresh(context: Context, state: NativePushState) = start(context, state, forced = false)

    /** Keeps a provider request from scheduling work after its account was invalidated. */
    fun refreshIfCurrent(context: Context, state: NativePushState, generation: Long) =
        start(context, state, forced = false, expectedGeneration = generation)

    /** The vault just changed, so re-read it regardless of the last pass. */
    fun refreshNow(context: Context, state: NativePushState) = start(context, state, forced = true)

    /** A completed write may refresh only the account generation that started it. */
    fun refreshNowIfCurrent(
        context: Context,
        state: NativePushState,
        generation: Long,
    ) = start(context, state, forced = true, expectedGeneration = generation)

    private fun start(
        context: Context,
        state: NativePushState,
        forced: Boolean,
        expectedGeneration: Long? = null,
    ) {
        val appContext = context.applicationContext
        coordinator.start(forced, expectedGeneration) { generation ->
            try {
                refreshVaultCatalog(
                    source = UniffiVaultListingSource(state),
                    catalog = VaultCatalogStore.of(appContext),
                    publish = { write -> coordinator.publishIfCurrent(generation, write) },
                )
            } catch (failure: Throwable) {
                // A cold catalog is recoverable; never crash a system-bound
                // provider process over a cache refresh.
                Log.w(TAG, "vault catalog refresh failed (${failure.javaClass.simpleName})")
            }
        }
    }

    /** Opens the account-cleanup fence, invalidates late writers, then cancels and joins. */
    suspend fun beginAccountCleanup() = coordinator.beginAccountCleanup()

    /** Releases the fence only after rows and keys have been removed. */
    fun endAccountCleanup() = coordinator.endAccountCleanup()

    internal fun captureGeneration(): Long = coordinator.captureGeneration()

    internal suspend fun <T> publishIfCurrent(
        generation: Long,
        write: suspend () -> T,
    ): T? = coordinator.publishIfCurrent(generation, write)
}

/**
 * Single-flight refresh lifecycle shared by catalog listing and provider
 * hydration. Forced work requested during an opportunistic pass is retained,
 * while generation-gated publication cannot cross account cleanup.
 */
internal class VaultRefreshCoordinator(
    private val scope: CoroutineScope,
    private val opportunisticIntervalMs: Long,
    private val nowMs: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()
    private val publication = Mutex()
    private var generation: Long = 0
    private var running: Job? = null
    private var pendingForced: (suspend (Long) -> Unit)? = null
    private var lastStartedAtMs: Long = 0
    private var cleanupInProgress: Boolean = false

    fun start(
        forced: Boolean,
        expectedGeneration: Long? = null,
        work: suspend (Long) -> Unit,
    ) {
        synchronized(lock) {
            if (cleanupInProgress) return
            if (expectedGeneration != null && generation != expectedGeneration) return
            if (running?.isActive == true) {
                if (forced) pendingForced = work
                return
            }
            val now = nowMs()
            if (!forced && now - lastStartedAtMs < opportunisticIntervalMs) return
            launchLocked(work, now)
        }
    }

    fun captureGeneration(): Long = synchronized(lock) { generation }

    suspend fun <T> publishIfCurrent(
        capturedGeneration: Long,
        write: suspend () -> T,
    ): T? = publication.withLock {
        if (captureGeneration() == capturedGeneration) write() else null
    }

    suspend fun beginAccountCleanup() {
        val job = synchronized(lock) {
            cleanupInProgress = true
            generation += 1
            lastStartedAtMs = 0
            pendingForced = null
            running.also { running = null }
        }
        job?.cancel()
        // Wait for a publication that entered before invalidation to finish;
        // account cleanup runs only after this barrier and the worker join.
        publication.withLock { }
        job?.join()
    }

    fun endAccountCleanup() {
        synchronized(lock) { cleanupInProgress = false }
    }

    private fun launchLocked(work: suspend (Long) -> Unit, startedAtMs: Long) {
        val startedGeneration = generation
        lastStartedAtMs = startedAtMs
        running = scope.launch {
            try {
                work(startedGeneration)
            } finally {
                synchronized(lock) {
                    if (generation == startedGeneration) {
                        running = null
                        pendingForced?.let { pending ->
                            pendingForced = null
                            launchLocked(pending, nowMs())
                        }
                    }
                }
            }
        }
    }
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

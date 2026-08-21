package app.openbubbles.nativeapp.credentials

import android.content.Context
import app.openbubbles.nativeapp.data.APNClient
import app.openbubbles.nativeapp.data.APNService
import app.openbubbles.nativeapp.data.PushStateHolder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.RetrieveKeysCallback
import uniffi.rust_lib_bluebubbles.SavedPasskey
import uniffi.rust_lib_bluebubbles.SavedPassword

/** Secrets for one site, alive only for the duration of a single request. */
internal class SiteConfig(
    val passwords: List<SavedPassword>,
    val passkeys: List<SavedPasskey>,
)

/**
 * Waits for the Apple backend, starting the push service if needed. Returns
 * `null` rather than throwing when it never arrives: a system-bound provider
 * has to answer, and an unavailable backend is an expected state (signed out,
 * cold process, keychain locked).
 */
internal suspend fun awaitPushState(context: Context): NativePushState? =
    PushStateHolder.state ?: suspendCancellableCoroutine { continuation ->
        val client = APNClient(context)
        val resumed = AtomicBoolean(false)
        continuation.invokeOnCancellation { client.destroy() }
        client.bind { service: APNService ->
            val state = service.pushState
            client.destroy()
            if (resumed.compareAndSet(false, true)) continuation.resume(state)
        }
    }

/** Bridges the one-shot Rust site lookup onto a coroutine. */
internal suspend fun NativePushState.awaitSiteConfig(site: String): SiteConfig =
    suspendCancellableCoroutine { continuation ->
        val resumed = AtomicBoolean(false)
        getSiteConfig(
            site,
            object : RetrieveKeysCallback {
                override fun keys(passwords: List<SavedPassword>, passkeys: List<SavedPasskey>) {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resume(SiteConfig(passwords, passkeys))
                    }
                }
            },
        )
    }

package app.openbubbles.nativeapp.data

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Unlock the process-wide Rust keystore before importing an iCloud escrow
 * bottle. The Rust-owned keystore instance must receive the authenticated
 * cipher, while the Activity context gives the system prompt a window.
 */
suspend fun unlockICloudKeychain(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
    val promptContext = context.findActivity() ?: context
    return suspendCancellableCoroutine { continuation ->
        try {
            RustBoot.unlockKeystore(promptContext, "Secure iCloud Keychain") { success ->
                if (continuation.isActive) continuation.resume(success)
            }
        } catch (_: Throwable) {
            if (continuation.isActive) continuation.resume(false)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

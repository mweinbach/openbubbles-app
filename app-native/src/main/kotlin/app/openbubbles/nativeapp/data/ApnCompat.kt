package app.openbubbles.nativeapp.data

import android.content.Context
import app.openbubbles.nativeapp.service.NativePushService
import uniffi.rust_lib_bluebubbles.NativePushState
import java.util.concurrent.Executors

/**
 * Compatibility shim for the ported FaceTime/credentials subsystems, which
 * were written against the Flutter app's APNService binding client. The
 * native app keeps the live [NativePushState] in [PushStateHolder] — same
 * process, no binding needed. If the service isn't running yet, we start it
 * and deliver once the state installs (bounded wait).
 */
class APNService private constructor() {
    val pushState: NativePushState? get() = PushStateHolder.state

    companion object {
        internal val instance = APNService()
    }
}

class APNClient(val context: Context) {

    private val executor = Executors.newSingleThreadExecutor()

    fun bind(cb: (APNService) -> Unit) {
        executor.execute {
            if (PushStateHolder.state == null) {
                runCatching { NativePushService.start(context) }
                // Bounded wait for the state to install (login/restore).
                val deadline = System.currentTimeMillis() + 5_000
                while (PushStateHolder.state == null &&
                    System.currentTimeMillis() < deadline
                ) {
                    Thread.sleep(100)
                }
            }
            cb(APNService.instance)
        }
    }

    fun destroy() {
        executor.shutdownNow()
    }
}

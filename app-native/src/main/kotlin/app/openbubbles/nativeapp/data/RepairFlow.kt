package app.openbubbles.nativeapp.data

/**
 * One-shot handoff from Settings' "Repair iCloud sync" to the login screen.
 *
 * Repair must default to the SESSION re-auth ("Continue as …", which reads
 * the stored Apple session and never runs `reset_user`): a typed-password
 * sign-in triggers a full iCloud re-provision against Apple, and Apple
 * hard-limits how many of those a single hardware identity gets
 * (ICLOUD_UNSUPPORTED_DEVICE once burned). The login screen consumes this
 * flag and auto-attempts the sessioned login; only if that fails does the
 * user fall back to entering credentials.
 */
object RepairFlow {
    @Volatile
    private var pendingSessionRepair: Boolean = false

    fun requestSessionRepair() {
        pendingSessionRepair = true
    }

    /** Returns true exactly once per [requestSessionRepair]. */
    fun consumeSessionRepair(): Boolean {
        val pending = pendingSessionRepair
        pendingSessionRepair = false
        return pending
    }
}

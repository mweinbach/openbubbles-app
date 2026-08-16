package app.openbubbles.nativeapp.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * In-app update feed, published as the `update.json` asset of each GitHub
 * Release by [scripts/publish-update.sh]. Fields the updater refuses to guess
 * are required; cosmetic ones default.
 */
@Serializable
data class UpdateManifest(
    /** Monotonic Android versionCode of the release. */
    @SerialName("versionCode") val versionCode: Long,
    @SerialName("versionName") val versionName: String,
    /** Release-asset file name of the universal APK. */
    @SerialName("apkAsset") val apkAsset: String,
    /** Lowercase hex SHA-256 of the APK bytes. */
    @SerialName("sha256") val sha256: String,
    /** Exact APK size in bytes; guards truncated downloads. */
    @SerialName("bytes") val bytes: Long = 0L,
    /** Human-readable release notes (markdown-ish, shown in Settings). */
    @SerialName("notes") val notes: String = "",
    /**
     * Force-update floor: when the installed versionCode is below this the
     * update cannot be deferred. 0 = never force.
     */
    @SerialName("minVersionCode") val minVersionCode: Long = 0L,
) {
    /** Comparison-safe digest: trims and lowercases so hex case never matters. */
    fun normalizedSha256(): String = sha256.trim().lowercase()
}

/** Outcome of comparing a fetched feed against on-device state. */
sealed interface UpdateDecision {
    /** Installed versionCode >= feed's (or feed's is stale vs. what we saw). */
    data object UpToDate : UpdateDecision

    /** Feed is older than a version this device has already seen — rollback blocked. */
    data object RollbackBlocked : UpdateDecision

    /** User asked to skip exactly this version. */
    data class Deferred(val versionCode: Long) : UpdateDecision

    /** Normal optional update. */
    data class Available(val manifest: UpdateManifest) : UpdateDecision

    /** Installed version is below the feed's force floor; skipping is not offered. */
    data class Mandatory(val manifest: UpdateManifest) : UpdateDecision

    companion object {
        /**
         * Pure decision — no Android types, unit-testable on the JVM.
         *
         * @param installedCode  versionCode of the running build
         * @param deferredCode   versionCode the user chose to skip (0 = none)
         * @param highestSeenCode highest versionCode ever offered to this device
         *                        (local rollback floor; 0 = none seen yet)
         */
        fun evaluate(
            installedCode: Long,
            manifest: UpdateManifest,
            deferredCode: Long = 0L,
            highestSeenCode: Long = 0L,
        ): UpdateDecision = when {
            manifest.versionCode <= installedCode -> UpToDate
            manifest.versionCode < highestSeenCode -> RollbackBlocked
            installedCode < manifest.minVersionCode -> Mandatory(manifest)
            manifest.versionCode == deferredCode -> Deferred(manifest.versionCode)
            else -> Available(manifest)
        }
    }
}

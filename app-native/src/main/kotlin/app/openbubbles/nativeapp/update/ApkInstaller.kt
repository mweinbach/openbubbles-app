package app.openbubbles.nativeapp.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import java.io.File
import java.security.MessageDigest

/**
 * Commits a verified APK through Android's `PackageInstaller` as an in-place
 * update of this very app. The platform independently enforces the two rules
 * that make self-update safe here: the new APK must be signed with the same
 * key as the installed build, and its versionCode must be higher.
 *
 * Android 8.0+ (our minSdk) requires the user to grant this app the per-app
 * "Install unknown apps" toggle before any install; see [canInstall] and
 * [unknownSourcesIntent]. Every self-update must show Android's system
 * confirmation; becoming the installer-of-record must never turn updates
 * into unattended package installations.
 */
object ApkInstaller {
    const val ACTION_INSTALL_RESULT = "app.openbubbles.nativeapp.action.UPDATE_INSTALL_RESULT"
    const val EXTRA_VERSION_NAME = "update_version_name"
    const val EXTRA_VERSION_CODE = "update_version_code"

    /** True when the user has granted "Install unknown apps" for this app. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /** Deep-link to this app's "Install unknown apps" settings toggle. */
    fun unknownSourcesIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${context.packageName}".toUri(),
        )

    /**
     * Fail-fast pre-check: does the downloaded APK carry the same signing
     * certificate as the running build? Purely advisory — `PackageInstaller`
     * enforces it at commit time anyway; this just converts an opaque session
     * failure into a clear error before one is opened.
     */
    fun signaturesMatch(context: Context, apk: File): Boolean {
        val flags = signingInfoFlags()
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }.getOrNull() ?: return false
        val incoming = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return false
        val installedDigests = signingDigests(installed)
        return installedDigests.isNotEmpty() && installedDigests == signingDigests(incoming)
    }

    /** Authenticate package identity, declared build, upgrade direction, and signer. */
    fun verifyUpdate(context: Context, apk: File, manifest: UpdateManifest) {
        val flags = signingInfoFlags()
        val installed = runCatching {
            context.packageManager.getPackageInfo(context.packageName, flags)
        }.getOrElse { throw SecurityException("could not inspect installed app identity", it) }
        val incoming = runCatching {
            context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags)
        }.getOrElse { throw SecurityException("downloaded APK could not be inspected", it) }
            ?: throw SecurityException("downloaded APK is not a valid Android package")
        val result = verifyUpdateArchiveIdentity(
            installedPackageName = context.packageName,
            installedVersionCode = PackageInfoCompat.getLongVersionCode(installed),
            installedSignerDigests = signingDigests(installed),
            advertisedVersionCode = manifest.versionCode,
            archivePackageName = incoming.packageName,
            archiveVersionCode = PackageInfoCompat.getLongVersionCode(incoming),
            archiveSignerDigests = signingDigests(incoming),
        )
        if (result != UpdateArchiveVerification.VERIFIED) {
            throw SecurityException(
                when (result) {
                    UpdateArchiveVerification.PACKAGE_MISMATCH ->
                        "downloaded APK package does not match installed app"
                    UpdateArchiveVerification.VERSION_MISMATCH ->
                        "downloaded APK version does not match advertised build"
                    UpdateArchiveVerification.NOT_AN_UPGRADE ->
                        "downloaded APK is not newer than installed app"
                    UpdateArchiveVerification.SIGNER_MISMATCH ->
                        "downloaded APK signing certificate does not match installed app"
                    UpdateArchiveVerification.VERIFIED -> error("unreachable verified APK failure")
                },
            )
        }
    }

    /**
     * Opens a PackageInstaller session, streams [apk] in, and commits it.
     * The commit verdict arrives in [UpdateInstallReceiver].
     *
     * @throws SecurityException on signature pre-check failure.
     * @throws IllegalArgumentException when the install permission is missing.
     */
    fun install(context: Context, apk: File, manifest: UpdateManifest) {
        require(canInstall(context)) {
            "install blocked: 'Install unknown apps' not granted for ${context.packageName}"
        }
        verifyUpdate(context, apk, manifest)

        val packageInstaller = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
            }
        }
        val sessionId = packageInstaller.createSession(params)
        val session = packageInstaller.openSession(sessionId)
        try {
            session.openWrite("base", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val result = Intent(context, UpdateInstallReceiver::class.java)
                .setAction(ACTION_INSTALL_RESULT)
                .setPackage(context.packageName)
                .putExtra(EXTRA_VERSION_NAME, manifest.versionName)
                .putExtra(EXTRA_VERSION_CODE, manifest.versionCode)
            var flags = PendingIntent.FLAG_UPDATE_CURRENT
            if (Build.VERSION.SDK_INT >= 31) {
                // The system fills in the status extras; the PendingIntent
                // must be mutable for that to work.
                flags = flags or PendingIntent.FLAG_MUTABLE
            }
            session.commit(
                PendingIntent.getBroadcast(context, sessionId, result, flags).intentSender,
            )
        } catch (e: Exception) {
            runCatching { session.abandon() }
            throw e
        }
    }

    /** SHA-256 digests of every signing certificate in [info]. */
    private fun signingDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= 28) {
            val signingInfo = info.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.toList()
            } else {
                signingInfo.signingCertificateHistory.toList()
            }
        } else {
            @Suppress("DEPRECATION")
            info.signatures?.toList() ?: emptyList()
        }
        return signatures.map { sig ->
            MessageDigest.getInstance("SHA-256").digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
        }.toSet()
    }

    private fun signingInfoFlags(): Int = if (Build.VERSION.SDK_INT >= 28) {
        PackageManager.GET_SIGNING_CERTIFICATES
    } else {
        @Suppress("DEPRECATION")
        PackageManager.GET_SIGNATURES
    }
}

internal enum class UpdateArchiveVerification {
    VERIFIED,
    PACKAGE_MISMATCH,
    VERSION_MISMATCH,
    NOT_AN_UPGRADE,
    SIGNER_MISMATCH,
}

internal fun verifyUpdateArchiveIdentity(
    installedPackageName: String,
    installedVersionCode: Long,
    installedSignerDigests: Set<String>,
    advertisedVersionCode: Long,
    archivePackageName: String?,
    archiveVersionCode: Long,
    archiveSignerDigests: Set<String>,
): UpdateArchiveVerification = when {
    archivePackageName != installedPackageName -> UpdateArchiveVerification.PACKAGE_MISMATCH
    archiveVersionCode != advertisedVersionCode -> UpdateArchiveVerification.VERSION_MISMATCH
    archiveVersionCode <= installedVersionCode -> UpdateArchiveVerification.NOT_AN_UPGRADE
    installedSignerDigests.isEmpty() || installedSignerDigests != archiveSignerDigests ->
        UpdateArchiveVerification.SIGNER_MISMATCH
    else -> UpdateArchiveVerification.VERIFIED
}

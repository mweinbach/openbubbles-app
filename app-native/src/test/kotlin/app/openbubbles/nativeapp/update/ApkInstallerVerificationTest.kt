package app.openbubbles.nativeapp.update

import kotlin.test.Test
import kotlin.test.assertEquals

class ApkInstallerVerificationTest {
    private val installedPackage = "com.openbubbles.messaging"
    private val installedSigner = setOf("trusted-signing-certificate")

    private fun verify(
        advertisedVersionCode: Long = 101L,
        archivePackageName: String? = installedPackage,
        archiveVersionCode: Long = advertisedVersionCode,
        installedSignerDigests: Set<String> = installedSigner,
        archiveSignerDigests: Set<String> = installedSigner,
    ): UpdateArchiveVerification = verifyUpdateArchiveIdentity(
        installedPackageName = installedPackage,
        installedVersionCode = 100L,
        installedSignerDigests = installedSignerDigests,
        advertisedVersionCode = advertisedVersionCode,
        archivePackageName = archivePackageName,
        archiveVersionCode = archiveVersionCode,
        archiveSignerDigests = archiveSignerDigests,
    )

    @Test
    fun `matching package signer and higher declared build are authenticated`() {
        assertEquals(UpdateArchiveVerification.VERIFIED, verify())
    }

    @Test
    fun `signed artifact from another package is rejected`() {
        assertEquals(
            UpdateArchiveVerification.PACKAGE_MISMATCH,
            verify(archivePackageName = "app.another.package"),
        )
    }

    @Test
    fun `missing archive package identity is rejected`() {
        assertEquals(
            UpdateArchiveVerification.PACKAGE_MISMATCH,
            verify(archivePackageName = null),
        )
    }

    @Test
    fun `inflated advertisement cannot authenticate an older signed artifact`() {
        assertEquals(
            UpdateArchiveVerification.VERSION_MISMATCH,
            verify(advertisedVersionCode = Long.MAX_VALUE, archiveVersionCode = 101L),
        )
    }

    @Test
    fun `different signing certificate is rejected`() {
        assertEquals(
            UpdateArchiveVerification.SIGNER_MISMATCH,
            verify(archiveSignerDigests = setOf("attacker-signing-certificate")),
        )
    }

    @Test
    fun `missing signing certificates never match each other`() {
        assertEquals(
            UpdateArchiveVerification.SIGNER_MISMATCH,
            verify(installedSignerDigests = emptySet(), archiveSignerDigests = emptySet()),
        )
    }

    @Test
    fun `signed downgrade is rejected even with a matching advertisement`() {
        assertEquals(
            UpdateArchiveVerification.NOT_AN_UPGRADE,
            verify(advertisedVersionCode = 99L),
        )
    }

    @Test
    fun `reinstalling the current build is rejected`() {
        assertEquals(
            UpdateArchiveVerification.NOT_AN_UPGRADE,
            verify(advertisedVersionCode = 100L),
        )
    }
}

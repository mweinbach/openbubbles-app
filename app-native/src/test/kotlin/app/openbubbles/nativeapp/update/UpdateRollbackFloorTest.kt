package app.openbubbles.nativeapp.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateRollbackFloorTest {
    private fun manifest(versionCode: Long) = UpdateManifest(
        versionCode = versionCode,
        versionName = versionCode.toString(),
        apkAsset = "openbubbles-$versionCode.apk",
        sha256 = "a".repeat(64),
    )

    @Test
    fun `poisoned legacy advertisement does not prevent real updates`() {
        val floor = trustedRollbackFloor(
            RollbackFloorEvidence(
                installedVersionCode = 100L,
                legacyAdvertisedVersionCode = Long.MAX_VALUE,
            ),
        )

        assertEquals(100L, floor)
        assertIs<UpdateDecision.Available>(
            UpdateDecision.evaluate(
                installedCode = 100L,
                manifest = manifest(101L),
                highestVerifiedCode = floor,
            ),
        )
    }

    @Test
    fun `legacy migration preserves a cryptographically authenticated pending build`() {
        val floor = trustedRollbackFloor(
            RollbackFloorEvidence(
                installedVersionCode = 100L,
                legacyAdvertisedVersionCode = Long.MAX_VALUE,
                authenticatedPendingVersionCode = 120L,
            ),
        )

        assertEquals(120L, floor)
        assertEquals(
            UpdateDecision.RollbackBlocked,
            UpdateDecision.evaluate(
                installedCode = 100L,
                manifest = manifest(110L),
                highestVerifiedCode = floor,
            ),
        )
    }

    @Test
    fun `missing or rejected pending artifacts do not inherit an advertised floor`() {
        val floor = trustedRollbackFloor(
            RollbackFloorEvidence(
                installedVersionCode = 100L,
                legacyAdvertisedVersionCode = 900_000L,
                authenticatedPendingVersionCode = 0L,
            ),
        )

        assertEquals(100L, floor)
    }

    @Test
    fun `existing verified rollback floor never regresses`() {
        assertEquals(
            130L,
            trustedRollbackFloor(
                RollbackFloorEvidence(
                    installedVersionCode = 100L,
                    legacyAdvertisedVersionCode = 110L,
                    verifiedVersionCode = 130L,
                    authenticatedPendingVersionCode = 120L,
                ),
            ),
        )
    }

    @Test
    fun `newer installed build raises the trusted rollback floor`() {
        assertEquals(
            140L,
            trustedRollbackFloor(
                RollbackFloorEvidence(
                    installedVersionCode = 140L,
                    legacyAdvertisedVersionCode = Long.MAX_VALUE,
                    verifiedVersionCode = 130L,
                ),
            ),
        )
    }

    @Test
    fun `overlapping checks cannot publish below a newly authenticated floor`() {
        assertFalse(
            canPublishVerifiedUpdate(
                installedVersionCode = 100L,
                currentVerifiedFloor = 130L,
                candidateVersionCode = 120L,
            ),
        )
        assertTrue(
            canPublishVerifiedUpdate(
                installedVersionCode = 100L,
                currentVerifiedFloor = 130L,
                candidateVersionCode = 130L,
            ),
        )
    }

    @Test
    fun `installed and older signed builds cannot be published as updates`() {
        assertFalse(canPublishVerifiedUpdate(100L, 0L, 99L))
        assertFalse(canPublishVerifiedUpdate(100L, 0L, 100L))
        assertTrue(canPublishVerifiedUpdate(100L, 100L, 101L))
    }
}

package app.openbubbles.nativeapp.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UpdateDecisionTest {

    private fun manifest(
        versionCode: Long = 10,
        minVersionCode: Long = 0,
    ) = UpdateManifest(
        versionCode = versionCode,
        versionName = "$versionCode.0",
        apkAsset = "openbubbles-$versionCode.apk",
        sha256 = "00",
        minVersionCode = minVersionCode,
    )

    @Test
    fun `newer feed is available`() {
        val decision = UpdateDecision.evaluate(installedCode = 9, manifest = manifest(10))
        assertIs<UpdateDecision.Available>(decision)
        assertEquals(10L, decision.manifest.versionCode)
    }

    @Test
    fun `equal and older feeds are up to date`() {
        assertEquals(UpdateDecision.UpToDate, UpdateDecision.evaluate(10, manifest(10)))
        assertEquals(UpdateDecision.UpToDate, UpdateDecision.evaluate(11, manifest(10)))
    }

    @Test
    fun `feed older than an authenticated build is a blocked rollback`() {
        val decision = UpdateDecision.evaluate(
            installedCode = 9,
            manifest = manifest(10),
            highestVerifiedCode = 12,
        )
        assertEquals(UpdateDecision.RollbackBlocked, decision)
    }

    @Test
    fun `feed newer than rollback floor passes`() {
        val decision = UpdateDecision.evaluate(
            installedCode = 9,
            manifest = manifest(12),
            highestVerifiedCode = 11,
        )
        assertIs<UpdateDecision.Available>(decision)
    }

    @Test
    fun `deferred version is skipped`() {
        val decision = UpdateDecision.evaluate(
            installedCode = 9,
            manifest = manifest(10),
            deferredCode = 10,
        )
        assertEquals(UpdateDecision.Deferred(10L), decision)
    }

    @Test
    fun `force floor overrides deferral`() {
        val decision = UpdateDecision.evaluate(
            installedCode = 8,
            manifest = manifest(10, minVersionCode = 9),
            deferredCode = 10,
        )
        assertIs<UpdateDecision.Mandatory>(decision)
        assertEquals(10L, decision.manifest.versionCode)
    }

    @Test
    fun `force floor only bites below it`() {
        val decision = UpdateDecision.evaluate(
            installedCode = 9,
            manifest = manifest(10, minVersionCode = 9),
        )
        assertIs<UpdateDecision.Available>(decision)
        assertTrue(decision.manifest.minVersionCode == 9L)
    }
}

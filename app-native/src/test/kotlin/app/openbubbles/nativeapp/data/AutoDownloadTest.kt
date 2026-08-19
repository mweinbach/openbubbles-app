package app.openbubbles.nativeapp.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AutoDownloadTest {

    @Test
    fun `default ceiling is 10 MiB`() {
        assertEquals(10L * 1024 * 1024, MessagingPrefs.DEFAULT_AUTO_DOWNLOAD_MAX_BYTES)
        assertEquals(
            AutoDownloadLimit.MB_10,
            AutoDownloadLimit.fromPersistedValue(MessagingPrefs.DEFAULT_AUTO_DOWNLOAD_MAX_BYTES),
        )
    }

    @Test
    fun `unknown persisted value falls back to 10 MiB`() {
        assertEquals(AutoDownloadLimit.MB_10, AutoDownloadLimit.fromPersistedValue(123L))
    }

    @Test
    fun `off disables every download`() {
        assertFalse(
            isAutoDownloadEligible(
                mime = "image/jpeg",
                totalBytes = 1L,
                hasTransferMetadata = true,
                maxBytes = AutoDownloadLimit.OFF.persistedValue,
            ),
        )
    }

    @Test
    fun `media types are eligible and other files are not`() {
        val ceiling = AutoDownloadLimit.MB_10.persistedValue
        assertTrue(isAutoDownloadEligible("image/jpeg", 100L, true, ceiling))
        assertTrue(isAutoDownloadEligible("video/quicktime", 100L, true, ceiling))
        assertTrue(isAutoDownloadEligible("audio/mp4", 100L, true, ceiling))
        assertTrue(isAutoDownloadEligible("application/pdf", 100L, true, ceiling))
        assertTrue(
            isAutoDownloadEligible(
                mime = "application/octet-stream",
                totalBytes = 100L,
                hasTransferMetadata = true,
                maxBytes = ceiling,
                name = "RenderedVideo.mov",
            ),
        )
        assertTrue(
            isAutoDownloadEligible(
                mime = "image/heic",
                totalBytes = 100L,
                hasTransferMetadata = true,
                maxBytes = ceiling,
            ),
        )
        assertFalse(isAutoDownloadEligible(null, 100L, true, ceiling))
        assertFalse(isAutoDownloadEligible("application/zip", 100L, true, ceiling))
    }

    @Test
    fun `size at the ceiling downloads and over it waits`() {
        val ceiling = AutoDownloadLimit.MB_10.persistedValue
        assertTrue(isAutoDownloadEligible("image/jpeg", ceiling, true, ceiling))
        assertFalse(isAutoDownloadEligible("image/jpeg", ceiling + 1L, true, ceiling))
    }

    @Test
    fun `unlimited ignores size`() {
        assertTrue(
            isAutoDownloadEligible(
                mime = "video/quicktime",
                totalBytes = 8L * 1024 * 1024 * 1024,
                hasTransferMetadata = true,
                maxBytes = MessagingPrefs.AUTO_DOWNLOAD_UNLIMITED,
            ),
        )
    }

    @Test
    fun `missing transfer metadata never auto downloads`() {
        // Rows without rustpush/cloud metadata cannot be fetched (MMS), so the
        // download chip stays the only path.
        assertFalse(
            isAutoDownloadEligible(
                mime = "image/jpeg",
                totalBytes = 100L,
                hasTransferMetadata = false,
                maxBytes = MessagingPrefs.AUTO_DOWNLOAD_UNLIMITED,
            ),
        )
    }

    @Test
    fun `unknown size is treated as small`() {
        // Apple's transfer records almost always declare a size; a size-less
        // voice memo must not be blocked from inline playback.
        assertTrue(
            isAutoDownloadEligible(
                mime = "audio/mp4",
                totalBytes = null,
                hasTransferMetadata = true,
                maxBytes = AutoDownloadLimit.MB_10.persistedValue,
            ),
        )
    }

    @Test
    fun `wifi-only skips metered networks`() {
        assertTrue(shouldAutoDownloadOnCurrentNetwork(eligible = true, wifiOnly = true, unmetered = true))
        assertFalse(shouldAutoDownloadOnCurrentNetwork(eligible = true, wifiOnly = true, unmetered = false))
        assertTrue(shouldAutoDownloadOnCurrentNetwork(eligible = true, wifiOnly = false, unmetered = false))
        assertFalse(shouldAutoDownloadOnCurrentNetwork(eligible = false, wifiOnly = false, unmetered = true))
    }

    @Test
    fun `nullable database size normalizes without unboxing`() {
        assertEquals(null, knownAutoDownloadSize(null))
        assertEquals(null, knownAutoDownloadSize(0L))
        assertEquals(null, knownAutoDownloadSize(-1L))
        assertEquals(1L, knownAutoDownloadSize(1L))
    }
}

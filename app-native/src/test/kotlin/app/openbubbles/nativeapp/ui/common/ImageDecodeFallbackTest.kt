package app.openbubbles.nativeapp.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ImageDecodeFallbackTest {

    @Test
    fun `successful original decode preserves HDR and skips SDR fallback`() {
        var fallbackAttempts = 0

        val decoded = decodeWithSdrFallback(
            decodeOriginal = { "original-hdr" },
            decodeSdr = {
                fallbackAttempts += 1
                "fallback-sdr"
            },
        )

        assertEquals("original-hdr", decoded)
        assertEquals(0, fallbackAttempts)
    }

    @Test
    fun `failed original decode retries once as SDR`() {
        var fallbackAttempts = 0

        val decoded = decodeWithSdrFallback(
            decodeOriginal = { null },
            decodeSdr = {
                fallbackAttempts += 1
                "fallback-sdr"
            },
        )

        assertEquals("fallback-sdr", decoded)
        assertEquals(1, fallbackAttempts)
    }

    @Test
    fun `failed SDR decode keeps the existing preview fallback available`() {
        val decoded = decodeWithSdrFallback<String>(
            decodeOriginal = { null },
            decodeSdr = { null },
        )

        assertNull(decoded)
    }
}

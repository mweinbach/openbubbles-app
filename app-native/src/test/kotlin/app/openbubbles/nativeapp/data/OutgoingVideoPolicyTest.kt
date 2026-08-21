package app.openbubbles.nativeapp.data

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class OutgoingVideoPolicyTest {
    private fun metadata(
        sizeBytes: Long? = MAX_OUTGOING_DRAFT_BYTES + 1,
        durationMs: Long? = 15_000,
        width: Int? = 3840,
        height: Int? = 2160,
        rotationDegrees: Int = 0,
        videoMime: String? = "video/avc",
        isHdr: Boolean = false,
        frameRate: Float? = 30f,
    ) = OutgoingVideoMetadata(
        sizeBytes = sizeBytes,
        durationMs = durationMs,
        width = width,
        height = height,
        rotationDegrees = rotationDegrees,
        videoMime = videoMime,
        isHdr = isHdr,
        frameRate = frameRate,
    )

    private fun planOf(metadata: OutgoingVideoMetadata): VideoCompressionPlan =
        assertIs<OutgoingVideoDecision.OfferCompression>(outgoingVideoDecision(metadata)).plan

    // ---- only-if-needed boundary -------------------------------------------

    @Test
    fun `video exactly at the draft ceiling is sent untouched`() {
        val decision = outgoingVideoDecision(metadata(sizeBytes = MAX_OUTGOING_DRAFT_BYTES))
        assertEquals(OutgoingVideoDecision.SendOriginal, decision)
    }

    @Test
    fun `video one byte over the draft ceiling is offered compression`() {
        val decision = outgoingVideoDecision(metadata(sizeBytes = MAX_OUTGOING_DRAFT_BYTES + 1))
        assertIs<OutgoingVideoDecision.OfferCompression>(decision)
    }

    @Test
    fun `unknown size after a failed bounded copy counts as oversized`() {
        val decision = outgoingVideoDecision(metadata(sizeBytes = null))
        assertIs<OutgoingVideoDecision.OfferCompression>(decision)
    }

    @Test
    fun `oversized video without readable dimensions is rejected as unreadable`() {
        assertEquals(
            OutgoingVideoDecision.RejectUnreadable,
            outgoingVideoDecision(metadata(width = null)),
        )
        assertEquals(
            OutgoingVideoDecision.RejectUnreadable,
            outgoingVideoDecision(metadata(height = null)),
        )
        assertEquals(
            OutgoingVideoDecision.RejectUnreadable,
            outgoingVideoDecision(metadata(width = 0, height = 0)),
        )
    }

    @Test
    fun `custom ceiling is honored`() {
        val small = metadata(sizeBytes = 10)
        assertEquals(OutgoingVideoDecision.SendOriginal, outgoingVideoDecision(small, maxBytes = 10))
        assertIs<OutgoingVideoDecision.OfferCompression>(outgoingVideoDecision(small, maxBytes = 9))
    }

    // ---- 1080p target ------------------------------------------------------

    @Test
    fun `4k landscape scales to 1080 output height`() {
        assertEquals(1080, planOf(metadata(width = 3840, height = 2160)).targetHeight)
    }

    @Test
    fun `stored portrait 4k keeps the long side at 1920`() {
        assertEquals(1920, planOf(metadata(width = 2160, height = 3840)).targetHeight)
    }

    @Test
    fun `rotated 4k capture is treated as portrait`() {
        assertEquals(
            1920,
            planOf(metadata(width = 3840, height = 2160, rotationDegrees = 90)).targetHeight,
        )
        assertEquals(
            1920,
            planOf(metadata(width = 3840, height = 2160, rotationDegrees = 270)).targetHeight,
        )
    }

    @Test
    fun `videos already at or under 1080p keep their dimensions`() {
        assertNull(planOf(metadata(width = 1920, height = 1080)).targetHeight)
        assertNull(planOf(metadata(width = 1280, height = 720)).targetHeight)
        assertNull(planOf(metadata(width = 1080, height = 1920)).targetHeight)
    }

    @Test
    fun `odd scaled output heights round down to even codec dimensions`() {
        // 2159 short side -> scale 1080/2159; 3000 * scale = 1500.7 -> 1501 -> 1500.
        assertEquals(1500, planOf(metadata(width = 2159, height = 3000)).targetHeight)
    }

    // ---- bitrate + HDR -----------------------------------------------------

    @Test
    fun `1080p30 sdr output requests the expected bitrate`() {
        // 1920 * 1080 * 30 * 0.07
        assertEquals(4_354_560, planOf(metadata(width = 3840, height = 2160)).targetVideoBitrate)
    }

    @Test
    fun `hdr sources keep hdr and request a higher bitrate`() {
        val sdr = planOf(metadata(isHdr = false))
        val hdr = planOf(metadata(isHdr = true))
        assertFalse(sdr.keepHdr)
        assertTrue(hdr.keepHdr)
        assertEquals(5_443_200, hdr.targetVideoBitrate)
        assertTrue(hdr.targetVideoBitrate > sdr.targetVideoBitrate)
    }

    @Test
    fun `bitrate clamps to the floor for small sources`() {
        val plan = planOf(metadata(width = 1280, height = 720))
        assertEquals(MIN_VIDEO_COMPRESSION_BITRATE, plan.targetVideoBitrate)
    }

    @Test
    fun `bitrate clamps to the ceiling for high frame rates`() {
        val plan = planOf(metadata(frameRate = 120f))
        assertEquals(MAX_VIDEO_COMPRESSION_BITRATE, plan.targetVideoBitrate)
    }

    @Test
    fun `missing frame rate falls back to the assumed default`() {
        assertEquals(
            planOf(metadata(frameRate = 30f)).targetVideoBitrate,
            planOf(metadata(frameRate = null)).targetVideoBitrate,
        )
    }

    // ---- estimate + codec detection ----------------------------------------

    @Test
    fun `size estimate combines video bitrate and duration with overhead`() {
        val plan = planOf(metadata(durationMs = 15_000))
        // (4_354_560 + 128_000) * 15s / 8 * 1.05
        assertEquals(8_825_040L, plan.estimatedOutputBytes)
    }

    @Test
    fun `size estimate is absent when duration is unknown`() {
        assertNull(planOf(metadata(durationMs = null)).estimatedOutputBytes)
        assertNull(planOf(metadata(durationMs = 0)).estimatedOutputBytes)
    }

    @Test
    fun `already-hevc sources are detected case-insensitively`() {
        assertTrue(planOf(metadata(videoMime = "video/hevc")).alreadyHevc)
        assertTrue(planOf(metadata(videoMime = "VIDEO/HEVC")).alreadyHevc)
        assertFalse(planOf(metadata(videoMime = "video/avc")).alreadyHevc)
        assertFalse(planOf(metadata(videoMime = null)).alreadyHevc)
    }

    // ---- derived-output revalidation ---------------------------------------

    @Test
    fun `derived output validation rejects empty and over-limit files`() {
        assertFalse(isDerivedVideoWithinPolicy(0))
        assertFalse(isDerivedVideoWithinPolicy(-1))
        assertTrue(isDerivedVideoWithinPolicy(1))
        assertTrue(isDerivedVideoWithinPolicy(MAX_OUTGOING_DRAFT_BYTES))
        assertFalse(isDerivedVideoWithinPolicy(MAX_OUTGOING_DRAFT_BYTES + 1))
    }

    @Test
    fun `even dimension rounding never yields odd or sub-codec sizes`() {
        assertEquals(2, evenDimension(0.4))
        assertEquals(2, evenDimension(2.0))
        assertEquals(1500, evenDimension(1500.7))
        assertEquals(1080, evenDimension(1080.2))
    }
}

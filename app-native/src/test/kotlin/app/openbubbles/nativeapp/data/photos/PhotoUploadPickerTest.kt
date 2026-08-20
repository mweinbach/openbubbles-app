package app.openbubbles.nativeapp.data.photos

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PhotoUploadPickerTest {
    @Test
    fun originalExifTimeIncludesOffsetAndSubseconds() {
        assertEquals(
            Instant.parse("2026-08-19T16:34:56.700Z").toEpochMilli(),
            parseExifOriginalDateTime(
                dateTime = "2026:08:19 12:34:56",
                subSeconds = "7",
                offset = "-04:00",
            ),
        )
    }

    @Test
    fun originalExifTimeAcceptsSecondaryDateFormat() {
        assertEquals(
            Instant.parse("2026-08-19T07:04:56.123Z").toEpochMilli(),
            parseExifOriginalDateTime(
                dateTime = "2026-08-19 12:34:56",
                subSeconds = "12345",
                offset = "+05:30",
            ),
        )
    }

    @Test
    fun malformedExifTimesAreIgnored() {
        assertNull(parseExifOriginalDateTime("0000:00:00 00:00:00", null, null))
        assertNull(parseExifOriginalDateTime("2026:08:19", null, null))
        assertNull(parseExifOriginalDateTime("2026:08:19 12:34:56", null, "+15:00"))
    }

    @Test
    fun bitmapSamplingBoundsDecodeMemoryBeforeExactScale() {
        assertEquals(1, bitmapSampleSize(800, 600, 4096))
        assertEquals(4, bitmapSampleSize(16_384, 12_000, 4096))
        assertEquals(16, bitmapSampleSize(8_000, 6_000, 414))
    }
}

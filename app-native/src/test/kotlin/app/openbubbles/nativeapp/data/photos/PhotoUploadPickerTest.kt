package app.openbubbles.nativeapp.data.photos

import androidx.exifinterface.media.ExifInterface
import app.openbubbles.core.photos.PhotoTimeZone
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoUploadPickerTest {
    @Test
    fun originalExifTimeWithoutOffsetIsReadInTheDeviceZone() {
        // 10:46:04 on a New York summer morning is 14:46:04Z, not 10:46:04Z.
        assertEquals(
            Instant.parse("2026-08-20T14:46:04.190Z").toEpochMilli(),
            parseExifOriginalDateTime(
                dateTime = "2026:08:20 10:46:04",
                subSeconds = "190",
                offset = null,
                zone = ZoneId.of("America/New_York"),
            ),
        )
        // Winter: the same wall clock is 15:46:04Z because DST has ended.
        assertEquals(
            Instant.parse("2026-01-20T15:46:04Z").toEpochMilli(),
            parseExifOriginalDateTime("2026:01:20 10:46:04", null, null, ZoneId.of("America/New_York")),
        )
    }

    @Test
    fun deviceZoneIsResolvedAtTheCaptureWallClock() {
        val newYork = ZoneId.of("America/New_York")
        assertEquals(
            PhotoTimeZone("America/New_York", -4 * 3600),
            uploadTimeZone("2026:08:20 10:46:04", null, newYork, 0L),
        )
        assertEquals(
            PhotoTimeZone("America/New_York", -5 * 3600),
            uploadTimeZone("2026:01:20 10:46:04", null, newYork, 0L),
        )
        // Without a camera clock the best-known instant decides the offset.
        val winterInstant = Instant.parse("2026-01-20T15:00:00Z").toEpochMilli()
        assertEquals(
            PhotoTimeZone("America/New_York", -5 * 3600),
            uploadTimeZone(null, winterInstant, newYork, Instant.parse("2026-08-01T00:00:00Z").toEpochMilli()),
        )
        assertEquals(
            PhotoTimeZone("Asia/Kolkata", 19_800),
            uploadTimeZone("garbage", null, ZoneId.of("Asia/Kolkata"), 0L),
        )
        assertEquals(LocalDateTime.of(2026, 8, 20, 10, 46, 4), parseExifLocalDateTime("2026:08:20 10:46:04"))
        assertNull(parseExifLocalDateTime("0000:00:00 00:00:00"))
        assertNull(parseExifLocalDateTime("2026-08-20T10:46:04"))
    }

    @Test
    fun normalizedJpegInheritsCaptureTagsButNotLayoutTags() {
        for (tag in listOf(
            ExifInterface.TAG_MAKE,
            ExifInterface.TAG_MODEL,
            ExifInterface.TAG_LENS_MODEL,
            ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
            ExifInterface.TAG_F_NUMBER,
            ExifInterface.TAG_EXPOSURE_TIME,
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
            ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
            ExifInterface.TAG_GPS_LATITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_H_POSITIONING_ERROR,
        )) {
            assertTrue(tag in NormalizedJpegExifTags, tag)
            assertFalse(tag in NormalizedJpegExifExclusions, tag)
        }
        for (tag in listOf(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.TAG_PIXEL_X_DIMENSION,
            ExifInterface.TAG_PIXEL_Y_DIMENSION,
            ExifInterface.TAG_MAKER_NOTE,
            ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH,
        )) {
            assertTrue(tag in NormalizedJpegExifExclusions, tag)
            assertFalse(tag in NormalizedJpegExifTags, tag)
        }
        assertEquals(NormalizedJpegExifTags.size, NormalizedJpegExifTags.toSet().size)
    }

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

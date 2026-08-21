package app.openbubbles.nativeapp.ui.photos

import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.photos.PhotoSummary
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 2025-10-09 12:00 UTC, the clock every fixture and section title is read against. */
private const val NOW = 1_760_011_200_000L
private val UTC: ZoneId = ZoneId.of("UTC")
private const val DAY = 86_400_000L

private fun photo(
    id: String,
    capturedAtMs: Long? = null,
    addedAtMs: Long? = null,
    favorite: Boolean = false,
    hidden: Boolean = false,
    kind: PhotoMediaKind = PhotoMediaKind.Image,
    width: Int? = 4032,
    height: Int? = 3024,
    originalSize: Long? = 4_200_000,
    livePhoto: Boolean = false,
    filename: String? = "IMG_$id.HEIC",
) = PhotoSummary(
    id = id,
    assetId = "asset-$id",
    filename = filename,
    mediaKind = kind,
    livePhoto = livePhoto,
    width = width,
    height = height,
    originalSize = originalSize,
    previewSize = 102_000,
    capturedAtMs = capturedAtMs,
    addedAtMs = addedAtMs,
    favorite = favorite,
    hidden = hidden,
)

class VisiblePhotosTest {
    @Test
    fun `hidden assets are never listed`() {
        val visible = visiblePhotos(
            listOf(photo("a", capturedAtMs = NOW), photo("hidden", capturedAtMs = NOW, hidden = true)),
            PhotoFilter.All,
        )
        assertEquals(listOf("a"), visible.map { it.id })
    }

    @Test
    fun `the newest capture comes first, whatever order the page arrived in`() {
        val visible = visiblePhotos(
            listOf(
                photo("old", capturedAtMs = NOW - 30 * DAY),
                photo("new", capturedAtMs = NOW - DAY),
                photo("middle", capturedAtMs = NOW - 5 * DAY),
            ),
            PhotoFilter.All,
        )
        assertEquals(listOf("new", "middle", "old"), visible.map { it.id })
    }

    @Test
    fun `capture time wins over the date iCloud indexed the asset`() {
        // Imported today, taken last year: it belongs with last year.
        val visible = visiblePhotos(
            listOf(
                photo("imported", capturedAtMs = NOW - 300 * DAY, addedAtMs = NOW),
                photo("recent", capturedAtMs = NOW - DAY, addedAtMs = NOW - DAY),
            ),
            PhotoFilter.All,
        )
        assertEquals(listOf("recent", "imported"), visible.map { it.id })
    }

    @Test
    fun `an asset with no capture time falls back to when it was added`() {
        assertEquals(NOW, photoTakenAtMs(photo("a", addedAtMs = NOW)))
        assertEquals(NOW, photoTakenAtMs(photo("a", capturedAtMs = NOW, addedAtMs = 1)))
        assertNull(photoTakenAtMs(photo("a", capturedAtMs = 0, addedAtMs = 0)))
    }

    @Test
    fun `undated assets sort last in a stable order`() {
        val visible = visiblePhotos(
            listOf(photo("z-undated"), photo("a-undated"), photo("dated", capturedAtMs = NOW)),
            PhotoFilter.All,
        )
        assertEquals(listOf("dated", "a-undated", "z-undated"), visible.map { it.id })
    }

    @Test
    fun `filters select favourites and videos`() {
        val assets = listOf(
            photo("photo", capturedAtMs = NOW),
            photo("fave", capturedAtMs = NOW, favorite = true),
            photo("video", capturedAtMs = NOW, kind = PhotoMediaKind.Video),
        )
        assertEquals(3, visiblePhotos(assets, PhotoFilter.All).size)
        assertEquals(listOf("fave"), visiblePhotos(assets, PhotoFilter.Favorites).map { it.id })
        assertEquals(listOf("video"), visiblePhotos(assets, PhotoFilter.Videos).map { it.id })
    }
}

class PhotoTimelineTest {
    private val assets = listOf(
        photo("today-1", capturedAtMs = NOW - 3_600_000),
        photo("today-2", capturedAtMs = NOW - 7_200_000),
        photo("yesterday", capturedAtMs = NOW - DAY - 3_600_000),
        photo("last-week", capturedAtMs = NOW - 6 * DAY),
        photo("last-year", capturedAtMs = NOW - 400 * DAY),
        photo("undated"),
    )

    @Test
    fun `days group into one section each, newest first`() {
        val sections = photoTimeline(assets, PhotoGrouping.Day, zone = UTC, nowMillis = NOW)
        assertEquals(
            listOf("Today", "Yesterday", "Fri, 3 Oct", "4 Sep 2024", "No date"),
            sections.map { it.title },
        )
        assertEquals(listOf("today-1", "today-2"), sections.first().assets.map { it.id })
    }

    @Test
    fun `months and years collapse the same assets into fewer sections`() {
        val byMonth = photoTimeline(assets, PhotoGrouping.Month, zone = UTC, nowMillis = NOW)
        assertEquals(listOf("This month", "September 2024", "No date"), byMonth.map { it.title })
        val byYear = photoTimeline(assets, PhotoGrouping.Year, zone = UTC, nowMillis = NOW)
        assertEquals(listOf("2025", "2024", "No date"), byYear.map { it.title })
    }

    @Test
    fun `every asset appears exactly once at every density`() {
        PhotoGrouping.entries.forEach { grouping ->
            val flattened = photoTimeline(assets, grouping, zone = UTC, nowMillis = NOW)
                .flatMap(PhotoSection::assets)
            assertEquals(assets.size, flattened.size, "grouping $grouping")
            assertEquals(flattened.map { it.id }.distinct().size, flattened.size, "grouping $grouping")
        }
    }

    @Test
    fun `section keys are unique so the grid can key on them`() {
        PhotoGrouping.entries.forEach { grouping ->
            val keys = photoTimeline(assets, grouping, zone = UTC, nowMillis = NOW).map { it.key }
            assertEquals(keys.distinct().size, keys.size, "grouping $grouping")
        }
    }

    @Test
    fun `a filter that matches nothing produces no sections rather than empty ones`() {
        assertTrue(
            photoTimeline(assets, PhotoGrouping.Day, PhotoFilter.Videos, UTC, NOW).isEmpty(),
        )
    }

    @Test
    fun `the same day in different zones can be a different section`() {
        // 23:30 UTC on 9 October is already 01:30 on the 10th in Berlin.
        val lateNight = listOf(photo("late", capturedAtMs = NOW + 41_400_000L))
        val utc = photoTimeline(lateNight, PhotoGrouping.Day, zone = UTC, nowMillis = NOW)
        val berlin = photoTimeline(
            lateNight,
            PhotoGrouping.Day,
            zone = ZoneId.of("Europe/Berlin"),
            nowMillis = NOW,
        )
        assertEquals("Today", utc.single().title)
        assertEquals("Fri, 10 Oct", berlin.single().title, "Berlin is already on the next day")
    }
}

class PhotoGroupingTest {
    @Test
    fun `density steps stop at the ends of the scale`() {
        assertNull(PhotoGrouping.Day.denser())
        assertEquals(PhotoGrouping.Month, PhotoGrouping.Day.wider())
        assertEquals(PhotoGrouping.Day, PhotoGrouping.Month.denser())
        assertNull(PhotoGrouping.Year.wider())
    }

    @Test
    fun `denser groupings show more columns`() {
        assertTrue(PhotoGrouping.Day.columns < PhotoGrouping.Month.columns)
        assertTrue(PhotoGrouping.Month.columns < PhotoGrouping.Year.columns)
    }

    @Test
    fun `a pinch must travel before it re-flows the grid`() {
        assertNull(groupingForPinch(PhotoGrouping.Month, 1.1f))
        assertNull(groupingForPinch(PhotoGrouping.Month, 0.95f))
        assertEquals(PhotoGrouping.Day, groupingForPinch(PhotoGrouping.Month, 1.4f))
        assertEquals(PhotoGrouping.Year, groupingForPinch(PhotoGrouping.Month, 0.6f))
    }

    @Test
    fun `a pinch at the end of the scale changes nothing`() {
        assertNull(groupingForPinch(PhotoGrouping.Day, 2f))
        assertNull(groupingForPinch(PhotoGrouping.Year, 0.4f))
    }

    @Test
    fun `a degenerate gesture is ignored`() {
        assertNull(groupingForPinch(PhotoGrouping.Month, 0f))
        assertNull(groupingForPinch(PhotoGrouping.Month, Float.NaN))
    }
}

class PhotoInfoTest {
    @Test
    fun `counts read as photos and videos`() {
        assertEquals("No items", photoCountLabel(emptyList()))
        assertEquals("1 photo", photoCountLabel(listOf(photo("a"))))
        assertEquals(
            "2 photos · 1 video",
            photoCountLabel(
                listOf(photo("a"), photo("b"), photo("v", kind = PhotoMediaKind.Video)),
            ),
        )
    }

    @Test
    fun `the info sheet shows what the bounded listing returned`() {
        val rows = photoInfoRows(
            photo("a", capturedAtMs = NOW, favorite = true, livePhoto = true),
            zone = UTC,
        ).toMap()
        assertEquals("IMG_a.HEIC", rows["Name"])
        assertEquals("Thu, 9 Oct 2025 · 12:00", rows["Taken"])
        assertEquals("4032 × 3024", rows["Dimensions"])
        assertEquals("12.2 MP", rows["Resolution"])
        assertEquals("4.2 MB", rows["Original"])
        assertEquals("Live Photo", rows["Kind"])
        assertEquals("Yes", rows["Favorite"])
    }

    @Test
    fun `the info sheet never invents metadata the protocol withholds`() {
        val rows = photoInfoRows(
            photo("a", width = null, height = null, originalSize = null, filename = null),
            zone = UTC,
        ).toMap()
        assertFalse(rows.containsKey("Name"))
        assertFalse(rows.containsKey("Dimensions"))
        assertFalse(rows.containsKey("Original"))
        assertFalse(rows.keys.any { it.contains("Location") || it.contains("People") })
        assertEquals("Photo", rows["Kind"])
    }

    @Test
    fun `an added-only asset is labelled as added, not taken`() {
        val rows = photoInfoRows(photo("a", addedAtMs = NOW), zone = UTC).toMap()
        assertTrue(rows.containsKey("Added"))
        assertFalse(rows.containsKey("Taken"))
    }

    @Test
    fun `byte sizes scale into readable units`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("938 KB", formatBytes(938_000))
        assertEquals("4.2 MB", formatBytes(4_200_000))
        assertEquals("1.5 GB", formatBytes(1_500_000_000))
    }
}

package app.openbubbles.nativeapp.ui.photos

import app.openbubbles.core.photos.PhotoMediaKind
import app.openbubbles.core.photos.PhotoSummary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The library's timeline: how a flat page of iCloud metadata becomes dated
 * sections at a chosen density.
 *
 * All pure. The grid, the section headers, the pinch density, the filters, and
 * the info sheet all read from here, so what the screen shows is provable on the
 * host instead of only by eye.
 */

/** How tightly the grid is packed, and therefore how coarsely it is grouped. */
enum class PhotoGrouping(val label: String, val columns: Int) {
    Day("Days", 3),
    Month("Months", 5),
    Year("Years", 7),
    ;

    /** One step denser (a pinch out), or null at the end of the scale. */
    fun denser(): PhotoGrouping? = entries.getOrNull(ordinal - 1)

    /** One step wider (a pinch in), or null at the end of the scale. */
    fun wider(): PhotoGrouping? = entries.getOrNull(ordinal + 1)
}

/** Which assets the grid shows. */
enum class PhotoFilter(val label: String) {
    All("All"),
    Favorites("Favorites"),
    Videos("Videos"),
}

/** One dated run of assets with its header text. */
data class PhotoSection(
    val key: String,
    val title: String,
    val assets: List<PhotoSummary>,
)

/**
 * When a photo was taken.
 *
 * Capture time is what a person means by "when"; the date it landed in iCloud is
 * only a fallback for assets Apple returned without one.
 */
fun photoTakenAtMs(asset: PhotoSummary): Long? =
    asset.capturedAtMs?.takeIf { it > 0 } ?: asset.addedAtMs?.takeIf { it > 0 }

/**
 * The assets the grid shows, newest first.
 *
 * Hidden assets are never listed: Apple hides them from the library view, and a
 * client that quietly showed them would leak exactly what the flag is for.
 * Ordering is by capture time rather than the added-date index the protocol
 * pages by, so a photo imported today from last summer sits with last summer.
 */
fun visiblePhotos(assets: List<PhotoSummary>, filter: PhotoFilter): List<PhotoSummary> = assets
    .asSequence()
    .filterNot(PhotoSummary::hidden)
    .filter { asset ->
        when (filter) {
            PhotoFilter.All -> true
            PhotoFilter.Favorites -> asset.favorite
            PhotoFilter.Videos -> asset.mediaKind == PhotoMediaKind.Video
        }
    }
    .sortedWith(
        compareByDescending<PhotoSummary> { photoTakenAtMs(it) ?: Long.MIN_VALUE }
            // Undated assets keep a stable order instead of shuffling per page.
            .thenBy { it.id },
    )
    .toList()

/**
 * Groups [assets] into dated sections at [grouping]'s granularity.
 *
 * Assets Apple returned with no usable date go last under their own heading
 * rather than being dropped or dated with today.
 */
fun photoTimeline(
    assets: List<PhotoSummary>,
    grouping: PhotoGrouping,
    filter: PhotoFilter = PhotoFilter.All,
    zone: ZoneId = ZoneId.systemDefault(),
    nowMillis: Long = System.currentTimeMillis(),
): List<PhotoSection> {
    val visible = visiblePhotos(assets, filter)
    if (visible.isEmpty()) return emptyList()
    val sections = LinkedHashMap<String, MutableList<PhotoSummary>>()
    val titles = HashMap<String, String>()
    visible.forEach { asset ->
        val takenAt = photoTakenAtMs(asset)
        val key = sectionKey(takenAt, grouping, zone)
        titles.getOrPut(key) { sectionTitle(takenAt, grouping, zone, nowMillis) }
        sections.getOrPut(key) { mutableListOf() } += asset
    }
    return sections.map { (key, group) ->
        PhotoSection(key = key, title = titles.getValue(key), assets = group)
    }
}

private const val UNDATED_KEY = "undated"

private fun sectionKey(takenAtMs: Long?, grouping: PhotoGrouping, zone: ZoneId): String {
    val date = takenAtMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: return UNDATED_KEY
    return when (grouping) {
        PhotoGrouping.Day -> "d-${date.year}-${date.monthValue}-${date.dayOfMonth}"
        PhotoGrouping.Month -> "m-${date.year}-${date.monthValue}"
        PhotoGrouping.Year -> "y-${date.year}"
    }
}

private val DayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.US)
private val DayWithYearFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
private val MonthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

/** "Today" / "Yesterday" / "Sat, 11 Oct" / "11 Oct 2023" / "October 2025" / "2024". */
internal fun sectionTitle(
    takenAtMs: Long?,
    grouping: PhotoGrouping,
    zone: ZoneId,
    nowMillis: Long,
): String {
    val date = takenAtMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        ?: return "No date"
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when (grouping) {
        PhotoGrouping.Day -> when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> DayFormat.format(date)
            else -> DayWithYearFormat.format(date)
        }
        PhotoGrouping.Month -> if (date.year == today.year && date.monthValue == today.monthValue) {
            "This month"
        } else {
            MonthFormat.format(date)
        }
        PhotoGrouping.Year -> date.year.toString()
    }
}

/**
 * The density a pinch lands on.
 *
 * A pinch has to travel a real distance before the grid re-flows, otherwise a
 * scroll with two fingers resizes the library by accident. Returns null when the
 * gesture should change nothing.
 */
fun groupingForPinch(
    current: PhotoGrouping,
    zoomFactor: Float,
    threshold: Float = 1.35f,
): PhotoGrouping? {
    if (!zoomFactor.isFinite() || zoomFactor <= 0f) return null
    return when {
        zoomFactor >= threshold -> current.denser()
        zoomFactor <= 1f / threshold -> current.wider()
        else -> null
    }
}

/** "3 photos" / "1 video" / "128 items" for a header or a count line. */
fun photoCountLabel(assets: List<PhotoSummary>): String {
    if (assets.isEmpty()) return "No items"
    val videos = assets.count { it.mediaKind == PhotoMediaKind.Video }
    val photos = assets.size - videos
    val parts = buildList {
        if (photos > 0) add(if (photos == 1) "1 photo" else "$photos photos")
        if (videos > 0) add(if (videos == 1) "1 video" else "$videos videos")
    }
    return parts.joinToString(" · ")
}

private val InfoDateFormat = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · HH:mm", Locale.US)

/**
 * The rows of the viewer's info sheet, in reading order.
 *
 * Only metadata the bounded listing already returned appears here: no location,
 * no people, no captions — those deliberately never leave Rust.
 */
fun photoInfoRows(
    asset: PhotoSummary,
    zone: ZoneId = ZoneId.systemDefault(),
): List<Pair<String, String>> = buildList {
    asset.filename?.takeIf { it.isNotBlank() }?.let { add("Name" to it) }
    photoTakenAtMs(asset)?.let { taken ->
        val captured = asset.capturedAtMs
        val label = if (captured != null && captured > 0) "Taken" else "Added"
        add(label to InfoDateFormat.format(Instant.ofEpochMilli(taken).atZone(zone)))
    }
    val width = asset.width
    val height = asset.height
    if (width != null && height != null && width > 0 && height > 0) {
        add("Dimensions" to "$width × $height")
        megapixels(width, height)?.let { add("Resolution" to it) }
    }
    asset.originalSize?.takeIf { it > 0 }?.let { add("Original" to formatBytes(it)) }
    add(
        "Kind" to when (asset.mediaKind) {
            PhotoMediaKind.Image -> if (asset.livePhoto) "Live Photo" else "Photo"
            PhotoMediaKind.Video -> "Video"
            PhotoMediaKind.Unknown -> "Unknown"
        },
    )
    if (asset.favorite) add("Favorite" to "Yes")
}

private fun megapixels(width: Int, height: Int): String? {
    val mp = width.toLong() * height.toLong() / 1_000_000.0
    if (mp < 0.1) return null
    return String.format(Locale.US, "%.1f MP", mp)
}

/** "4.2 MB" / "938 KB" / "512 B". */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> String.format(Locale.US, "%.1f MB", bytes / 1_000_000.0)
    bytes >= 1_000 -> "${bytes / 1_000} KB"
    else -> "$bytes B"
}

/** Today, in [zone]; kept here so section titles and tests share one clock path. */
internal fun todayIn(zone: ZoneId, nowMillis: Long): LocalDate =
    Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

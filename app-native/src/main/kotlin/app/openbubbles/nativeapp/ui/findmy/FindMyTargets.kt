package app.openbubbles.nativeapp.ui.findmy

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.round

/**
 * Tracking model shared by the map and the list.
 *
 * Devices, friends, and items arrive from three unrelated Apple services with
 * three different shapes. The map cannot care: it needs one flat list of things
 * that may have a position. Everything here is pure so ordering, staleness,
 * trails, and every string on screen are provable on the host.
 */

enum class FmTargetKind { Device, Friend, Item }

/** Feature-local filters, ordered like the native Find My people-first experience. */
enum class FmFindMySection(val title: String, val kind: FmTargetKind) {
    People("People", FmTargetKind.Friend),
    Devices("Devices", FmTargetKind.Device),
    Items("Items", FmTargetKind.Item),
}

internal fun findMySectionTargets(
    targets: List<FmTarget>,
    section: FmFindMySection,
): List<FmTarget> = targets.filter { it.kind == section.kind }

/** One trackable thing: a device, a followed friend, or a beacon item. */
data class FmTarget(
    val id: String,
    val kind: FmTargetKind,
    val name: String,
    val point: FmPoint? = null,
    val model: String? = null,
    /** Apple's authoritative device family, preferred for the map glyph. */
    val deviceClass: String? = null,
    val emoji: String? = null,
    val batteryPercent: Int? = null,
    val batteryStatus: String? = null,
    /** Contact handle, used for the friend avatar and for contact colours. */
    val address: String? = null,
    /** Sharer's handle for a shared item. */
    val sharedBy: String? = null,
    /** The account has Lost Mode on for this device. */
    val lostMode: Boolean = false,
    /** Apple is currently trying to locate this friend. */
    val locating: Boolean = false,
    /** This is the device the app runs on. */
    val thisDevice: Boolean = false,
) {
    val located: Boolean get() = point != null
}

/** How the whole tracked set is ordered, and what its camera should frame. */
data class FmTracking(
    val targets: List<FmTarget> = emptyList(),
    /** Recent fixes per target id, oldest first. */
    val trails: Map<String, List<FmPoint>> = emptyMap(),
) {
    fun target(id: String?): FmTarget? = id?.let { key -> targets.firstOrNull { it.id == key } }

    val located: List<FmTarget> get() = targets.filter(FmTarget::located)
}

/** Fixes older than this read as approximate rather than current. */
const val FM_STALE_FIX_MS: Long = 30 * 60_000L

/** How many fixes per target the session keeps for the map track. */
const val FM_TRAIL_LIMIT: Int = 24

/**
 * Flattens the three services into one ordered list.
 *
 * Devices come first, then friends, then items — the order the sections have
 * always had — and inside each kind the located entries come first, freshest
 * first, so the top of the list is what the user can actually go find. Ties
 * break on name so the list never reshuffles between refreshes.
 */
fun findMyTargets(
    devices: List<FmDeviceUi>,
    friends: List<FmFriendUi>,
    items: List<FmItemUi>,
): List<FmTarget> {
    val mapped = devices.map { device ->
        FmTarget(
            id = "device:${device.id}",
            kind = FmTargetKind.Device,
            name = device.name,
            point = device.location,
            model = device.model,
            deviceClass = device.deviceClass,
            batteryPercent = device.batteryPercent,
            batteryStatus = device.batteryStatus,
            lostMode = device.lostModeEnabled,
            thisDevice = device.thisDevice,
        )
    } + friends.map { friend ->
        FmTarget(
            id = "friend:${friend.id}",
            kind = FmTargetKind.Friend,
            name = friend.name,
            point = friend.location,
            address = friend.address,
            locating = friend.locating,
        )
    } + items.map { item ->
        FmTarget(
            id = "item:${item.id}",
            kind = FmTargetKind.Item,
            name = item.name,
            point = item.location,
            model = item.model,
            emoji = item.emoji,
            batteryPercent = item.batteryPercent,
            sharedBy = item.sharedBy,
        )
    }
    return mapped.sortedWith(
        compareBy<FmTarget> { it.kind.ordinal }
            .thenBy { !it.located }
            .thenByDescending { it.point?.timestampMs ?: Long.MIN_VALUE }
            .thenBy { it.name.lowercase() },
    )
}

/**
 * Appends a fix to a target's track.
 *
 * A refresh that returns the same fix must not lengthen the track, and a track
 * is capped so a screen left open all afternoon cannot grow without bound.
 */
fun appendTrail(
    existing: List<FmPoint>,
    point: FmPoint?,
    limit: Int = FM_TRAIL_LIMIT,
): List<FmPoint> {
    if (point == null || limit <= 0) return existing
    val last = existing.lastOrNull()
    if (last != null) {
        val lastTime = last.timestampMs
        val pointTime = point.timestampMs
        if (lastTime != null && (pointTime == null || pointTime <= lastTime)) return existing
        if (lastTime == null && pointTime == null &&
            last.latitude == point.latitude && last.longitude == point.longitude
        ) {
            return existing
        }
    }
    return (existing + point).takeLast(limit)
}

/** True when a fix is old enough that the UI should say so. */
fun isStaleFix(point: FmPoint?, nowMillis: Long): Boolean {
    val timestamp = point?.timestampMs?.takeIf { it > 0L } ?: return point != null
    return nowMillis - timestamp > FM_STALE_FIX_MS
}

/** "just now", "7 min ago", "3 h ago", or a short date once it is days old. */
fun fixFreshness(timestampMs: Long?, nowMillis: Long): String? {
    if (timestampMs == null || timestampMs <= 0L) return null
    val ageMs = nowMillis - timestampMs
    if (ageMs < 0) return "just now"
    val minutes = ageMs / 60_000
    val hours = minutes / 60
    return when {
        ageMs < 60_000 -> "just now"
        hours < 1 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        else -> DateTimeFormatter.ofPattern("M/d/yy")
            .format(Instant.ofEpochMilli(timestampMs).atZone(ZoneId.systemDefault()))
    }
}

/** "18 m" / "1.2 km" fix radius. */
fun fixAccuracy(meters: Double?): String? = meters?.takeIf { it > 0 }?.let {
    if (it < 1000) "${round(it).toInt()} m" else String.format(Locale.US, "%.1f km", it / 1000)
}

/** "120 m away" / "4.3 km away" between two fixes. */
fun distanceLabel(meters: Double): String = when {
    meters < 1000 -> "${round(meters).toInt()} m away"
    else -> String.format(Locale.US, "%.1f km away", meters / 1000)
}

/**
 * The single supporting line under a target's name.
 *
 * Battery, then freshness, then accuracy, then anything caller-specific — the
 * same order every row uses so the list scans vertically.
 */
fun targetSummary(target: FmTarget, nowMillis: Long): String {
    val parts = mutableListOf<String>()
    if (target.thisDevice) parts += "This device"
    target.batteryPercent?.let { parts += "$it%" }
    target.batteryStatus?.let { parts += it }
    val point = target.point
    if (point == null) {
        parts += if (target.locating) "Locating…" else "No location"
    } else {
        fixFreshness(point.timestampMs, nowMillis)?.let { parts += it }
        fixAccuracy(point.accuracyMeters)?.let { parts += it }
    }
    if (target.lostMode) parts += "Lost Mode"
    target.sharedBy?.let { parts += "shared by $it" }
    return parts.joinToString(" · ")
}

/** Native Find My makes the place, not accuracy or battery, the row's primary detail. */
fun targetLocationSummary(target: FmTarget, nowMillis: Long): String {
    val point = target.point ?: return if (target.locating) "Locating…" else "Location unavailable"
    return listOfNotNull(
        point.address?.takeIf(String::isNotBlank) ?: "Address unavailable",
        fixFreshness(point.timestampMs, nowMillis),
    ).joinToString(" · ")
}

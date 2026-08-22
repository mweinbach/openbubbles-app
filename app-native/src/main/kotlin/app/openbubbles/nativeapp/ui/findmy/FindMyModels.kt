package app.openbubbles.nativeapp.ui.findmy

import app.openbubbles.nativeapp.data.UiContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UFmAddress
import uniffi.rust_lib_bluebubbles.UFmDevice
import uniffi.rust_lib_bluebubbles.UFmFriend
import uniffi.rust_lib_bluebubbles.UFmItem
import uniffi.rust_lib_bluebubbles.UFmLocation
import uniffi.rust_lib_bluebubbles.UFmReport
import kotlin.math.roundToInt

/**
 * UI-facing FindMy models. The screen and view model depend only on these +
 * [FindMyPort]; [RustFindMyPort] maps the generated UniFFI records into the
 * deliberately smaller display models.
 */

/** One location fix, normalized for display. */
data class FmPoint(
    val latitude: Double,
    val longitude: Double,
    /** Fix radius in meters, when known. */
    val accuracyMeters: Double? = null,
    /** Fix time in epoch milliseconds, when known. */
    val timestampMs: Long? = null,
    /** Reverse-geocoded street line Apple already returned, when present. */
    val address: String? = null,
)

/** A Find My device (this account's iPhone, Mac, Watch…). */
data class FmDeviceUi(
    val id: String,
    val name: String,
    val model: String? = null,
    /** Battery charge in percent, when known. */
    val batteryPercent: Int? = null,
    /** Raw battery status ("Charging", "Charged"…), when known. */
    val batteryStatus: String? = null,
    val location: FmPoint? = null,
    /** Apple's device class ("iPhone", "Mac"…), used for the marker glyph. */
    val deviceClass: String? = null,
    /** Read-only: the account already has Lost Mode on for this device. */
    val lostModeEnabled: Boolean = false,
    /** The device the app is running on. */
    val thisDevice: Boolean = false,
)

/** A friend this account follows (Find My friends). */
data class FmFriendUi(
    val id: String,
    val name: String,
    /** First share-acceptance handle (contact resolution key). */
    val address: String? = null,
    val location: FmPoint? = null,
    /** Apple is resolving a newer fix for this friend right now. */
    val locating: Boolean = false,
)

/** A Find My item (AirTag / accessory, own or shared). */
data class FmItemUi(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val model: String? = null,
    /** Battery charge in percent, when the item reports it. */
    val batteryPercent: Int? = null,
    /** Sharer's handle for shared items, when known. */
    val sharedBy: String? = null,
    val location: FmPoint? = null,
)

/**
 * Data seam for the FindMy screen. Getters return the last known data
 * (offline-capable); refresh functions hit Apple and return the new data.
 * Implementations throw when the push state (or the Rust bindings) is
 * unavailable.
 */
interface FindMyPort {
    /** True when a live push state is installed at all. */
    fun isAvailable(): Boolean

    /** Last known devices without network traffic. */
    suspend fun devices(): List<FmDeviceUi>

    /** Refreshes devices from Apple; returns the new list. */
    suspend fun refreshDevices(): List<FmDeviceUi>

    /** Last known followed friends without network traffic. */
    suspend fun friends(): List<FmFriendUi>

    /** Refreshes followed friends from Apple; returns the new list. */
    suspend fun refreshFriends(): List<FmFriendUi>

    /** Last known beacon items without network traffic. */
    suspend fun items(): List<FmItemUi>

    /** Syncs beacon item positions; returns the new list. */
    suspend fun refreshItems(): List<FmItemUi>
}

/**
 * Bridges the generated UniFFI `NativePushState` FindMy methods into
 * [FindMyPort]. Typed calls keep this adapter safe under shrinking and make
 * contract drift a compile-time failure instead of a runtime reflection miss.
 */
class RustFindMyPort(
    private val stateProvider: () -> NativePushState?,
) : FindMyPort {

    override fun isAvailable(): Boolean = stateProvider() != null

    override suspend fun devices(): List<FmDeviceUi> {
        val raw = runInterruptible(Dispatchers.IO) { requireState().getDevices() }
        return raw.mapIndexed(::mapDevice)
    }

    override suspend fun refreshDevices(): List<FmDeviceUi> {
        val raw = runInterruptible(Dispatchers.IO) { requireState().refreshDevices() }
        return raw.mapIndexed(::mapDevice)
    }

    override suspend fun friends(): List<FmFriendUi> {
        val raw = runInterruptible(Dispatchers.IO) { requireState().getFollowing() }
        return raw.map { mapFriend(it) }
    }

    override suspend fun refreshFriends(): List<FmFriendUi> {
        val raw = runInterruptible(Dispatchers.IO) { requireState().refreshFollowing() }
        return raw.map { mapFriend(it) }
    }

    override suspend fun items(): List<FmItemUi> {
        val raw = runInterruptible(Dispatchers.IO) { requireState().getCachedBeaconItems() }
        return raw.map(UFmItem::toUi)
    }

    override suspend fun refreshItems(): List<FmItemUi> {
        val raw = runInterruptible(Dispatchers.IO) { requireState().getBeaconItems() }
        return raw.map(UFmItem::toUi)
    }

    private fun requireState(): NativePushState =
        stateProvider() ?: error("not connected")

    private fun mapDevice(index: Int, raw: UFmDevice): FmDeviceUi {
        val model = raw.modelDisplayName.nonBlank()
            ?: raw.rawDeviceModel.nonBlank()
            ?: raw.deviceModel.nonBlank()
        val name = raw.name.nonBlank()
            ?: raw.deviceDisplayName.nonBlank()
            ?: model
            ?: "Device ${index + 1}"
        // FoundDevice.batteryLevel is a 0..1 fraction.
        val batteryFraction = raw.batteryLevel
        val batteryPercent = batteryFraction
            ?.takeIf { it >= 0.0 && it <= 1.5 }
            ?.let { (it * 100).roundToInt().coerceIn(0, 100) }
            ?: batteryFraction?.roundToInt()?.coerceIn(0, 100)
        return FmDeviceUi(
            id = raw.id.nonBlank() ?: raw.baUuid.nonBlank() ?: name,
            name = name,
            model = model,
            batteryPercent = batteryPercent,
            batteryStatus = raw.batteryStatus.nonBlank(),
            location = raw.location?.toUiPoint(),
            deviceClass = raw.deviceClass.nonBlank(),
            lostModeEnabled = raw.lostModeEnabled == true,
            thisDevice = raw.thisDevice == true,
        )
    }

    private suspend fun mapFriend(raw: UFmFriend): FmFriendUi {
        val address = (raw.invitationAcceptedHandles + raw.invitationFromHandles).firstOrNull()
        val contactName = address?.let { addr ->
            runCatching { UiContacts.contactNames?.invoke(addr)?.first }.getOrNull()
        }
        return raw.toUi(contactName)
    }
}

private fun String?.nonBlank(): String? = this?.takeIf(String::isNotBlank)

private const val APPLE_EPOCH_UNIX_MS = 978_307_200_000L

/** Find My mixes Unix seconds/milliseconds with milliseconds since Apple's 2001 epoch. */
internal fun normalizeFindMyTimestamp(value: Long): Long {
    if (value <= 0L) return value
    val milliseconds = if (value < 100_000_000_000L) value * 1_000L else value
    return if (milliseconds < APPLE_EPOCH_UNIX_MS) {
        milliseconds + APPLE_EPOCH_UNIX_MS
    } else {
        milliseconds
    }
}

internal fun UFmLocation.toUiPoint(): FmPoint = FmPoint(
    latitude = latitude,
    longitude = longitude,
    accuracyMeters = horizontalAccuracy,
    timestampMs = normalizeFindMyTimestamp(timestamp),
    address = address?.let(::formatFindMyAddress),
)

/** Apple already supplied this address; resolving it must not contact another map provider. */
private fun formatFindMyAddress(address: UFmAddress): String? {
    address.formattedAddressLines
        ?.filter(String::isNotBlank)
        ?.takeIf { it.isNotEmpty() }
        ?.let { return it.joinToString(", ") }
    return listOfNotNull(
        address.streetAddress.nonBlank() ?: address.streetName.nonBlank(),
        address.locality.nonBlank(),
        address.administrativeArea.nonBlank() ?: address.stateCode.nonBlank(),
    ).takeIf { it.isNotEmpty() }?.joinToString(", ")
}

internal fun UFmFriend.toUi(contactName: String? = null): FmFriendUi {
    val handle = (invitationAcceptedHandles + invitationFromHandles).firstOrNull()
    return FmFriendUi(
        id = id.nonBlank() ?: "friend",
        name = contactName.nonBlank() ?: handle ?: "Friend",
        address = handle,
        location = lastLocation?.toUiPoint(),
        locating = locateInProgress,
    )
}

internal fun UFmItem.toUi(): FmItemUi {
    val displayModel = model.nonBlank()
    return FmItemUi(
        id = id.nonBlank() ?: associatedBeacon.nonBlank() ?: "item",
        name = name.nonBlank() ?: displayModel ?: "Item",
        emoji = emoji.nonBlank(),
        model = displayModel,
        batteryPercent = batteryLevel?.toInt()?.coerceIn(0, 100),
        sharedBy = ownerHandle.nonBlank(),
        location = lastReport?.toUiPoint(),
    )
}

private fun UFmReport.toUiPoint(): FmPoint = FmPoint(
    latitude = lat.toDouble(),
    longitude = long.toDouble(),
    accuracyMeters = horizontalAccuracy.toDouble(),
    timestampMs = timestampMs.toLong(),
)

/** Deterministic sample data for previews (and offline UI testing). */
private const val PREVIEW_NOW_MILLIS = 1_760_000_000_000L

class FakeFindMyPort(
    private val failRefresh: Boolean = false,
) : FindMyPort {
    override fun isAvailable() = true

    private val devices = listOf(
        FmDeviceUi(
            id = "d1", name = "Max's iPhone", model = "iPhone 16 Pro",
            batteryPercent = 78, batteryStatus = "Charging",
            location = FmPoint(
                latitude = 37.7749,
                longitude = -122.4194,
                accuracyMeters = 65.0,
                timestampMs = PREVIEW_NOW_MILLIS - 120_000,
                address = "1 Market St, San Francisco, CA",
            ),
            deviceClass = "iPhone",
            thisDevice = true,
        ),
        FmDeviceUi(
            id = "d2", name = "MacBook Pro", model = "MacBook Pro 14\"",
            batteryPercent = 42,
            location = FmPoint(
                latitude = 37.7840,
                longitude = -122.4010,
                accuracyMeters = 240.0,
                timestampMs = PREVIEW_NOW_MILLIS - 43 * 60_000,
                address = "500 Howard St, San Francisco, CA",
            ),
            deviceClass = "Mac",
        ),
        FmDeviceUi(id = "d3", name = "Apple Watch", batteryPercent = null, location = null),
    )

    private val friends = listOf(
        FmFriendUi(
            id = "f1", name = "Mom", address = "mom@icloud.com",
            location = FmPoint(
                latitude = 37.7699,
                longitude = -122.4269,
                accuracyMeters = 18.0,
                timestampMs = PREVIEW_NOW_MILLIS - 8 * 60_000,
                address = "Haight St, San Francisco, CA",
            ),
        ),
        FmFriendUi(id = "f2", name = "+1 (555) 010-9999", location = null, locating = true),
    )

    private val items = listOf(
        FmItemUi(
            id = "i1", name = "Keys", emoji = "🔑", batteryPercent = 88,
            location = FmPoint(
                latitude = 37.7799,
                longitude = -122.4150,
                accuracyMeters = 12.0,
                timestampMs = PREVIEW_NOW_MILLIS - 60_000,
                address = "Union Square, San Francisco, CA",
            ),
        ),
        FmItemUi(
            id = "i2", name = "Backpack", emoji = "🎒", sharedBy = "mom@icloud.com",
            location = FmPoint(
                latitude = 37.7660,
                longitude = -122.4100,
                accuracyMeters = 400.0,
                timestampMs = PREVIEW_NOW_MILLIS - 3 * 3_600_000,
            ),
        ),
    )

    override suspend fun devices() = devices
    override suspend fun refreshDevices(): List<FmDeviceUi> {
        if (failRefresh) error("offline")
        return devices
    }

    override suspend fun friends() = friends
    override suspend fun refreshFriends(): List<FmFriendUi> {
        if (failRefresh) error("offline")
        return friends
    }

    override suspend fun items() = items
    override suspend fun refreshItems(): List<FmItemUi> {
        if (failRefresh) error("offline")
        return items
    }
}

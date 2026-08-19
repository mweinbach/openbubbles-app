package app.openbubbles.nativeapp.ui.findmy

import app.openbubbles.nativeapp.data.UiContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UFmDevice
import uniffi.rust_lib_bluebubbles.UFmFriend
import uniffi.rust_lib_bluebubbles.UFmItem
import uniffi.rust_lib_bluebubbles.UFmLocation
import uniffi.rust_lib_bluebubbles.UFmReport
import kotlin.math.roundToInt

/** One location fix, normalized for display. */
data class FmPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null,
    val timestampMs: Long? = null,
)

/** A Find My device (this account's iPhone, Mac, Watch…). */
data class FmDeviceUi(
    val id: String,
    val name: String,
    val model: String? = null,
    val batteryPercent: Int? = null,
    val batteryStatus: String? = null,
    val location: FmPoint? = null,
    val lostModeCapable: Boolean = false,
    val lostModeEnabled: Boolean = false,
)

/** A friend this account follows (Find My friends). */
data class FmFriendUi(
    val id: String,
    val name: String,
    val address: String? = null,
    val location: FmPoint? = null,
)

/** A Find My item (AirTag / accessory, own or shared). */
data class FmItemUi(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val model: String? = null,
    val batteryPercent: Int? = null,
    val sharedBy: String? = null,
    val location: FmPoint? = null,
)

/**
 * Data seam for the FindMy screen. Getters return the last known data
 * (offline-capable); refresh functions hit Apple and return the new data.
 */
interface FindMyPort {
    fun isAvailable(): Boolean
    suspend fun devices(): List<FmDeviceUi>
    suspend fun refreshDevices(): List<FmDeviceUi>
    suspend fun friends(): List<FmFriendUi>
    suspend fun refreshFriends(): List<FmFriendUi>
    suspend fun items(): List<FmItemUi>
    suspend fun refreshItems(): List<FmItemUi>
}

internal object FindMyMapping {
    fun device(raw: UFmDevice, index: Int): FmDeviceUi {
        val model = raw.modelDisplayName ?: raw.rawDeviceModel ?: raw.deviceModel
        val name = raw.name ?: raw.deviceDisplayName ?: model ?: "Device ${index + 1}"
        return FmDeviceUi(
            id = raw.id ?: raw.baUuid ?: name,
            name = name,
            model = model,
            batteryPercent = batteryPercent(raw.batteryLevel),
            batteryStatus = raw.batteryStatus,
            location = point(raw.location),
            lostModeCapable = raw.lostModeCapable == true,
            lostModeEnabled = raw.lostModeEnabled == true,
        )
    }

    suspend fun friend(raw: UFmFriend): FmFriendUi {
        val address = (raw.invitationAcceptedHandles + raw.invitationFromHandles).firstOrNull()
        val contactName = address?.let { addr ->
            runCatching { UiContacts.contactNames?.invoke(addr)?.first }.getOrNull()
        }
        return FmFriendUi(
            id = raw.id,
            name = contactName?.takeIf { it.isNotBlank() } ?: address ?: "Friend",
            address = address,
            location = point(raw.lastLocation),
        )
    }

    fun item(raw: UFmItem): FmItemUi = FmItemUi(
        id = raw.id.ifBlank { raw.associatedBeacon }.ifBlank { "item" },
        name = raw.name.ifBlank { raw.model }.ifBlank { "Item" },
        emoji = raw.emoji.takeIf { it.isNotBlank() },
        model = raw.model.takeIf { it.isNotBlank() },
        batteryPercent = raw.batteryLevel?.toInt()?.coerceIn(0, 100),
        sharedBy = raw.ownerHandle,
        location = report(raw.lastReport),
    )

    fun point(location: UFmLocation?): FmPoint? {
        if (location == null) return null
        return FmPoint(
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyMeters = location.horizontalAccuracy,
            timestampMs = epochMs(location.timestamp) ?: epochMs(location.secureLocationTs)
                ?: epochMs(location.locationTimestamp),
        )
    }

    fun report(report: UFmReport?): FmPoint? {
        if (report == null) return null
        return FmPoint(
            latitude = report.lat.toDouble(),
            longitude = report.long.toDouble(),
            accuracyMeters = report.horizontalAccuracy.toDouble(),
            timestampMs = report.timestampMs.toLong(),
        )
    }

    fun batteryPercent(level: Double?): Int? {
        if (level == null) return null
        return if (level in 0.0..1.5) {
            (level * 100).roundToInt().coerceIn(0, 100)
        } else {
            level.roundToInt().coerceIn(0, 100)
        }
    }

    fun epochMs(value: Long?): Long? {
        if (value == null || value <= 0L) return null
        return if (value < 100_000_000_000L) value * 1000L else value
    }
}

class RustFindMyPort(
    private val stateProvider: () -> NativePushState?,
) : FindMyPort {

    override fun isAvailable(): Boolean = stateProvider() != null

    override suspend fun devices(): List<FmDeviceUi> =
        mapIndexed { state -> state.getDevices() }

    override suspend fun refreshDevices(): List<FmDeviceUi> =
        mapIndexed { state -> state.refreshDevices() }

    override suspend fun friends(): List<FmFriendUi> =
        mapFriends { state -> state.getFollowing() }

    override suspend fun refreshFriends(): List<FmFriendUi> =
        mapFriends { state -> state.refreshFollowing() }

    override suspend fun items(): List<FmItemUi> {
        val state = requireState()
        val raw = runInterruptible(Dispatchers.IO) { state.getCachedBeaconItems() }
        return raw.map(FindMyMapping::item)
    }

    override suspend fun refreshItems(): List<FmItemUi> {
        val state = requireState()
        val raw = runInterruptible(Dispatchers.IO) { state.getBeaconItems() }
        return raw.map(FindMyMapping::item)
    }

    private fun requireState(): NativePushState =
        stateProvider() ?: error("not connected")

    private suspend fun mapIndexed(load: (NativePushState) -> List<UFmDevice>): List<FmDeviceUi> {
        val state = requireState()
        val raw = runInterruptible(Dispatchers.IO) { load(state) }
        return raw.mapIndexed { index, device -> FindMyMapping.device(device, index) }
    }

    private suspend fun mapFriends(load: (NativePushState) -> List<UFmFriend>): List<FmFriendUi> {
        val state = requireState()
        val raw = runInterruptible(Dispatchers.IO) { load(state) }
        return raw.map { FindMyMapping.friend(it) }
    }
}

class FakeFindMyPort(
    private val failRefresh: Boolean = false,
) : FindMyPort {
    override fun isAvailable() = true

    private val devices = listOf(
        FmDeviceUi(
            id = "d1", name = "Max's iPhone", model = "iPhone 16 Pro",
            batteryPercent = 78, batteryStatus = "Charging",
            location = FmPoint(37.7749, -122.4194, 65.0, System.currentTimeMillis() - 120_000),
        ),
        FmDeviceUi(
            id = "d2", name = "MacBook Pro", model = "MacBook Pro 14\"",
            batteryPercent = 42,
            location = FmPoint(37.3349, -122.0090, 240.0, System.currentTimeMillis() - 43 * 60_000),
            lostModeCapable = true,
            lostModeEnabled = true,
        ),
        FmDeviceUi(id = "d3", name = "Apple Watch", batteryPercent = null, location = null),
    )

    private val friends = listOf(
        FmFriendUi(
            id = "f1", name = "Mom", address = "mom@icloud.com",
            location = FmPoint(40.7128, -74.0060, 18.0, System.currentTimeMillis() - 8 * 60_000),
        ),
        FmFriendUi(id = "f2", name = "+1 (555) 010-9999", location = null),
    )

    private val items = listOf(
        FmItemUi(
            id = "i1", name = "Keys", emoji = "🔑", batteryPercent = 88,
            location = FmPoint(37.7799, -122.4150, 12.0, System.currentTimeMillis() - 60_000),
        ),
        FmItemUi(
            id = "i2", name = "Backpack", emoji = "🎒", sharedBy = "mom@icloud.com",
            location = FmPoint(37.7700, -122.4100, 400.0, System.currentTimeMillis() - 3 * 3_600_000),
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

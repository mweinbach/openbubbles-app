package app.openbubbles.nativeapp.ui.findmy

import app.openbubbles.nativeapp.data.UiContacts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runInterruptible
import uniffi.rust_lib_bluebubbles.NativePushState
import java.lang.reflect.Method
import kotlin.math.roundToInt

/**
 * UI-facing FindMy models. The screen and view model depend ONLY on these +
 * [FindMyPort]; the Rust adapter below maps whatever the uniffi bindings
 * expose into them.
 *
 * INTEGRATOR NOTE (rust bindings):
 * The intended native surface is, on `NativePushState`:
 *   getDevices(): List<UFmDevice>,  refreshDevices()
 *   getFollowing(): List<UFmFriend>, refreshFollowing()
 *   getBeaconItems(): List<UFmItem>
 * (fields mirror the rustpush `findmy` structs: FoundDevice / Follow /
 * Location / LocationReport / DartBeacon). Until those bindings are
 * regenerated into
 * android/app/src/main/kotlin/uniffi/rust_lib_bluebubbles/, this file does
 * NOT reference them at compile time. [RustFindMyPort] bridges via
 * reflection on the exact method names above, guarded by runCatching — so
 * the screen compiles and runs today (sections surface "unavailable") and
 * lights up without any code change once the bindings land. To swap to
 * direct calls later, replace only [RustFindMyPort].
 */

/** One location fix, normalized for display. */
data class FmPoint(
    val latitude: Double,
    val longitude: Double,
    /** Fix radius in meters, when known. */
    val accuracyMeters: Double? = null,
    /** Fix time in epoch milliseconds, when known. */
    val timestampMs: Long? = null,
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
)

/** A friend this account follows (Find My friends). */
data class FmFriendUi(
    val id: String,
    val name: String,
    /** First share-acceptance handle (contact resolution key). */
    val address: String? = null,
    val location: FmPoint? = null,
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
 * Bridges the uniffi `NativePushState` FindMy methods into [FindMyPort] via
 * reflection (see the integrator note at the top of this file). Every call
 * is guarded so a missing/incompatible binding degrades to an error state
 * instead of crashing.
 */
class RustFindMyPort(
    private val stateProvider: () -> NativePushState?,
) : FindMyPort {

    override fun isAvailable(): Boolean = stateProvider() != null

    override suspend fun devices(): List<FmDeviceUi> =
        mapList("getDevices") { index, element -> mapDevice(element, index) }

    override suspend fun refreshDevices(): List<FmDeviceUi> {
        val state = requireState()
        val raw = runInterruptible(Dispatchers.IO) {
            val result = runCatching { invoke(state, "refreshDevices") }
            // refreshDevices() may return the refreshed list directly; when
            // it doesn't (or fails to), fall back to the cached getter.
            (result.getOrNull() as? List<*>) ?: invokeList(state, "getDevices")
        }
        return raw.withIndex().map { (index, element) -> mapDevice(element, index) }
    }

    override suspend fun friends(): List<FmFriendUi> =
        mapList("getFollowing") { _, element -> mapFriend(element) }

    override suspend fun refreshFriends(): List<FmFriendUi> {
        val state = requireState()
        val raw = runInterruptible(Dispatchers.IO) {
            val result = runCatching { invoke(state, "refreshFollowing") }
            (result.getOrNull() as? List<*>) ?: invokeList(state, "getFollowing")
        }
        val out = ArrayList<FmFriendUi>(raw.size)
        for (element in raw) out.add(mapFriend(element))
        return out
    }

    override suspend fun items(): List<FmItemUi> =
        mapList("getCachedBeaconItems") { _, element -> mapItem(element) }

    override suspend fun refreshItems(): List<FmItemUi> {
        val state = requireState()
        val raw = runInterruptible(Dispatchers.IO) {
            invokeList(state, "getBeaconItems")
        }
        return raw.map { mapItem(it) }
    }

    // ------------------------------------------------------------------
    // Reflection plumbing
    // ------------------------------------------------------------------

    private fun requireState(): NativePushState =
        stateProvider() ?: error("not connected")

    private suspend fun <T> mapList(
        getter: String,
        mapper: suspend (Int, Any?) -> T,
    ): List<T> {
        val state = requireState()
        val raw = runInterruptible(Dispatchers.IO) { invokeList(state, getter) }
        val out = ArrayList<T>(raw.size)
        for (index in raw.indices) out.add(mapper(index, raw[index]))
        return out
    }

    /** Invokes a no-arg method and casts the result to a list (empty on null). */
    private fun invokeList(state: NativePushState, name: String): List<*> {
        val result = invoke(state, name)
        return result as? List<*> ?: emptyList<Any>()
    }

    private fun invoke(state: NativePushState, name: String): Any? {
        val method = findMethod(state.javaClass, name)
            ?: throw NoSuchMethodException(
                "NativePushState.$name — FindMy bindings not present (regen uniffi)",
            )
        return method.invoke(state)
    }

    private fun findMethod(cls: Class<*>, name: String): Method? =
        cls.methods.firstOrNull { it.name == name && it.parameterCount == 0 }

    /** Reads a property (getter first, field fallback) — null on any miss. */
    private fun Any?.prop(name: String): Any? {
        if (this == null) return null
        val cap = name.replaceFirstChar { it.uppercase() }
        val value = runCatching {
            val getter = listOf("get$cap", "is$cap", name)
                .asSequence()
                .mapNotNull { runCatching { javaClass.getMethod(it) }.getOrNull() }
                .firstOrNull()
            if (getter != null) {
                getter.invoke(this)
            } else {
                val field = javaClass.declaredFields.firstOrNull { it.name == name }
                field?.isAccessible = true
                field?.get(this)
            }
        }.getOrNull()
        return value
    }

    private fun Any?.asString(): String? = (this as? String)?.takeIf { it.isNotBlank() }

    private fun Any?.asDouble(): Double? = when (this) {
        is Double -> this
        is Float -> toDouble()
        is Number -> toDouble()
        else -> null
    }

    /** Epoch-ms normalization: FindMy mixes ms timestamps and SystemTime seconds. */
    private fun Any?.asEpochMs(): Long? {
        val value = when (this) {
            is Long -> this
            is java.math.BigInteger -> toLong()
            is Number -> toLong()
            else -> return null
        }
        return if (value > 0 && value < 100_000_000_000L) value * 1000L else value
    }

    private fun Any?.toListOfStrings(): List<String> =
        (this as? List<*>)?.mapNotNull { it as? String }.orEmpty()

    // ------------------------------------------------------------------
    // Struct mappers (defensive: unknown/renamed fields fall back to null)
    // ------------------------------------------------------------------

    /** rustpush `Location` (FMF) or `LocationReport` (beacons) shaped object. */
    private fun mapPoint(raw: Any?): FmPoint? {
        if (raw == null) return null
        val latitude = raw.prop("latitude").asDouble() ?: raw.prop("lat").asDouble()
        val longitude = raw.prop("longitude").asDouble() ?: raw.prop("long").asDouble()
        if (latitude == null || longitude == null) return null
        return FmPoint(
            latitude = latitude,
            longitude = longitude,
            accuracyMeters = raw.prop("horizontalAccuracy").asDouble(),
            timestampMs = raw.prop("timestamp").asEpochMs()
                ?: raw.prop("timestampMs").asEpochMs()
                ?: raw.prop("secureLocationTs").asEpochMs(),
        )
    }

    /** rustpush `FoundDevice` shaped object. */
    private fun mapDevice(raw: Any?, index: Int): FmDeviceUi {
        val model = raw.prop("modelDisplayName").asString()
            ?: raw.prop("rawDeviceModel").asString()
            ?: raw.prop("deviceModel").asString()
        val name = raw.prop("name").asString()
            ?: raw.prop("deviceDisplayName").asString()
            ?: model
            ?: "Device ${index + 1}"
        // FoundDevice.batteryLevel is a 0..1 fraction.
        val batteryFraction = raw.prop("batteryLevel").asDouble()
        val batteryPercent = batteryFraction
            ?.takeIf { it >= 0.0 && it <= 1.5 }
            ?.let { (it * 100).roundToInt().coerceIn(0, 100) }
            ?: batteryFraction?.roundToInt()?.coerceIn(0, 100)
        return FmDeviceUi(
            id = raw.prop("id").asString() ?: raw.prop("baUUID").asString() ?: name,
            name = name,
            model = model,
            batteryPercent = batteryPercent,
            batteryStatus = raw.prop("batteryStatus").asString(),
            location = mapPoint(raw.prop("location")),
        )
    }

    /** rustpush `Follow` shaped object (name resolved via contacts). */
    private suspend fun mapFriend(raw: Any?): FmFriendUi {
        val id = raw.prop("id").asString() ?: "friend"
        val address = (
            raw.prop("invitationAcceptedHandles").toListOfStrings() +
                raw.prop("invitationFromHandles").toListOfStrings()
            ).firstOrNull()
        val contactName = address?.let { addr ->
            runCatching { UiContacts.contactNames?.invoke(addr)?.first }.getOrNull()
        }
        return FmFriendUi(
            id = id,
            name = contactName?.takeIf { it.isNotBlank() } ?: address ?: "Friend",
            address = address,
            location = mapPoint(raw.prop("lastLocation") ?: raw.prop("location")),
        )
    }

    /** rustpush `DartBeacon` shaped object (own or shared item). */
    private fun mapItem(raw: Any?): FmItemUi {
        val naming = raw.prop("naming")
        val id = raw.prop("id").asString() ?: naming.prop("associatedBeacon").asString() ?: "item"
        val model = raw.prop("model").asString()
        val name = naming.prop("name").asString() ?: model ?: "Item"
        return FmItemUi(
            id = id,
            name = name,
            emoji = naming.prop("emoji").asString(),
            model = model,
            batteryPercent = raw.prop("batteryLevel").asDouble()?.roundToInt(),
            sharedBy = raw.prop("shared").prop("ownerHandle").asString(),
            location = mapPoint(raw.prop("lastReport")),
        )
    }
}

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
            location = FmPoint(37.7749, -122.4194, 65.0, PREVIEW_NOW_MILLIS - 120_000),
        ),
        FmDeviceUi(
            id = "d2", name = "MacBook Pro", model = "MacBook Pro 14\"",
            batteryPercent = 42,
            location = FmPoint(37.3349, -122.0090, 240.0, PREVIEW_NOW_MILLIS - 43 * 60_000),
        ),
        FmDeviceUi(id = "d3", name = "Apple Watch", batteryPercent = null, location = null),
    )

    private val friends = listOf(
        FmFriendUi(
            id = "f1", name = "Mom", address = "mom@icloud.com",
            location = FmPoint(40.7128, -74.0060, 18.0, PREVIEW_NOW_MILLIS - 8 * 60_000),
        ),
        FmFriendUi(id = "f2", name = "+1 (555) 010-9999", location = null),
    )

    private val items = listOf(
        FmItemUi(
            id = "i1", name = "Keys", emoji = "🔑", batteryPercent = 88,
            location = FmPoint(37.7799, -122.4150, 12.0, PREVIEW_NOW_MILLIS - 60_000),
        ),
        FmItemUi(
            id = "i2", name = "Backpack", emoji = "🎒", sharedBy = "mom@icloud.com",
            location = FmPoint(37.7700, -122.4100, 400.0, PREVIEW_NOW_MILLIS - 3 * 3_600_000),
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

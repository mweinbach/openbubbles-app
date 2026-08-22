package app.openbubbles.nativeapp.ui.findmy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import uniffi.rust_lib_bluebubbles.UFmAddress
import uniffi.rust_lib_bluebubbles.UFmFriend
import uniffi.rust_lib_bluebubbles.UFmItem
import uniffi.rust_lib_bluebubbles.UFmLocation
import uniffi.rust_lib_bluebubbles.UFmReport

class FindMyMappingTest {
    @Test
    fun `friend location and Apple supplied address survive the UniFFI mapping`() {
        val friend = UFmFriend(
            createTimestamp = 0,
            expires = 0,
            id = "friend-id",
            invitationAcceptedHandles = listOf("person@icloud.com"),
            invitationFromHandles = emptyList(),
            isFromMessages = false,
            offerId = null,
            onlyInEvent = false,
            personIdHash = "hash",
            secureLocationsCapable = true,
            shallowOrLiveSecureLocationsCapable = true,
            source = "icloud",
            tkPermission = true,
            updateTimestamp = 0,
            fallbackToLegacyAllowed = null,
            optedNotToShare = false,
            lastLocation = UFmLocation(
                address = UFmAddress(
                    administrativeArea = "NY",
                    country = "United States",
                    countryCode = "US",
                    formattedAddressLines = listOf("Jennings Rd", "Lloyd Harbor, NY"),
                    locality = "Lloyd Harbor",
                    stateCode = "NY",
                    streetAddress = null,
                    streetName = "Jennings Rd",
                ),
                altitude = 0.0,
                floorLevel = 0,
                horizontalAccuracy = 18.0,
                isInaccurate = false,
                latitude = 40.903,
                locationId = "location-id",
                locationTimestamp = null,
                longitude = -73.459,
                secureLocationTs = 0,
                timestamp = 781_692_800_000,
                verticalAccuracy = 0.0,
                positionType = null,
                isOld = false,
                locationFinished = true,
            ),
            locateInProgress = false,
        )

        val mapped = friend.toUi(contactName = "Taylor")

        assertEquals("Taylor", mapped.name)
        assertEquals("person@icloud.com", mapped.address)
        assertEquals(40.903, mapped.location?.latitude)
        assertEquals(-73.459, mapped.location?.longitude)
        assertEquals("Jennings Rd, Lloyd Harbor, NY", mapped.location?.address)
        assertEquals(1_760_000_000_000, mapped.location?.timestampMs)
        assertTrue(findMyTargets(emptyList(), listOf(mapped), emptyList()).single().located)
    }

    @Test
    fun `Find My timestamps normalize Apple milliseconds and Unix seconds`() {
        assertEquals(1_760_000_000_000, normalizeFindMyTimestamp(781_692_800_000))
        assertEquals(1_760_000_000_000, normalizeFindMyTimestamp(1_760_000_000))
        assertEquals(1_760_000_000_000, normalizeFindMyTimestamp(1_760_000_000_000))
    }

    @Test
    fun `flattened beacon fields map without reflective nesting assumptions`() {
        val item = UFmItem(
            emoji = "tag",
            name = "Backpack",
            associatedBeacon = "beacon-id",
            roleId = 1,
            lastReport = UFmReport(
                lat = 37.5f,
                long = -122.25f,
                horizontalAccuracy = 12u,
                status = 0u,
                confidence = 0u,
                timestampMs = 1_760_000_000_000uL,
                keyIndex = 3uL,
            ),
            productId = 0,
            batteryLevel = 82,
            vendorId = 0,
            model = "Accessory",
            systemVersion = "1",
            id = "record-id",
            shareId = "share-id",
            acceptanceState = 1,
            ownerHandle = "owner",
        )

        assertEquals(
            FmItemUi(
                id = "record-id",
                name = "Backpack",
                emoji = "tag",
                model = "Accessory",
                batteryPercent = 82,
                sharedBy = "owner",
                location = FmPoint(
                    latitude = 37.5,
                    longitude = -122.25,
                    accuracyMeters = 12.0,
                    timestampMs = 1_760_000_000_000,
                ),
            ),
            item.toUi(),
        )
    }

    @Test
    fun `beacon mapping falls back to flattened associated id and model`() {
        val item = UFmItem(
            emoji = "",
            name = "",
            associatedBeacon = "beacon-id",
            roleId = 0,
            lastReport = null,
            productId = 0,
            batteryLevel = 140,
            vendorId = 0,
            model = "Tracker",
            systemVersion = "",
            id = "",
            shareId = null,
            acceptanceState = null,
            ownerHandle = "",
        )

        assertEquals("beacon-id", item.toUi().id)
        assertEquals("Tracker", item.toUi().name)
        assertEquals(100, item.toUi().batteryPercent)
    }
}

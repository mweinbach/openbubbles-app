package app.openbubbles.nativeapp.ui.findmy

import kotlin.test.Test
import kotlin.test.assertEquals
import uniffi.rust_lib_bluebubbles.UFmItem
import uniffi.rust_lib_bluebubbles.UFmReport

class FindMyMappingTest {
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

package app.openbubbles.nativeapp.ui.findmy

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val NOW = 1_760_000_000_000L

/** The flattened tracking list the map and the list both read. */
class FindMyTargetsTest {
    private fun device(id: String, name: String, point: FmPoint? = null, thisDevice: Boolean = false) =
        FmDeviceUi(id = id, name = name, location = point, thisDevice = thisDevice)

    @Test
    fun `devices then people then items`() {
        val targets = findMyTargets(
            devices = listOf(device("d", "Phone")),
            friends = listOf(FmFriendUi(id = "f", name = "Mom")),
            items = listOf(FmItemUi(id = "i", name = "Keys")),
        )
        assertEquals(
            listOf(FmTargetKind.Device, FmTargetKind.Friend, FmTargetKind.Item),
            targets.map { it.kind },
        )
        assertEquals(listOf("device:d", "friend:f", "item:i"), targets.map { it.id })
    }

    @Test
    fun `located targets come before unlocated ones, freshest first`() {
        val targets = findMyTargets(
            devices = listOf(
                device("stale", "Stale", FmPoint(1.0, 1.0, timestampMs = NOW - 3_600_000)),
                device("none", "Unlocated"),
                device("fresh", "Fresh", FmPoint(2.0, 2.0, timestampMs = NOW - 60_000)),
            ),
            friends = emptyList(),
            items = emptyList(),
        )
        assertEquals(listOf("Fresh", "Stale", "Unlocated"), targets.map { it.name })
    }

    @Test
    fun `equally fresh targets keep a stable alphabetical order`() {
        val point = FmPoint(1.0, 1.0, timestampMs = NOW)
        val first = findMyTargets(
            devices = listOf(device("b", "Beta", point), device("a", "alpha", point)),
            friends = emptyList(),
            items = emptyList(),
        )
        val again = findMyTargets(
            devices = listOf(device("a", "alpha", point), device("b", "Beta", point)),
            friends = emptyList(),
            items = emptyList(),
        )
        assertEquals(listOf("alpha", "Beta"), first.map { it.name })
        assertEquals(first.map { it.id }, again.map { it.id })
    }

    @Test
    fun `every kind carries the metadata its row and pin need`() {
        val targets = findMyTargets(
            devices = listOf(
                FmDeviceUi(
                    id = "d",
                    name = "Phone",
                    model = "iPhone 16",
                    deviceClass = "Watch",
                    batteryPercent = 80,
                    batteryStatus = "Charging",
                    location = FmPoint(1.0, 2.0, 30.0, NOW, address = "1 Market St"),
                    lostModeEnabled = true,
                    thisDevice = true,
                ),
            ),
            friends = listOf(
                FmFriendUi(id = "f", name = "Mom", address = "mom@icloud.com", locating = true),
            ),
            items = listOf(
                FmItemUi(id = "i", name = "Keys", emoji = "🔑", batteryPercent = 50, sharedBy = "dad@icloud.com"),
            ),
        )
        val (deviceTarget, friendTarget, itemTarget) = targets
        assertTrue(deviceTarget.located && deviceTarget.lostMode && deviceTarget.thisDevice)
        assertEquals("Watch", deviceTarget.deviceClass)
        assertEquals("1 Market St", deviceTarget.point?.address)
        assertEquals("mom@icloud.com", friendTarget.address)
        assertTrue(friendTarget.locating)
        assertFalse(friendTarget.located)
        assertEquals("🔑", itemTarget.emoji)
        assertEquals("dad@icloud.com", itemTarget.sharedBy)
    }

    @Test
    fun `ids are namespaced so the three services cannot collide`() {
        val targets = findMyTargets(
            devices = listOf(device("same", "Device")),
            friends = listOf(FmFriendUi(id = "same", name = "Friend")),
            items = listOf(FmItemUi(id = "same", name = "Item")),
        )
        assertEquals(3, targets.map { it.id }.distinct().size)
    }
}

class FindMyPaneSplitTest {
    @Test
    fun `separating hinge becomes the exact pane gutter`() {
        val split = findMyPaneSplit(
            containerWidthDp = 900f,
            hingeLeftDp = 430f,
            hingeRightDp = 450f,
        )

        assertTrue(split.usesHinge)
        assertEquals(430f, split.panelWidthDp)
        assertEquals(20f, split.gutterWidthDp)
    }

    @Test
    fun `edge hinge falls back to the regular split`() {
        val split = findMyPaneSplit(
            containerWidthDp = 700f,
            hingeLeftDp = 120f,
            hingeRightDp = 140f,
        )

        assertFalse(split.usesHinge)
        assertEquals(380f, split.panelWidthDp)
    }
}

/** Session tracks. */
class FindMyTrailTest {
    @Test
    fun `a new fix extends the track`() {
        val first = FmPoint(1.0, 1.0, timestampMs = NOW - 60_000)
        val second = FmPoint(1.1, 1.1, timestampMs = NOW)
        val trail = appendTrail(appendTrail(emptyList(), first), second)
        assertEquals(listOf(first, second), trail)
    }

    @Test
    fun `refreshing without movement does not extend the track`() {
        val point = FmPoint(1.0, 1.0, timestampMs = NOW)
        val once = appendTrail(emptyList(), point)
        assertEquals(once, appendTrail(once, point.copy()))
    }

    @Test
    fun `an older cached fix cannot reverse a chronological track`() {
        val newest = FmPoint(1.0, 1.0, timestampMs = NOW)
        val older = FmPoint(2.0, 2.0, timestampMs = NOW - 60_000)

        assertEquals(listOf(newest), appendTrail(listOf(newest), older))
    }

    @Test
    fun `a target with no fix has no track`() {
        assertTrue(appendTrail(emptyList(), null).isEmpty())
    }

    @Test
    fun `a screen left open all afternoon keeps a bounded track`() {
        var trail = emptyList<FmPoint>()
        repeat(200) { index ->
            trail = appendTrail(trail, FmPoint(index * 0.01, 0.0, timestampMs = NOW + index))
        }
        assertEquals(FM_TRAIL_LIMIT, trail.size)
        // The newest fixes are the ones kept.
        assertEquals(NOW + 199, trail.last().timestampMs)
    }
}

/** Every string the rows and cards show. */
class FindMyFormattingTest {
    @Test
    fun `freshness reads in the units a person would use`() {
        assertEquals("just now", fixFreshness(NOW - 5_000, NOW))
        assertEquals("7 min ago", fixFreshness(NOW - 7 * 60_000, NOW))
        assertEquals("3 h ago", fixFreshness(NOW - 3 * 3_600_000, NOW))
        assertNull(fixFreshness(null, NOW))
        assertNull(fixFreshness(0L, NOW))
    }

    @Test
    fun `a fix stamped slightly in the future is not described as old`() {
        assertEquals("just now", fixFreshness(NOW + 5_000, NOW))
    }

    @Test
    fun `a days-old fix falls back to a date`() {
        val label = fixFreshness(NOW - 5L * 24 * 3_600_000, NOW)
        assertTrue(label!!.contains("/"), "expected a date, got $label")
    }

    @Test
    fun `accuracy and distance switch to kilometres`() {
        assertEquals("18 m", fixAccuracy(18.0))
        assertEquals("1.2 km", fixAccuracy(1_240.0))
        assertNull(fixAccuracy(0.0))
        assertNull(fixAccuracy(null))
        assertEquals("120 m away", distanceLabel(120.0))
        assertEquals("4.3 km away", distanceLabel(4_320.0))
    }

    @Test
    fun `staleness has a threshold, not a guess`() {
        assertFalse(isStaleFix(FmPoint(1.0, 1.0, timestampMs = NOW - 60_000), NOW))
        assertTrue(isStaleFix(FmPoint(1.0, 1.0, timestampMs = NOW - FM_STALE_FIX_MS - 1), NOW))
        assertFalse(isStaleFix(null, NOW))
    }

    @Test
    fun `a summary orders battery, freshness and accuracy the same way every row`() {
        val target = FmTarget(
            id = "device:d",
            kind = FmTargetKind.Device,
            name = "Phone",
            point = FmPoint(1.0, 1.0, 120.0, NOW - 2 * 60_000),
            batteryPercent = 78,
            batteryStatus = "Charging",
            thisDevice = true,
        )
        assertEquals("This device · 78% · Charging · 2 min ago · 120 m", targetSummary(target, NOW))
    }

    @Test
    fun `an unlocated target says so, and says when it is being located`() {
        val target = FmTarget(id = "friend:f", kind = FmTargetKind.Friend, name = "Mom")
        assertEquals("No location", targetSummary(target, NOW))
        assertEquals("Locating…", targetSummary(target.copy(locating = true), NOW))
    }

    @Test
    fun `lost mode and sharing are called out`() {
        val device = FmTarget(
            id = "device:d",
            kind = FmTargetKind.Device,
            name = "Phone",
            point = FmPoint(1.0, 1.0, timestampMs = NOW),
            lostMode = true,
        )
        assertTrue(targetSummary(device, NOW).endsWith("Lost Mode"))
        val item = FmTarget(
            id = "item:i",
            kind = FmTargetKind.Item,
            name = "Keys",
            point = FmPoint(1.0, 1.0, timestampMs = NOW),
            sharedBy = "mom@icloud.com",
        )
        assertTrue(targetSummary(item, NOW).endsWith("shared by mom@icloud.com"))
    }
}

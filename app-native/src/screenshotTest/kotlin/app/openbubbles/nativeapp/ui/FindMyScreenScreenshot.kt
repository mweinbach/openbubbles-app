package app.openbubbles.nativeapp.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import app.openbubbles.nativeapp.ui.findmy.FakeFindMyPort
import app.openbubbles.nativeapp.ui.findmy.FindMyScreen
import app.openbubbles.nativeapp.ui.findmy.FindMyUiState
import app.openbubbles.nativeapp.ui.findmy.FmPoint
import app.openbubbles.nativeapp.ui.theme.OpenBubblesTheme
import com.android.tools.screenshot.PreviewTest
import kotlinx.coroutines.runBlocking

/**
 * Find My tracker fixtures.
 *
 * The renderer has no network, so these show the map's offline behaviour on
 * purpose: pins, accuracy circles, this session's track, and the scale bar are
 * all drawn from the projection, with the tile graticule standing in for
 * imagery. That is exactly the state a user sees with imagery turned off.
 */
private const val FIXED_NOW = 1_760_000_000_000L

private fun trackerState(
    selectedTargetId: String? = null,
    liveUpdates: Boolean = true,
    refreshErrors: List<String> = emptyList(),
): FindMyUiState {
    val port = FakeFindMyPort()
    val state = FindMyUiState(
        loading = false,
        devices = runBlocking { port.devices() },
        friends = runBlocking { port.friends() },
        items = runBlocking { port.items() },
        selectedTargetId = selectedTargetId,
        liveUpdates = liveUpdates,
        refreshErrors = refreshErrors,
        lastUpdatedAtMs = FIXED_NOW - 20_000,
    )
    return state.copy(
        trails = mapOf(
            "device:d1" to listOf(
                FmPoint(37.7712, -122.4260, 40.0, FIXED_NOW - 12 * 60_000),
                FmPoint(37.7731, -122.4223, 38.0, FIXED_NOW - 8 * 60_000),
                FmPoint(37.7749, -122.4194, 65.0, FIXED_NOW - 2 * 60_000),
            ),
        ),
    )
}

@PreviewTest
@Preview(name = "findmy-map", device = Devices.PHONE, showBackground = true)
@Preview(
    name = "findmy-map-dark",
    device = Devices.PHONE,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun FindMyTrackerScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        FindMyScreen(
            uiState = trackerState(),
            onRefresh = {},
            onBack = {},
            nowMillis = FIXED_NOW,
        )
    }
}

/** A selected target: detail card with address, distance, and its actions. */
@PreviewTest
@Preview(name = "findmy-selected", device = Devices.PHONE, showBackground = true)
@Composable
fun FindMySelectedScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        FindMyScreen(
            uiState = trackerState(selectedTargetId = "item:i1"),
            onRefresh = {},
            onBack = {},
            nowMillis = FIXED_NOW,
        )
    }
}

/** People is the default section and a selected friend's real fix remains visible on the map. */
@PreviewTest
@Preview(name = "findmy-people-location", device = Devices.PHONE, showBackground = true)
@Composable
fun FindMyPeopleLocationScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        FindMyScreen(
            uiState = trackerState(selectedTargetId = "friend:f1"),
            onRefresh = {},
            onBack = {},
            nowMillis = FIXED_NOW,
        )
    }
}

/** Paused tracking after a failed refresh: last known locations stay on the map. */
@PreviewTest
@Preview(name = "findmy-offline", device = Devices.PHONE, showBackground = true)
@Composable
fun FindMyOfflineScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        FindMyScreen(
            uiState = trackerState(
                liveUpdates = false,
                refreshErrors = listOf("Devices: offline"),
            ),
            onRefresh = {},
            onBack = {},
            nowMillis = FIXED_NOW,
        )
    }
}

/** Wide window: the list and the map are panes, never stacked chrome. */
@PreviewTest
@Preview(name = "findmy-expanded", widthDp = 1280, heightDp = 800, showBackground = true)
@Composable
fun FindMyExpandedScreenshot() {
    OpenBubblesTheme(dynamicColor = false) {
        FindMyScreen(
            uiState = trackerState(selectedTargetId = "device:d1"),
            onRefresh = {},
            onBack = {},
            nowMillis = FIXED_NOW,
        )
    }
}

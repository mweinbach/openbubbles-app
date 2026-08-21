package app.openbubbles.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.test.uiautomator.UiAutomatorTestScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until

internal const val TARGET_PACKAGE = "com.openbubbles.messaging"
private const val TARGET_ACTIVITY = "app.openbubbles.nativeapp.NativeMainActivity"
private const val INITIAL_ROUTE = "initial_route"
private const val CHATS_ROUTE = "chats"
private const val PHOTOS_ROUTE = "photos"
private const val JOURNEY_READY_TIMEOUT_MS = 30_000L

internal enum class OpenBubblesJourney(
    val route: String,
    val readyTag: String,
    val scrollableTag: String,
) {
    Chats(CHATS_ROUTE, "benchmark_chat_list_ready", "benchmark_chat_list_scrollable"),
    Photos(PHOTOS_ROUTE, "benchmark_photos_ready", "benchmark_photos_scrollable"),
}

internal fun UiAutomatorTestScope.launch(journey: OpenBubblesJourney) {
    startActivityIntent(
        Intent(Intent.ACTION_MAIN).apply {
            component = ComponentName(TARGET_PACKAGE, TARGET_ACTIVITY)
            addCategory(Intent.CATEGORY_LAUNCHER)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            // Startup and chat-list coverage use the ordinary launcher path.
            // Only Photos needs the supported explicit-route entry point.
            if (journey != OpenBubblesJourney.Chats) {
                putExtra(INITIAL_ROUTE, journey.route)
            }
        },
    )
    waitForAppToBeVisible(TARGET_PACKAGE)
    waitForStableInActiveWindow()
    check(device.wait(Until.hasObject(By.res(journey.readyTag)), JOURNEY_READY_TIMEOUT_MS)) {
        "${journey.name} did not expose its ready marker"
    }
}

internal fun UiAutomatorTestScope.scrollCurrentJourney(journey: OpenBubblesJourney) {
    // The marker appears only after Compose has laid out enough real content
    // to scroll. This avoids recording an empty/onboarding route or swiping a
    // transient title while the backing list refreshes.
    check(device.wait(Until.hasObject(By.res(journey.scrollableTag)), JOURNEY_READY_TIMEOUT_MS)) {
        "${journey.name} has no populated scrollable content"
    }
    val bounds = checkNotNull(device.findObject(By.res(journey.scrollableTag))).visibleBounds
    val x = bounds.centerX()
    val upperY = bounds.top + bounds.height() * 2 / 10
    val lowerY = bounds.bottom - bounds.height() / 10
    repeat(3) {
        device.swipe(x, lowerY, x, upperY, 12)
    }
    repeat(3) {
        device.swipe(x, upperY, x, lowerY, 12)
    }
    waitForStableInActiveWindow()
}

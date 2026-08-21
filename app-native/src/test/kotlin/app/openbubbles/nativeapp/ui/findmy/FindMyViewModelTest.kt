package app.openbubbles.nativeapp.ui.findmy

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class FindMyViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial refresh starts all sections in parallel`() = runTest(dispatcher) {
        val port = ParallelRefreshPort()
        val model = FindMyViewModel(port)

        runCurrent()

        assertTrue(port.devicesStarted.isCompleted)
        assertTrue(port.friendsStarted.isCompleted)
        assertTrue(port.itemsStarted.isCompleted)
        assertTrue(model.uiState.value.refreshing)

        port.devicesRelease.complete(Unit)
        port.friendsRelease.complete(Unit)
        port.itemsRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(FmDeviceUi("device", "Phone")), model.uiState.value.devices)
        assertEquals(listOf(FmFriendUi("friend", "Friend")), model.uiState.value.friends)
        assertEquals(listOf(FmItemUi("item", "Keys")), model.uiState.value.items)
        assertEquals(1, port.devicesRefreshes)
        assertEquals(1, port.friendsRefreshes)
        assertEquals(1, port.itemsRefreshes)
    }

    // While tracking is live the view model always has a pending timer, so these
    // tests step the virtual clock deliberately and never wait for idle until
    // tracking has been stopped again.

    @Test
    fun `live tracking only runs while the screen is visible`() = runTest(dispatcher) {
        val port = MovingPort()
        val model = FindMyViewModel(port, liveIntervalMs = 1_000L)
        advanceUntilIdle()
        val afterFirst = port.refreshes

        // Not visible: the timer must not be running at all.
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(afterFirst, port.refreshes)

        model.setVisible(true)
        advanceTimeBy(3_100)
        runCurrent()
        assertTrue(port.refreshes >= afterFirst + 3, "expected live refreshes, got ${port.refreshes}")

        model.setVisible(false)
        val whileVisible = port.refreshes
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(whileVisible, port.refreshes, "leaving the screen must stop tracking")
        advanceUntilIdle()
    }

    @Test
    fun `pausing live updates stops the timer without hiding the data`() = runTest(dispatcher) {
        val port = MovingPort()
        val model = FindMyViewModel(port, liveIntervalMs = 1_000L)
        advanceUntilIdle()
        model.setVisible(true)
        advanceTimeBy(2_100)
        runCurrent()
        val running = port.refreshes

        model.setLiveUpdates(false)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(running, port.refreshes)
        assertFalse(model.uiState.value.liveUpdates)
        assertTrue(model.uiState.value.targets.isNotEmpty(), "paused tracking keeps the last data")

        model.setLiveUpdates(true)
        advanceTimeBy(1_100)
        runCurrent()
        assertTrue(port.refreshes > running)

        model.setVisible(false)
        advanceUntilIdle()
    }

    @Test
    fun `each new fix is recorded on the target's track`() = runTest(dispatcher) {
        val port = MovingPort()
        val model = FindMyViewModel(port, liveIntervalMs = 1_000L)
        advanceUntilIdle()
        model.setVisible(true)
        advanceTimeBy(3_100)
        runCurrent()
        model.setVisible(false)
        advanceUntilIdle()

        val trail = model.uiState.value.trail("device:device")
        assertTrue(trail.size >= 3, "expected a track, got ${trail.size} fixes")
        // Oldest first, and strictly moving: no repeated fix was recorded.
        assertEquals(trail.sortedBy { it.timestampMs }, trail)
        assertEquals(trail.distinct(), trail)
    }

    @Test
    fun `an unavailable account never starts tracking`() = runTest(dispatcher) {
        val port = MovingPort(available = false)
        val model = FindMyViewModel(port, liveIntervalMs = 1_000L)
        advanceUntilIdle()
        model.setVisible(true)
        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(0, port.refreshes)
        assertTrue(model.uiState.value.unavailable)
        advanceUntilIdle()
    }

    @Test
    fun `selecting a target is what opens its card`() = runTest(dispatcher) {
        val model = FindMyViewModel(MovingPort(), liveIntervalMs = 1_000L)
        advanceUntilIdle()
        model.select("device:device")
        assertEquals("device:device", model.uiState.value.selectedTarget?.id)
        model.select(null)
        assertNull(model.uiState.value.selectedTarget)
    }
}

/** A port whose device keeps moving, so tracking has something to record. */
private class MovingPort(private val available: Boolean = true) : FindMyPort {
    var refreshes = 0
        private set

    override fun isAvailable(): Boolean = available

    override suspend fun devices(): List<FmDeviceUi> = listOf(device(0))

    override suspend fun refreshDevices(): List<FmDeviceUi> {
        refreshes += 1
        return listOf(device(refreshes))
    }

    override suspend fun friends(): List<FmFriendUi> = emptyList()
    override suspend fun refreshFriends(): List<FmFriendUi> = emptyList()
    override suspend fun items(): List<FmItemUi> = emptyList()
    override suspend fun refreshItems(): List<FmItemUi> = emptyList()

    private fun device(step: Int) = FmDeviceUi(
        id = "device",
        name = "Phone",
        location = FmPoint(
            latitude = 37.0 + step * 0.001,
            longitude = -122.0,
            accuracyMeters = 20.0,
            timestampMs = 1_760_000_000_000L + step * 60_000L,
        ),
    )
}

private class ParallelRefreshPort : FindMyPort {
    val devicesStarted = CompletableDeferred<Unit>()
    val friendsStarted = CompletableDeferred<Unit>()
    val itemsStarted = CompletableDeferred<Unit>()
    val devicesRelease = CompletableDeferred<Unit>()
    val friendsRelease = CompletableDeferred<Unit>()
    val itemsRelease = CompletableDeferred<Unit>()
    var devicesRefreshes = 0
    var friendsRefreshes = 0
    var itemsRefreshes = 0

    override fun isAvailable(): Boolean = true

    override suspend fun devices(): List<FmDeviceUi> = emptyList()

    override suspend fun refreshDevices(): List<FmDeviceUi> {
        devicesRefreshes += 1
        devicesStarted.complete(Unit)
        devicesRelease.await()
        return listOf(FmDeviceUi("device", "Phone"))
    }

    override suspend fun friends(): List<FmFriendUi> = emptyList()

    override suspend fun refreshFriends(): List<FmFriendUi> {
        friendsRefreshes += 1
        friendsStarted.complete(Unit)
        friendsRelease.await()
        return listOf(FmFriendUi("friend", "Friend"))
    }

    override suspend fun items(): List<FmItemUi> = emptyList()

    override suspend fun refreshItems(): List<FmItemUi> {
        itemsRefreshes += 1
        itemsStarted.complete(Unit)
        itemsRelease.await()
        return listOf(FmItemUi("item", "Keys"))
    }
}

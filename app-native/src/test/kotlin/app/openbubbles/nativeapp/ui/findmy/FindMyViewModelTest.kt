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
import kotlinx.coroutines.awaitCancellation
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
        assertTrue(model.uiState.value.awaitingInitialRefresh)

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
        assertFalse(model.uiState.value.awaitingInitialRefresh)
    }

    @Test
    fun `initial refresh keeps cached targets visible while loading fresh data`() = runTest(dispatcher) {
        val cachedDevice = FmDeviceUi("cached", "Cached phone")
        val port = ParallelRefreshPort(cachedDevices = listOf(cachedDevice))
        val model = FindMyViewModel(port)

        runCurrent()

        assertTrue(model.uiState.value.refreshing)
        assertEquals(listOf(cachedDevice), model.uiState.value.devices)
        assertFalse(model.uiState.value.awaitingInitialRefresh)

        port.devicesRelease.complete(Unit)
        port.friendsRelease.complete(Unit)
        port.itemsRelease.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `failed initial refresh without cached targets exposes a recoverable error`() = runTest(dispatcher) {
        val model = FindMyViewModel(RefreshOutcomePort())

        advanceUntilIdle()

        assertTrue(model.uiState.value.isEmpty)
        assertTrue(model.uiState.value.hasInitialRefreshError)
        assertFalse(model.uiState.value.awaitingInitialRefresh)
        assertEquals(
            listOf("Devices: offline", "Friends: offline", "Items: offline"),
            model.uiState.value.refreshErrors,
        )
    }

    @Test
    fun `successful initial refresh distinguishes an empty account from a failure`() = runTest(dispatcher) {
        val model = FindMyViewModel(RefreshOutcomePort(failRefresh = false))

        advanceUntilIdle()

        assertTrue(model.uiState.value.isEmpty)
        assertFalse(model.uiState.value.hasInitialRefreshError)
        assertFalse(model.uiState.value.awaitingInitialRefresh)
        assertTrue(model.uiState.value.refreshErrors.isEmpty())
    }

    @Test
    fun `cached targets remain available when refreshing fails`() = runTest(dispatcher) {
        val cachedDevice = FmDeviceUi("cached", "Cached phone")
        val model = FindMyViewModel(RefreshOutcomePort(cachedDevices = listOf(cachedDevice)))

        advanceUntilIdle()

        assertEquals(listOf(cachedDevice), model.uiState.value.devices)
        assertFalse(model.uiState.value.isEmpty)
        assertFalse(model.uiState.value.hasInitialRefreshError)
        assertEquals(3, model.uiState.value.refreshErrors.size)
    }

    @Test
    fun `refreshed people locations become visible map targets and retain their place`() = runTest(dispatcher) {
        val location = FmPoint(
            latitude = 40.903,
            longitude = -73.459,
            timestampMs = 1_760_000_000_000,
            address = "Jennings Rd, Lloyd Harbor, NY",
        )
        val person = FmFriendUi("person", "Taylor", "person@icloud.com", location)
        val port = RefreshOutcomePort(failRefresh = false, refreshedFriends = listOf(person))
        val model = FindMyViewModel(port)

        advanceUntilIdle()

        assertEquals(listOf(person), model.uiState.value.friends)
        assertEquals(listOf("friend:person"), model.uiState.value.locatedTargets.map { it.id })
        assertEquals("Jennings Rd, Lloyd Harbor, NY", model.uiState.value.locatedTargets.single().point?.address)
        assertEquals(listOf(location), model.uiState.value.trail("friend:person"))
    }

    @Test
    fun `cached people locations remain visible when their refresh fails`() = runTest(dispatcher) {
        val person = FmFriendUi(
            id = "person",
            name = "Taylor",
            location = FmPoint(40.903, -73.459, timestampMs = 1_760_000_000_000),
        )
        val model = FindMyViewModel(RefreshOutcomePort(cachedFriends = listOf(person)))

        advanceUntilIdle()

        assertEquals(listOf(person), model.uiState.value.friends)
        assertEquals(listOf("friend:person"), model.uiState.value.locatedTargets.map { it.id })
        assertTrue(model.uiState.value.refreshErrors.any { it.startsWith("Friends:") })
    }

    @Test
    fun `retrying failed initial refresh clears the error after a successful response`() = runTest(dispatcher) {
        val port = RefreshOutcomePort()
        val model = FindMyViewModel(port)
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasInitialRefreshError)

        val refreshedDevice = FmDeviceUi("device", "Recovered phone")
        port.failRefresh = false
        port.refreshedDevices = listOf(refreshedDevice)
        model.refresh()
        advanceUntilIdle()

        assertEquals(listOf(refreshedDevice), model.uiState.value.devices)
        assertTrue(model.uiState.value.refreshErrors.isEmpty())
        assertFalse(model.uiState.value.hasInitialRefreshError)
        assertEquals(2, port.deviceRefreshes)
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
    fun `hiding the screen cancels an in-flight live refresh`() = runTest(dispatcher) {
        val port = CancelAwarePort()
        val model = FindMyViewModel(port, liveIntervalMs = 1_000L)
        advanceUntilIdle()

        model.setVisible(true)
        advanceTimeBy(1_000L)
        runCurrent()
        assertTrue(port.liveStarted.isCompleted)
        assertTrue(model.uiState.value.refreshing)

        model.setVisible(false)
        runCurrent()

        assertTrue(port.liveCancelled.isCompleted)
        assertFalse(model.uiState.value.refreshing)
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

private class ParallelRefreshPort(
    private val cachedDevices: List<FmDeviceUi> = emptyList(),
) : FindMyPort {
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

    override suspend fun devices(): List<FmDeviceUi> = cachedDevices

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

private class RefreshOutcomePort(
    private val cachedDevices: List<FmDeviceUi> = emptyList(),
    private val cachedFriends: List<FmFriendUi> = emptyList(),
    var failRefresh: Boolean = true,
    var refreshedFriends: List<FmFriendUi> = cachedFriends,
) : FindMyPort {
    var refreshedDevices: List<FmDeviceUi> = cachedDevices
    var deviceRefreshes: Int = 0
        private set

    override fun isAvailable(): Boolean = true

    override suspend fun devices(): List<FmDeviceUi> = cachedDevices

    override suspend fun refreshDevices(): List<FmDeviceUi> {
        deviceRefreshes += 1
        if (failRefresh) error("offline")
        return refreshedDevices
    }

    override suspend fun friends(): List<FmFriendUi> = cachedFriends

    override suspend fun refreshFriends(): List<FmFriendUi> {
        if (failRefresh) error("offline")
        return refreshedFriends
    }

    override suspend fun items(): List<FmItemUi> = emptyList()

    override suspend fun refreshItems(): List<FmItemUi> {
        if (failRefresh) error("offline")
        return emptyList()
    }
}

private class CancelAwarePort : FindMyPort {
    val liveStarted = CompletableDeferred<Unit>()
    val liveCancelled = CompletableDeferred<Unit>()
    private var deviceRefreshes = 0
    private var friendRefreshes = 0
    private var itemRefreshes = 0

    override fun isAvailable(): Boolean = true
    override suspend fun devices(): List<FmDeviceUi> = emptyList()
    override suspend fun friends(): List<FmFriendUi> = emptyList()
    override suspend fun items(): List<FmItemUi> = emptyList()

    override suspend fun refreshDevices(): List<FmDeviceUi> {
        deviceRefreshes += 1
        if (deviceRefreshes == 1) return emptyList()
        liveStarted.complete(Unit)
        try {
            awaitCancellation()
        } finally {
            liveCancelled.complete(Unit)
        }
    }

    override suspend fun refreshFriends(): List<FmFriendUi> {
        friendRefreshes += 1
        if (friendRefreshes == 1) return emptyList()
        awaitCancellation()
    }

    override suspend fun refreshItems(): List<FmItemUi> {
        itemRefreshes += 1
        if (itemRefreshes == 1) return emptyList()
        awaitCancellation()
    }
}

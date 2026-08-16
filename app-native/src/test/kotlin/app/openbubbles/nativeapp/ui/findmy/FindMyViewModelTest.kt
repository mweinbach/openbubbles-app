package app.openbubbles.nativeapp.ui.findmy

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

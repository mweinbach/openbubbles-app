package app.openbubbles.nativeapp.ui.sharedalbums

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SharedAlbumsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val familyAlbum = SharedAlbumUi("1", "Family", "Alex", null, 4, false, false, null)
    private val vacationAlbum = SharedAlbumUi("2", "Vacation", "Sam", null, 8, false, false, null)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `fake port lists and filters albums`() = runTest {
        val port = FakeSharedAlbumsPort(
            albums = listOf(
                familyAlbum,
                vacationAlbum,
            ),
        )

        assertEquals(listOf("Family"), filterSharedAlbums(port.list(false), "alex").map { it.name })
        assertEquals(listOf("Vacation"), filterSharedAlbums(port.list(false), "vac").map { it.name })
    }

    @Test
    fun `fake port toggles album sync`() = runTest {
        val port = FakeSharedAlbumsPort(
            albums = listOf(familyAlbum),
        )

        port.setSync("1", "/pictures/family")

        assertEquals(true, port.list(false).single().syncing)
        assertEquals("/pictures/family", port.list(false).single().location)
    }

    @Test
    fun `newer refresh owns loading state and albums when initial load completes first`() =
        runTest(dispatcher) {
            val port = DeferredSharedAlbumsPort()
            val model = SharedAlbumsViewModel(port)
            runCurrent()

            assertEquals(1, port.listCalls.size)
            model.refresh(remote = true)
            runCurrent()
            assertTrue(model.uiState.value.loading)
            assertTrue(model.uiState.value.refreshing)

            port.listCalls[0].result.complete(listOf(familyAlbum))
            runCurrent()

            assertEquals(2, port.listCalls.size)
            assertTrue(model.uiState.value.albums.isEmpty())
            assertTrue(model.uiState.value.loading)
            assertTrue(model.uiState.value.refreshing)

            port.listCalls[1].result.complete(listOf(vacationAlbum))
            runCurrent()

            assertEquals(listOf("Vacation"), model.uiState.value.albums.map { it.name })
            assertFalse(model.uiState.value.loading)
            assertFalse(model.uiState.value.refreshing)
            assertFalse(model.uiState.value.busy)
        }

    @Test
    fun `queued action stays busy after stale refresh finishes and owns final albums`() =
        runTest(dispatcher) {
            val port = DeferredSharedAlbumsPort()
            val model = SharedAlbumsViewModel(port)
            runCurrent()
            port.listCalls[0].result.complete(listOf(familyAlbum))
            runCurrent()

            model.refresh(remote = true)
            runCurrent()
            model.acceptToken(" invitation ")
            runCurrent()

            assertTrue(model.uiState.value.refreshing)
            assertTrue(model.uiState.value.busy)
            port.listCalls[1].result.complete(listOf(familyAlbum.copy(name = "Stale family")))
            runCurrent()

            assertEquals(listOf("Family"), model.uiState.value.albums.map { it.name })
            assertFalse(model.uiState.value.refreshing)
            assertTrue(model.uiState.value.busy)
            assertEquals("invitation", port.acceptTokenStarted.await())

            port.acceptTokenRelease.complete(Unit)
            runCurrent()
            assertEquals(3, port.listCalls.size)
            port.listCalls[2].result.complete(listOf(vacationAlbum))
            runCurrent()

            assertEquals(listOf("Vacation"), model.uiState.value.albums.map { it.name })
            assertFalse(model.uiState.value.loading)
            assertFalse(model.uiState.value.refreshing)
            assertFalse(model.uiState.value.busy)
        }

    @Test
    fun `new selection cancels stale asset load without clearing its busy state`() =
        runTest(dispatcher) {
            val port = DeferredSharedAlbumsPort()
            val model = SharedAlbumsViewModel(port)
            runCurrent()
            port.listCalls[0].result.complete(listOf(familyAlbum, vacationAlbum))
            runCurrent()

            model.select(familyAlbum)
            runCurrent()
            assertEquals(listOf("1"), port.assetCalls.map { it.albumId })

            model.select(vacationAlbum)
            runCurrent()
            assertEquals(listOf("1", "2"), port.assetCalls.map { it.albumId })
            assertEquals("2", model.uiState.value.selected?.id)
            assertTrue(model.uiState.value.assets.isEmpty())
            assertTrue(model.uiState.value.busy)

            port.assetCalls[0].result.complete(listOf(SharedAlbumAssetUi("old", "old.jpg")))
            runCurrent()
            assertTrue(model.uiState.value.assets.isEmpty())
            assertTrue(model.uiState.value.busy)

            port.assetCalls[1].result.complete(listOf(SharedAlbumAssetUi("new", "new.jpg")))
            runCurrent()

            assertEquals(listOf("new"), model.uiState.value.assets.map { it.id })
            assertEquals("2", model.uiState.value.selected?.id)
            assertFalse(model.uiState.value.busy)
        }

    private class DeferredSharedAlbumsPort : SharedAlbumsPort {
        data class ListCall(
            val refresh: Boolean,
            val result: CompletableDeferred<List<SharedAlbumUi>> = CompletableDeferred(),
        )

        data class AssetCall(
            val albumId: String,
            val result: CompletableDeferred<List<SharedAlbumAssetUi>> = CompletableDeferred(),
        )

        val listCalls = mutableListOf<ListCall>()
        val assetCalls = mutableListOf<AssetCall>()
        val acceptTokenStarted = CompletableDeferred<String>()
        val acceptTokenRelease = CompletableDeferred<Unit>()

        override suspend fun list(refresh: Boolean): List<SharedAlbumUi> =
            ListCall(refresh).also(listCalls::add).result.await()

        override suspend fun syncNow() = Unit
        override suspend fun accept(albumId: String) = Unit

        override suspend fun acceptToken(token: String) {
            acceptTokenStarted.complete(token)
            acceptTokenRelease.await()
        }

        override suspend fun setSync(albumId: String, folder: String?) = Unit

        override suspend fun assets(albumId: String): List<SharedAlbumAssetUi> =
            AssetCall(albumId).also(assetCalls::add).result.await()
    }
}

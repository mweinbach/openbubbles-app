package app.openbubbles.core.repo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame

class StoreInvalidationCoordinatorTest {
    private lateinit var store: BoxStore
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = Files.createTempDirectory("ob-invalidation-coordinator").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `history writes emit one refresh after the publisher queue drains`() = runBlocking {
        val coordinator = StoreInvalidationCoordinators.forStore(store)
        val events = Channel<Unit>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.changesFor(StoreEntityChange.MESSAGE).collect(events::send)
        }
        // Let merge start both child collectors before committing test rows.
        delay(25)

        coordinator.coalesce {
            store.boxFor(Message::class.java).put(Message().apply { guid = "coalesced-1" })
            store.boxFor(Message::class.java).put(Message().apply { guid = "coalesced-2" })
        }

        assertNotNull(withTimeout(2_000) { events.receive() })
        assertNull(withTimeoutOrNull(150) { events.receive() })

        store.boxFor(Message::class.java).put(Message().apply { guid = "live" })
        assertNotNull(withTimeout(2_000) { events.receive() })
        collector.cancelAndJoin()
    }

    @Test
    fun `initial refresh is emitted only after change subscription is ready`() = runBlocking {
        val coordinator = StoreInvalidationCoordinators.forStore(store)
        val events = Channel<Unit>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            coordinator.changesForWithInitial(StoreEntityChange.MESSAGE).collect(events::send)
        }

        assertNotNull(withTimeout(2_000) { events.receive() })
        store.boxFor(Message::class.java).put(Message().apply { guid = "after-initial" })
        assertNotNull(withTimeout(2_000) { events.receive() })

        collector.cancelAndJoin()
    }

    @Test
    fun `transient repositories share one observer owner and released owner stays inactive`() =
        runBlocking {
            val coordinator = StoreInvalidationCoordinators.forStore(store)
            val chat = Chat().apply { guid = "observer-owner" }
            store.boxFor(Chat::class.java).put(chat)
            awaitPublisherBarrier()

            repeat(25) {
                ChatRepo(store).markRead(chat.id)
                ContactSync(store).displayInfoByHandleId()
                assertSame(coordinator, StoreInvalidationCoordinators.forStore(store))
            }
            awaitPublisherBarrier()

            val generationAtRelease = coordinator.generationFor(StoreEntityChange.CHAT)
            StoreInvalidationCoordinators.release(store)
            chat.isArchived = true
            store.boxFor(Chat::class.java).put(chat)
            awaitPublisherBarrier()

            assertEquals(
                generationAtRelease,
                coordinator.generationFor(StoreEntityChange.CHAT),
                "a released coordinator must not receive later store invalidations",
            )

            val replacement = StoreInvalidationCoordinators.forStore(store)
            assertNotSame(coordinator, replacement)
            StoreInvalidationCoordinators.release(store)
        }

    private suspend fun awaitPublisherBarrier() {
        val reached = CompletableDeferred<Unit>()
        store.subscribe().single().observer { reached.complete(Unit) }
        withTimeout(2_000) { reached.await() }
    }
}

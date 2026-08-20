package app.openbubbles.core.repo

import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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
}

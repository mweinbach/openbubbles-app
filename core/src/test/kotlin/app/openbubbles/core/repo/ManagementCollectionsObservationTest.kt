package app.openbubbles.core.repo

import app.openbubbles.core.model.ChatListItem
import app.openbubbles.core.model.MessageItem
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ThreadContextElement
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManagementCollectionsObservationTest {
    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var chat: Chat
    private lateinit var message: Message

    @Before
    fun setUp() {
        testDir = Files.createTempDirectory("ob-management-collections").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        chat = Chat().apply { guid = "management-chat" }
            .also(store.boxFor(Chat::class.java)::put)
        message = Message().apply {
            guid = "management-message"
            text = "A recoverable message"
            dateCreated = Date(1_000L)
            this.chat.target = this@ManagementCollectionsObservationTest.chat
        }.also(store.boxFor(Message::class.java)::put)
    }

    @After
    fun tearDown() {
        releaseStoreInvalidationObservers(store)
        store.closeThreadResources()
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `deleted chats react to soft delete restore and permanent deletion`() = runBlocking {
        val repo = ChatRepo(store)
        val updates = Channel<List<ChatListItem>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeRecentlyDeleted().closeObserverThreadResources().collect(updates::send)
        }

        try {
            assertTrue(updates.receiveWithinTimeout().isEmpty())

            repo.softDelete(chat.id)
            assertEquals(listOf(chat.id), updates.receiveWithinTimeout().map { it.id })

            repo.restoreDeleted(chat.id)
            assertTrue(updates.receiveWithinTimeout().isEmpty())

            repo.softDelete(chat.id)
            assertEquals(listOf(chat.id), updates.receiveWithinTimeout().map { it.id })

            repo.permanentlyDelete(chat.id)
            assertTrue(updates.receiveWithinTimeout().isEmpty())
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `deleted chat count observes native count across every deletion transition`() = runBlocking {
        val repo = ChatRepo(store)
        val counts = Channel<Long>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeRecentlyDeletedCount().closeObserverThreadResources().collect(counts::send)
        }

        try {
            assertEquals(0L, counts.receiveWithinTimeout())

            repo.softDelete(chat.id)
            assertEquals(1L, counts.receiveWithinTimeout())

            repo.restoreDeleted(chat.id)
            assertEquals(0L, counts.receiveWithinTimeout())

            repo.softDelete(chat.id)
            assertEquals(1L, counts.receiveWithinTimeout())

            repo.permanentlyDelete(chat.id)
            assertEquals(0L, counts.receiveWithinTimeout())
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `bookmarks observe repository and direct ObjectBox mutations`() = runBlocking {
        val repo = MessageRepo(store)
        val updates = Channel<List<MessageItem>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeBookmarked(chat.id).closeObserverThreadResources().collect(updates::send)
        }

        try {
            assertTrue(updates.receiveWithinTimeout().isEmpty())

            repo.setBookmarked(listOf(message.id), true)
            assertEquals(listOf(message.id), updates.receiveWithinTimeout().map { it.id })

            repo.setBookmarked(listOf(message.id), false)
            assertTrue(updates.receiveWithinTimeout().isEmpty())

            val messageBox = store.boxFor(Message::class.java)
            messageBox.put(messageBox.get(message.id).apply { isBookmarked = true })
            assertEquals(listOf(message.id), updates.receiveWithinTimeout().map { it.id })

            messageBox.put(messageBox.get(message.id).apply { dateDeleted = Date(2_000L) })
            assertTrue(updates.receiveWithinTimeout().isEmpty())

            repo.restoreDeleted(listOf(message.id))
            assertEquals(listOf(message.id), updates.receiveWithinTimeout().map { it.id })

            repo.deleteLocal(listOf(message.id))
            assertTrue(updates.receiveWithinTimeout().isEmpty())
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `deleted messages react to external deletion restore and permanent removal`() = runBlocking {
        val repo = MessageRepo(store)
        val updates = Channel<List<MessageItem>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeRecentlyDeleted().closeObserverThreadResources().collect(updates::send)
        }

        try {
            assertTrue(updates.receiveWithinTimeout().isEmpty())

            val messageBox = store.boxFor(Message::class.java)
            messageBox.put(messageBox.get(message.id).apply { dateDeleted = Date(2_000L) })
            assertEquals(listOf(message.id), updates.receiveWithinTimeout().map { it.id })

            repo.restoreDeleted(listOf(message.id))
            assertTrue(updates.receiveWithinTimeout().isEmpty())

            messageBox.put(messageBox.get(message.id).apply { dateDeleted = Date(3_000L) })
            assertEquals(listOf(message.id), updates.receiveWithinTimeout().map { it.id })

            repo.deleteLocal(listOf(message.id))
            assertTrue(updates.receiveWithinTimeout().isEmpty())
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `chat scoped deleted messages ignore mutations in other conversations`() = runBlocking {
        val repo = MessageRepo(store)
        val otherChat = Chat().apply { guid = "other-management-chat" }
            .also(store.boxFor(Chat::class.java)::put)
        val otherMessage = Message().apply {
            guid = "other-management-message"
            text = "Other conversation"
            dateCreated = Date(1_001L)
            this.chat.target = otherChat
        }.also(store.boxFor(Message::class.java)::put)
        val updates = Channel<List<MessageItem>>(Channel.UNLIMITED)
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            repo.observeRecentlyDeleted(chat.id).closeObserverThreadResources().collect(updates::send)
        }

        try {
            assertTrue(updates.receiveWithinTimeout().isEmpty())

            val messageBox = store.boxFor(Message::class.java)
            messageBox.put(messageBox.get(otherMessage.id).apply { dateDeleted = Date(2_000L) })
            messageBox.put(messageBox.get(message.id).apply { dateDeleted = Date(3_000L) })

            assertEquals(listOf(message.id), updates.receiveWithinTimeout().map { it.id })
        } finally {
            collector.cancelAndJoin()
        }
    }

    private suspend fun <T> Channel<T>.receiveWithinTimeout(): T =
        withTimeout(10_000L) { receive() }

    /** Fuses into the repository's IO context and releases readers on their owner thread. */
    private fun <T> Flow<T>.closeObserverThreadResources(): Flow<T> =
        flowOn(ObserverThreadCleanup(store))

    private class ObserverThreadCleanup(
        private val store: BoxStore,
    ) : ThreadContextElement<Unit> {
        companion object Key : CoroutineContext.Key<ObserverThreadCleanup>

        override val key: CoroutineContext.Key<ObserverThreadCleanup>
            get() = Key

        override fun updateThreadContext(context: CoroutineContext) = Unit

        override fun restoreThreadContext(context: CoroutineContext, oldState: Unit) {
            store.closeThreadResources()
        }
    }
}

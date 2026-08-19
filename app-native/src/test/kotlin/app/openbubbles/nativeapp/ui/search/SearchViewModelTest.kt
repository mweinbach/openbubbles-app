package app.openbubbles.nativeapp.ui.search

import app.openbubbles.core.contacts.RawContact
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.RichLinkPreview
import app.openbubbles.nativeapp.data.SearchRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    private val coffeeChat = ChatListItem(
        id = 7L,
        title = "Coffee Crew",
        snippet = "espresso tomorrow?",
        date = 0L,
        unread = 0,
        pinned = false,
        avatarColor = 0xFF006C4C,
        avatarAddress = "crew@example.com",
    )
    private val hikeChat = ChatListItem(
        id = 8L,
        title = "Weekend hike",
        snippet = "see you at the trailhead",
        date = 0L,
        unread = 0,
        pinned = false,
        avatarColor = 0xFF386A20,
    )

    private fun message(id: Long, text: String, chatId: Long, link: RichLinkPreview? = null) =
        MessageItem(
            id = id,
            text = text,
            isFromMe = false,
            date = 1_759_700_000_000,
            status = MessageStatus.READ,
            isGroupEvent = false,
            reactionEmoji = null,
            senderAddress = "alex@icloud.com",
            guid = "g$id",
            richLink = link,
            chatId = chatId,
        )

    private inner class FakeSearch(
        private val messages: List<MessageItem> = emptyList(),
        private val links: List<MessageItem> = emptyList(),
        private val people: List<RawContact> = emptyList(),
        private val messageSearch: (suspend (String) -> List<MessageItem>)? = null,
    ) : SearchRepository {
        val messageQueries = mutableListOf<String>()

        override suspend fun searchMessages(query: String, limit: Int): List<MessageItem> {
            messageQueries += query
            return messageSearch?.invoke(query) ?: messages
        }

        override suspend fun searchLinks(query: String, limit: Int) = links
        override suspend fun contacts() = people
    }

    private inner class FakeChats(
        private val items: List<ChatListItem>,
    ) : ChatListRepository {
        override fun chats(): Flow<List<ChatListItem>> = flowOf(items)
        override fun markRead(id: Long) = Unit
        override fun setPinned(id: Long, pinned: Boolean) = Unit
        override fun setMuted(id: Long, muted: Boolean) = Unit
        override fun setArchived(id: Long, archived: Boolean) = Unit
        override fun delete(id: Long) = Unit
    }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `single character queries search nothing`() = runTest(dispatcher) {
        val model = SearchViewModel(
            FakeSearch(messages = listOf(message(1, "coffee", 7))),
            FakeChats(listOf(coffeeChat)),
        )
        backgroundScope.launch { model.uiState.collect() }
        model.onQueryChange("c")
        advanceUntilIdle()

        val state = model.uiState.value
        assertTrue(state.chats.isEmpty())
        assertTrue(state.messages.isEmpty())
    }

    @Test
    fun `query debounce exposes searching until the settled result completes`() = runTest(dispatcher) {
        val search = FakeSearch(messages = listOf(message(1, "coffee", 7)))
        val model = SearchViewModel(search, FakeChats(listOf(coffeeChat)))
        backgroundScope.launch { model.uiState.collect() }
        runCurrent()

        model.onQueryChange("coffee")
        runCurrent()

        assertTrue(model.uiState.value.searching)
        assertFalse(model.uiState.value.hasResults)
        assertTrue(search.messageQueries.isEmpty())

        advanceTimeBy(249)
        runCurrent()
        assertTrue(search.messageQueries.isEmpty())
        assertTrue(model.uiState.value.searching)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("coffee"), search.messageQueries)
        assertFalse(model.uiState.value.searching)
        assertEquals(listOf("g1"), model.uiState.value.messages.map { it.guid })
    }

    @Test
    fun `rapid query replacement never exposes results from the previous query`() = runTest(dispatcher) {
        val requests = mutableMapOf<String, CompletableDeferred<List<MessageItem>>>()
        val search = FakeSearch(
            messageSearch = { query ->
                CompletableDeferred<List<MessageItem>>().also { requests[query] = it }.await()
            },
        )
        val model = SearchViewModel(search, FakeChats(listOf(coffeeChat, hikeChat)))
        backgroundScope.launch { model.uiState.collect() }
        runCurrent()

        model.onQueryChange("coffee")
        advanceTimeBy(250)
        runCurrent()
        assertTrue(model.uiState.value.searching)
        assertEquals(setOf("coffee"), requests.keys)

        model.onQueryChange("hike")
        runCurrent()
        requests.getValue("coffee").complete(listOf(message(1, "coffee", 7)))
        runCurrent()

        assertEquals("hike", model.uiState.value.query)
        assertTrue(model.uiState.value.searching)
        assertFalse(model.uiState.value.hasResults)

        advanceTimeBy(250)
        runCurrent()
        requests.getValue("hike").complete(listOf(message(2, "hike", 8)))
        runCurrent()

        assertFalse(model.uiState.value.searching)
        assertEquals(listOf("g2"), model.uiState.value.messages.map { it.guid })
        assertEquals(listOf("Weekend hike"), model.uiState.value.chats.map { it.title })
    }

    @Test
    fun `failed search exposes an error instead of a false empty result`() = runTest(dispatcher) {
        val search = FakeSearch(
            messageSearch = { throw IllegalStateException("search unavailable") },
        )
        val model = SearchViewModel(search, FakeChats(listOf(coffeeChat)))
        backgroundScope.launch { model.uiState.collect() }
        runCurrent()

        model.onQueryChange("missing")
        advanceTimeBy(250)
        runCurrent()

        assertEquals(listOf("missing"), search.messageQueries)
        assertFalse(model.uiState.value.searching)
        assertFalse(model.uiState.value.hasResults)
        assertEquals("search unavailable", model.uiState.value.error)
    }

    @Test
    fun `avatar generation refreshes query tagged contact results`() = runTest(dispatcher) {
        val generation = MutableStateFlow(0)
        val search = FakeSearch(
            people = listOf(
                RawContact(
                    id = "p1",
                    displayName = "Coffee Person",
                    firstName = null,
                    lastName = null,
                    avatarPath = "/avatars/old.img",
                    addresses = listOf("coffee@example.com"),
                ),
            ),
        )
        val model = SearchViewModel(search, FakeChats(emptyList()), generation)
        backgroundScope.launch { model.uiState.collect() }
        model.onQueryChange("coffee")
        advanceUntilIdle()

        assertEquals(listOf("coffee"), search.messageQueries)
        generation.value = 1
        advanceUntilIdle()

        assertEquals(listOf("coffee", "coffee"), search.messageQueries)
        assertFalse(model.uiState.value.searching)
    }

    @Test
    fun `all four sections populate for a matching query`() = runTest(dispatcher) {
        val model = SearchViewModel(
            FakeSearch(
                messages = listOf(message(1, "coffee at the trailhead?", 8)),
                links = listOf(
                    message(
                        2,
                        "https://coffee.example.com",
                        7,
                        link = RichLinkPreview(
                            url = "https://coffee.example.com",
                            displayHost = "coffee.example.com",
                            title = "Coffee Guide",
                            summary = null,
                            imageBytes = null,
                            imageMime = null,
                            iconBytes = null,
                            iconMime = null,
                        ),
                    ),
                ),
                people = listOf(
                    RawContact(
                        id = "p1", displayName = "Courtney Coffeeson",
                        firstName = null, lastName = null,
                        avatarPath = null, addresses = listOf("courtney@example.com"),
                    ),
                ),
            ),
            FakeChats(listOf(coffeeChat, hikeChat)),
        )
        backgroundScope.launch { model.uiState.collect() }
        model.onQueryChange("coffee")
        advanceUntilIdle()

        val state = model.uiState.value
        assertEquals(listOf("Coffee Crew"), state.chats.map { it.title })
        assertEquals(listOf("Courtney Coffeeson"), state.people.map { it.displayName })
        assertEquals(listOf("g1"), state.messages.map { it.guid })
        assertEquals(listOf("g2"), state.links.map { it.guid })
        // Message rows join back to the conversation for title and tap target.
        assertEquals("Weekend hike", state.messages.single().chat?.title)
        assertEquals(8L, state.messages.single().chatId)
    }

    @Test
    fun `a message surfaced as a link is not repeated as a message`() = runTest(dispatcher) {
        val both = message(
            3,
            "https://coffee.example.com",
            7,
            link = RichLinkPreview(
                url = "https://coffee.example.com",
                displayHost = "coffee.example.com",
                title = null,
                summary = null,
                imageBytes = null,
                imageMime = null,
                iconBytes = null,
                iconMime = null,
            ),
        )
        val model = SearchViewModel(
            FakeSearch(messages = listOf(both), links = listOf(both)),
            FakeChats(listOf(coffeeChat)),
        )
        backgroundScope.launch { model.uiState.collect() }
        model.onQueryChange("coffee")
        advanceUntilIdle()

        val state = model.uiState.value
        assertTrue(state.messages.isEmpty())
        assertEquals(listOf("g3"), state.links.map { it.guid })
    }

    @Test
    fun `clearing the query clears every section`() = runTest(dispatcher) {
        val model = SearchViewModel(
            FakeSearch(messages = listOf(message(1, "coffee", 7))),
            FakeChats(listOf(coffeeChat)),
        )
        backgroundScope.launch { model.uiState.collect() }
        model.onQueryChange("coffee")
        advanceUntilIdle()
        assertTrue(model.uiState.value.hasResults)

        model.onQueryChange("")
        advanceUntilIdle()
        assertTrue(!model.uiState.value.hasResults)
    }
}

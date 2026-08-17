package app.openbubbles.core

import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Transcript search queries: body text and link-carrying messages across
 * every conversation, with reactions and deleted rows excluded. Rows are
 * written directly — search is a read path and needs no ingest pipeline.
 */
class MessageRepoSearchTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var repo: MessageRepo
    private lateinit var chat: Chat

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-search-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        repo = MessageRepo(store)
        chat = Chat().apply { guid = "chat-search-1" }
        store.boxFor(Chat::class.java).put(chat)
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    private fun putMessage(
        guid: String,
        text: String?,
        dateMillis: Long,
        linkJson: String? = null,
        reactionToGuid: String? = null,
        deleted: Boolean = false,
    ) {
        val message = Message().apply {
            this.guid = guid
            this.text = text
            dateCreated = Date(dateMillis)
            isFromMe = false
            dbMetadata = linkJson
            associatedMessageGuid = reactionToGuid
            if (reactionToGuid != null) associatedMessageType = "love"
            if (deleted) dateDeleted = Date(dateMillis)
            this.chat.target = this@MessageRepoSearchTest.chat
        }
        store.boxFor(Message::class.java).put(message)
    }

    @Test
    fun `text search matches case-insensitively and orders newest first`() {
        putMessage("m1", "Grabbing COFFEE now", dateMillis = 1_000)
        putMessage("m2", "want anything from the coffee place?", dateMillis = 2_000)
        putMessage("m3", "see you at the trailhead", dateMillis = 3_000)

        val hits = repo.searchText("coffee")
        assertEquals(listOf("m2", "m1"), hits.map { it.guid })
    }

    @Test
    fun `text search skips reactions and deleted rows`() {
        putMessage("m1", "coffee sounds great", dateMillis = 1_000)
        putMessage("m2", "coffee", dateMillis = 2_000, reactionToGuid = "m1")
        putMessage("m3", "coffee tomorrow?", dateMillis = 3_000, deleted = true)

        val hits = repo.searchText("coffee")
        assertEquals(listOf("m1"), hits.map { it.guid })
    }

    @Test
    fun `text search caps the page and trims the needle`() {
        repeat(30) { index ->
            putMessage("m$index", "yosemite trip", dateMillis = 1_000L + index)
        }
        assertEquals(25, repo.searchText("  yosemite  ").size)
        assertTrue(repo.searchText("   ").isEmpty())
    }

    @Test
    fun `link search requires a link and matches body or metadata`() {
        putMessage("m1", "check https://www.nps.gov/yose/index.htm out", dateMillis = 1_000)
        putMessage("m2", "totally unrelated note about yosemite", dateMillis = 2_000)
        putMessage(
            "m3",
            "look at this",
            dateMillis = 3_000,
            linkJson = """{"data":{"URL":{"NS.base":"","NS.relative":"https://nps.gov/planyourvisit"},"title":"Yosemite National Park"}}""",
        )

        val hits = repo.searchLinks("yose")
        // m1 matches via its body URL; m3 via the link metadata title/URL;
        // m2 matches the needle but carries no link.
        assertEquals(listOf("m3", "m1"), hits.map { it.guid })
    }

    @Test
    fun `link search skips reactions and deleted rows`() {
        putMessage("m1", "https://example.com is great", dateMillis = 1_000)
        putMessage("m2", "https://example.com", dateMillis = 2_000, reactionToGuid = "m1")
        putMessage("m3", "https://example.com again", dateMillis = 3_000, deleted = true)

        val hits = repo.searchLinks("example.com")
        assertEquals(listOf("m1"), hits.map { it.guid })
    }
}

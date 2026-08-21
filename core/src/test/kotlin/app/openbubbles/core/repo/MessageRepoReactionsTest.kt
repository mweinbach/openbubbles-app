package app.openbubbles.core.repo

import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import java.util.Date
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The transcript names who reacted, so the projection has to carry every
 * active tapback with its sender instead of only the newest emoji.
 */
class MessageRepoReactionsTest {
    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var repo: MessageRepo
    private lateinit var chat: Chat

    @Before
    fun setUp() {
        testDir = Files.createTempDirectory("ob-message-reactions").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        repo = MessageRepo(store)
        chat = Chat().apply { guid = "chat-reactions" }
        store.boxFor(Chat::class.java).put(chat)
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `every active tapback is projected with its sender`() {
        val alex = handle("alex@icloud.com")
        val mark = handle("mark@icloud.com")
        target("target-1", "we got the permit!!")
        reaction("react-alex", "target-1", "love", sender = alex, timestamp = 200L)
        reaction("react-mark", "target-1", "laugh", sender = mark, timestamp = 300L)
        reaction("react-me", "target-1", "love", fromMe = true, timestamp = 400L)

        val item = repo.messages(chat.id).single { it.guid == "target-1" }

        assertEquals(
            listOf("love" to "alex@icloud.com", "laugh" to "mark@icloud.com", "love" to null),
            item.reactions.map { it.type to it.senderAddress },
        )
        assertEquals(listOf(false, false, true), item.reactions.map { it.isFromMe })
        // The newest reaction still drives the single-emoji legacy field.
        assertEquals("love", item.reactionType)
    }

    @Test
    fun `a removal drops only that sender's reaction`() {
        val alex = handle("alex@icloud.com")
        target("target-2", "picking up the rental")
        reaction("react-alex", "target-2", "love", sender = alex, timestamp = 200L)
        reaction("react-me", "target-2", "love", fromMe = true, timestamp = 300L)
        reaction("unreact-me", "target-2", "-love", fromMe = true, timestamp = 400L)

        val item = repo.messages(chat.id).single { it.guid == "target-2" }

        assertEquals(listOf("alex@icloud.com"), item.reactions.map { it.senderAddress })
        assertTrue(item.reactions.none { it.isFromMe })
    }

    @Test
    fun `custom emoji tapbacks keep their emoji`() {
        val alex = handle("alex@icloud.com")
        target("target-3", "red bean ice cream?")
        reaction("react-alex", "target-3", "emoji", sender = alex, timestamp = 200L, emoji = "🔥")

        val item = repo.messages(chat.id).single { it.guid == "target-3" }

        assertEquals("🔥", item.reactions.single().emoji)
    }

    private fun handle(address: String): Handle = Handle().apply {
        this.address = address
        service = "iMessage"
        uniqueAddressAndService = "$address/$service"
    }.also(store.boxFor(Handle::class.java)::put)

    private fun target(guid: String, text: String) {
        store.boxFor(Message::class.java).put(Message().apply {
            this.guid = guid
            this.text = text
            dateCreated = Date(100L)
            hasReactions = true
            chat.target = this@MessageRepoReactionsTest.chat
        })
    }

    private fun reaction(
        guid: String,
        targetGuid: String,
        type: String,
        timestamp: Long,
        sender: Handle? = null,
        fromMe: Boolean = false,
        emoji: String? = null,
    ) {
        store.boxFor(Message::class.java).put(Message().apply {
            this.guid = guid
            associatedMessageGuid = targetGuid
            associatedMessageType = type
            associatedMessageEmoji = emoji
            dateCreated = Date(timestamp)
            isFromMe = fromMe
            sender?.let { handleRelation.target = it }
            chat.target = this@MessageRepoReactionsTest.chat
        })
    }
}

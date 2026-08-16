package app.openbubbles.core.repo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Date
import kotlin.test.assertEquals

class ChatRepoContactTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-chat-contact-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `chat title uses contact linked to its participant handle`() {
        val handle = Handle().apply {
            address = "friend@icloud.com"
            service = "iMessage"
            uniqueAddressAndService = "$address/$service"
        }
        store.boxFor(Handle::class.java).put(handle)
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:friend",
                    displayName = "Friendly Person",
                    firstName = "Friendly",
                    lastName = "Person",
                    avatarPath = null,
                    addresses = listOf("friend@icloud.com"),
                ),
            ),
        )
        val chat = Chat().apply {
            guid = "iMessage;-;friend@icloud.com"
            chatIdentifier = "friend@icloud.com"
            isRpSms = false
            handles.add(handle)
        }
        store.boxFor(Chat::class.java).put(chat)

        assertEquals("Friendly Person", ChatRepo(store).chats().single().title)
    }

    @Test
    fun `direct imessage chats for one contact form one conversation`() {
        val firstHandle = handle("+15550000001")
        val secondHandle = handle("+15550000002")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:multi-address",
                    displayName = "Jamie Example",
                    firstName = "Jamie",
                    lastName = "Example",
                    avatarPath = "/avatars/jamie.png",
                    addresses = listOf(firstHandle.address, secondHandle.address),
                ),
            ),
        )
        val older = chat("chat-first", firstHandle, "first address", 100L, unread = true)
        val newer = chat("chat-second", secondHandle, "second address", 200L, unread = true)

        val item = ChatRepo(store).chats().single()

        assertEquals("Jamie Example", item.title)
        assertEquals("second address", item.snippet)
        assertEquals(2, item.unreadCount)
        assertEquals("/avatars/jamie.png", item.avatarPath)
        assertEquals(listOf(older.id, newer.id).sorted(), item.memberChatIds)
        assertEquals(newer.id, item.preferredChatId)
        assertEquals(
            listOf(older.id, newer.id).sorted(),
            ChatRepo(store).relatedDirectChatIds(older.id),
        )
    }

    @Test
    fun `matching names without a shared contact never merge`() {
        val firstHandle = handle("first@example.com")
        val secondHandle = handle("second@example.com")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact("contact:first", "Alex", null, null, null, listOf(firstHandle.address)),
                RawContact("contact:second", "Alex", null, null, null, listOf(secondHandle.address)),
            ),
        )
        chat("chat-first", firstHandle, "one", 100L)
        chat("chat-second", secondHandle, "two", 200L)

        assertEquals(2, ChatRepo(store).chats().size)
    }

    private fun handle(address: String): Handle = Handle().apply {
        this.address = address
        service = "iMessage"
        uniqueAddressAndService = "$address/$service"
    }.also(store.boxFor(Handle::class.java)::put)

    private fun chat(
        guid: String,
        handle: Handle,
        text: String,
        timestamp: Long,
        unread: Boolean = false,
    ): Chat {
        val chat = Chat().apply {
            this.guid = guid
            chatIdentifier = handle.address
            isRpSms = false
            handles.add(handle)
        }
        store.boxFor(Chat::class.java).put(chat)
        val message = Message().apply {
            this.guid = "message-$guid"
            this.text = text
            dateCreated = Date(timestamp)
            isFromMe = false
            this.chat.target = chat
        }
        store.boxFor(Message::class.java).put(message)
        chat.dbLatestMessage.target = message
        chat.dbOnlyLatestMessageDate = message.dateCreated
        chat.hasUnreadMessage = unread
        store.boxFor(Chat::class.java).put(chat)
        return chat
    }
}

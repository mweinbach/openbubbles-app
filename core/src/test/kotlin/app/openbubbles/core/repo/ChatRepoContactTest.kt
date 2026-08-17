package app.openbubbles.core.repo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.db.Chat
import app.openbubbles.db.ContactV2
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
    fun `direct chat list item uses the synced contact photo`() {
        val handle = handle("friend@icloud.com")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:friend",
                    displayName = "Friendly Person",
                    firstName = "Friendly",
                    lastName = "Person",
                    avatarPath = "/avatars/friend.png",
                    addresses = listOf("friend@icloud.com"),
                ),
            ),
        )
        chat("iMessage;-;friend@icloud.com", handle, "hello", 100L)

        val item = ChatRepo(store).chats().single()
        assertEquals("Friendly Person", item.title)
        assertEquals("/avatars/friend.png", item.avatarPath)
        assertEquals("friend@icloud.com", item.avatarAddress)
        assertEquals(false, item.isGroup)
    }

    @Test
    fun `direct chat still shows a contact photo when handle backlinks are missing`() {
        val handle = handle("friend@icloud.com")
        store.boxFor(ContactV2::class.java).put(
            ContactV2().apply {
                nativeContactId = "icloud:unlinked-friend"
                displayName = "Unlinked Friend"
                avatarPath = "/avatars/unlinked.png"
                addresses = listOf("friend@icloud.com")
                isNative = true
            },
        )
        chat("iMessage;-;friend@icloud.com", handle, "hello", 100L)

        val item = ChatRepo(store).chats().single()
        assertEquals("Unlinked Friend", item.title)
        assertEquals("/avatars/unlinked.png", item.avatarPath)
    }

    @Test
    fun `cloudkit one-to-one chats resolve contact photos from the chat identifier`() {
        val me = handle("me@icloud.com")
        val friend = handle("friend@icloud.com")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:friend",
                    displayName = "Friendly Person",
                    firstName = "Friendly",
                    lastName = "Person",
                    avatarPath = "/avatars/friend.png",
                    addresses = listOf("friend@icloud.com"),
                ),
            ),
        )
        val fromIcloud = Chat().apply {
            guid = "iMessage;-;friend@icloud.com"
            chatIdentifier = "friend@icloud.com"
            style = 45
            isRpSms = false
            displayName = "Friendly Person"
            handles.add(me)
            handles.add(friend)
        }
        store.boxFor(Chat::class.java).put(fromIcloud)

        val item = ChatRepo(store).chats().single()
        assertEquals("Friendly Person", item.title)
        assertEquals("/avatars/friend.png", item.avatarPath)
        assertEquals("friend@icloud.com", item.avatarAddress)
        assertEquals(false, item.isGroup)
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
    fun `cloudkit one-to-one chats that include self still merge by contact`() {
        val me = handle("me@icloud.com")
        val mobile = handle("+15550000001")
        val email = handle("jamie@icloud.com")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:jamie",
                    displayName = "Jamie Example",
                    firstName = "Jamie",
                    lastName = "Example",
                    avatarPath = "/avatars/jamie.png",
                    addresses = listOf(mobile.address, email.address),
                ),
            ),
        )
        val fromIcloud = Chat().apply {
            guid = "iMessage;-;+15550000001"
            chatIdentifier = "+15550000001"
            style = 45
            isRpSms = false
            usingHandle = "mailto:me@icloud.com"
            handles.add(me)
            handles.add(mobile)
        }
        store.boxFor(Chat::class.java).put(fromIcloud)
        val live = chat("chat-email", email, "from email", 300L, unread = true)

        val item = ChatRepo(store) { setOf("mailto:me@icloud.com") }.chats().single()

        assertEquals("Jamie Example", item.title)
        assertEquals("from email", item.snippet)
        assertEquals(false, item.isGroup)
        assertEquals(1, item.participantCount)
        assertEquals(listOf(fromIcloud.id, live.id).sorted(), item.memberChatIds)
        assertEquals(live.id, item.preferredChatId)
    }

    @Test
    fun `new chat reuses the newest conversation for another address of the same contact`() {
        val firstHandle = handle("+15550000001")
        val secondHandle = handle("+15550000002")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:jamie",
                    displayName = "Jamie Example",
                    firstName = "Jamie",
                    lastName = "Example",
                    avatarPath = null,
                    addresses = listOf(firstHandle.address, secondHandle.address),
                ),
            ),
        )
        val existing = chat("chat-first", firstHandle, "already chatting", 400L)

        val opened = ChatRepo(store).findOrCreateByAddresses(
            listOf("tel:+15550000002"),
            "iMessage",
        )

        assertEquals(existing.id, opened.id)
        assertEquals(1, ChatRepo(store).chats().size)
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

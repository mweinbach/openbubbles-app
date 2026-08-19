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
import kotlin.test.assertNull
import kotlin.test.assertTrue

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

    /**
     * A synced wallpaper can land on any member of a contact-merged
     * conversation (e.g. the phone-number chat while the email chat is
     * newest). The merged list item must still surface it, or the open
     * transcript renders no background.
     */
    @Test
    fun `merged conversation surfaces a wallpaper stored on a non-newest member`() {
        val first = handle("+15550000001")
        val second = handle("+15550000002")
        val third = handle("jamie@icloud.com")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:jamie-3",
                    displayName = "Jamie Example",
                    firstName = "Jamie",
                    lastName = "Example",
                    avatarPath = null,
                    addresses = listOf(first.address, second.address, third.address),
                ),
            ),
        )
        chat("chat-oldest", first, "oldest", 100L)
        val middle = chat("chat-middle", second, "middle", 200L)
        chat("chat-newest", third, "newest", 300L)

        store.boxFor(Chat::class.java).put(
            store.boxFor(Chat::class.java).get(middle.id).apply {
                transcriptPosterPath = "/backgrounds/shared-42-7.img"
                transcriptBackgroundVersion = 7L
            },
        )

        val item = ChatRepo(store).chats().single()
        assertEquals("/backgrounds/shared-42-7.img", item.transcriptBackgroundPath)
        assertEquals(7L, item.transcriptBackgroundVersion)
    }

    @Test
    fun `sender override persists per protocol chat and projects into the list item`() {
        val handle = handle("friend@icloud.com")
        val chat = chat("iMessage;-;friend@icloud.com", handle, "hello", 100L)
        store.boxFor(Chat::class.java).put(
            store.boxFor(Chat::class.java).get(chat.id).apply {
                usingHandle = "mailto:me@icloud.com"
            },
        )
        val repo = ChatRepo(store)

        repo.setSenderOverride(chat.id, "tel:+15550009999")
        var item = repo.chats().single()
        assertEquals("tel:+15550009999", item.senderOverride)
        assertEquals("mailto:me@icloud.com", item.receivedOnHandle)
        assertEquals(
            "tel:+15550009999",
            store.boxFor(Chat::class.java).get(chat.id).senderOverride,
        )

        repo.setSenderOverride(chat.id, null)
        item = repo.chats().single()
        assertEquals(null, item.senderOverride)
        assertEquals(null, store.boxFor(Chat::class.java).get(chat.id).senderOverride)
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

    @Test
    fun `participant addresses union related direct chats`() {
        val mobile = handle("+15550000001")
        val email = handle("jamie@icloud.com")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:jamie",
                    displayName = "Jamie Example",
                    firstName = "Jamie",
                    lastName = "Example",
                    avatarPath = null,
                    addresses = listOf(mobile.address, email.address),
                ),
            ),
        )
        val first = chat("chat-mobile", mobile, "from phone", 100L)
        chat("chat-email", email, "from email", 200L)

        assertEquals(
            listOf("+15550000001", "jamie@icloud.com").sorted(),
            ChatRepo(store).participantAddresses(first.id).sorted(),
        )
    }

    @Test
    fun `group reaction snippet uses the contact first name`() {
        val actor = handle("+14243614182")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "icloud:alex",
                    displayName = "Alex Chen",
                    firstName = "Alex",
                    lastName = "Chen",
                    avatarPath = null,
                    addresses = listOf(actor.address),
                ),
            ),
        )
        groupChat(
            guid = "iMessage;+;tea",
            title = "Japanese tea enjoyas",
            handles = listOf(actor, handle("+15550000099")),
            latest = { chat ->
                val target = Message().apply {
                    guid = "target-tea"
                    text = "mf posting on a 1 year old thread"
                    dateCreated = Date(100L)
                    isFromMe = false
                    this.chat.target = chat
                }
                store.boxFor(Message::class.java).put(target)
                Message().apply {
                    guid = "react-tea"
                    associatedMessageGuid = "target-tea"
                    associatedMessageType = "emphasize"
                    dateCreated = Date(200L)
                    isFromMe = false
                    handleRelation.target = actor
                    this.chat.target = chat
                }
            },
        )

        val item = ChatRepo(store).chats().single()
        assertEquals("Japanese tea enjoyas", item.title)
        assertEquals("Alex emphasized “mf posting on a 1 year old thread”", item.snippet)
    }

    @Test
    fun `group message snippet prefixes the contact first name`() {
        val sender = handle("+15167549533")
        ContactSync(store).upsertContacts(
            listOf(
                RawContact(
                    id = "native:bobby",
                    displayName = "Bobby Example",
                    firstName = "Bobby",
                    lastName = "Example",
                    avatarPath = null,
                    addresses = listOf("(516) 754-9533"),
                ),
            ),
        )
        groupChat(
            guid = "iMessage;+;capital",
            title = "Bobby’s Capital",
            handles = listOf(sender, handle("+15550000088")),
            latest = { chat ->
                Message().apply {
                    guid = "msg-capital"
                    text = "Nah man keep it"
                    dateCreated = Date(300L)
                    isFromMe = false
                    handleRelation.target = sender
                    this.chat.target = chat
                }
            },
        )

        assertEquals(
            "Bobby: Nah man keep it",
            ChatRepo(store).chats().single().snippet,
        )
    }

    @Test
    fun `group event snippet names the contact instead of the handle`() {
        val actor = handle("+14243614182")
        val other = handle("+15551230000")
        other.originalROWID = 77L
        store.boxFor(Handle::class.java).put(other)
        ContactSync(store).upsertContacts(
            listOf(
                RawContact("icloud:alex", "Alex Chen", "Alex", "Chen", null, listOf(actor.address)),
                RawContact("icloud:sam", "Sam Lee", "Sam", "Lee", null, listOf(other.address)),
            ),
        )
        groupChat(
            guid = "iMessage;+;added",
            title = "Weekend Crew",
            handles = listOf(actor, other),
            latest = { chat ->
                Message().apply {
                    guid = "event-add"
                    itemType = 1L
                    groupActionType = 0L
                    otherHandle = 77L
                    dateCreated = Date(400L)
                    isFromMe = false
                    handleRelation.target = actor
                    this.chat.target = chat
                }
            },
        )

        assertEquals(
            "Alex added Sam to the conversation.",
            ChatRepo(store).chats().single().snippet,
        )
    }

    @Test
    fun `participant addresses fall back to the chat identifier`() {
        val chat = Chat().apply {
            guid = "iMessage;-;friend@icloud.com"
            chatIdentifier = "friend@icloud.com"
            style = 45
            isRpSms = false
        }
        store.boxFor(Chat::class.java).put(chat)

        assertEquals(
            listOf("friend@icloud.com"),
            ChatRepo(store).participantAddresses(chat.id),
        )
    }

    @Test
    fun `chat options persist locks and block with archive`() {
        val handle = handle("friend@icloud.com")
        val chat = chat("chat-options", handle, "hello", 100L)
        val repo = ChatRepo(store)

        repo.setLockChatName(chat.id, true)
        repo.setLockChatIcon(chat.id, true)
        repo.setBlocked(chat.id, blocked = true, archive = true)

        val stored = store.boxFor(Chat::class.java).get(chat.id)
        assertTrue(stored.lockChatName)
        assertTrue(stored.lockChatIcon)
        assertTrue(stored.isArchived)
        assertEquals("mute", stored.muteType)
        assertTrue(store.boxFor(Handle::class.java).get(handle.id).blocked == true)
        assertTrue(repo.isBlocked(chat.id))
    }

    @Test
    fun `restoring a deleted chat restores its messages`() {
        val handle = handle("friend@icloud.com")
        val chat = chat("chat-deleted", handle, "hello", 100L)
        val deletedAt = Date(500L)
        val chatBox = store.boxFor(Chat::class.java)
        val messageBox = store.boxFor(Message::class.java)
        chatBox.put(chatBox.get(chat.id).apply { dateDeleted = deletedAt })
        val message = messageBox.all.single()
        messageBox.put(message.apply { dateDeleted = deletedAt })

        val repo = ChatRepo(store)
        assertEquals(chat.id, repo.recentlyDeleted().single().id)

        repo.restoreDeleted(chat.id)

        assertNull(chatBox.get(chat.id).dateDeleted)
        assertNull(messageBox.get(message.id).dateDeleted)
        assertTrue(repo.recentlyDeleted().isEmpty())
    }

    private fun handle(address: String): Handle = Handle().apply {
        this.address = address
        service = "iMessage"
        uniqueAddressAndService = "$address/$service"
    }.also(store.boxFor(Handle::class.java)::put)

    private fun groupChat(
        guid: String,
        title: String,
        handles: List<Handle>,
        latest: (Chat) -> Message,
    ): Chat {
        val chat = Chat().apply {
            this.guid = guid
            chatIdentifier = guid
            displayName = title
            style = 43L
            isRpSms = false
            this.handles.addAll(handles)
        }
        store.boxFor(Chat::class.java).put(chat)
        val message = latest(chat)
        store.boxFor(Message::class.java).put(message)
        chat.dbLatestMessage.target = message
        chat.dbOnlyLatestMessageDate = message.dateCreated
        store.boxFor(Chat::class.java).put(chat)
        return chat
    }

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

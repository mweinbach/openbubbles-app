package app.openbubbles.nativeapp.data

import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.db.Attachment
import app.openbubbles.db.Chat
import app.openbubbles.db.ContactV2
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class MessagingAccountIsolationTest {
    private lateinit var directory: File
    private lateinit var documents: File
    private lateinit var store: BoxStore

    @BeforeTest
    fun setUp() {
        directory = Files.createTempDirectory("ob-messaging-account-isolation").toFile()
        documents = directory.resolve("app_flutter").also { check(it.mkdirs()) }
        store = MyObjectBox.builder().directory(directory.resolve("objectbox")).build()
    }

    @AfterTest
    fun tearDown() {
        store.close()
        directory.deleteRecursively()
    }

    @Test
    fun `owner identity follows normalized Apple account rather than changing handles`() {
        val first = messagingAccountOwner("  Alice@Example.COM  ", setOf("mailto:old@example.com"))
        val reconnect = messagingAccountOwner("alice@example.com", setOf("tel:+15550000000"))
        val second = messagingAccountOwner("bob@example.com", setOf("mailto:old@example.com"))

        assertEquals(first, reconnect)
        assertNotEquals(first, second)
        assertEquals(64, first.length)
    }

    @Test
    fun `handle fallback is deterministic and never accepts an unidentified account`() {
        val first = messagingAccountOwner(null, setOf(" MAILTO:Alice@Example.Com ", "tel:+15551234567"))
        val reordered = messagingAccountOwner(" ", setOf("TEL:+15551234567", "mailto:alice@example.com"))

        assertEquals(first, reordered)
        assertFailsWith<IllegalStateException> {
            messagingAccountOwner(null, emptySet())
        }
    }

    @Test
    fun `account transitions preserve first install and same owner but detect a switch`() {
        assertEquals(MessagingAccountTransition.INITIAL, messagingAccountTransition(null, "alice"))
        assertEquals(MessagingAccountTransition.SAME_ACCOUNT, messagingAccountTransition("alice", "alice"))
        assertEquals(MessagingAccountTransition.DIFFERENT_ACCOUNT, messagingAccountTransition("alice", "bob"))
        assertFailsWith<IllegalArgumentException> {
            messagingAccountTransition("alice", " ")
        }
    }

    @Test
    fun `account invalidation joins writers and rejects stale callbacks before reactivation`() = runTest {
        val registry = MessagingAccountWorkRegistry(backgroundScope)
        val accountA = registry.activate()
        val started = CompletableDeferred<Unit>()
        val cancellationObserved = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        var finalized = false

        assertNotNull(
            registry.launch(accountA) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cancellationObserved.complete(Unit)
                        releaseCleanup.await()
                        finalized = true
                    }
                }
            },
        )
        started.await()

        val cleanup = async { registry.invalidateAndJoin() }
        cancellationObserved.await()

        assertFalse(cleanup.isCompleted)
        assertFalse(registry.isCurrent(accountA))
        assertNull(registry.launch(accountA) { error("a stale account writer ran") })
        releaseCleanup.complete(Unit)
        cleanup.await()
        assertTrue(finalized)

        val accountB = registry.activate()
        assertNotEquals(accountA, accountB)
        assertFalse(registry.isCurrent(accountA))
        assertTrue(registry.isCurrent(accountB))
        assertNull(registry.launch(accountA) { error("the prior account was reactivated") })
        val freshWriter = CompletableDeferred<Unit>()
        assertNotNull(registry.launch(accountB) { freshWriter.complete(Unit) })
        freshWriter.await()
        registry.invalidateAndJoin()
    }

    @Test
    fun `switch clears Apple history and iCloud contacts but preserves carrier data`() {
        val handles = store.boxFor(Handle::class.java)
        val carrierHandle = Handle().apply {
            address = "+15551234567"
            service = "SMS"
            uniqueAddressAndService = "$address/$service"
        }.also(handles::put)
        val appleHandle = Handle().apply {
            address = "alice@example.com"
            service = "iMessage"
            uniqueAddressAndService = "$address/$service"
        }.also(handles::put)

        val chats = store.boxFor(Chat::class.java)
        val carrierChat = Chat().apply {
            guid = "carrier-chat"
            isRpSms = true
            this.handles.add(carrierHandle)
        }.also(chats::put)
        val appleChat = Chat().apply {
            guid = "apple-chat"
            isRpSms = false
            this.handles.add(appleHandle)
        }.also(chats::put)

        val messages = store.boxFor(Message::class.java)
        val carrierMessage = Message().apply {
            guid = "carrier-message"
            text = "keep the SIM conversation"
            chat.target = carrierChat
            handleRelation.target = carrierHandle
        }.also(messages::put)
        val appleMessage = Message().apply {
            guid = "apple-message"
            text = "private Apple account history"
            chat.target = appleChat
            handleRelation.target = appleHandle
        }.also(messages::put)

        val attachments = store.boxFor(Attachment::class.java)
        val carrierAttachment = Attachment().apply {
            guid = "carrier-attachment"
            transferName = "carrier.jpg"
            message.target = carrierMessage
        }.also(attachments::put)
        val appleAttachment = Attachment().apply {
            guid = "apple-attachment"
            transferName = "private.jpg"
            message.target = appleMessage
        }.also(attachments::put)
        val disk = AttachmentStore(store, documents)
        val carrierPayload = disk.pathFor(carrierAttachment).apply {
            requireNotNull(parentFile).mkdirs()
            writeText("carrier payload")
        }
        val applePayload = disk.pathFor(appleAttachment).apply {
            requireNotNull(parentFile).mkdirs()
            writeText("Apple payload")
        }
        val orphan = disk.attachmentsDir.resolve("orphaned-apple-upload").apply {
            mkdirs()
            resolve("partial").writeText("previous account")
        }
        val icons = documents.resolve(AttachmentStore.GROUP_ICONS_DIR_NAME).apply {
            mkdirs()
            resolve("private-group.png").writeText("Apple group")
        }
        val unrelated = documents.resolve("keep-this-store-sidecar").apply { writeText("keep") }

        val contacts = store.boxFor(ContactV2::class.java)
        ContactV2().apply {
            nativeContactId = "icloud:alice-card"
            displayName = "Alice iCloud"
            this.handles.add(appleHandle)
        }.also(contacts::put)
        ContactV2().apply {
            nativeContactId = "device:123"
            displayName = "Device Contact"
            isNative = true
            this.handles.add(carrierHandle)
            this.handles.add(appleHandle)
        }.also(contacts::put)

        clearMessagingAccountStore(store, documents)

        assertEquals(listOf("carrier-chat"), chats.all.map { it.guid })
        assertEquals(listOf("carrier-message"), messages.all.map { it.guid })
        assertEquals(listOf("carrier-attachment"), attachments.all.map { it.guid })
        assertEquals(listOf(carrierHandle.id), handles.all.map { it.id })
        val remainingContact = contacts.all.single()
        assertEquals("device:123", remainingContact.nativeContactId)
        assertEquals(listOf(carrierHandle.id), remainingContact.handles.map { it.id })
        assertEquals("carrier payload", carrierPayload.readText())
        assertFalse(applePayload.exists())
        assertFalse(orphan.exists())
        assertFalse(icons.exists())
        assertEquals("keep", unrelated.readText())
    }

    @Test
    fun `messaging cache cleanup rejects traversal and never follows symlinks`() {
        val outside = Files.createTempDirectory("ob-messaging-cache-outside")
        try {
            val privateCache = documents.toPath().resolve("chat_backgrounds")
            Files.createDirectories(privateCache)
            val protectedFile = outside.resolve("must-stay.jpg")
            Files.writeString(protectedFile, "keep")
            Files.createSymbolicLink(privateCache.resolve("escape"), outside)
            Files.createSymbolicLink(privateCache.resolve("dangling"), outside.resolve("missing"))

            clearOwnedMessagingRoot(documents, "chat_backgrounds")

            assertFalse(Files.exists(privateCache))
            assertEquals("keep", Files.readString(protectedFile))
            assertFailsWith<IllegalArgumentException> {
                clearOwnedMessagingRoot(documents, "../objectbox")
            }
        } finally {
            outside.toFile().deleteRecursively()
        }
    }
}

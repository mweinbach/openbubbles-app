package app.openbubbles.core.repo

import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.model.MessageStatus
import app.openbubbles.core.sync.InMemoryCloudSyncStateStore
import app.openbubbles.db.Attachment
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MessageDeletionLifecycleTest {
    private lateinit var root: File
    private lateinit var documents: File
    private lateinit var privateFiles: File
    private lateinit var store: BoxStore
    private lateinit var cloud: InMemoryCloudSyncStateStore
    private lateinit var repo: MessageRepo

    @Before
    fun setUp() {
        root = Files.createTempDirectory("ob-message-deletion-lifecycle").toFile()
        documents = root.resolve("documents").also { check(it.mkdirs()) }
        privateFiles = root.resolve("files").also { check(it.mkdirs()) }
        store = MyObjectBox.builder().directory(root.resolve("objectbox")).build()
        cloud = InMemoryCloudSyncStateStore()
        StoreDeletionCoordinators.register(
            store = store,
            attachmentsRoot = documents,
            privateRoots = listOf(privateFiles),
            cloudDeletionSink = object : CloudDeletionSink {
                override fun enqueue(recordIds: CloudDeletionRecordIds) {
                    cloud.savePendingChatDeletes((cloud.pendingChatDeletes() + recordIds.chatRecordIds).distinct())
                    cloud.savePendingMessageDeletes(
                        (cloud.pendingMessageDeletes() + recordIds.messageRecordIds).distinct(),
                    )
                    cloud.savePendingAttachmentDeletes(
                        (cloud.pendingAttachmentDeletes() + recordIds.attachmentRecordIds).distinct(),
                    )
                    cloud.saveSuppressedMessageRecordIds(
                        cloud.suppressedMessageRecordIds().filterNot(recordIds.messageRecordIds::contains),
                    )
                    cloud.saveSuppressedAttachmentRecordIds(
                        cloud.suppressedAttachmentRecordIds().filterNot(recordIds.attachmentRecordIds::contains),
                    )
                }

                override fun restore(recordIds: CloudDeletionRecordIds) {
                    cloud.acknowledgePendingChatDeletes(recordIds.chatRecordIds)
                    cloud.acknowledgePendingMessageDeletes(recordIds.messageRecordIds)
                    cloud.acknowledgePendingAttachmentDeletes(recordIds.attachmentRecordIds)
                    cloud.acknowledgeSuppressedMessageTombstones(recordIds.messageRecordIds)
                    cloud.acknowledgeSuppressedAttachmentTombstones(recordIds.attachmentRecordIds)
                }

                override fun suppressLocally(recordIds: CloudDeletionRecordIds) {
                    cloud.saveSuppressedMessageRecordIds(
                        (cloud.suppressedMessageRecordIds() + recordIds.messageRecordIds).distinct(),
                    )
                    cloud.saveSuppressedAttachmentRecordIds(
                        (cloud.suppressedAttachmentRecordIds() + recordIds.attachmentRecordIds).distinct(),
                    )
                }
            },
        )
        repo = MessageRepo(store)
    }

    @After
    fun tearDown() {
        StoreDeletionCoordinators.unregister(store)
        store.close()
        root.deleteRecursively()
    }

    @Test
    fun `message and link searches exclude deleted parent chats without shrinking the result page`() {
        val visible = chat("visible", false)
        val deleted = chat("deleted", false).also {
            it.dateDeleted = Date(1L)
            store.boxFor(Chat::class.java).put(it)
        }
        message(visible, "visible-older", "secret https://example.com", createdAt = 1L)
        message(visible, "visible-newer", "secret https://example.com/new", createdAt = 2L)
        message(deleted, "deleted-newest", "secret https://example.com/private", createdAt = 3L)

        assertEquals(listOf("visible-newer", "visible-older"), repo.searchText("secret", 2).map { it.guid })
        assertEquals(listOf("visible-newer", "visible-older"), repo.searchLinks("example.com", 2).map { it.guid })
    }

    @Test
    fun `local-only deletion cleans every owned payload and suppresses only its exact cloud records`() {
        val chat = chat("apple", false)
        val message = message(chat, "private-message", "private", recordId = "record-message")
        val attachment = attachment(message, "private-attachment", "record-attachment")
        val disk = AttachmentStore(store, documents)
        val directory = disk.directoryFor(attachment.guid).also { check(it.mkdirs()) }
        val payload = directory.resolve("private.jpg").apply { writeText("private") }
        directory.resolve("private.jpg.thumbnail").writeText("thumbnail")
        directory.resolve(".private.jpg.openbubbles-partial").writeText("partial")
        directory.resolve("old-private-name.jpg").writeText("stale")
        val sibling = documents.resolve("attachments/other-account-data").apply {
            mkdirs()
            resolve("keep.jpg").writeText("keep")
        }

        repo.deleteLocal(listOf(message.id))

        assertNull(store.boxFor(Message::class.java).get(message.id))
        assertNull(store.boxFor(Attachment::class.java).get(attachment.id))
        assertFalse(payload.exists())
        assertFalse(directory.exists())
        assertTrue(sibling.resolve("keep.jpg").exists())
        assertEquals(listOf("record-message"), cloud.suppressedMessageRecordIds())
        assertEquals(listOf("record-attachment"), cloud.suppressedAttachmentRecordIds())
        assertTrue(cloud.pendingMessageDeletes().isEmpty())
        assertTrue(cloud.pendingAttachmentDeletes().isEmpty())
    }

    @Test
    fun `all-device deletion durably queues Apple message and attachment but leaves carrier local`() {
        val apple = chat("apple", false)
        val carrier = chat("carrier", true)
        val appleMessage = message(apple, "apple-message", "apple", recordId = "apple-record")
        val appleAttachment = attachment(appleMessage, "apple-attachment", "apple-attachment-record")
        val carrierMessage = message(carrier, "carrier-message", "carrier", recordId = "carrier-record")
        attachment(carrierMessage, "carrier-attachment", "carrier-attachment-record")

        repo.deleteEverywhere(listOf(appleMessage.id, carrierMessage.id))

        assertEquals(listOf("apple-record"), cloud.pendingMessageDeletes())
        assertEquals(listOf("apple-attachment-record"), cloud.pendingAttachmentDeletes())
        assertTrue(cloud.suppressedMessageRecordIds().isEmpty())
        assertNull(store.boxFor(Attachment::class.java).get(appleAttachment.id))
        assertEquals(0L, store.boxFor(Message::class.java).count())
    }

    @Test
    fun `all-device deletion fails closed before removing a row when durable queue is unavailable`() {
        val apple = chat("apple", false)
        val message = message(apple, "apple-message", "apple", recordId = "apple-record")
        StoreDeletionCoordinators.unregister(store)

        assertFailsWith<IllegalStateException> {
            repo.deleteEverywhere(listOf(message.id))
        }

        assertNotNull(store.boxFor(Message::class.java).get(message.id))
    }

    @Test
    fun `soft-deleted chat queues every zone and restoring retracts unflushed records`() {
        val chat = chat("apple", false, recordId = "chat-record")
        val message = message(chat, "apple-message", "hello", recordId = "message-record")
        attachment(message, "apple-attachment", "attachment-record")
        val chats = ChatRepo(store)

        assertEquals("chat-record", chats.softDelete(chat.id))
        assertEquals(listOf("chat-record"), cloud.pendingChatDeletes())
        assertEquals(listOf("message-record"), cloud.pendingMessageDeletes())
        assertEquals(listOf("attachment-record"), cloud.pendingAttachmentDeletes())

        chats.restoreDeleted(chat.id)

        assertNull(store.boxFor(Chat::class.java).get(chat.id).dateDeleted)
        assertTrue(cloud.pendingChatDeletes().isEmpty())
        assertTrue(cloud.pendingMessageDeletes().isEmpty())
        assertTrue(cloud.pendingAttachmentDeletes().isEmpty())
    }

    @Test
    fun `permanent chat deletion removes attachment rows and only allowlisted private files`() {
        val avatar = privateFiles.resolve("chat_avatars/avatar.jpg").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("private avatar")
        }
        val background = privateFiles.resolve("chat_backgrounds/background.jpg").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("private background")
        }
        val userExport = root.resolve("user-export.jpg").apply { writeText("do not remove") }
        val chat = chat("apple", false, recordId = "chat-record").apply {
            customAvatarPath = avatar.absolutePath
            customBackgroundPath = background.absolutePath
            transcriptPosterPath = userExport.absolutePath
            store.boxFor(Chat::class.java).put(this)
        }
        val message = message(chat, "apple-message", "hello", recordId = "message-record")
        val attachment = attachment(message, "apple-attachment", "attachment-record")
        val payload = AttachmentStore(store, documents).pathFor(attachment).apply {
            requireNotNull(parentFile).mkdirs()
            writeText("private payload")
        }

        ChatRepo(store).permanentlyDelete(chat.id)

        assertEquals(0L, store.boxFor(Chat::class.java).count())
        assertEquals(0L, store.boxFor(Message::class.java).count())
        assertEquals(0L, store.boxFor(Attachment::class.java).count())
        assertFalse(payload.exists())
        assertFalse(avatar.exists())
        assertFalse(background.exists())
        assertEquals("do not remove", userExport.readText())
        assertEquals(listOf("chat-record"), cloud.pendingChatDeletes())
        assertEquals(listOf("message-record"), cloud.pendingMessageDeletes())
        assertEquals(listOf("attachment-record"), cloud.pendingAttachmentDeletes())
    }

    @Test
    fun `cancelled failed cloud-backed outgoing messages never request remote deletion`() {
        val chat = chat("apple", false)
        val row = message(chat, "error-protocol: temporary", "retry", recordId = "message-record").apply {
            isFromMe = true
            errorMessage = "temporary"
            store.boxFor(Message::class.java).put(this)
        }
        attachment(row, "private-attachment", "attachment-record")

        assertTrue(repo.cancelOutgoing(row.id))

        assertTrue(cloud.pendingMessageDeletes().isEmpty())
        assertTrue(cloud.pendingAttachmentDeletes().isEmpty())
        assertEquals(listOf("message-record"), cloud.suppressedMessageRecordIds())
        assertEquals(listOf("attachment-record"), cloud.suppressedAttachmentRecordIds())
    }

    @Test
    fun `carrier temporary identifiers report sent delivered and failed without changing Apple semantics`() {
        val carrier = chat("carrier", true)
        val apple = chat("apple", false)
        val sms = message(carrier, "temp-carrier", "hello").apply { isFromMe = true }
        val imessage = message(apple, "temp-apple", "hello").apply { isFromMe = true }

        assertEquals(MessageStatus.SENT, repo.statusOf(sms))
        assertEquals(MessageStatus.SENDING, repo.statusOf(imessage))

        sms.sendingServiceId = "carrier"
        assertEquals(MessageStatus.SENDING, repo.statusOf(sms))
        sms.sendingServiceId = null
        sms.dateDelivered = Date(5L)
        assertEquals(MessageStatus.DELIVERED, repo.statusOf(sms))
        sms.error = 1L
        assertEquals(MessageStatus.FAILED, repo.statusOf(sms))
    }

    @Test
    fun `retry restores protocol failure identity without losing metadata or attachment rows`() {
        val chat = chat("apple", false)
        val failed = message(chat, "error-protocol: timeout", "hello").apply {
            isFromMe = true
            stagingGuid = "real-apple-guid"
            error = 5L
            errorMessage = "timeout"
            threadOriginatorGuid = "thread-root"
            threadOriginatorPart = "0"
            subject = "subject"
            store.boxFor(Message::class.java).put(this)
        }
        val media = attachment(failed, "attachment", "attachment-record")

        val retried = assertNotNull(repo.retryOutgoing(failed.id))

        assertEquals("real-apple-guid", retried.guid)
        assertEquals("real-apple-guid", retried.stagingGuid)
        assertNull(retried.error)
        assertNull(retried.errorMessage)
        assertEquals(MessageRepo.DEFAULT_SENDING_SERVICE_ID, retried.sendingServiceId)
        assertEquals("thread-root", retried.threadOriginatorGuid)
        assertEquals("0", retried.threadOriginatorPart)
        assertEquals("subject", retried.subject)
        assertNotNull(store.boxFor(Attachment::class.java).get(media.id))
    }

    @Test
    fun `retry refuses conflicting protocol identity and terminal delivered rows`() {
        val chat = chat("apple", false)
        val existing = message(chat, "already-present", "existing")
        val failed = message(chat, "error-protocol: collision", "failed").apply {
            isFromMe = true
            stagingGuid = existing.guid
            errorMessage = "failed"
            store.boxFor(Message::class.java).put(this)
        }

        assertNull(repo.retryOutgoing(failed.id))

        failed.stagingGuid = "new-guid"
        failed.dateDelivered = Date(1L)
        store.boxFor(Message::class.java).put(failed)
        assertNull(repo.retryOutgoing(failed.id))
    }

    @Test
    fun `attachment staging preserves reply thread identity`() = runBlocking {
        val chat = chat("apple", false)

        val staged = repo.stageOutgoingMessageWithAttachments(
            chatGuid = chat.guid,
            sender = "mailto:me@example.com",
            text = "reply",
            stagingGuid = "temp-reply",
            attachments = listOf(
                MessageRepo.OutgoingAttachmentStage(
                    guid = "temp-reply_att0",
                    mimeType = "image/jpeg",
                    uti = "public.jpeg",
                    transferName = "reply.jpg",
                    totalBytes = 10L,
                ),
            ),
            threadOriginatorGuid = "root-message",
            threadOriginatorPart = "1",
        )

        assertEquals("root-message", staged.threadOriginatorGuid)
        assertEquals("1", staged.threadOriginatorPart)
    }

    private fun chat(guid: String, carrier: Boolean, recordId: String? = null): Chat = Chat().apply {
        this.guid = guid
        isRpSms = carrier
        ckRecordId = recordId
    }.also(store.boxFor(Chat::class.java)::put)

    private fun message(
        chat: Chat,
        guid: String,
        text: String,
        recordId: String? = null,
        createdAt: Long = 1L,
    ): Message = Message().apply {
        this.guid = guid
        this.text = text
        ckRecordId = recordId
        dateCreated = Date(createdAt)
        this.chat.target = chat
    }.also(store.boxFor(Message::class.java)::put)

    private fun attachment(message: Message, guid: String, recordId: String): Attachment = Attachment().apply {
        this.guid = guid
        transferName = "private.jpg"
        ckRecordId = recordId
        this.message.target = message
    }.also(store.boxFor(Attachment::class.java)::put)
}

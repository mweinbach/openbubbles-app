package app.openbubbles.db

import io.objectbox.BoxStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Round-trip sanity for the UID-parity entities. Store compatibility with
 * the Flutter app is guaranteed separately by the byte-identical
 * objectbox-model.json seed (see tools/gen_db_entities.py); these tests
 * cover the Kotlin-side ergonomics M1 will rely on.
 */
class EntitiesTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-db-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
    }

    @After
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `chat and message round trip with relations`() {
        val chatBox = store.boxFor(Chat::class.java)
        val messageBox = store.boxFor(Message::class.java)
        val handleBox = store.boxFor(Handle::class.java)

        val h = Handle().apply {
            address = "+15550001111"
            service = "iMessage"
            uniqueAddressAndService = "iMessage;+15550001111"
        }
        handleBox.put(h)

        val c = Chat().apply {
            guid = "test-chat-guid"
            handles.add(h)
        }
        chatBox.put(c)

        val m = Message().apply {
            guid = "test-msg-guid"
            text = "hello from kotlin"
            isFromMe = true
            dateCreated = java.util.Date()
            chat.target = c
        }
        m.handleRelation.target = h
        messageBox.put(m)

        c.dbLatestMessage.target = m
        chatBox.put(c)

        val readBack = chatBox[c.id]
        assertNotNull(readBack)
        assertEquals("test-chat-guid", readBack.guid)
        assertEquals(1, readBack.handles.size)
        assertEquals("+15550001111", readBack.handles[0].address)
        assertEquals("hello from kotlin", readBack.dbLatestMessage.target.text)
        assertTrue(readBack.messages.isNotEmpty())
        assertEquals("test-msg-guid", readBack.messages[0].guid)
    }

    @Test
    fun `message attachments backlink`() {
        val chatBox = store.boxFor(Chat::class.java)
        val messageBox = store.boxFor(Message::class.java)
        val attachmentBox = store.boxFor(Attachment::class.java)

        val c = Chat().apply { guid = "a-chat" }
        chatBox.put(c)
        val m = Message().apply {
            guid = "msg-with-att"
            chat.target = c
        }
        messageBox.put(m)
        attachmentBox.put(Attachment().apply {
            guid = "att-guid"
            mimeType = "image/png"
            message.target = m
        })

        val read = messageBox[m.id]!!
        assertEquals(1, read.dbAttachments.size)
        assertEquals("image/png", read.dbAttachments[0].mimeType)
    }

    @Test
    fun `attachment flex metadata integers survive backlink load`() {
        val chatBox = store.boxFor(Chat::class.java)
        val messageBox = store.boxFor(Message::class.java)
        val attachmentBox = store.boxFor(Attachment::class.java)

        val c = Chat().apply { guid = "flex-chat" }
        chatBox.put(c)
        val m = Message().apply {
            guid = "msg-with-flex-meta"
            hasAttachments = true
            chat.target = c
        }
        messageBox.put(m)
        attachmentBox.put(Attachment().apply {
            guid = "flex-att-guid"
            mimeType = "image/png"
            metadata = mapOf(
                "messagePart" to 1L,
                "width" to 1024,
            )
            exif = mapOf("Orientation" to 6)
            message.target = m
        })

        val read = messageBox[m.id]!!
        // MessageRepo.toItem reads ToMany.size, which materializes backlinks
        // and runs FlexObjectConverter — the production crash path.
        assertEquals(1, read.dbAttachments.size)
        val attachment = read.dbAttachments[0]
        val metadata = attachment.metadata
        assertNotNull(metadata)
        assertEquals(1, (metadata["messagePart"] as Number).toInt())
        assertEquals(1024, (metadata["width"] as Number).toInt())
        assertEquals(6, (attachment.exif["Orientation"] as Number).toInt())
    }

    @Test
    fun `objectbox flex converter can reflect FlexBuffers parentWidth`() {
        val field = Class.forName("io.objectbox.flatbuffers.FlexBuffers\$Reference")
            .getDeclaredField("parentWidth")
        field.isAccessible = true
        assertEquals(Int::class.javaPrimitiveType, field.type)
    }

    @Test
    fun `unique guid enforced`() {
        val messageBox = store.boxFor(Message::class.java)
        messageBox.put(Message().apply { guid = "dup" })
        val second = Message().apply { guid = "dup" }
        try {
            messageBox.put(second)
            throw AssertionError("expected unique constraint violation")
        } catch (e: io.objectbox.exception.UniqueViolationException) {
            // expected — matches Dart-side @Unique behavior (replace not set
            // in the seed flags for guid)
        }
    }
}

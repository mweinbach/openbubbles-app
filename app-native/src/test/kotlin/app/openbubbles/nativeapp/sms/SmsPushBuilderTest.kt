package app.openbubbles.nativeapp.sms

import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.db.Attachment
import app.openbubbles.db.Attachment_
import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UPart
import uniffi.rust_lib_bluebubbles.UPushMessage

/**
 * Receive-path contract tests for the on-device SMS/MMS builders: pure shape
 * assertions for the [SmsPushBuilder] outputs, plus ingest cases pushing the
 * fabricated [UPushMessage] through the real [MessageIngestor] (ObjectBox JVM
 * runtime) to prove an incoming SIM SMS lands like a relayed-SMS iMessage
 * push — isRpSms chat, SMS-service handle, unread flag, deterministic dedupe.
 */
class SmsPushBuilderTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var ingestor: MessageIngestor

    private val mePhone = "tel:+15550001111"
    private val myHandles = setOf(mePhone, "mailto:me@icloud.com")

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-sms-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        ingestor = MessageIngestor(store)
    }

    @After
    fun tearDown() {
        ingestor.close()
        store.close()
        testDir.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Pure builder shape
    // ------------------------------------------------------------------

    @Test
    fun `sms builder shapes a relayed-sms style push`() {
        val push = SmsPushBuilder.buildIncomingSms(
            senderAddress = "+15552223333",
            body = "hello there",
            timestampMs = 1_735_689_600_000L,
            myPhoneHandles = listOf(mePhone),
        )

        val inst = (push as UPushMessage.IMessage).inst
        assertEquals("tel:+15552223333", inst.sender)
        assertEquals(listOf("tel:+15552223333", mePhone), inst.conversation?.participants)
        assertEquals(1_735_689_600_000uL, inst.sentTimestamp)
        assertTrue(inst.id.startsWith("sms-"))

        val normal = inst.message as UMessage.Normal
        assertTrue(normal.isSms)
        assertEquals("hello there", (normal.parts.single().part as UPart.Text).text)
    }

    @Test
    fun `sms guid is deterministic for redelivered broadcasts`() {
        val a = SmsPushBuilder.smsGuid("+15552223333", 42L, "hi")
        val b = SmsPushBuilder.smsGuid("+15552223333", 42L, "hi")
        val c = SmsPushBuilder.smsGuid("+15552223333", 43L, "hi")
        assertEquals(a, b)
        assertTrue(a != c)
    }

    @Test
    fun `address normalization handles emails and formatting noise`() {
        assertEquals("tel:+15552223333", SmsPushBuilder.toRustAddress("+1 (555) 222-3333"))
        assertEquals("tel:+15552223333", SmsPushBuilder.toRustAddress("tel:+15552223333"))
        assertEquals("mailto:gate@carrier.com", SmsPushBuilder.toRustAddress("gate@carrier.com"))
    }

    @Test
    fun `mms builder emits text and attachment parts with deterministic indices`() {
        val push = SmsPushBuilder.buildIncomingMms(
            guid = SmsPushBuilder.mmsGuid(77),
            senderAddress = "+15552223333",
            participantAddresses = listOf("+15554445555"),
            text = "look at this",
            attachments = listOf(
                SmsPushBuilder.MmsAttachment("image/jpeg", "pic.jpg"),
                SmsPushBuilder.MmsAttachment("video/mp4", "clip.mp4"),
            ),
            timestampMs = 1_735_689_600_000L,
            myPhoneHandles = listOf(mePhone),
        )

        val inst = (push as UPushMessage.IMessage).inst
        assertEquals("mms-77", inst.id)
        val normal = inst.message as UMessage.Normal
        assertTrue(normal.isSms)
        assertEquals(3, normal.parts.size)
        assertEquals("look at this", (normal.parts[0].part as UPart.Text).text)
        val att0 = normal.parts[1].part as UPart.Attachment
        assertEquals("image/jpeg", att0.mime)
        assertEquals("public.jpeg", att0.uti)
        assertEquals(0uL, normal.parts[1].idx)
        assertEquals(1uL, normal.parts[2].idx)
        assertTrue(att0.xml.isEmpty())
        assertEquals(
            listOf("tel:+15552223333", "tel:+15554445555", mePhone),
            inst.conversation?.participants,
        )
    }

    @Test
    fun `uti mapping covers common mms media`() {
        assertEquals("public.jpeg", SmsPushBuilder.utiForMime("image/jpeg"))
        assertEquals("public.png", SmsPushBuilder.utiForMime("image/png"))
        assertEquals("public.movie", SmsPushBuilder.utiForMime("video/x-matroska"))
        assertEquals("public.data", SmsPushBuilder.utiForMime(null))
        assertEquals("public.data", SmsPushBuilder.utiForMime("application/octet-stream"))
        assertEquals("public.heic", SmsPushBuilder.utiForMime("IMAGE/HEIC; charset=utf-8"))
    }

    // ------------------------------------------------------------------
    // Ingest integration (builder output -> real MessageIngestor)
    // ------------------------------------------------------------------

    private fun messageByGuid(guid: String): Message? =
        store.boxFor(Message::class.java)
            .query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    /** The receivers ingest via the suspend entry point; tests call it the same way. */
    private fun ingest(push: UPushMessage): Chat? =
        kotlinx.coroutines.runBlocking { ingestor.ingest(push, myHandles) }

    @Test
    fun `incoming sms ingests into an isRpSms chat with sms service handle`() {
        val push = SmsPushBuilder.buildIncomingSms(
            senderAddress = "+15552223333",
            body = "on-device hello",
            timestampMs = 1_735_689_600_000L,
            myPhoneHandles = listOf(mePhone),
        )

        val chat = ingest(push)
        assertNotNull(chat)
        assertEquals(true, chat.isRpSms)
        val handle = chat.handles.single()
        assertEquals("+15552223333", handle.address)
        assertEquals("SMS", handle.service)
        assertTrue(chat.hasUnreadMessage)
        assertEquals(mePhone, chat.usingHandle)

        val inst = (push as UPushMessage.IMessage).inst
        val row = messageByGuid(inst.id)
        assertNotNull(row)
        assertEquals("on-device hello", row.text)
        assertEquals(false, row.isFromMe)
        assertEquals(inst.id, liveArrivalGuid(push, newlyIngested = true))
        assertNull(liveArrivalGuid(push, newlyIngested = false))
    }

    @Test
    fun `redelivered broadcast dedupes on the deterministic guid`() {
        val push = SmsPushBuilder.buildIncomingSms(
            senderAddress = "+15552223333",
            body = "dup check",
            timestampMs = 1_735_689_600_001L,
            myPhoneHandles = listOf(mePhone),
        )
        val first = kotlinx.coroutines.runBlocking { ingestor.ingestWithResult(push, myHandles) }
        val replay = kotlinx.coroutines.runBlocking { ingestor.ingestWithResult(push, myHandles) }

        val inst = (push as UPushMessage.IMessage).inst
        store.boxFor(Message::class.java)
            .query()
            .equal(Message_.guid, inst.id, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { assertEquals(1L, it.count()) }
        assertEquals(inst.id, liveArrivalGuid(push, first.isNewIncomingMessage))
        assertNull(liveArrivalGuid(push, replay.isNewIncomingMessage))
    }

    @Test
    fun `same sender lands in the same chat across messages`() {
        val first = ingest(
            SmsPushBuilder.buildIncomingSms("+15552223333", "one", 1_000L, listOf(mePhone)),
        )
        val second = ingest(
            SmsPushBuilder.buildIncomingSms("+15552223333", "two", 2_000L, listOf(mePhone)),
        )
        assertNotNull(first)
        assertNotNull(second)
        assertEquals(first.id, second.id)
        // Re-read: `first` is a detached copy from before the second ingest.
        val fresh = store.boxFor(Chat::class.java).get(first.id)
        assertEquals("two", fresh?.dbLatestMessage?.target?.text)
    }

    @Test
    fun `incoming mms ingests attachment metadata rows`() {
        val push = SmsPushBuilder.buildIncomingMms(
            guid = SmsPushBuilder.mmsGuid(5),
            senderAddress = "+15552223333",
            participantAddresses = emptyList(),
            text = null,
            attachments = listOf(SmsPushBuilder.MmsAttachment("image/jpeg", "pic.jpg")),
            timestampMs = 1_735_689_600_002L,
            myPhoneHandles = listOf(mePhone),
        )
        val chat = ingest(push)
        assertNotNull(chat)
        assertEquals(true, chat.isRpSms)

        val inst = (push as UPushMessage.IMessage).inst
        val row = messageByGuid(inst.id)
        assertNotNull(row)
        assertTrue(row.hasAttachments)

        val attachment = store.boxFor(Attachment::class.java)
            .query()
            .equal(Attachment_.guid, "${inst.id}_0", QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
        assertNotNull(attachment)
        assertEquals("image/jpeg", attachment.mimeType)
        assertEquals("pic.jpg", attachment.transferName)
    }
}

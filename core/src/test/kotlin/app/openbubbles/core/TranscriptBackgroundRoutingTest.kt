package app.openbubbles.core

import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.sync.TranscriptBackgroundHandler
import app.openbubbles.core.sync.TranscriptBackgroundUpdate
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.UPushMessage
import java.io.File
import kotlin.test.assertEquals

/**
 * Apple chat wallpapers are an iMessage feature, but a phone number usually
 * has two 1:1 chat rows: the SMS thread and the iMessage thread, both with
 * the same handle address and chatIdentifier. Wallpaper routing used to bind
 * whichever row the query returned first (typically the older SMS row), so
 * the poster was written to a chat the iMessage transcript never reads and
 * the background silently "didn't work". These tests pin that the wallpaper
 * lands on the iMessage row when both exist.
 */
class TranscriptBackgroundRoutingTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File

    private val phone = "+15551234567"
    private val myHandles = setOf("mailto:me@icloud.com")
    private val updates = mutableListOf<TranscriptBackgroundUpdate>()

    private lateinit var ingestor: MessageIngestor

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-bg-routing-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        updates.clear()
        ingestor = MessageIngestor(
            store,
            scope = CoroutineScope(Dispatchers.Unconfined),
            transcriptBackgroundHandler = TranscriptBackgroundHandler { update -> updates += update },
        )
    }

    @After
    fun tearDown() {
        ingestor.close()
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `wallpaper by peer address prefers the iMessage twin over the older SMS chat`() = runBlocking<Unit> {
        directChat(guid = "SMS;-;$phone", service = "SMS", isSms = true)
        val imessage = directChat(guid = "iMessage;-;$phone", service = "iMessage", isSms = false)

        ingestor.ingest(push(background(chatId = phone, version = 21uL)), myHandles)

        assertEquals(listOf(imessage.id), updates.map { it.chatId })
    }

    @Test
    fun `wallpaper by sender fallback prefers the iMessage twin over the older SMS chat`() = runBlocking<Unit> {
        directChat(guid = "SMS;-;$phone", service = "SMS", isSms = true)
        val imessage = directChat(guid = "iMessage;-;$phone", service = "iMessage", isSms = false)

        ingestor.ingest(push(background(chatId = null, version = 22uL)), myHandles)

        assertEquals(listOf(imessage.id), updates.map { it.chatId })
    }

    @Test
    fun `wallpaper still lands on the SMS chat when it is the only row`() = runBlocking<Unit> {
        val sms = directChat(guid = "SMS;-;$phone", service = "SMS", isSms = true)

        ingestor.ingest(push(background(chatId = phone, version = 23uL)), myHandles)

        assertEquals(listOf(sms.id), updates.map { it.chatId })
    }

    private fun directChat(guid: String, service: String, isSms: Boolean): Chat {
        val handle = Handle().apply {
            address = phone
            this.service = service
            uniqueAddressAndService = "$phone/$service"
        }
        store.boxFor(Handle::class.java).put(handle)
        val chat = Chat().apply {
            this.guid = guid
            chatIdentifier = phone
            isRpSms = isSms
            handles.add(handle)
        }
        store.boxFor(Chat::class.java).put(chat)
        return chat
    }

    private fun background(chatId: String?, version: ULong) = UMessageInst(
        id = "bg-$version",
        sender = "tel:$phone",
        conversation = null,
        message = UMessage.SetTranscriptBackground(
            json = "{}",
            version = version,
            chatId = chatId,
            remove = false,
            mmcsXml = "<mmcs/>",
        ),
        sentTimestamp = 1_700_000_600_000uL,
        sendDelivered = false,
        verificationFailed = false,
    )

    private fun push(inst: UMessageInst) = UPushMessage.IMessage(inst)
}

package app.openbubbles.nativeapp.service

import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.model.MessageStatus
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.db.Chat
import app.openbubbles.db.Handle
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import uniffi.rust_lib_bluebubbles.UConversation
import uniffi.rust_lib_bluebubbles.UIndexedPart
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.UPart
import uniffi.rust_lib_bluebubbles.UPushMessage

class NotificationReplySendTest {
    private lateinit var store: BoxStore
    private lateinit var testDir: File

    @BeforeTest
    fun setUp() {
        testDir = Files.createTempDirectory("ob-notification-reply").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
    }

    @AfterTest
    fun tearDown() {
        store.close()
        testDir.deleteRecursively()
    }

    @Test
    fun `notification reply is visible before network send completes`() = runBlocking {
        val friend = Handle().apply {
            address = "friend@icloud.com"
            service = "iMessage"
            uniqueAddressAndService = "$address/$service"
        }.also(store.boxFor(Handle::class.java)::put)
        val chat = Chat().apply {
            guid = "chat-guid"
            handles.add(friend)
        }.also(store.boxFor(Chat::class.java)::put)
        val messageRepo = MessageRepo(store)
        val ingestor = MessageIngestor(store)
        val myHandle = "mailto:me@icloud.com"
        val echo = outgoingEcho(chat.guid, myHandle)

        val result = sendAppleNotificationReply(
            store = store,
            chatGuid = chat.guid,
            sender = myHandle,
            text = "Reply from notification",
            send = {
                val staged = messageRepo.messages(chat.id).single()
                assertEquals("Reply from notification", staged.text)
                assertEquals(MessageStatus.SENDING, staged.status)
                assertTrue(staged.isFromMe)
                echo
            },
            ingest = { inst ->
                ingestor.ingest(UPushMessage.IMessage(inst), setOf(myHandle))
            },
        )

        assertNull(result.localEchoError)
        val saved = messageRepo.messages(chat.id).single()
        assertEquals(echo.id, saved.guid)
        assertEquals("Reply from notification", saved.text)
        assertEquals(MessageStatus.SENDING, saved.status)

        ingestor.ingest(UPushMessage.SendConfirm(uuid = echo.id, error = null), setOf(myHandle))

        val confirmed = messageRepo.messages(chat.id).single()
        assertEquals(echo.id, confirmed.guid)
        assertEquals(MessageStatus.SENT, confirmed.status)
    }

    @Test
    fun `notification reply waits for cold-start push restoration`() = runTest {
        val state = MutableStateFlow<String?>(null)
        var serviceStarts = 0
        backgroundScope.launch {
            kotlinx.coroutines.delay(1_000)
            state.value = "connected"
        }

        val restored = awaitNotificationReplyState(
            currentState = { state.value },
            startService = {
                serviceStarts += 1
                true
            },
            stateFlow = state,
            timeoutMs = 8_000,
        )

        assertEquals("connected", restored)
        assertEquals(1, serviceStarts)
    }

    @Test
    fun `notification reply does not wait when push is already connected`() = runTest {
        val state = MutableStateFlow<String?>("connected")
        var serviceStarts = 0

        val restored = awaitNotificationReplyState(
            currentState = { state.value },
            startService = {
                serviceStarts += 1
                true
            },
            stateFlow = state,
            timeoutMs = 8_000,
        )

        assertEquals("connected", restored)
        assertEquals(0, serviceStarts)
    }

    @Test
    fun `notification reply contains unexpected receiver failures`() = runTest {
        val failure = IllegalStateException("broken notification state")

        val caught = runNotificationReplySafely { throw failure }

        assertTrue(caught === failure)
    }

    private fun outgoingEcho(chatGuid: String, sender: String) = UMessageInst(
        id = "notification-reply-guid",
        sender = sender,
        conversation = UConversation(
            participants = listOf(sender, "mailto:friend@icloud.com"),
            cvName = null,
            senderGuid = chatGuid,
            afterGuid = chatGuid,
        ),
        message = UMessage.Normal(
            parts = listOf(UIndexedPart(UPart.Text("Reply from notification", ""), null, null)),
            effect = null,
            replyGuid = null,
            replyPart = null,
            subject = null,
            voice = false,
            isSms = false,
            appJson = null,
            linkJson = null,
        ),
        sentTimestamp = 1_700_000_000_000uL,
        sendDelivered = false,
        verificationFailed = false,
    )
}

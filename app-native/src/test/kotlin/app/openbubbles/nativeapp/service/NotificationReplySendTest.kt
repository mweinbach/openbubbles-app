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
import kotlinx.coroutines.runBlocking
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
        assertEquals(MessageStatus.SENT, saved.status)
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

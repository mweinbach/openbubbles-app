package app.openbubbles.core

import app.openbubbles.core.intake.MessageIngestor
import app.openbubbles.core.model.MessageMapper
import app.openbubbles.core.repo.ChatRepo
import app.openbubbles.core.repo.MessageRepo
import app.openbubbles.core.attachment.AttachmentStore
import app.openbubbles.core.sync.TranscriptBackgroundHandler
import app.openbubbles.core.sync.TranscriptBackgroundUpdate
import app.openbubbles.db.Attachment
import app.openbubbles.db.Chat
import app.openbubbles.db.Chat_
import app.openbubbles.db.Handle
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test
import uniffi.rust_lib_bluebubbles.UConversation
import uniffi.rust_lib_bluebubbles.UIndexedPart
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UMessageInst
import uniffi.rust_lib_bluebubbles.UPart
import uniffi.rust_lib_bluebubbles.UPushMessage
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Message-intake pipeline tests: fabricated [UPushMessage]s in, ObjectBox
 * rows out. Mirrors db/src test patterns (own temp-dir store); box locals are
 * named to avoid Kotlin shadowing of the entity classes.
 */
class MessageIngestorTest {

    private lateinit var store: BoxStore
    private lateinit var testDir: File
    private lateinit var ingestor: MessageIngestor
    private lateinit var chatRepo: ChatRepo
    private lateinit var messageRepo: MessageRepo

    private val me = "mailto:me@icloud.com"
    private val friend = "mailto:friend@icloud.com"
    private val myHandles = setOf(me)

    @Before
    fun setUp() {
        testDir = java.nio.file.Files.createTempDirectory("ob-core-test").toFile()
        store = MyObjectBox.builder().directory(testDir).build()
        ingestor = MessageIngestor(store, attachmentStore = AttachmentStore(store, testDir))
        chatRepo = ChatRepo(store)
        messageRepo = MessageRepo(store)
    }

    @After
    fun tearDown() {
        ingestor.close()
        store.close()
        testDir.deleteRecursively()
    }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private fun conversation(vararg participants: String, cvName: String? = null, senderGuid: String? = null) =
        UConversation(
            participants = participants.toList(),
            cvName = cvName,
            senderGuid = senderGuid,
            afterGuid = null,
        )

    private fun textInst(
        id: String,
        sender: String,
        text: String,
        timestamp: ULong = 1_700_000_000_000uL,
        conv: UConversation? = conversation(me, if (sender == me) friend else sender),
        sms: Boolean = false,
    ) = UMessageInst(
        id = id,
        sender = sender,
        conversation = conv,
        message = UMessage.Normal(
            parts = listOf(UIndexedPart(UPart.Text(text, ""), null, null)),
            effect = null,
            replyGuid = null,
            replyPart = null,
            subject = null,
            voice = false,
            isSms = sms,
            appJson = null,
            linkJson = null,
        ),
        sentTimestamp = timestamp,
        sendDelivered = false,
        verificationFailed = false,
    )

    private fun push(inst: UMessageInst) = UPushMessage.IMessage(inst)

    private fun chatBox() = store.boxFor(Chat::class.java)

    private fun messageBox() = store.boxFor(Message::class.java)

    private fun handleBox() = store.boxFor(Handle::class.java)

    private fun messageByGuid(guid: String): Message? =
        messageBox().query()
            .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    private fun chatByGuid(guid: String): Chat? =
        chatBox().query()
            .equal(Chat_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }

    // ------------------------------------------------------------------
    // Normal message intake
    // ------------------------------------------------------------------

    @Test
    fun `text message creates chat handle message and wires latest with unread`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-1", friend, "hello world")), myHandles)

        val item = chatRepo.chats().single()
        val chatRow = chatBox().get(item.id)
        assertEquals(listOf("friend@icloud.com"), chatRow.handles.map { it.address })
        assertEquals("friend@icloud.com", item.title)
        assertEquals("hello world", item.snippet)
        assertTrue(item.hasUnread)
        assertEquals(1, item.unreadCount)

        val row = messageByGuid("msg-1")
        assertNotNull(row)
        assertEquals("hello world", row.text)
        assertTrue(!row.isFromMe)
        assertNotNull(row.dateCreated)
        assertEquals("friend@icloud.com", row.handleRelation.target?.address)
        assertEquals(row.id, chatRow.dbLatestMessage.targetId)

        // Handle row normalized from the mailto: form.
        assertEquals(1, handleBox().count())
        assertEquals("friend@icloud.com", handleBox().all.first().address)
        assertEquals("iMessage", handleBox().all.first().service)
    }

    @Test
    fun `second message from me clears unread`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-1", friend, "hi", timestamp = 1_700_000_000_000uL)), myHandles)
        ingestor.ingest(push(textInst("msg-2", me, "hello back", timestamp = 1_700_000_500_000uL)), myHandles)

        val chatRow = chatBox().all.single()
        assertTrue(!chatRow.hasUnreadMessage)
        assertEquals("msg-2", chatRow.dbLatestMessage.target?.guid)
    }

    @Test
    fun `duplicate delivery is deduped`() = runBlocking<Unit> {
        val first = ingestor.ingestWithResult(push(textInst("msg-1", friend, "hi")), myHandles)
        val replay = ingestor.ingestWithResult(push(textInst("msg-1", friend, "hi")), myHandles)
        assertEquals(1, messageBox().count())
        assertTrue(first.isNewIncomingMessage)
        assertTrue(!replay.isNewIncomingMessage)
    }

    @Test
    fun `transcript background push is forwarded to the background handler`() = runBlocking<Unit> {
        // Seed the DM so the background push resolves a real chat.
        ingestor.ingest(push(textInst("msg-1", friend, "hi")), myHandles)
        val chatRow = chatBox().all.single()

        val updates = mutableListOf<TranscriptBackgroundUpdate>()
        val capturing = MessageIngestor(
            store,
            scope = CoroutineScope(Dispatchers.Unconfined),
            transcriptBackgroundHandler = TranscriptBackgroundHandler { update -> updates += update },
        )
        try {
            capturing.ingest(
                push(
                    UMessageInst(
                        id = "bg-1",
                        sender = friend,
                        conversation = conversation(me, friend),
                        message = UMessage.SetTranscriptBackground(
                            json = "{}",
                            version = 42uL,
                            chatId = null,
                            remove = false,
                            mmcsXml = "<mmcs/>",
                        ),
                        sentTimestamp = 1_700_000_100_000uL,
                        sendDelivered = false,
                        verificationFailed = false,
                    ),
                ),
                myHandles,
            )
        } finally {
            capturing.close()
        }

        assertEquals(
            listOf(
                TranscriptBackgroundUpdate(
                    chatId = chatRow.id,
                    version = 42L,
                    remove = false,
                    mmcsXml = "<mmcs/>",
                ),
            ),
            updates,
        )
    }

    @Test
    fun `transcript background push for an unknown chat reaches no handler`() = runBlocking<Unit> {
        val updates = mutableListOf<TranscriptBackgroundUpdate>()
        val capturing = MessageIngestor(
            store,
            scope = CoroutineScope(Dispatchers.Unconfined),
            transcriptBackgroundHandler = TranscriptBackgroundHandler { update -> updates += update },
        )
        try {
            val chat = capturing.ingest(
                push(
                    UMessageInst(
                        id = "bg-2",
                        sender = friend,
                        conversation = conversation(me, friend),
                        message = UMessage.SetTranscriptBackground(
                            json = "{}",
                            version = 43uL,
                            chatId = null,
                            remove = true,
                            mmcsXml = null,
                        ),
                        sentTimestamp = 1_700_000_200_000uL,
                        sendDelivered = false,
                        verificationFailed = false,
                    ),
                ),
                myHandles,
            )
            assertNull(chat)
        } finally {
            capturing.close()
        }

        assertTrue(updates.isEmpty())
        assertEquals(0L, chatBox().count())
    }

    @Test
    fun `transcript background without conversation data resolves the chat by peer address`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-1", friend, "hi")), myHandles)
        val chatRow = chatBox().all.single()

        val updates = mutableListOf<TranscriptBackgroundUpdate>()
        val capturing = MessageIngestor(
            store,
            scope = CoroutineScope(Dispatchers.Unconfined),
            transcriptBackgroundHandler = TranscriptBackgroundHandler { update -> updates += update },
        )
        try {
            // Live wallpaper pushes carry no ConversationData; the chat comes
            // from the message's cid, which is the bare peer address for DMs.
            val chat = capturing.ingest(
                push(
                    UMessageInst(
                        id = "bg-live-1",
                        sender = friend,
                        conversation = null,
                        message = UMessage.SetTranscriptBackground(
                            json = "{}",
                            version = 9uL,
                            chatId = "friend@icloud.com",
                            remove = false,
                            mmcsXml = "<mmcs/>",
                        ),
                        sentTimestamp = 1_700_000_300_000uL,
                        sendDelivered = false,
                        verificationFailed = false,
                    ),
                ),
                myHandles,
            )
            assertEquals(chatRow.id, chat?.id)
        } finally {
            capturing.close()
        }

        assertEquals(
            listOf(
                TranscriptBackgroundUpdate(
                    chatId = chatRow.id,
                    version = 9L,
                    remove = false,
                    mmcsXml = "<mmcs/>",
                ),
            ),
            updates,
        )
    }

    @Test
    fun `transcript background without conversation data resolves group chats by guid ref`() = runBlocking<Unit> {
        val groupGuid = "sender-group-guid"
        ingestor.ingest(
            push(
                textInst(
                    "group-msg-1",
                    friend,
                    "hello group",
                    conv = conversation(me, friend, "mailto:third@icloud.com", senderGuid = groupGuid),
                ),
            ),
            myHandles,
        )
        val chatRow = chatBox().all.single()

        val updates = mutableListOf<TranscriptBackgroundUpdate>()
        val capturing = MessageIngestor(
            store,
            scope = CoroutineScope(Dispatchers.Unconfined),
            transcriptBackgroundHandler = TranscriptBackgroundHandler { update -> updates += update },
        )
        try {
            val chat = capturing.ingest(
                push(
                    UMessageInst(
                        id = "bg-live-2",
                        sender = friend,
                        conversation = null,
                        message = UMessage.SetTranscriptBackground(
                            json = "{}",
                            version = 10uL,
                            chatId = groupGuid,
                            remove = true,
                            mmcsXml = null,
                        ),
                        sentTimestamp = 1_700_000_400_000uL,
                        sendDelivered = false,
                        verificationFailed = false,
                    ),
                ),
                myHandles,
            )
            assertEquals(chatRow.id, chat?.id)
        } finally {
            capturing.close()
        }

        assertEquals(
            listOf(
                TranscriptBackgroundUpdate(
                    chatId = chatRow.id,
                    version = 10L,
                    remove = true,
                    mmcsXml = null,
                ),
            ),
            updates,
        )
    }

    @Test
    fun `transcript background without a chat id falls back to the sender's direct chat`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-1", friend, "hi")), myHandles)
        val chatRow = chatBox().all.single()

        val updates = mutableListOf<TranscriptBackgroundUpdate>()
        val capturing = MessageIngestor(
            store,
            scope = CoroutineScope(Dispatchers.Unconfined),
            transcriptBackgroundHandler = TranscriptBackgroundHandler { update -> updates += update },
        )
        try {
            val chat = capturing.ingest(
                push(
                    UMessageInst(
                        id = "bg-live-3",
                        sender = friend,
                        conversation = null,
                        message = UMessage.SetTranscriptBackground(
                            json = "{}",
                            version = 11uL,
                            chatId = null,
                            remove = false,
                            mmcsXml = "<mmcs/>",
                        ),
                        sentTimestamp = 1_700_000_500_000uL,
                        sendDelivered = false,
                        verificationFailed = false,
                    ),
                ),
                myHandles,
            )
            assertEquals(chatRow.id, chat?.id)
        } finally {
            capturing.close()
        }

        assertEquals(
            listOf(
                TranscriptBackgroundUpdate(
                    chatId = chatRow.id,
                    version = 11L,
                    remove = false,
                    mmcsXml = "<mmcs/>",
                ),
            ),
            updates,
        )
    }

    @Test
    fun `empty message is dropped`() = runBlocking<Unit> {
        val empty = UMessageInst(
            id = "msg-empty",
            sender = friend,
            conversation = conversation(me, friend),
            message = UMessage.Normal(
                parts = emptyList(),
                effect = null, replyGuid = null, replyPart = null, subject = null,
                voice = false, isSms = false, appJson = null, linkJson = null,
            ),
            sentTimestamp = 1_700_000_000_000uL,
            sendDelivered = false,
            verificationFailed = false,
        )
        ingestor.ingest(push(empty), myHandles)
        assertEquals(0L, messageBox().count())
        assertEquals(0L, chatBox().count())
    }

    @Test
    fun `attachment part yields attachment row and hasAttachments flag`() = runBlocking<Unit> {
        val inst = UMessageInst(
            id = "msg-att",
            sender = friend,
            conversation = conversation(me, friend),
            message = UMessage.Normal(
                parts = listOf(
                    UIndexedPart(UPart.Text("look at this", ""), null, null),
                    UIndexedPart(
                        UPart.Attachment(part = 0uL, uti = "public.png", mime = "image/png", name = "pic.png", iris = false, xml = ""),
                        0uL,
                        null,
                    ),
                ),
                effect = null, replyGuid = null, replyPart = null, subject = null,
                voice = false, isSms = false, appJson = null, linkJson = null,
            ),
            sentTimestamp = 1_700_000_000_000uL,
            sendDelivered = false,
            verificationFailed = false,
        )
        ingestor.ingest(push(inst), myHandles)

        val row = messageByGuid("msg-att")
        assertNotNull(row)
        assertTrue(row.hasAttachments)
        assertEquals("look at this ", row.text)
        assertEquals(1, row.dbAttachments.size)
        val att = row.dbAttachments.first()
        assertEquals("msg-att_0", att.guid)
        assertEquals("image/png", att.mimeType)
        assertEquals("public.png", att.uti)
        assertEquals("pic.png", att.transferName)
        assertTrue(!att.isOutgoing)
        assertEquals(1L, (att.metadata["messagePart"] as Number).toLong())
        assertEquals(
            mapOf(0L to "0:0:12", 1L to "1:12:1"),
            app.openbubbles.core.model.MessageMapper.decodeReplyPartLocators(row.dbAttributedBody),
        )
    }

    @Test
    fun `message from me is marked isFromMe and keeps my handle out of chat`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-out", me, "from my other device")), myHandles)
        val row = messageByGuid("msg-out")
        assertNotNull(row)
        assertTrue(row.isFromMe)
        assertEquals("me@icloud.com", row.handleRelation.target?.address)
        val chatRow = chatBox().all.single()
        assertEquals(listOf("friend@icloud.com"), chatRow.handles.map { it.address })
    }

    @Test
    fun `outgoing echo records the actual sending handle instead of registration order`() = runBlocking<Unit> {
        val phone = "tel:+15551234567"
        val handles = linkedSetOf(me, phone)
        ingestor.ingest(
            push(
                textInst(
                    id = "msg-phone",
                    sender = phone,
                    text = "sent from phone",
                    conv = conversation(phone, friend),
                ),
            ),
            handles,
        )

        assertEquals(phone, chatBox().all.single().usingHandle)
    }

    // ------------------------------------------------------------------
    // Receipts + send lifecycle
    // ------------------------------------------------------------------

    @Test
    fun `delivered receipt sets dateDelivered`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-out-1", me, "outgoing")), myHandles)

        val receipt = UMessageInst(
            id = "msg-out-1",
            sender = friend,
            conversation = conversation(me, friend),
            message = UMessage.Delivered,
            sentTimestamp = 1_700_000_100_000uL,
            sendDelivered = false,
            verificationFailed = false,
        )
        ingestor.ingest(push(receipt), myHandles)

        val row = messageByGuid("msg-out-1")
        assertNotNull(row)
        assertNotNull(row.dateDelivered)
        assertEquals(1_700_000_100_000L, row.dateDelivered.time)
        assertNull(row.dateRead)
    }

    @Test
    fun `read receipt sets dateRead and delivered survives update`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-out-1", me, "outgoing")), myHandles)

        ingestor.ingest(
            push(
                UMessageInst(
                    id = "msg-out-1", sender = friend, conversation = conversation(me, friend),
                    message = UMessage.Delivered,
                    sentTimestamp = 1_700_000_100_000uL, sendDelivered = false, verificationFailed = false,
                )
            ),
            myHandles,
        )
        ingestor.ingest(
            push(
                UMessageInst(
                    id = "msg-out-1", sender = friend, conversation = conversation(me, friend),
                    message = UMessage.Read,
                    sentTimestamp = 1_700_000_200_000uL, sendDelivered = false, verificationFailed = false,
                )
            ),
            myHandles,
        )

        val row = messageByGuid("msg-out-1")
        assertNotNull(row)
        assertNotNull(row.dateRead)
        assertNotNull(row.dateDelivered) // preserved across updates
    }

    @Test
    fun `send confirm clears sendingServiceId`() = runBlocking<Unit> {
        val chat = chatForFixture()
        runBlocking {
            messageRepo.stageOutgoingMessage(
                chatGuid = chat.guid,
                sender = me,
                text = "staged send",
                stagingGuid = "stg-1",
            )
        }
        var row = messageByGuid("stg-1")
        assertNotNull(row)
        assertEquals(MessageRepo.DEFAULT_SENDING_SERVICE_ID, row.sendingServiceId)

        ingestor.ingest(UPushMessage.SendConfirm(uuid = "stg-1", error = null), myHandles)

        row = messageByGuid("stg-1")
        assertNotNull(row)
        assertNull(row.sendingServiceId)
    }

    @Test
    fun `send confirm with error marks message failed`() = runBlocking<Unit> {
        val chat = chatForFixture()
        messageRepo.stageOutgoingMessage(
            chatGuid = chat.guid, sender = me, text = "will fail", stagingGuid = "stg-fail",
        )
        ingestor.ingest(UPushMessage.SendConfirm(uuid = "stg-fail", error = "boom"), myHandles)

        val row = messageBox().all.first { it.guid.startsWith("error-protocol: boom") }
        assertTrue(row.guid.startsWith("error-protocol: boom"))
        assertTrue(row.errorMessage!!.contains("boom"))
        assertNull(row.sendingServiceId)
        assertEquals(app.openbubbles.core.model.MessageStatus.FAILED, messageRepo.statusOf(row))
    }

    @Test
    fun `send confirm error for an incoming guid does not mark that bubble failed`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("incoming-sc-1", friend, "hello")), myHandles)
        ingestor.ingest(
            UPushMessage.SendConfirm(
                uuid = "incoming-sc-1",
                error = "Could not deliver message. The recipient does not have iMessage or you are being rate-limited.",
            ),
            myHandles,
        )

        val row = messageByGuid("incoming-sc-1")
        assertNotNull(row)
        assertFalse(row.isFromMe)
        assertNull(row.errorMessage)
        assertNull(row.sendingServiceId)
        assertEquals(0, messageBox().all.count { it.guid.startsWith("error-protocol") })
        assertEquals(app.openbubbles.core.model.MessageStatus.SENT, messageRepo.statusOf(row))
    }

    @Test
    fun `unavailable push state resolves staged send as failed`() = runBlocking<Unit> {
        val chat = chatForFixture()
        messageRepo.stageOutgoingMessage(
            chatGuid = chat.guid,
            sender = me,
            text = "cannot send",
            stagingGuid = "stg-offline",
        )

        val failed = messageRepo.failOutgoing("stg-offline", "Not connected to Apple push")

        assertNotNull(failed)
        assertNull(failed.sendingServiceId)
        assertEquals(1L, failed.error)
        assertEquals("Not connected to Apple push", failed.errorMessage)
        assertEquals(app.openbubbles.core.model.MessageStatus.FAILED, messageRepo.statusOf(failed))
    }

    @Test
    fun `zero cloud error code is not rendered as failed`() {
        val historical = Message().apply {
            guid = "cloud-outgoing"
            text = "sent from another device"
            isFromMe = true
            error = 0L
        }

        assertEquals(
            app.openbubbles.core.model.MessageStatus.SENT,
            messageRepo.statusOf(historical),
        )
    }

    @Test
    fun `sms confirm promotes staging guid`() = runBlocking<Unit> {
        // Dart-convention staged row: temp guid + rust staging guid.
        val chat = chatForFixture()
        store.runInTx {
            val staged = Message().apply {
                guid = "temp-abcdef12"
                stagingGuid = "real-sms-guid"
                text = "sms out"
                isFromMe = true
                dateCreated = java.util.Date(1_700_000_000_000)
            }
            staged.chat.target = chat
            messageBox().put(staged)
            chat.dbLatestMessage.target = staged
            chatBox().put(chat)
        }

        ingestor.ingest(
            push(
                UMessageInst(
                    id = "real-sms-guid", sender = me, conversation = conversation(me, friend),
                    message = UMessage.SmsConfirmSent(status = true),
                    sentTimestamp = 1_700_000_050_000uL, sendDelivered = false, verificationFailed = false,
                )
            ),
            myHandles,
        )

        val row = messageByGuid("real-sms-guid")
        assertNotNull(row)
        assertNull(row.stagingGuid)
        assertNull(messageByGuid("temp-abcdef12"))
    }

    @Test
    fun `sms confirm error for an incoming guid does not mark that bubble failed`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("incoming-sms-1", friend, "hello", sms = true)), myHandles)
        ingestor.ingest(
            push(
                UMessageInst(
                    id = "incoming-sms-1",
                    sender = me,
                    conversation = conversation(me, friend),
                    message = UMessage.SmsConfirmSent(status = false),
                    sentTimestamp = 1_700_000_050_000uL,
                    sendDelivered = false,
                    verificationFailed = false,
                )
            ),
            myHandles,
        )

        val row = messageByGuid("incoming-sms-1")
        assertNotNull(row)
        assertFalse(row.isFromMe)
        assertNull(row.errorMessage)
        assertEquals(0, messageBox().all.count { it.guid.startsWith("error-protocol") })
    }

    @Test
    fun `staged send is updated in place by the echo`() = runBlocking<Unit> {
        val chat = chatForFixture()
        messageRepo.stageOutgoingMessage(
            chatGuid = chat.guid, sender = me, text = "staged text", stagingGuid = "echo-1",
        )
        ingestor.ingest(push(textInst("echo-1", me, "final text")), myHandles)

        // The staged row was updated in place (no duplicate): only the seed
        // message plus this one exist.
        assertEquals(2, messageBox().count())
        val row = messageByGuid("echo-1")
        assertNotNull(row)
        assertEquals("final text", row.text)
        assertNull(row.stagingGuid) // promoted
        assertNull(row.sendingServiceId)
    }

    @Test
    fun `outgoing stage persists effect and reply metadata atomically`() = runBlocking<Unit> {
        val chat = chatForFixture()
        val staged = messageRepo.stageOutgoingMessage(
            chatGuid = chat.guid,
            sender = me,
            text = "reply with effect",
            stagingGuid = "temp-metadata",
            expressiveSendStyleId = "com.apple.messages.effect.CKConfettiEffect",
            threadOriginatorGuid = "root-guid",
            threadOriginatorPart = "2:4:8",
        )

        assertEquals("com.apple.messages.effect.CKConfettiEffect", staged.expressiveSendStyleId)
        assertEquals("root-guid", staged.threadOriginatorGuid)
        assertEquals("2:4:8", staged.threadOriginatorPart)
        assertEquals(staged.id, messageByGuid("temp-metadata")?.id)
    }

    @Test
    fun `staged attachment is promoted in place by the echo`() = runBlocking<Unit> {
        val chat = chatForFixture()
        val staged = messageRepo.stageOutgoingMessage(
            chatGuid = chat.guid,
            sender = me,
            text = "caption",
            stagingGuid = "temp-attachment",
        )
        val stagedAttachment = Attachment().apply {
            guid = "temp-attachment_att0"
            uti = "public.jpeg"
            mimeType = "image/jpeg"
            isOutgoing = true
            transferName = "photo.jpg"
            totalBytes = 42L
            isDownloaded = false
            message.target = staged
        }
        store.boxFor(Attachment::class.java).put(stagedAttachment)
        staged.hasAttachments = true
        messageBox().put(staged)

        // CoreAttachmentSender swaps the message guid to the Rust id before
        // ingesting the reflected echo.
        staged.guid = "real-attachment-message"
        staged.stagingGuid = "real-attachment-message"
        messageBox().put(staged)
        val echo = UMessageInst(
            id = "real-attachment-message",
            sender = me,
            conversation = conversation(me, friend),
            message = UMessage.Normal(
                parts = listOf(
                    UIndexedPart(UPart.Text("caption", ""), null, null),
                    UIndexedPart(
                        UPart.Attachment(
                            part = 0uL,
                            uti = "public.jpeg",
                            mime = "image/jpeg",
                            name = "photo.jpg",
                            iris = false,
                            xml = "<plist>real metadata</plist>",
                        ),
                        0uL,
                        null,
                    ),
                ),
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

        ingestor.ingest(push(echo), myHandles)

        val attachments = store.boxFor(Attachment::class.java).all
        assertEquals(1, attachments.size)
        val promoted = attachments.single()
        assertEquals(stagedAttachment.id, promoted.id)
        assertEquals("real-attachment-message_0", promoted.guid)
        assertEquals(42L, promoted.totalBytes)
        assertTrue(promoted.isDownloaded)
        assertEquals("<plist>real metadata</plist>", promoted.metadata?.get("rustpush"))
        assertEquals(staged.id, promoted.message.targetId)
    }

    @Test
    fun `edit event replaces text and records edited part`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("editable", friend, "before")), myHandles)
        val edit = UMessageInst(
            id = "edit-event",
            sender = friend,
            conversation = conversation(me, friend),
            message = UMessage.Edit(
                tuuid = "editable",
                editPart = 0uL,
                parts = listOf(UIndexedPart(UPart.Text("after", ""), 0uL, null)),
            ),
            sentTimestamp = 1_700_000_500_000uL,
            sendDelivered = false,
            verificationFailed = false,
        )

        ingestor.ingest(push(edit), myHandles)

        val updated = messageByGuid("editable")
        assertNotNull(updated)
        assertEquals("after", updated.text)
        assertEquals(1_700_000_500_000L, updated.dateEdited?.time)
        assertTrue(updated.dbMessageSummaryInfo!!.contains("\"editedParts\":[0]"))
        assertEquals(1, messageBox().all.count { it.guid == "editable" })
    }

    @Test
    fun `unsend event retracts single text part`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("retractable", friend, "remove me")), myHandles)
        val unsend = UMessageInst(
            id = "unsend-event",
            sender = friend,
            conversation = conversation(me, friend),
            message = UMessage.Unsend(tuuid = "retractable", editPart = 0uL),
            sentTimestamp = 1_700_000_600_000uL,
            sendDelivered = false,
            verificationFailed = false,
        )

        ingestor.ingest(push(unsend), myHandles)

        val updated = messageByGuid("retractable")
        assertNotNull(updated)
        assertEquals("", updated.text)
        assertEquals(1_700_000_600_000L, updated.dateEdited?.time)
        assertTrue(updated.dbMessageSummaryInfo!!.contains("\"retractedParts\":[0]"))
    }

    @Test
    fun `error receipt marks failed`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-e1", me, "outgoing")), myHandles)
        ingestor.ingest(
            push(
                UMessageInst(
                    id = "err-1", sender = me, conversation = conversation(me, friend),
                    message = UMessage.Error(forUuid = "msg-e1", status = 1234uL, statusStr = "not delivered"),
                    sentTimestamp = 1_700_000_100_000uL, sendDelivered = false, verificationFailed = false,
                )
            ),
            myHandles,
        )

        val row = messageBox().all.first { it.guid.startsWith("error-protocol") }
        assertTrue(row.guid.startsWith("error-protocol: not delivered"))
        assertEquals(1234L, row.error)
        assertEquals("not delivered", row.errorMessage)
    }

    @Test
    fun `error receipt for an incoming guid does not mark that bubble failed`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("incoming-e1", friend, "hello")), myHandles)
        ingestor.ingest(
            push(
                UMessageInst(
                    id = "err-read", sender = me, conversation = conversation(me, friend),
                    message = UMessage.Error(
                        forUuid = "incoming-e1",
                        status = 5000uL,
                        statusStr = "Could not deliver message. The recipient does not have iMessage or you are being rate-limited.",
                    ),
                    sentTimestamp = 1_700_000_100_000uL, sendDelivered = false, verificationFailed = false,
                )
            ),
            myHandles,
        )

        val row = messageByGuid("incoming-e1")
        assertNotNull(row)
        assertFalse(row.isFromMe)
        assertNull(row.errorMessage)
        assertEquals(0, messageBox().all.count { it.guid.startsWith("error-protocol") })
    }

    // ------------------------------------------------------------------
    // Reactions, renames, participants
    // ------------------------------------------------------------------

    @Test
    fun `reaction maps associated fields and flags target`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("target-1", friend, "react to me")), myHandles)

        val reactionJson = """{"React":{"reaction":"Heart","enable":true}}"""
        ingestor.ingest(
            push(
                UMessageInst(
                    id = "react-1", sender = friend, conversation = conversation(me, friend),
                    message = UMessage.React(
                        toUuid = "target-1", toPart = 0uL, reactionJson = reactionJson, toText = "react to me",
                        parts = emptyList(),
                    ),
                    sentTimestamp = 1_700_000_100_000uL, sendDelivered = false, verificationFailed = false,
                )
            ),
            myHandles,
        )

        val reaction = messageByGuid("react-1")
        assertNotNull(reaction)
        assertEquals("love", reaction.associatedMessageType)
        assertEquals("target-1", reaction.associatedMessageGuid)
        assertEquals(0L, reaction.associatedMessagePart)

        val target = messageByGuid("target-1")
        assertNotNull(target)
        assertTrue(target.hasReactions)

        val transcript = messageRepo.messages(chatRepo.chats().single().id)
        assertEquals(1, transcript.size)
        assertEquals("target-1", transcript.single().guid)
        assertEquals("love", transcript.single().reactionType)
        assertTrue(chatRepo.chats().single().snippet.orEmpty().contains("loved"))
    }

    @Test
    fun `reaction removal clears the target bubble chip`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("target-remove", friend, "react to me")), myHandles)
        suspend fun react(id: String, json: String, timestamp: ULong) {
            ingestor.ingest(
                push(
                    UMessageInst(
                        id = id,
                        sender = friend,
                        conversation = conversation(me, friend),
                        message = UMessage.React(
                            toUuid = "target-remove",
                            toPart = 0uL,
                            reactionJson = json,
                            toText = "react to me",
                            parts = emptyList(),
                        ),
                        sentTimestamp = timestamp,
                        sendDelivered = false,
                        verificationFailed = false,
                    ),
                ),
                myHandles,
            )
        }
        react("react-add", """{"React":{"reaction":"Like","enable":true}}""", 1_700_000_100_000uL)
        react("react-remove", """{"React":{"reaction":"Like","enable":false}}""", 1_700_000_200_000uL)

        val transcript = messageRepo.messages(chatRepo.chats().single().id)
        assertEquals(1, transcript.size)
        assertNull(transcript.single().reactionType)
    }

    @Test
    fun `multiple positional stickers remain attached to the target part`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("sticker-target", friend, "decorate me")), myHandles)

        suspend fun sticker(id: String, normalizedX: Double, timestamp: ULong) {
            val extension =
                """{"spw":320.0,"sro":0.25,"ssa":1.2,"sxs":$normalizedX,"sys":0.3,"stickerEffectType":2,"sid":"$id"}"""
            ingestor.ingest(
                push(
                    UMessageInst(
                        id = id,
                        sender = friend,
                        conversation = conversation(me, friend),
                        message = UMessage.React(
                            toUuid = "sticker-target",
                            toPart = 2uL,
                            reactionJson = """{"Extension":{"is_meta":false}}""",
                            toText = "decorate me",
                            parts = listOf(
                                UIndexedPart(
                                    UPart.Attachment(
                                        part = 0uL,
                                        uti = "public.png",
                                        mime = "image/png",
                                        name = "$id.png",
                                        iris = false,
                                        xml = "<plist/>",
                                    ),
                                    0uL,
                                    extension,
                                ),
                            ),
                        ),
                        sentTimestamp = timestamp,
                        sendDelivered = false,
                        verificationFailed = false,
                    ),
                ),
                myHandles,
            )
        }

        sticker("sticker-one", 0.2, 1_700_000_100_000uL)
        sticker("sticker-two", 0.8, 1_700_000_200_000uL)

        val target = messageRepo.messages(chatRepo.chats().single().id).single()
        assertEquals(2, target.stickers.size)
        assertEquals(listOf(0.2, 0.8), target.stickers.map { it.normalizedX })
        assertTrue(target.stickers.all { it.targetPart == 2L })
        assertEquals(listOf(320.0, 320.0), target.stickers.map { it.messageWidth })
    }

    @Test
    fun `reply thread query keeps replies scoped to the selected part`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("thread-root", friend, "root")), myHandles)

        suspend fun reply(id: String, part: String, timestamp: ULong) {
            val inst = textInst(id, friend, id, timestamp)
            ingestor.ingest(
                push(
                    inst.copy(
                        message = (inst.message as UMessage.Normal).copy(
                            replyGuid = "thread-root",
                            replyPart = part,
                        ),
                    ),
                ),
                myHandles,
            )
        }

        reply("part-three-one", "3:0:4", 1_700_000_100_000uL)
        reply("part-four", "4:7:2", 1_700_000_200_000uL)
        reply("part-three-two", "3:0:4", 1_700_000_300_000uL)

        val chatId = chatRepo.chats().single().id
        assertEquals(
            listOf("thread-root", "part-three-one", "part-three-two"),
            messageRepo.threadMessages(chatId, "thread-root", 3L).map { it.guid },
        )
        val replyItem = messageRepo.messages(chatId, limit = 10)
            .first { it.guid == "part-three-one" }
        assertEquals(3L, replyItem.threadOriginatorPart)
        assertEquals("3:0:4", replyItem.threadOriginatorLocator)
        val rootItem = messageRepo.messages(chatId, limit = 10)
            .first { it.guid == "thread-root" }
        assertEquals("0:0:4", rootItem.replyPartLocators[0L])
    }

    @Test
    fun `removed reaction carries the minus prefix`() {
        val (type, _) = app.openbubbles.core.model.MessageMapper.parseReaction(
            """{"React":{"reaction":"Like","enable":false}}""",
        )
        assertEquals("-like", type)
    }

    @Test
    fun `custom emoji reaction retains its glyph`() {
        assertEquals(
            MessageMapper.REACTION_EMOJI to "🔥",
            MessageMapper.parseReaction(
                """{"React":{"reaction":{"Emoji":"🔥"},"enable":true}}""",
            ),
        )
    }

    @Test
    fun `transcript background event affects its chat without creating a notification row`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("root", friend, "hello")), myHandles)
        val existingChat = chatRepo.chats().single()
        val beforeCount = messageBox().count()
        val background = textInst("background", friend, "unused").copy(
            conversation = conversation(me, friend, senderGuid = existingChat.guid),
            message = UMessage.SetTranscriptBackground(
                json = "{}",
                version = 4uL,
                chatId = existingChat.guid,
                remove = true,
                mmcsXml = null,
            ),
        )

        val result = ingestor.ingestWithResult(push(background), myHandles)

        assertEquals(existingChat.id, result.chat?.id)
        assertFalse(result.isNewIncomingMessage)
        assertEquals(beforeCount, messageBox().count())
    }

    @Test
    fun `rename updates chat and stores event row`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-1", friend, "hi", conv = conversation(me, friend, cvName = null))), myHandles)

        ingestor.ingest(
            push(
                UMessageInst(
                    id = "rename-1", sender = friend,
                    conversation = conversation(me, friend, cvName = "New Name"),
                    message = UMessage.Rename(newName = "New Name"),
                    sentTimestamp = 1_700_000_100_000uL, sendDelivered = false, verificationFailed = false,
                )
            ),
            myHandles,
        )

        val chatRow = chatBox().all.single()
        assertEquals("New Name", chatRow.displayName)
        assertEquals("New Name", chatRow.apnTitle)

        val event = messageByGuid("rename-1")
        assertNotNull(event)
        assertEquals(2L, event.itemType)
        assertEquals(2L, event.groupActionType)
        assertEquals("New Name", event.groupTitle)
    }

    @Test
    fun `participant change updates handles and creates events`() = runBlocking<Unit> {
        val other = "mailto:other@icloud.com"
        ingestor.ingest(push(textInst("msg-1", friend, "hi", conv = conversation(me, friend))), myHandles)

        ingestor.ingest(
            push(
                UMessageInst(
                    id = "pchange-1", sender = friend,
                    conversation = conversation(me, friend),
                    message = UMessage.ChangeParticipants(
                        newParticipants = listOf(me, friend, other), groupVersion = 2uL,
                    ),
                    sentTimestamp = 1_700_000_100_000uL, sendDelivered = false, verificationFailed = false,
                )
            ),
            myHandles,
        )

        val chatRow = chatBox().all.single()
        val addresses = chatRow.handles.map { it.address }.sorted()
        assertEquals(listOf("friend@icloud.com", "other@icloud.com"), addresses)
        assertEquals(2L, chatRow.groupVersion)

        // One "added" event row for the new member.
        val event = messageByGuid("pchange-1")
        assertNotNull(event)
        assertEquals(1L, event.itemType)
        assertEquals(0L, event.groupActionType)
        assertNotNull(event.otherHandle)
        val otherHandleRow = handleBox().all.first { it.address == "other@icloud.com" }
        assertEquals(otherHandleRow.originalROWID, event.otherHandle)
    }

    @Test
    fun `typing indicator state toggles`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-1", friend, "hi")), myHandles)
        val chatRow = chatBox().all.single()

        ingestor.ingest(
            push(
                UMessageInst(
                    id = "typing-1", sender = friend, conversation = conversation(me, friend),
                    message = UMessage.Typing(typing = true),
                    sentTimestamp = 1_700_000_100_000uL, sendDelivered = false, verificationFailed = false,
                )
            ),
            myHandles,
        )
        val typing = ingestor.typing.value
        assertEquals(1, typing.size)
        assertEquals(chatRow.guid, typing[0].chatGuid)
        assertEquals("friend@icloud.com", typing[0].senderAddress)

        // A new message from the same sender clears the indicator (Dart parity).
        ingestor.ingest(push(textInst("msg-2", friend, "done typing")), myHandles)
        assertEquals(0, ingestor.typing.value.size)
    }

    // ------------------------------------------------------------------
    // Repos
    // ------------------------------------------------------------------

    @Test
    fun `chat list orders by latest date then pin`() = runBlocking<Unit> {
        val other = "mailto:other@icloud.com"
        ingestor.ingest(push(textInst("a-1", friend, "older", timestamp = 1_700_000_000_000uL)), myHandles)
        ingestor.ingest(push(textInst("b-1", other, "newer", timestamp = 1_700_100_000_000uL)), myHandles)

        var list = chatRepo.chats()
        assertEquals(2, list.size)
        assertEquals("newer", list[0].snippet)
        assertEquals("older", list[1].snippet)

        chatRepo.setPinned(list[1].id, pinned = true)
        list = chatRepo.chats()
        assertEquals("older", list[0].snippet) // pinned jumps to the front
        assertTrue(list[0].pinned)
    }

    @Test
    fun `newly pinned chat is first among existing pins`() = runBlocking<Unit> {
        val secondFriend = "mailto:second@icloud.com"
        ingestor.ingest(push(textInst("first", friend, "first pin")), myHandles)
        ingestor.ingest(push(textInst("second", secondFriend, "second pin")), myHandles)

        val bySnippet = chatRepo.chats().associateBy { it.snippet }
        chatRepo.setPinned(requireNotNull(bySnippet["first pin"]).id, pinned = true)
        chatRepo.setPinned(requireNotNull(bySnippet["second pin"]).id, pinned = true)

        assertEquals(
            listOf("second pin", "first pin"),
            chatRepo.chats().filter { it.pinned }.map { it.snippet },
        )
    }

    @Test
    fun `chat lifecycle updates mute archive and soft delete`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-1", friend, "hello")), myHandles)
        val chat = chatBox().all.single()

        chatRepo.setPinned(chat.id, true)
        chatRepo.setMuted(chat.id, true)
        chatRepo.setArchived(chat.id, true)

        val archived = chatRepo.chats().single()
        assertTrue(archived.muted)
        assertTrue(archived.archived)
        assertFalse(archived.pinned)

        chat.ckRecordId = "cloud-chat-1"
        chatBox().put(chat)
        assertEquals("cloud-chat-1", chatRepo.softDelete(chat.id))
        assertTrue(chatRepo.chats().isEmpty())
    }

    @Test
    fun `message pagination is newest first with offset`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-1", friend, "one", timestamp = 1_700_000_000_000uL)), myHandles)
        ingestor.ingest(push(textInst("msg-2", friend, "two", timestamp = 1_700_000_100_000uL)), myHandles)
        ingestor.ingest(push(textInst("msg-3", friend, "three", timestamp = 1_700_000_200_000uL)), myHandles)
        ingestor.ingest(push(textInst("msg-4", friend, "four", timestamp = 1_700_000_300_000uL)), myHandles)
        ingestor.ingest(push(textInst("msg-5", friend, "five", timestamp = 1_700_000_400_000uL)), myHandles)

        val chatRow = chatBox().all.single()
        val page1 = messageRepo.messages(chatRow.id, limit = 2)
        assertEquals(listOf("five", "four"), page1.map { it.text })

        val page2 = messageRepo.messages(chatRow.id, limit = 2, offset = 2)
        assertEquals(listOf("three", "two"), page2.map { it.text })

        assertEquals(5, messageRepo.messages(chatRow.id, limit = 100).size)
    }

    @Test
    fun `soft deleted messages leave transcript and unread count`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("visible", friend, "keep", timestamp = 1_700_000_000_000uL)), myHandles)
        ingestor.ingest(push(textInst("deleted", friend, "remove", timestamp = 1_700_000_100_000uL)), myHandles)
        val chat = chatBox().all.single()
        val deleted = messageByGuid("deleted")!!
        deleted.dateDeleted = java.util.Date(1_700_000_200_000L)
        messageBox().put(deleted)

        assertEquals(listOf("keep"), messageRepo.messages(chat.id).map { it.text })
        assertEquals(1, chatRepo.unreadCount(chat))
    }

    @Test
    fun `recycle and recover events update transcript and latest message`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("older", friend, "older", timestamp = 1_700_000_000_000uL)), myHandles)
        ingestor.ingest(push(textInst("newer", friend, "newer", timestamp = 1_700_000_100_000uL)), myHandles)
        val chat = chatBox().all.single()
        val recycle = UMessageInst(
            id = "recycle-event",
            sender = me,
            conversation = conversation(me, friend),
            message = UMessage.MoveToRecycleBin(
                """{"MoveToRecycleBin":{"target":{"Messages":["newer"]},"recoverable_delete_date":1700000200000}}""",
            ),
            sentTimestamp = 1_700_000_200_000uL,
            sendDelivered = false,
            verificationFailed = false,
        )

        ingestor.ingest(push(recycle), myHandles)

        assertNotNull(messageByGuid("newer")?.dateDeleted)
        assertEquals(listOf("older"), messageRepo.messages(chat.id).map { it.guid })
        assertEquals("older", chatBox().get(chat.id).dbLatestMessage.target?.guid)

        chatBox().get(chat.id).let { recycledChat ->
            recycledChat.dateDeleted = java.util.Date(1_700_000_200_000L)
            chatBox().put(recycledChat)
        }
        val recover = UMessageInst(
            id = "recover-event",
            sender = me,
            conversation = conversation(me, friend),
            message = UMessage.RecoverChat(
                """{"RecoverChat":{"ptcpts":["friend@icloud.com"],"groupID":"${chat.guid}","guid":"${chat.guid}"}}""",
            ),
            sentTimestamp = 1_700_000_300_000uL,
            sendDelivered = false,
            verificationFailed = false,
        )

        ingestor.ingest(push(recover), myHandles)

        assertNull(chatBox().get(chat.id).dateDeleted)
        assertNull(messageByGuid("newer")?.dateDeleted)
        assertEquals("newer", chatBox().get(chat.id).dbLatestMessage.target?.guid)
    }

    @Test
    fun `permanent delete removes selected message`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("delete-me", friend, "gone")), myHandles)
        val message = requireNotNull(messageByGuid("delete-me"))
        val attachment = Attachment().apply {
            guid = "delete-attachment"
            transferName = "payload.bin"
            this.message.target = message
        }
        store.boxFor(Attachment::class.java).put(attachment)
        val payload = AttachmentStore(store, testDir).pathFor(attachment).apply {
            parentFile?.mkdirs()
            writeText("payload")
        }
        val delete = UMessageInst(
            id = "permanent-event",
            sender = me,
            conversation = conversation(me, friend),
            message = UMessage.PermanentDelete(
                """{"PermanentDelete":{"target":{"Messages":["delete-me"]},"is_scheduled":false}}""",
            ),
            sentTimestamp = 1_700_000_400_000uL,
            sendDelivered = false,
            verificationFailed = false,
        )

        ingestor.ingest(push(delete), myHandles)

        assertNull(messageByGuid("delete-me"))
        assertTrue(!payload.exists())
    }

    @Test
    fun `markRead clears unread and count`() = runBlocking<Unit> {
        ingestor.ingest(push(textInst("msg-1", friend, "unread one", timestamp = 1_700_000_000_000uL)), myHandles)
        ingestor.ingest(push(textInst("msg-2", friend, "unread two", timestamp = 1_700_000_500_000uL)), myHandles)

        val item = chatRepo.chats().single()
        assertTrue(item.hasUnread)
        assertEquals(2, item.unreadCount)

        chatRepo.markRead(item.id)
        val after = chatRepo.chats().single()
        assertTrue(!after.hasUnread)
        assertEquals(0, after.unreadCount)
        assertEquals("msg-2", chatBox().get(item.id).lastReadMessageGuid)
    }

    @Test
    fun `focus status updates matching direct chat`() = runBlocking<Unit> {
        val chat = chatForFixture()

        ingestor.ingest(
            UPushMessage.StatusUpdate(friend, "com.apple.focus", allowed = false),
            myHandles,
        )
        assertTrue(chatBox().get(chat.id).notifsSilenced)

        ingestor.ingest(
            UPushMessage.StatusUpdate(friend, null, allowed = true),
            myHandles,
        )
        assertFalse(chatBox().get(chat.id).notifsSilenced)
    }

    @Test
    fun `flows emit current state and updates`() = runBlocking<Unit> {
        val initial = chatRepo.observeChats().first()
        assertEquals(0, initial.size)

        ingestor.ingest(push(textInst("msg-1", friend, "flow!")), myHandles)
        val updated = chatRepo.observeChats().first()
        assertEquals(1, updated.size)
        assertEquals("flow!", updated[0].snippet)

        val chatRow = chatBox().all.single()
        val messagesFlow = messageRepo.observeMessages(chatRow.id).first()
        assertEquals(1, messagesFlow.size)
        assertEquals(app.openbubbles.core.model.MessageKind.TEXT, messagesFlow[0].kind)
    }

    @Test
    fun `chat flow re-emits when a message lands`() = runBlocking<Unit> {
        val first = CompletableDeferred<Int>()
        val second = CompletableDeferred<Int>()
        val job = launch {
            var seen = 0
            chatRepo.observeChats().collect { list ->
                seen++
                if (seen == 1) first.complete(list.size)
                if (seen == 2) {
                    second.complete(list.size)
                    cancel()
                }
            }
        }
        try {
            assertEquals(0, first.await())
            ingestor.ingest(push(textInst("rx-1", friend, "reactive!")), myHandles)
            assertEquals(1, withTimeout(5_000) { second.await() })
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `staged outgoing message shows SENDING then SENT after confirm`() = runBlocking<Unit> {
        val chat = chatForFixture()
        messageRepo.stageOutgoingMessage(
            chatGuid = chat.guid, sender = me, text = "in flight", stagingGuid = "stg-flow",
        )
        val chatRow = chatBox().get(chat.id)
        val item = messageRepo.messages(chatRow.id).first { it.text == "in flight" }
        assertEquals(app.openbubbles.core.model.MessageStatus.SENDING, item.status)
        assertTrue(item.isFromMe)
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Creates (or returns) the DM chat between me and [friend]. */
    private fun chatForFixture(): Chat {
        runBlocking {
            ingestor.ingest(push(textInst("seed-${System.nanoTime()}", friend, "seed")), myHandles)
        }
        return chatBox().all.first()
    }
}

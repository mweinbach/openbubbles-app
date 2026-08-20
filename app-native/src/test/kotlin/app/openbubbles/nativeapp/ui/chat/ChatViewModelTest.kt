package app.openbubbles.nativeapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import app.openbubbles.nativeapp.data.AttachmentSender
import app.openbubbles.nativeapp.data.ChatListItem
import app.openbubbles.nativeapp.data.ChatListRepository
import app.openbubbles.nativeapp.data.FaceTimeCaller
import app.openbubbles.nativeapp.data.FaceTimeLaunch
import app.openbubbles.nativeapp.data.MessageActions
import app.openbubbles.nativeapp.data.MessageItem
import app.openbubbles.nativeapp.data.MessageListRepository
import app.openbubbles.nativeapp.data.MessageStatus
import app.openbubbles.nativeapp.data.OutgoingAttachment
import app.openbubbles.nativeapp.data.OutgoingAttachmentSend
import app.openbubbles.nativeapp.data.OutgoingStickerSend
import app.openbubbles.nativeapp.data.OutgoingTextSend
import app.openbubbles.nativeapp.data.ReadReceiptSender
import app.openbubbles.nativeapp.data.Sender
import app.openbubbles.nativeapp.data.SmsSender
import app.openbubbles.nativeapp.data.StickerSender
import app.openbubbles.nativeapp.data.StickerPlacement
import app.openbubbles.nativeapp.data.StickerTransform
import app.openbubbles.nativeapp.data.TypingEntry
import app.openbubbles.nativeapp.data.TypingRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val viewModelStore = ViewModelStore()
    private var nextViewModelKey = 0

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        viewModelStore.clear()
        Dispatchers.resetMain()
    }

    @Test
    fun `prefetched messages are visible before the live query emits`() = runTest(dispatcher) {
        val warmed = listOf(message(id = 11L, guid = "warm", text = "already here"))
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = object : MessageListRepository by StaticMessages {
                override fun cached(chatId: Long) = warmed
                override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> =
                    kotlinx.coroutines.flow.emptyFlow()
            },
        )
        assertEquals(listOf("already here"), model.uiState.value.messages.map { it.text })

        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        runCurrent()

        assertEquals(listOf("already here"), model.uiState.value.messages.map { it.text })
    }

    @Test
    fun `reply targets the thread root`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val actions = RecordingActions()
        val model = model(sender, actions)
        val reply = message(
            guid = "child",
            replyToGuid = "root",
            replyToPart = 4L,
            replyToPartLocator = "4:7:3",
        )

        model.beginReply(reply)
        model.onInputChange("thread reply")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(Triple(7L, "thread reply", "root"), sender.reply)
        assertEquals("4:7:3", sender.replyPartLocator)
    }

    @Test
    fun `grouped contact sends new messages through its most recent address`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val model = model(
            sender = sender,
            actions = RecordingActions(),
            chatListRepository = GroupedChats,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.onInputChange("latest route")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(9L to "latest route", sender.sent)
    }

    @Test
    fun `composer clears only after local staging is accepted`() = runTest(dispatcher) {
        val sender = BlockingSender(messageId = 41L)
        val model = model(sender, RecordingActions())
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.onInputChange("stays visible")
        model.sendMessage()
        runCurrent()
        sender.started.await()

        assertEquals("stays visible", model.uiState.value.input)
        assertEquals(true, model.uiState.value.textSendInProgress)

        sender.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("", model.uiState.value.input)
        assertEquals(false, model.uiState.value.textSendInProgress)
    }

    @Test
    fun `older send completion does not overwrite a newer draft`() = runTest(dispatcher) {
        val sender = BlockingSender(messageId = 42L)
        val model = model(sender, RecordingActions())
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.onInputChange("first")
        model.sendMessage()
        runCurrent()
        sender.started.await()
        model.onInputChange("new draft")
        sender.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("new draft", model.uiState.value.input)
    }

    @Test
    fun `sms chat routes directly to sms sender`() = runTest(dispatcher) {
        val imessage = RecordingSender()
        val sms = RecordingSmsSender()
        val model = model(
            sender = imessage,
            actions = RecordingActions(),
            chatListRepository = SmsChats,
            smsSender = sms,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.onInputChange("carrier")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(7L to "carrier", sms.sent)
        assertEquals(null, imessage.sent)
    }

    @Test
    fun `scroll event waits for the staged row to reach the transcript`() = runTest(dispatcher) {
        val messages = MutableMessages(emptyList())
        val sender = RecordingSender(messageId = 43L)
        val model = model(sender, RecordingActions(), messageRepository = messages)
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.onInputChange("scroll after row")
        model.sendMessage()
        advanceUntilIdle()
        assertEquals(null, model.uiState.value.outgoingSendEvent)

        messages.value.value = listOf(
            message(id = 43L, guid = "temp-43", text = "scroll after row"),
        )
        advanceUntilIdle()

        assertEquals(43L, model.uiState.value.outgoingSendEvent?.messageId)
        model.consumeOutgoingSendEvent(43L)
        runCurrent()
        assertEquals(null, model.uiState.value.outgoingSendEvent)
    }

    @Test
    fun `grouped contact actions retain the message source address`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val actions = RecordingActions()
        val model = model(sender, actions, chatListRepository = GroupedChats)
        val target = message(guid = "alternate", text = "hello", chatId = 9L)

        model.beginReply(target)
        model.onInputChange("reply")
        model.sendMessage()
        model.react(target, 0L, 1)
        model.unsend(target)
        advanceUntilIdle()

        assertEquals(9L, sender.reply?.first)
        assertEquals(9L, actions.reactionChatId)
        assertEquals(9L to "alternate", actions.unsend)
    }

    @Test
    fun `reply to a root message sends its full selected run locator`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val model = model(sender, RecordingActions())

        model.beginReply(
            message(guid = "root", replyPartLocators = mapOf(3L to "3:7:5")),
            part = 3L,
        )
        model.onInputChange("part reply")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(Triple(7L, "part reply", "root"), sender.reply)
        assertEquals("3:7:5", sender.replyPartLocator)
    }

    @Test
    fun `legacy plain text reply gets a utf16 whole-run locator`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val model = model(sender, RecordingActions())

        model.beginReply(message(guid = "root", text = "Hi 👋"))
        model.onInputChange("reply")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals("0:0:5", sender.replyPartLocator)
    }

    @Test
    fun `sticker placement reaches the native sticker sender unchanged`() = runTest(dispatcher) {
        val stickerSender = RecordingStickerSender()
        val model = model(RecordingSender(), RecordingActions(), stickerSender = stickerSender)
        val file = File.createTempFile("sticker", ".png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val attachment = OutgoingAttachment(file, "image/png", "public.png", "sticker.png", file.length())
        val transform = StickerTransform(320.0, 0.25, 0.75, 0.5, 1.4, effectType = 2L)

        model.sendSticker(message(guid = "target", text = "decorate"), 2L, attachment, transform)
        advanceUntilIdle()

        assertEquals(7L, stickerSender.chatId)
        assertEquals("target", stickerSender.targetGuid)
        assertEquals(2L, stickerSender.targetPart)
        assertEquals("decorate", stickerSender.targetText)
        assertEquals(transform, stickerSender.transform)
        file.delete()
    }

    @Test
    fun `edit is visible and composer clears before native action completes`() = runTest(dispatcher) {
        val target = message(guid = "target", text = "before")
        val messages = MutableMessages(listOf(target))
        val actions = DeferredActions()
        val model = model(
            RecordingSender(),
            actions,
            messageRepository = messages,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.beginEdit(target)
        model.onInputChange("after")
        model.sendMessage()
        runCurrent()
        actions.editStarted.await()

        assertEquals("after", model.uiState.value.messages.single().text)
        assertEquals(true, model.uiState.value.messages.single().edited)
        assertEquals("", model.uiState.value.input)
        assertEquals(null, model.uiState.value.editingMessage)

        actions.editRelease.complete(Unit)
        advanceUntilIdle()
    }

    @Test
    fun `reaction and unsend render immediately and roll back on failure`() = runTest(dispatcher) {
        val target = message(guid = "target", text = "before")
        val messages = MutableMessages(listOf(target))
        val actions = DeferredActions(failReaction = true, failUnsend = true)
        val model = model(
            RecordingSender(),
            actions,
            messageRepository = messages,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.react(target, 0L, 1)
        model.unsend(target)
        runCurrent()
        actions.reactionStarted.await()
        actions.unsendStarted.await()

        assertEquals("👍", model.uiState.value.messages.single().reactionEmoji)
        assertEquals(true, model.uiState.value.messages.single().unsent)

        actions.reactionRelease.complete(Unit)
        actions.unsendRelease.complete(Unit)
        advanceUntilIdle()

        assertEquals(null, model.uiState.value.messages.single().reactionEmoji)
        assertEquals(false, model.uiState.value.messages.single().unsent)
    }

    @Test
    fun `sticker preview is visible before upload completes and rolls back`() = runTest(dispatcher) {
        val target = message(guid = "target", text = "decorate")
        val messages = MutableMessages(listOf(target))
        val stickerSender = DeferredStickerSender(fail = true)
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = messages,
            stickerSender = stickerSender,
        )
        val file = File.createTempFile("sticker-preview", ".png").apply {
            writeBytes(byteArrayOf(1, 2, 3))
        }
        val attachment = OutgoingAttachment(file, "image/png", "public.png", "sticker.png", file.length())
        val transform = StickerTransform(320.0, 0.25, 0.75, 0.5, 1.4, effectType = 2L)
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.sendSticker(target, 2L, attachment, transform)
        runCurrent()
        stickerSender.started.await()

        val optimistic = model.uiState.value.messages.single().stickers.single()
        assertEquals(2L, optimistic.targetPart)
        assertEquals(file, model.uiState.value.optimisticStickerFiles[optimistic.attachmentGuid])

        stickerSender.release.complete(Unit)
        advanceUntilIdle()

        assertEquals(emptyList(), model.uiState.value.messages.single().stickers)
        assertEquals(emptyMap(), model.uiState.value.optimisticStickerFiles)
        file.delete()
    }

    @Test
    fun `edit reaction and unsend reach native action layer`() = runTest(dispatcher) {
        val actions = RecordingActions()
        val model = model(RecordingSender(), actions)
        val target = message(guid = "target", text = "before")

        model.beginEdit(target)
        model.onInputChange("after")
        model.sendMessage()
        model.react(target, 2L, 3)
        model.unsend(target)
        advanceUntilIdle()

        assertEquals(Triple(7L, "target", "after"), actions.edit)
        assertEquals(Triple("target", 3, "before"), actions.reaction)
        assertEquals(2L, actions.reactionPart)
        assertEquals(7L to "target", actions.unsend)
    }

    @Test
    fun `attachment in sms chat never enters imessage uploader`() = runTest(dispatcher) {
        val attachmentSender = RecordingAttachmentSender()
        val smsAttachmentSender = RecordingAttachmentSender()
        val model = model(
            sender = RecordingSender(),
            actions = RecordingActions(),
            chatListRepository = SmsChats,
            attachmentSender = attachmentSender,
            smsAttachmentSender = smsAttachmentSender,
        )
        val file = File.createTempFile("mms-route", ".jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val attachment = OutgoingAttachment(file, "image/jpeg", "public.jpeg", "photo.jpg", file.length())

        model.onInputChange("caption")
        model.stageAttachment(attachment)
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(7L to "caption", smsAttachmentSender.chatId to smsAttachmentSender.caption)
        assertEquals(listOf("photo.jpg"), smsAttachmentSender.attachmentNames)
        assertEquals(0, attachmentSender.calls)
        file.delete()
    }

    @Test
    fun `staged attachments ride the next send with the typed caption`() = runTest(dispatcher) {
        val attachmentSender = RecordingAttachmentSender()
        val model = model(RecordingSender(), RecordingActions(), attachmentSender = attachmentSender)
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        val one = tempAttachment("one.jpg")
        val two = tempAttachment("two.png")

        model.stageAttachment(one)
        model.stageAttachment(two)
        runCurrent()
        assertEquals(listOf(one, two), model.uiState.value.pendingAttachments)

        model.onInputChange("both photos")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(7L to "both photos", attachmentSender.chatId to attachmentSender.caption)
        assertEquals(listOf("one.jpg", "two.png"), attachmentSender.attachmentNames)
        assertEquals(emptyList(), model.uiState.value.pendingAttachments)
        assertEquals("", model.uiState.value.input)
        one.file.delete()
        two.file.delete()
    }

    @Test
    fun `attachment composer clears only after local staging is accepted`() = runTest(dispatcher) {
        val attachmentSender = BlockingAttachmentSender(messageId = 46L)
        val model = model(RecordingSender(), RecordingActions(), attachmentSender = attachmentSender)
        val attachment = tempAttachment("wait.jpg")
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.onInputChange("stays visible")
        model.stageAttachment(attachment)
        model.sendMessage()
        runCurrent()
        attachmentSender.started.await()

        assertEquals("stays visible", model.uiState.value.input)
        assertEquals(listOf(attachment), model.uiState.value.pendingAttachments)
        assertEquals(true, model.uiState.value.attachmentSendInProgress)

        attachmentSender.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("", model.uiState.value.input)
        assertEquals(emptyList(), model.uiState.value.pendingAttachments)
        assertEquals(false, model.uiState.value.attachmentSendInProgress)
        attachment.file.delete()
    }

    @Test
    fun `attachment completion preserves a newer draft and newly staged media`() = runTest(dispatcher) {
        val attachmentSender = BlockingAttachmentSender(messageId = 47L)
        val model = model(RecordingSender(), RecordingActions(), attachmentSender = attachmentSender)
        val sent = tempAttachment("sent.jpg")
        val next = tempAttachment("next.jpg")
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.onInputChange("first caption")
        model.stageAttachment(sent)
        model.sendMessage()
        runCurrent()
        attachmentSender.started.await()
        model.onInputChange("next caption")
        model.stageAttachment(next)

        attachmentSender.release.complete(Unit)
        advanceUntilIdle()

        assertEquals("next caption", model.uiState.value.input)
        assertEquals(listOf(next), model.uiState.value.pendingAttachments)
        sent.file.delete()
        next.file.delete()
    }

    @Test
    fun `staged attachments send without any typed text`() = runTest(dispatcher) {
        val attachmentSender = RecordingAttachmentSender()
        val model = model(RecordingSender(), RecordingActions(), attachmentSender = attachmentSender)
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        val one = tempAttachment("only.jpg")

        model.stageAttachment(one)
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(7L to null, attachmentSender.chatId to attachmentSender.caption)
        assertEquals(listOf("only.jpg"), attachmentSender.attachmentNames)
        assertEquals(emptyList(), model.uiState.value.pendingAttachments)
        one.file.delete()
    }

    @Test
    fun `removed staged attachment does not ride the send`() = runTest(dispatcher) {
        val attachmentSender = RecordingAttachmentSender()
        val model = model(RecordingSender(), RecordingActions(), attachmentSender = attachmentSender)
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        val one = tempAttachment("one.jpg")
        val two = tempAttachment("two.png")

        model.stageAttachment(one)
        model.stageAttachment(two)
        model.removePendingAttachment(one)
        runCurrent()
        assertEquals(listOf(two), model.uiState.value.pendingAttachments)

        model.sendMessage()
        advanceUntilIdle()

        assertEquals(listOf("two.png"), attachmentSender.attachmentNames)
        one.file.delete()
        two.file.delete()
    }

    @Test
    fun `failed attachment send restores the draft text and staged media`() = runTest(dispatcher) {
        val attachmentSender = FailingAttachmentSender()
        val model = model(RecordingSender(), RecordingActions(), attachmentSender = attachmentSender)
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        val one = tempAttachment("retry.jpg")

        model.onInputChange("caption")
        model.stageAttachment(one)
        model.sendMessage()
        advanceUntilIdle()

        assertEquals("caption", model.uiState.value.input)
        assertEquals(listOf(one), model.uiState.value.pendingAttachments)
        assertEquals("upload broke", model.uiState.value.actionError)
        one.file.delete()
    }

    @Test
    fun `facetime launch is exposed once and can be consumed`() = runTest(dispatcher) {
        val launch = FaceTimeLaunch("https://call.example", "me@example.com", "Test", "CALL-ID")
        val model = model(
            RecordingSender(),
            RecordingActions(),
            faceTimeCaller = FaceTimeCaller { launch },
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }

        model.startFaceTime()
        advanceUntilIdle()

        assertEquals(launch, model.uiState.value.faceTimeLaunch)
        assertEquals(false, model.uiState.value.faceTimeStarting)
        model.consumeFaceTimeLaunch()
        advanceUntilIdle()
        assertEquals(null, model.uiState.value.faceTimeLaunch)
    }

    @Test
    fun `historical effects are baselined and new effects are consumed once`() = runTest(dispatcher) {
        val messages = MutableMessages(
            listOf(message(id = 1L, guid = "historical", effectId = "historical-effect")),
        )
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = messages,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }

        advanceUntilIdle()
        assertEquals(null, model.uiState.value.screenEffect)

        messages.value.value += message(id = 2L, guid = "new", effectId = "new-effect")
        advanceUntilIdle()

        assertEquals(ScreenEffectTrigger(2L, "new-effect"), model.uiState.value.screenEffect)
        model.consumeScreenEffect(2L)
        advanceUntilIdle()
        assertEquals(null, model.uiState.value.screenEffect)
    }

    @Test
    fun `opening a thread keeps the tapped message when the query is empty`() = runTest(dispatcher) {
        val model = model(RecordingSender(), RecordingActions())
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        val tapped = message(guid = "child", replyToGuid = "missing")
        model.openReplyThread(tapped)
        advanceUntilIdle()

        val thread = model.uiState.value.replyThread
        assertEquals("missing", thread?.rootGuid)
        assertEquals(false, thread?.loading)
        assertEquals(listOf("child"), thread?.messages?.map { it.guid })
        assertEquals("missing", model.uiState.value.replyingTo?.rootGuid)
    }

    @Test
    fun `sending from an open thread keeps the thread and reply target`() = runTest(dispatcher) {
        val sender = RecordingSender()
        val root = message(id = 1L, guid = "root", text = "original")
        val child = message(id = 2L, guid = "child", text = "reply", replyToGuid = "root")
        val model = model(
            sender,
            RecordingActions(),
            messageRepository = object : MessageListRepository by StaticMessages {
                override fun thread(chatId: Long, rootGuid: String, part: Long) = listOf(root, child)
            },
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        model.openReplyThread(child)
        advanceUntilIdle()
        model.onInputChange("another")
        model.sendMessage()
        advanceUntilIdle()

        assertEquals(Triple(7L, "another", "root"), sender.reply)
        assertEquals("root", model.uiState.value.replyThread?.rootGuid)
        assertEquals("root", model.uiState.value.replyingTo?.rootGuid)
    }

    @Test
    fun `closing a thread without typed text clears the composer reply`() = runTest(dispatcher) {
        val model = model(RecordingSender(), RecordingActions())
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        model.openReplyThread(message(guid = "child", replyToGuid = "root"))
        advanceUntilIdle()
        model.closeReplyThread()
        advanceUntilIdle()

        assertEquals(null, model.uiState.value.replyThread)
        assertEquals(null, model.uiState.value.replyingTo)
    }

    @Test
    fun `new incoming messages while open mark the conversation read again`() = runTest(dispatcher) {
        val messages = MutableMessages(
            listOf(message(id = 1L, guid = "seen", text = "already here", fromMe = false)),
        )
        val receipts = RecordingReadReceipts()
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = messages,
            readReceiptSender = receipts,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        assertEquals(listOf<Pair<Long, String?>>(7L to null), receipts.marked)

        messages.value.value += message(
            id = 2L,
            guid = "fresh",
            text = "just arrived",
            fromMe = false,
            date = 1_700_000_000_500L,
        )
        advanceUntilIdle()

        assertEquals(listOf(7L to null, 7L to "fresh"), receipts.marked)
    }

    @Test
    fun `history-imported messages while open do not send another Apple receipt`() = runTest(dispatcher) {
        val messages = MutableMessages(
            listOf(message(id = 1L, guid = "seen", text = "already here", fromMe = false)),
        )
        val receipts = RecordingReadReceipts()
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = messages,
            readReceiptSender = receipts,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        messages.value.value += message(
            id = 2L,
            guid = "backfill",
            text = "from iCloud",
            fromMe = false,
            date = 1_600_000_000_000L,
        )
        advanceUntilIdle()

        assertEquals(listOf<Pair<Long, String?>>(7L to null), receipts.marked)
    }

    @Test
    fun `history sync pages do not send a receipt for every newest imported row`() = runTest(dispatcher) {
        val messages = MutableMessages(
            listOf(message(id = 1L, guid = "seen", text = "already here", fromMe = false)),
        )
        val receipts = RecordingReadReceipts()
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = messages,
            readReceiptSender = receipts,
            historySyncActive = { true },
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        messages.value.value += message(
            id = 2L,
            guid = "page-1",
            text = "imported",
            fromMe = false,
            date = 1_700_000_000_500L,
        )
        advanceUntilIdle()
        messages.value.value += message(
            id = 3L,
            guid = "page-2",
            text = "also imported",
            fromMe = false,
            date = 1_700_000_000_800L,
        )
        advanceUntilIdle()

        assertEquals(listOf<Pair<Long, String?>>(7L to null), receipts.marked)
    }

    @Test
    fun `live incoming after history sync still marks the conversation read`() = runTest(dispatcher) {
        val messages = MutableMessages(
            listOf(message(id = 1L, guid = "seen", text = "already here", fromMe = false)),
        )
        val receipts = RecordingReadReceipts()
        var syncing = true
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = messages,
            readReceiptSender = receipts,
            historySyncActive = { syncing },
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        messages.value.value += message(
            id = 2L,
            guid = "imported",
            text = "from iCloud",
            fromMe = false,
            date = 1_700_000_000_500L,
        )
        advanceUntilIdle()
        syncing = false
        messages.value.value += message(
            id = 3L,
            guid = "live",
            text = "just arrived",
            fromMe = false,
            date = 1_700_000_001_000L,
        )
        advanceUntilIdle()

        assertEquals(listOf(7L to null, 7L to "live"), receipts.marked)
    }

    @Test
    fun `slower reply lookup cannot overwrite a newer thread`() = runTest(dispatcher) {
        val messages = BlockingThreadMessages()
        val model = model(
            RecordingSender(),
            RecordingActions(),
            messageRepository = messages,
        )
        backgroundScope.launch(dispatcher) { model.uiState.collect() }
        advanceUntilIdle()

        model.openReplyThread(message(guid = "first"))
        assertTrue(messages.firstStarted.await(5, TimeUnit.SECONDS))

        model.openReplyThread(message(guid = "second"))
        assertTrue(messages.secondStarted.await(5, TimeUnit.SECONDS))
        messages.secondRelease.countDown()

        var secondLoaded = false
        for (attempt in 0 until 100) {
            runCurrent()
            val thread = model.uiState.value.replyThread
            if (thread?.rootGuid == "second" && !thread.loading) {
                secondLoaded = true
                break
            }
            Thread.sleep(10)
        }
        assertTrue(secondLoaded)

        messages.firstRelease.countDown()
        assertTrue(messages.firstReturned.await(5, TimeUnit.SECONDS))
        repeat(10) {
            runCurrent()
            Thread.sleep(5)
        }

        assertEquals("second", model.uiState.value.replyThread?.rootGuid)
        assertEquals(false, model.uiState.value.replyThread?.loading)
    }

    private fun model(
        sender: Sender,
        actions: MessageActions,
        chatListRepository: ChatListRepository = StaticChats,
        messageRepository: MessageListRepository = StaticMessages,
        attachmentSender: AttachmentSender = NoopAttachmentSender,
        stickerSender: StickerSender = StickerSender { _, _, _, _, sticker, _ ->
            OutgoingStickerSend(sticker.file.absolutePath)
        },
        faceTimeCaller: FaceTimeCaller = FaceTimeCaller { error("not used") },
        smsSender: SmsSender = NoopSmsSender,
        smsAttachmentSender: AttachmentSender = NoopAttachmentSender,
        readReceiptSender: ReadReceiptSender = ReadReceiptSender { _, _ -> },
        historySyncActive: () -> Boolean = { false },
        openedAtMs: Long = 1_700_000_000_000L,
    ): ChatViewModel {
        val created = ChatViewModel(
            chatId = 7L,
            chatListRepository = chatListRepository,
            messageRepository = messageRepository,
            sender = sender,
            messageActions = actions,
            faceTimeCaller = faceTimeCaller,
            attachmentSender = attachmentSender,
            stickerSender = stickerSender,
            typingRepository = NoopTyping,
            readReceiptSender = readReceiptSender,
            smsSender = smsSender,
            smsAttachmentSender = smsAttachmentSender,
            historySyncActive = historySyncActive,
            openedAtMs = openedAtMs,
            participantAddresses = { emptyList() },
            participantLookupDispatcher = dispatcher,
        )
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                checkNotNull(modelClass.cast(created))
        }
        return ViewModelProvider(viewModelStore, factory)
            .get("chat-test-${nextViewModelKey++}", ChatViewModel::class.java)
    }

    private fun message(
        id: Long = 1L,
        guid: String,
        text: String = "hello",
        effectId: String? = null,
        replyToGuid: String? = null,
        replyToPart: Long? = null,
        replyToPartLocator: String? = null,
        replyPartLocators: Map<Long, String> = emptyMap(),
        chatId: Long? = null,
        date: Long = 1_700_000_000_000L,
        fromMe: Boolean = true,
        reactionEmoji: String? = null,
        edited: Boolean = false,
        unsent: Boolean = false,
        stickers: List<StickerPlacement> = emptyList(),
    ) = MessageItem(
        id = id,
        text = text,
        isFromMe = fromMe,
        date = date,
        status = MessageStatus.SENT,
        isGroupEvent = false,
        reactionEmoji = reactionEmoji,
        edited = edited,
        unsent = unsent,
        expressiveSendStyleId = effectId,
        guid = guid,
        replyToGuid = replyToGuid,
        replyToPart = replyToPart,
        replyToPartLocator = replyToPartLocator,
        replyPartLocators = replyPartLocators,
        chatId = chatId,
        stickers = stickers,
    )
}

private object StaticChats : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> = flowOf(
        listOf(ChatListItem(7L, "Test", null, 0L, 0, false, 0L)),
    )

    override fun markRead(id: Long) = Unit
    override fun setPinned(id: Long, pinned: Boolean) = Unit
    override fun setMuted(id: Long, muted: Boolean) = Unit
    override fun setArchived(id: Long, archived: Boolean) = Unit
    override fun delete(id: Long) = Unit
}

private object GroupedChats : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> = flowOf(
        listOf(
            ChatListItem(
                id = 7L,
                title = "Grouped",
                snippet = null,
                date = 0L,
                unread = 0,
                pinned = false,
                avatarColor = 0L,
                memberChatIds = listOf(7L, 9L),
                preferredChatId = 9L,
            ),
        ),
    )

    override fun markRead(id: Long) = Unit
    override fun setPinned(id: Long, pinned: Boolean) = Unit
    override fun setMuted(id: Long, muted: Boolean) = Unit
    override fun setArchived(id: Long, archived: Boolean) = Unit
    override fun delete(id: Long) = Unit
}

private object SmsChats : ChatListRepository {
    override fun chats(): Flow<List<ChatListItem>> = flowOf(
        listOf(ChatListItem(7L, "SMS", null, 0L, 0, false, 0L, isSms = true)),
    )

    override fun markRead(id: Long) = Unit
    override fun setPinned(id: Long, pinned: Boolean) = Unit
    override fun setMuted(id: Long, muted: Boolean) = Unit
    override fun setArchived(id: Long, archived: Boolean) = Unit
    override fun delete(id: Long) = Unit
}

private object StaticMessages : MessageListRepository {
    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> =
        flowOf(emptyList())

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> = emptyList()
}

private class RecordingReadReceipts : ReadReceiptSender {
    val marked = mutableListOf<Pair<Long, String?>>()

    override suspend fun markRead(chatId: Long, messageGuid: String?) {
        marked += chatId to messageGuid
    }
}

private class MutableMessages(initial: List<MessageItem>) : MessageListRepository {
    val value = MutableStateFlow(initial)

    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> = value

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> = emptyList()
}

private class BlockingThreadMessages : MessageListRepository {
    val firstStarted = CountDownLatch(1)
    val secondStarted = CountDownLatch(1)
    val firstRelease = CountDownLatch(1)
    val secondRelease = CountDownLatch(1)
    val firstReturned = CountDownLatch(1)

    override fun messages(chatId: Long, limit: Int, before: Long?): Flow<List<MessageItem>> =
        flowOf(emptyList())

    override fun loadMore(chatId: Long, before: Long?, count: Int): List<MessageItem> = emptyList()

    override fun thread(chatId: Long, rootGuid: String, part: Long): List<MessageItem> {
        when (rootGuid) {
            "first" -> {
                firstStarted.countDown()
                check(firstRelease.await(5, TimeUnit.SECONDS))
                firstReturned.countDown()
            }
            "second" -> {
                secondStarted.countDown()
                check(secondRelease.await(5, TimeUnit.SECONDS))
            }
        }
        return emptyList()
    }
}

private class RecordingSender(
    private val messageId: Long = 40L,
) : Sender {
    var sent: Pair<Long, String>? = null
    var reply: Triple<Long, String, String>? = null
    var replyPartLocator: String? = null

    override suspend fun send(chatId: Long, text: String): OutgoingTextSend {
        sent = chatId to text
        return OutgoingTextSend(messageId)
    }

    override suspend fun sendReply(
        chatId: Long,
        text: String,
        replyGuid: String,
        replyPartLocator: String,
    ): OutgoingTextSend {
        reply = Triple(chatId, text, replyGuid)
        this.replyPartLocator = replyPartLocator
        return OutgoingTextSend(messageId)
    }
}

private class BlockingSender(
    private val messageId: Long,
) : Sender {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    override suspend fun send(chatId: Long, text: String): OutgoingTextSend {
        started.complete(Unit)
        release.await()
        return OutgoingTextSend(messageId)
    }
}

private class RecordingSmsSender : SmsSender {
    var sent: Pair<Long, String>? = null

    override suspend fun send(chatId: Long, text: String): OutgoingTextSend {
        sent = chatId to text
        return OutgoingTextSend(44L)
    }
}

private object NoopSmsSender : SmsSender {
    override suspend fun send(chatId: Long, text: String): OutgoingTextSend =
        OutgoingTextSend(45L)
}

private class RecordingActions : MessageActions {
    var edit: Triple<Long, String, String>? = null
    var reaction: Triple<String, Int, String>? = null
    var reactionPart: Long? = null
    var reactionEmoji: String? = null
    var reactionChatId: Long? = null
    var unsend: Pair<Long, String>? = null

    override suspend fun react(
        chatId: Long,
        messageGuid: String,
        messageText: String,
        messagePart: Long,
        reactionIndex: Int,
        emoji: String?,
        enable: Boolean,
    ) {
        reactionChatId = chatId
        reaction = Triple(messageGuid, reactionIndex, messageText)
        reactionPart = messagePart
        reactionEmoji = emoji
    }

    override suspend fun edit(chatId: Long, messageGuid: String, newText: String) {
        edit = Triple(chatId, messageGuid, newText)
    }

    override suspend fun unsend(chatId: Long, messageGuid: String) {
        unsend = chatId to messageGuid
    }
}

private object NoopAttachmentSender : AttachmentSender {
    override suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
    ) = OutgoingAttachmentSend(48L)
}

private class RecordingAttachmentSender : AttachmentSender {
    var calls = 0
    var chatId: Long? = null
    var caption: String? = null
    var attachmentNames: List<String> = emptyList()

    override suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
    ): OutgoingAttachmentSend {
        calls++
        this.chatId = chatId
        this.caption = caption
        attachmentNames = attachments.mapNotNull { it.name }
        return OutgoingAttachmentSend(49L)
    }
}

private class FailingAttachmentSender : AttachmentSender {
    override suspend fun send(chatId: Long, attachments: List<OutgoingAttachment>, caption: String?) =
        error("upload broke")
}

private class BlockingAttachmentSender(private val messageId: Long) : AttachmentSender {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    override suspend fun send(
        chatId: Long,
        attachments: List<OutgoingAttachment>,
        caption: String?,
    ): OutgoingAttachmentSend {
        started.complete(Unit)
        release.await()
        return OutgoingAttachmentSend(messageId)
    }
}

/** Temp-file [OutgoingAttachment] factory; callers delete `file` when done. */
private fun tempAttachment(name: String): OutgoingAttachment {
    val file = File.createTempFile("draft-att", ".${name.substringAfterLast('.')}").apply {
        writeBytes(byteArrayOf(1, 2, 3))
    }
    return OutgoingAttachment(file, "image/jpeg", "public.jpeg", name, file.length())
}

private class RecordingStickerSender : StickerSender {
    var chatId: Long? = null
    var targetGuid: String? = null
    var targetPart: Long? = null
    var targetText: String? = null
    var transform: StickerTransform? = null

    override suspend fun send(
        chatId: Long,
        targetGuid: String,
        targetPart: Long,
        targetText: String,
        sticker: OutgoingAttachment,
        transform: StickerTransform,
    ): OutgoingStickerSend {
        this.chatId = chatId
        this.targetGuid = targetGuid
        this.targetPart = targetPart
        this.targetText = targetText
        this.transform = transform
        return OutgoingStickerSend("recorded-sticker")
    }
}

private class DeferredStickerSender(private val fail: Boolean) : StickerSender {
    val started = CompletableDeferred<Unit>()
    val release = CompletableDeferred<Unit>()

    override suspend fun send(
        chatId: Long,
        targetGuid: String,
        targetPart: Long,
        targetText: String,
        sticker: OutgoingAttachment,
        transform: StickerTransform,
    ): OutgoingStickerSend {
        started.complete(Unit)
        release.await()
        if (fail) error("sticker failed")
        return OutgoingStickerSend("confirmed-sticker")
    }
}

private class DeferredActions(
    private val failReaction: Boolean = false,
    private val failEdit: Boolean = false,
    private val failUnsend: Boolean = false,
) : MessageActions {
    val reactionStarted = CompletableDeferred<Unit>()
    val reactionRelease = CompletableDeferred<Unit>()
    val editStarted = CompletableDeferred<Unit>()
    val editRelease = CompletableDeferred<Unit>()
    val unsendStarted = CompletableDeferred<Unit>()
    val unsendRelease = CompletableDeferred<Unit>()

    override suspend fun react(
        chatId: Long,
        messageGuid: String,
        messageText: String,
        messagePart: Long,
        reactionIndex: Int,
        emoji: String?,
        enable: Boolean,
    ) {
        reactionStarted.complete(Unit)
        reactionRelease.await()
        if (failReaction) error("reaction failed")
    }

    override suspend fun edit(chatId: Long, messageGuid: String, newText: String) {
        editStarted.complete(Unit)
        editRelease.await()
        if (failEdit) error("edit failed")
    }

    override suspend fun unsend(chatId: Long, messageGuid: String) {
        unsendStarted.complete(Unit)
        unsendRelease.await()
        if (failUnsend) error("unsend failed")
    }
}

private object NoopTyping : TypingRepository {
    override fun typing(): Flow<List<TypingEntry>> = flowOf(emptyList())
}

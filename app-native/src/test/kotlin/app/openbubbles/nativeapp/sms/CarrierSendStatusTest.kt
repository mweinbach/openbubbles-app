package app.openbubbles.nativeapp.sms

import app.openbubbles.db.Chat
import app.openbubbles.db.Message
import app.openbubbles.db.Message_
import app.openbubbles.db.MyObjectBox
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import java.io.File
import java.nio.file.Files
import java.util.Date
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

class CarrierSendStatusTest {
    private lateinit var root: File
    private lateinit var store: BoxStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("carrier-send-status").toFile()
        store = MyObjectBox.builder().directory(root).build()
    }

    @After
    fun tearDown() {
        store.close()
        root.deleteRecursively()
    }

    @Test
    fun `all recipients and segments must confirm before sending clears`() {
        val message = stageMessage("temp-group", recipients = 2, parts = 2)

        apply(message.guid, CarrierCallbackKind.SENT, recipient = 1, part = 1)
        apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 0)
        apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 0)
        apply(message.guid, CarrierCallbackKind.SENT, recipient = 1, part = 0)
        assertNotNull(stored(message.guid).sendingServiceId)

        apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 1)

        val complete = stored(message.guid)
        assertNull(complete.sendingServiceId)
        assertNull(complete.error)
        assertEquals(4, CarrierSendProgress.fromMetadata(complete.metadata)?.sent?.size)
        assertEquals("kept", complete.metadata?.get("existing"))
    }

    @Test
    fun `single MMS callback reaches sent terminal state`() {
        val message = stageMessage("temp-mms", recipients = 1, parts = 1)

        assertTrue(apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 0))

        val sent = stored(message.guid)
        assertNull(sent.sendingServiceId)
        assertNull(sent.error)
        assertNull(sent.dateDelivered)
    }

    @Test
    fun `later segment success cannot erase any prior recipient failure`() {
        val message = stageMessage("temp-failed-group", recipients = 2, parts = 2)

        apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 1, successful = false)
        apply(message.guid, CarrierCallbackKind.SENT, recipient = 1, part = 0)
        apply(message.guid, CarrierCallbackKind.SENT, recipient = 1, part = 1)
        apply(message.guid, CarrierCallbackKind.DELIVERED, recipient = 1, part = 0)

        val failed = stored(message.guid)
        assertEquals(1L, failed.error)
        assertEquals("carrier rejected component", failed.errorMessage)
        assertNull(failed.sendingServiceId)
        assertNull(failed.dateDelivered)
        assertTrue(CarrierSendProgress.fromMetadata(failed.metadata)?.failed == true)
    }

    @Test
    fun `failed delivery cannot be overwritten by later successful delivery`() {
        val message = stageMessage("temp-delivery-failure", recipients = 1, parts = 2)
        apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 0)
        apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 1)

        apply(message.guid, CarrierCallbackKind.DELIVERED, recipient = 0, part = 0, successful = false)
        apply(message.guid, CarrierCallbackKind.DELIVERED, recipient = 0, part = 1)

        val failed = stored(message.guid)
        assertEquals(1L, failed.error)
        assertNull(failed.dateDelivered)
    }

    @Test
    fun `delivery callbacks may precede sent confirmations without premature delivery`() {
        val message = stageMessage("temp-reordered", recipients = 1, parts = 2)
        val firstDelivery = Date(1234)

        apply(message.guid, CarrierCallbackKind.DELIVERED, recipient = 0, part = 1, deliveredAt = firstDelivery)
        apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 0)
        assertNotNull(stored(message.guid).sendingServiceId)
        assertNull(stored(message.guid).dateDelivered)

        apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 1)
        assertNull(stored(message.guid).sendingServiceId)
        assertNull(stored(message.guid).dateDelivered)

        val completedAt = Date(5678)
        apply(message.guid, CarrierCallbackKind.DELIVERED, recipient = 0, part = 0, deliveredAt = completedAt)
        assertEquals(completedAt, stored(message.guid).dateDelivered)

        apply(message.guid, CarrierCallbackKind.DELIVERED, recipient = 0, part = 0, deliveredAt = Date(9999))
        assertEquals(completedAt, stored(message.guid).dateDelivered)
    }

    @Test
    fun `invalid recipient or segment callbacks never settle another component`() {
        val message = stageMessage("temp-invalid", recipients = 1, parts = 1)

        assertFalse(apply(message.guid, CarrierCallbackKind.SENT, recipient = 1, part = 0))
        assertFalse(apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 1))
        assertNotNull(stored(message.guid).sendingServiceId)
        assertTrue(CarrierSendProgress.fromMetadata(stored(message.guid).metadata)?.sent.orEmpty().isEmpty())
    }

    @Test
    fun `late callback cannot recreate a cancelled message`() {
        val message = stageMessage("temp-deleted", recipients = 1, parts = 1)
        store.boxFor(Message::class.java).remove(message.id)

        assertFalse(apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 0))
        assertEquals(0L, store.boxFor(Message::class.java).count())
    }

    @Test
    fun `carrier callback cannot modify an imessage row`() {
        val chat = Chat().apply {
            guid = "imessage-chat"
            isRpSms = false
        }
        store.boxFor(Chat::class.java).put(chat)
        val message = Message().apply {
            guid = "temp-imessage"
            isFromMe = true
            sendingServiceId = "sending"
            this.chat.target = chat
        }
        store.boxFor(Message::class.java).put(message)

        assertFalse(apply(message.guid, CarrierCallbackKind.SENT, recipient = 0, part = 0))
        assertEquals("sending", stored(message.guid).sendingServiceId)
    }

    @Test
    fun `callback identity includes recipient segment and action`() {
        val sent = SmsSendStatusReceiver.ACTION_SENT
        val delivered = SmsSendStatusReceiver.ACTION_DELIVERED

        val baseline = carrierCallbackRequestCode("temp-identity", sent, 0, 0)
        assertNotEquals(baseline, carrierCallbackRequestCode("temp-identity", sent, 1, 0))
        assertNotEquals(baseline, carrierCallbackRequestCode("temp-identity", sent, 0, 1))
        assertNotEquals(baseline, carrierCallbackRequestCode("temp-identity", delivered, 0, 0))
    }

    @Test
    fun `carrier callback shape rejects unbounded recipient segment products`() {
        val failure = runCatching { CarrierSendProgress(recipientCount = 65, partCount = 64) }
        assertTrue(failure.isFailure)
    }

    @Test
    fun `queued carrier dispatch cancellation joins before irreversible modem claim`() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var claimed = false
        SmsBridge.launchOutgoing(messageId = 800_001L) {
            entered.complete(Unit)
            release.await()
            claimed = SmsBridge.beginOutgoingDispatch(800_001L)
        }
        entered.await()

        assertTrue(SmsBridge.cancelOutgoing(800_001L))
        assertFalse(claimed)
        assertFalse(SmsBridge.beginOutgoingDispatch(800_001L))
    }

    @Test
    fun `carrier dispatch cannot be cancelled once modem boundary is claimed`() = runBlocking {
        val claimed = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = SmsBridge.launchOutgoing(messageId = 800_002L) {
            assertTrue(SmsBridge.beginOutgoingDispatch(800_002L))
            claimed.complete(Unit)
            release.await()
        }
        claimed.await()

        assertFalse(SmsBridge.cancelOutgoing(800_002L))
        release.complete(Unit)
        job.join()
    }

    private fun stageMessage(guid: String, recipients: Int, parts: Int): Message {
        val chat = Chat().apply {
            this.guid = "carrier-chat-$guid"
            isRpSms = true
        }
        store.boxFor(Chat::class.java).put(chat)
        val message = Message().apply {
            this.guid = guid
            isFromMe = true
            sendingServiceId = "sending"
            metadata = linkedMapOf("existing" to "kept")
            this.chat.target = chat
        }
        store.boxFor(Message::class.java).put(message)
        prepareCarrierSendStatus(store, guid, recipients, parts)
        return message
    }

    private fun stored(guid: String): Message = store.boxFor(Message::class.java)
        .query()
        .equal(Message_.guid, guid, QueryBuilder.StringOrder.CASE_SENSITIVE)
        .build().use { query -> requireNotNull(query.findFirst()) }

    private fun apply(
        guid: String,
        kind: CarrierCallbackKind,
        recipient: Int,
        part: Int,
        successful: Boolean = true,
        deliveredAt: Date = Date(),
    ): Boolean = applyCarrierSendStatus(
        store = store,
        guid = guid,
        kind = kind,
        identity = CarrierCallbackIdentity(recipient, part),
        successful = successful,
        failureDescription = "carrier rejected component",
        deliveredAt = deliveredAt,
    )
}

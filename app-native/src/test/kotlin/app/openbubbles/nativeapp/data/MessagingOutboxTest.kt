package app.openbubbles.nativeapp.data

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

class MessagingOutboxTest {
    private lateinit var root: File

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("ob-messaging-outbox").toFile()
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun `queued attempts and mention metadata survive a process restart`() {
        val first = MessagingOutboxStore(root)
        val expected = entry(
            id = 42L,
            mentions = listOf(OutgoingOutboxMention(0, 6, "mailto:friend@example.com", "Friend")),
        )
        first.enqueue(expected)

        val restarted = MessagingOutboxStore(root)

        assertEquals(listOf(expected), restarted.entries(ACCOUNT_A))
        assertEquals(expected, restarted.entry(42L))
    }

    @Test
    fun `irreversible attempts remain distinguishable from safe replay after restart`() {
        val first = MessagingOutboxStore(root)
        first.enqueue(entry(1L))
        first.enqueue(entry(2L))
        first.update(2L, ACCOUNT_A) {
            it.copy(state = OutgoingOutboxState.DISPATCHING, attempt = 1)
        }

        val recovered = MessagingOutboxStore(root).entries(ACCOUNT_A)

        assertEquals(OutgoingOutboxState.QUEUED, recovered.single { it.messageId == 1L }.state)
        assertEquals(OutgoingOutboxState.DISPATCHING, recovered.single { it.messageId == 2L }.state)
    }

    @Test
    fun `an account can neither mutate nor replace another account attempt`() {
        val outbox = MessagingOutboxStore(root)
        outbox.enqueue(entry(9L))

        assertNull(outbox.update(9L, ACCOUNT_B) { it.copy(state = OutgoingOutboxState.CANCELLED) })
        assertFalse(outbox.remove(9L, ACCOUNT_B))
        assertFailsWith<IllegalStateException> {
            outbox.enqueue(entry(9L, owner = ACCOUNT_B))
        }
        assertEquals(OutgoingOutboxState.QUEUED, outbox.entry(9L)?.state)
    }

    @Test
    fun `clearing one account preserves independent owner entries`() {
        val outbox = MessagingOutboxStore(root)
        outbox.enqueue(entry(1L, owner = ACCOUNT_A))
        outbox.enqueue(entry(2L, owner = ACCOUNT_B))

        outbox.clearOwner(ACCOUNT_A)

        assertEquals(emptyList(), outbox.entries(ACCOUNT_A))
        assertEquals(listOf(2L), MessagingOutboxStore(root).entries(ACCOUNT_B).map { it.messageId })
    }

    @Test
    fun `malformed account identities fail closed before publication`() {
        val outbox = MessagingOutboxStore(root)

        assertFailsWith<IllegalArgumentException> {
            outbox.enqueue(entry(1L, owner = "another-account"))
        }
        assertFalse(root.resolve("outgoing-messages.json").exists())
    }

    @Test
    fun `pre-dispatch cancellation tombstones before joining the transport job`() = runTest {
        val registry = MessagingAccountWorkRegistry(backgroundScope)
        val generation = registry.activate()
        val started = CompletableDeferred<Unit>()
        val cleanupStarted = CompletableDeferred<Unit>()
        val releaseCleanup = CompletableDeferred<Unit>()
        var tombstoned = false

        assertNotNull(
            registry.launchMessage(generation, 21L) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        releaseCleanup.await()
                    }
                }
            },
        )
        started.await()

        val cancellation = async {
            registry.cancelMessageAndJoin(21L) { tombstoned = true }
        }
        cleanupStarted.await()

        assertTrue(tombstoned)
        assertFalse(cancellation.isCompleted)
        assertFalse(registry.canPublish(generation, 21L))
        assertFalse(registry.beginDispatch(generation, 21L))
        releaseCleanup.complete(Unit)
        assertTrue(cancellation.await())
        assertFalse(registry.hasMessage(21L))
        registry.invalidateAndJoin()
    }

    @Test
    fun `an irreversible send cannot be cancelled or launched twice`() = runTest {
        val registry = MessagingAccountWorkRegistry(backgroundScope)
        val generation = registry.activate()
        val started = CompletableDeferred<Unit>()

        assertNotNull(
            registry.launchMessage(generation, 31L) {
                started.complete(Unit)
                awaitCancellation()
            },
        )
        started.await()

        assertNull(registry.launchMessage(generation, 31L) { error("duplicate send launched") })
        assertTrue(registry.beginDispatch(generation, 31L))
        assertFalse(registry.beginDispatch(generation, 31L))
        assertFalse(registry.cancelMessageAndJoin(31L))
        assertTrue(registry.canPublish(generation, 31L))
        registry.invalidateAndJoin()
    }

    @Test
    fun `account invalidation fences retained per-message jobs`() = runTest {
        val registry = MessagingAccountWorkRegistry(backgroundScope)
        val accountA = registry.activate()
        val started = CompletableDeferred<Unit>()

        assertNotNull(
            registry.launchMessage(accountA, 41L) {
                started.complete(Unit)
                awaitCancellation()
            },
        )
        started.await()
        registry.invalidateAndJoin()

        assertFalse(registry.hasMessage(41L))
        assertFalse(registry.canPublish(accountA, 41L))
        val accountB = registry.activate()
        assertNull(registry.launchMessage(accountA, 41L) { error("stale account dispatched") })
        assertNotNull(registry.launchMessage(accountB, 41L) { })
        registry.invalidateAndJoin()
    }

    @Test
    fun `connection retry delays are bounded`() {
        assertEquals(2_000L, outgoingRetryDelayMs(0))
        assertEquals(4_000L, outgoingRetryDelayMs(1))
        assertEquals(120_000L, outgoingRetryDelayMs(100))
    }

    private fun entry(
        id: Long,
        owner: String = ACCOUNT_A,
        mentions: List<OutgoingOutboxMention> = emptyList(),
    ): OutgoingOutboxEntry = OutgoingOutboxEntry(
        accountOwner = owner,
        messageId = id,
        chatId = 7L,
        sender = "mailto:me@example.com",
        kind = OutgoingOutboxKind.TEXT,
        mentions = mentions,
    )

    private companion object {
        val ACCOUNT_A = "a".repeat(64)
        val ACCOUNT_B = "b".repeat(64)
    }
}

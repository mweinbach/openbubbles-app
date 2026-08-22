package app.openbubbles.nativeapp.ui.chat

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class MessageAttachmentIoTest {
    @Test
    fun `file work runs on io and successful callbacks return to the owner dispatcher`() = runTest {
        val ownerDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val owner = CoroutineScope(SupervisorJob() + ownerDispatcher)
        val events = mutableListOf<String>()

        launchMessageAttachmentIo(
            scope = owner,
            ioDispatcher = ioDispatcher,
            work = {
                assertSame(ioDispatcher, currentCoroutineContext()[ContinuationInterceptor])
                events += "io"
                "Saved to Downloads"
            },
            onSuccess = { message ->
                assertSame(ownerDispatcher, currentCoroutineContext()[ContinuationInterceptor])
                events += message
            },
            onFailure = { error -> throw AssertionError("Unexpected attachment failure", error) },
        )

        assertTrue(events.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf("io", "Saved to Downloads"), events)
        owner.cancel()
    }

    @Test
    fun `provider failures return to the owner without publishing success`() = runTest {
        val ownerDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val owner = CoroutineScope(SupervisorJob() + ownerDispatcher)
        var success = false
        var observedFailure: Throwable? = null

        launchMessageAttachmentIo(
            scope = owner,
            ioDispatcher = ioDispatcher,
            work = { throw IOException("provider unavailable") },
            onSuccess = { success = true },
            onFailure = { error ->
                assertSame(ownerDispatcher, currentCoroutineContext()[ContinuationInterceptor])
                observedFailure = error
            },
        )
        advanceUntilIdle()

        assertFalse(success)
        assertEquals("provider unavailable", assertIs<IOException>(observedFailure).message)
        owner.cancel()
    }

    @Test
    fun `destroyed owners cannot publish a late cancellation resistant result`() = runTest {
        val ownerDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val owner = CoroutineScope(SupervisorJob() + ownerDispatcher)
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var success = false
        var failure = false

        val action = launchMessageAttachmentIo(
            scope = owner,
            ioDispatcher = ioDispatcher,
            work = {
                started.complete(Unit)
                withContext(NonCancellable) { release.await() }
                "late attachment"
            },
            onSuccess = { success = true },
            onFailure = { failure = true },
        )
        runCurrent()
        assertTrue(started.isCompleted)

        owner.cancel()
        release.complete(Unit)
        advanceUntilIdle()

        assertTrue(action.isCancelled)
        assertFalse(success)
        assertFalse(failure)
    }

    @Test
    fun `successful exports are published without deleting user owned copies`() = runTest {
        val events = mutableListOf<String>()

        publishMessageAttachmentExport(
            reserve = { "downloads/pending" },
            write = { destination -> events += "write:$destination" },
            publish = { destination ->
                events += "publish:$destination"
                true
            },
            rollback = { destination -> events += "delete:$destination" },
        )

        assertEquals(
            listOf("write:downloads/pending", "publish:downloads/pending"),
            events,
        )
    }

    @Test
    fun `failed writes delete only their unpublished export`() = runTest {
        val ownerDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val owner = CoroutineScope(SupervisorJob() + ownerDispatcher)
        val events = mutableListOf<String>()

        launchMessageAttachmentIo(
            scope = owner,
            ioDispatcher = ioDispatcher,
            work = {
                publishMessageAttachmentExport(
                    reserve = { "downloads/pending" },
                    write = { throw IOException("copy failed") },
                    publish = { events += "publish"; true },
                    rollback = { destination -> events += "delete:$destination" },
                )
            },
            onSuccess = { events += "success" },
            onFailure = { error -> events += "error:${error.message}" },
        )
        advanceUntilIdle()

        assertEquals(listOf("delete:downloads/pending", "error:copy failed"), events)
        owner.cancel()
    }

    @Test
    fun `cancelled chunked copies roll back pending rows without late callbacks`() = runTest {
        val ownerDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val ownerJob = SupervisorJob()
        val owner = CoroutineScope(ownerJob + ownerDispatcher)
        val source = CancellingInputStream(ownerJob)
        val destination = ByteArrayOutputStream()
        val events = mutableListOf<String>()

        val action = launchMessageAttachmentIo(
            scope = owner,
            ioDispatcher = ioDispatcher,
            work = {
                publishMessageAttachmentExport(
                    reserve = { "downloads/pending" },
                    write = { copyMessageAttachmentBytes(source, destination) },
                    publish = { events += "publish"; true },
                    rollback = { uri -> events += "delete:$uri" },
                )
            },
            onSuccess = { events += "success" },
            onFailure = { events += "error" },
        )
        advanceUntilIdle()

        assertTrue(action.isCancelled)
        assertEquals("a", destination.toString(Charsets.UTF_8))
        assertEquals(2, source.readCount)
        assertEquals(listOf("delete:downloads/pending"), events)
    }

    @Test
    fun `null reservations fail without inventing an export to delete`() = runTest {
        val ownerDispatcher = StandardTestDispatcher(testScheduler)
        val ioDispatcher = StandardTestDispatcher(testScheduler)
        val owner = CoroutineScope(SupervisorJob() + ownerDispatcher)
        val events = mutableListOf<String>()

        launchMessageAttachmentIo(
            scope = owner,
            ioDispatcher = ioDispatcher,
            work = {
                publishMessageAttachmentExport<String>(
                    reserve = { null },
                    write = { events += "write" },
                    publish = { events += "publish"; true },
                    rollback = { events += "delete" },
                )
            },
            onSuccess = { events += "success" },
            onFailure = { error -> events += "error:${error.message}" },
        )
        advanceUntilIdle()

        assertEquals(listOf("error:Could not reserve the attachment export"), events)
        owner.cancel()
    }

    @Test
    fun `complete copies preserve every byte and flush their destination`() = runTest {
        val bytes = ByteArray(DEFAULT_BUFFER_SIZE * 2 + 17) { index -> index.toByte() }
        val destination = ByteArrayOutputStream()

        copyMessageAttachmentBytes(ByteArrayInputStream(bytes), destination)

        assertTrue(bytes.contentEquals(destination.toByteArray()))
    }

    private class CancellingInputStream(
        private val owner: Job,
    ) : InputStream() {
        var readCount = 0

        override fun read(): Int = error("Chunked attachment copies must read their shared buffer")

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            readCount += 1
            return when (readCount) {
                1 -> {
                    buffer[offset] = 'a'.code.toByte()
                    1
                }
                2 -> {
                    buffer[offset] = 'b'.code.toByte()
                    owner.cancel()
                    1
                }
                else -> error("Cancelled copies must not request another chunk")
            }
        }
    }
}

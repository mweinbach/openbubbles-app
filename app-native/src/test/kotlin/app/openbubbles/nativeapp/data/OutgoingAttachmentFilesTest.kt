package app.openbubbles.nativeapp.data

import java.nio.file.Files
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlinx.coroutines.test.runTest
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith
import org.junit.Test
import uniffi.rust_lib_bluebubbles.NativePushState
import uniffi.rust_lib_bluebubbles.UIndexedPart
import uniffi.rust_lib_bluebubbles.UMessage
import uniffi.rust_lib_bluebubbles.UPart
import uniffi.rust_lib_bluebubbles.UProgressCallback
import uniffi.rust_lib_bluebubbles.USendAttachmentsRequest

class OutgoingAttachmentFilesTest {
    @Test
    fun `bounded copy rejects one byte over the ceiling and leaves no accepted payload`() {
        val root = Files.createTempDirectory("outgoing-limit").toFile()
        val partial = root.resolve("payload.part")
        try {
            assertFailsWith<IOException> {
                copyWithByteLimit(ByteArrayInputStream(ByteArray(9)), partial, maxBytes = 8)
            }
            assertTrue(partial.length() <= 8L)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `owned draft cleanup refuses files outside cache draft folders`() {
        val root = Files.createTempDirectory("outgoing-owned").toFile()
        try {
            val owned = root.resolve("cache/outgoing/photo.jpg").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(1))
            }
            val foreign = root.resolve("files/photo.jpg").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(2))
            }

            assertTrue(deleteOwnedOutgoingDraft(owned, root.resolve("cache")))
            assertFalse(owned.exists())
            assertFalse(deleteOwnedOutgoingDraft(foreign, root.resolve("cache")))
            assertTrue(foreign.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `owned sibling promotion atomically replaces the canonical image`() {
        val root = Files.createTempDirectory("owned-promote").toFile()
        try {
            val current = root.resolve("avatar.img").apply { writeText("old") }
            val staged = root.resolve("pending.jpg").apply { writeText("new") }

            assertEquals(current, promoteOwnedSibling(staged, current))
            assertEquals("new", current.readText())
            assertFalse(staged.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `owned sibling promotion refuses a foreign staged image without replacing current`() {
        val root = Files.createTempDirectory("owned-promote-foreign").toFile()
        try {
            val current = root.resolve("profile/avatar.img").apply {
                requireNotNull(parentFile).mkdirs()
                writeText("old")
            }
            val foreign = root.resolve("picker/pending.jpg").apply {
                requireNotNull(parentFile).mkdirs()
                writeText("new")
            }

            assertFailsWith<IllegalArgumentException> { promoteOwnedSibling(foreign, current) }
            assertEquals("old", current.readText())
            assertTrue(foreign.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `draft ownership is anchored to the supplied app cache root`() {
        val root = Files.createTempDirectory("outgoing-anchor").toFile()
        try {
            val realCache = root.resolve("app-cache")
            val decoy = root.resolve("elsewhere/cache/outgoing/photo.jpg").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }

            assertFalse(deleteOwnedOutgoingDraft(decoy, realCache))
            assertTrue(decoy.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `startup reconciliation deletes only stale direct app cache drafts`() {
        val root = Files.createTempDirectory("outgoing-reconcile").toFile()
        try {
            val cache = root.resolve("cache")
            val staleOutgoing = cache.resolve("outgoing/stale.bin").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(1))
                setLastModified(1_000L)
            }
            val staleCapture = cache.resolve("captures/stale.jpg").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(2))
                setLastModified(1_000L)
            }
            val fresh = cache.resolve("outgoing/fresh.bin").apply {
                writeBytes(byteArrayOf(3))
                setLastModified(9_500L)
            }
            val nested = cache.resolve("outgoing/nested/keep.bin").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(4))
                setLastModified(1_000L)
            }
            val foreign = root.resolve("other/cache/outgoing/keep.bin").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(5))
                setLastModified(1_000L)
            }

            val removed = reconcileAbandonedOutgoingDrafts(
                cacheRoot = cache,
                nowMs = 10_000L,
                minimumAgeMs = 1_000L,
            )

            assertEquals(2, removed)
            assertFalse(staleOutgoing.exists())
            assertFalse(staleCapture.exists())
            assertTrue(fresh.exists())
            assertTrue(nested.exists())
            assertTrue(foreign.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `shared iMessage and MMS staging preserves every source on partial multi-file failure`() = runTest {
        val root = Files.createTempDirectory("outgoing-partial-stage").toFile()
        try {
            val cache = root.resolve("cache")
            val first = cache.resolve("outgoing/first.bin").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val second = cache.resolve("captures/second.bin").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(4, 5, 6, 7))
            }
            val firstCanonical = root.resolve("canonical/a/first.bin")
            val secondCanonical = root.resolve("canonical/b/second.bin")
            var persisted = false

            val failure = runCatching {
                stageOutgoingPayloadBatch(
                    stages = listOf(
                        OutgoingPayloadStage(first, firstCanonical),
                        OutgoingPayloadStage(second, secondCanonical),
                    ),
                    cacheRoot = cache,
                    maxBytes = 3,
                ) {
                    persisted = true
                }
            }.exceptionOrNull()

            assertTrue(failure is IOException)
            assertFalse(persisted)
            assertTrue(first.exists())
            assertTrue(second.exists())
            assertFalse(firstCanonical.exists())
            assertFalse(secondCanonical.exists())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `shared iMessage and MMS staging preserves sources when DB stage fails`() = runTest {
        val root = Files.createTempDirectory("outgoing-db-stage").toFile()
        try {
            val cache = root.resolve("cache")
            val sources = listOf("one", "two").map { name ->
                cache.resolve("outgoing/$name.bin").apply {
                    requireNotNull(parentFile).mkdirs()
                    writeBytes(byteArrayOf(1, 2, 3))
                }
            }
            val canonicals = listOf(
                root.resolve("canonical/a/one.bin"),
                root.resolve("canonical/b/two.bin"),
            )

            val failure = runCatching {
                stageOutgoingPayloadBatch(
                    stages = sources.zip(canonicals).map { (source, destination) ->
                        OutgoingPayloadStage(source, destination)
                    },
                    cacheRoot = cache,
                ) {
                    error("database stage failed")
                }
            }.exceptionOrNull()

            assertEquals("database stage failed", failure?.message)
            assertTrue(sources.all(File::exists))
            assertTrue(canonicals.none(File::exists))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `successful durable staging retires only sources under the real cache root`() = runTest {
        val root = Files.createTempDirectory("outgoing-stage-success").toFile()
        try {
            val cache = root.resolve("cache")
            val owned = cache.resolve("outgoing/owned.bin").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(1, 2, 3))
            }
            val foreign = root.resolve("other/cache/outgoing/foreign.bin").apply {
                requireNotNull(parentFile).mkdirs()
                writeBytes(byteArrayOf(4, 5, 6))
            }
            val canonicals = listOf(
                root.resolve("canonical/a/owned.bin"),
                root.resolve("canonical/b/foreign.bin"),
            )

            val result = stageOutgoingPayloadBatch(
                stages = listOf(
                    OutgoingPayloadStage(owned, canonicals[0]),
                    OutgoingPayloadStage(foreign, canonicals[1]),
                ),
                cacheRoot = cache,
            ) { payloads ->
                assertTrue(payloads.all(File::isFile))
                "persisted"
            }

            assertEquals("persisted", result)
            assertFalse(owned.exists())
            assertTrue(foreign.exists())
            assertContentEquals(byteArrayOf(1, 2, 3), canonicals[0].readBytes())
            assertContentEquals(byteArrayOf(4, 5, 6), canonicals[1].readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `iMessage attachment sends avoid the foreign progress callback`() {
        assertNull(attachmentSendProgressCallback())
    }

    @Test
    fun `partial send promotes every returned display attachment before ingest`() {
        val plan = returnedAttachmentPlan(
            normal = normalWith(
                UIndexedPart(
                    UPart.Attachment(
                        part = 0uL,
                        uti = "public.jpeg",
                        mime = "image/jpeg",
                        name = "first.jpg",
                        iris = false,
                        xml = "",
                    ),
                    null,
                    null,
                ),
            ),
            messageGuid = "real-message",
            stagedGuids = listOf("temp-message_att0", "temp-message_att1"),
        )

        assertFalse(plan.complete)
        assertEquals(1, plan.rawAttachmentCount)
        assertEquals(
            listOf("temp-message_att0" to "real-message_0"),
            plan.promotions,
        )
    }

    @Test
    fun `transport completeness counts raw SMIL attachment parts`() {
        val plan = returnedAttachmentPlan(
            normal = normalWith(
                UIndexedPart(
                    UPart.Attachment(
                        part = 0uL,
                        uti = "public.smil",
                        mime = "application/smil",
                        name = "presentation.smil",
                        iris = false,
                        xml = "",
                    ),
                    null,
                    null,
                ),
            ),
            messageGuid = "real-message",
            stagedGuids = listOf("temp-message_att0"),
        )

        assertTrue(plan.complete)
        assertEquals(1, plan.rawAttachmentCount)
        assertTrue(plan.persistedAttachmentGuids.isEmpty())
    }

    @Test
    fun `SMIL filtering preserves the staged identity of later attachments`() {
        val plan = returnedAttachmentPlan(
            normal = normalWith(
                UIndexedPart(
                    UPart.Attachment(
                        part = 0uL,
                        uti = "public.smil",
                        mime = "application/smil",
                        name = "presentation.smil",
                        iris = false,
                        xml = "",
                    ),
                    null,
                    null,
                ),
                UIndexedPart(
                    UPart.Attachment(
                        part = 1uL,
                        uti = "public.jpeg",
                        mime = "image/jpeg",
                        name = "photo.jpg",
                        iris = false,
                        xml = "",
                    ),
                    null,
                    null,
                ),
            ),
            messageGuid = "real-message",
            stagedGuids = listOf("temp-message_att0", "temp-message_att1"),
        )

        assertTrue(plan.complete)
        assertEquals(
            listOf("temp-message_att1" to "real-message_0"),
            plan.promotions,
        )
    }

    @Test
    fun `iMessage attachment send binding stays compact and suspending`() {
        val method = NativePushState::class.java.methods.single { it.name == "sendAttachments" }

        assertEquals(3, method.parameterCount)
        assertEquals(USendAttachmentsRequest::class.java, method.parameterTypes[0])
        assertEquals(UProgressCallback::class.java, method.parameterTypes[1])
        assertEquals(Continuation::class.java, method.parameterTypes.last())
    }

    private fun normalWith(vararg parts: UIndexedPart) = UMessage.Normal(
        parts = parts.toList(),
        effect = null,
        replyGuid = null,
        replyPart = null,
        subject = null,
        voice = false,
        isSms = false,
        appJson = null,
        linkJson = null,
        profileJson = null,
    )
}

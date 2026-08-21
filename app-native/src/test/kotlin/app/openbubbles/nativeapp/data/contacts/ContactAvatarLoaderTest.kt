package app.openbubbles.nativeapp.data.contacts

import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ContactAvatarLoaderTest {

    @Test
    fun `unchanged source hash skips decode and jpeg compression`() {
        val root = Files.createTempDirectory("contact-avatar-loader").toFile()
        try {
            val sourceBytes = ByteArray(128 * 1024) { (it % 251).toByte() }
            val source = root.resolve("avatar.heic").apply { writeBytes(sourceBytes) }
            val hash = sha256(sourceBytes)
            var decodes = 0
            val loader = ContactAvatarLoader { _, value ->
                decodes++
                ContactAvatarPhoto(byteArrayOf(9), value, width = 1, height = 1)
            }

            assertSame(ContactAvatarChange.Unchanged, loader.resolve(source.absolutePath, hash))
            assertEquals(0, decodes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `one apply pass hashes and decodes a shared changed avatar once`() {
        val root = Files.createTempDirectory("contact-avatar-cache").toFile()
        try {
            val sourceBytes = "same iCloud avatar".toByteArray()
            val source = root.resolve("avatar.img").apply { writeBytes(sourceBytes) }
            var decodes = 0
            val output = byteArrayOf(1, 2, 3)
            val loader = ContactAvatarLoader { _, hash ->
                decodes++
                ContactAvatarPhoto(output, hash, width = 10, height = 10)
            }

            val first = assertIs<ContactAvatarChange.Set>(loader.resolve(source.absolutePath, "old-a"))
            val second = assertIs<ContactAvatarChange.Set>(loader.resolve(source.absolutePath, "old-b"))

            assertEquals(1, decodes)
            assertEquals(sha256(sourceBytes), first.photo.hash)
            assertEquals(first.photo.hash, second.photo.hash)
            assertContentEquals(output, second.photo.bytes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `positive decoded cache evicts least recently used photos by byte budget`() {
        val root = Files.createTempDirectory("contact-avatar-lru").toFile()
        try {
            val firstFile = root.resolve("first.img").apply { writeText("first") }
            val secondFile = root.resolve("second.img").apply { writeText("second") }
            var decodes = 0
            val loader = ContactAvatarLoader(maxDecodedBytes = 3) { _, hash ->
                decodes++
                ContactAvatarPhoto(byteArrayOf(1, 2), hash, width = 1, height = 1)
            }

            assertIs<ContactAvatarChange.Set>(loader.resolve(firstFile.absolutePath, "old"))
            assertIs<ContactAvatarChange.Set>(loader.resolve(secondFile.absolutePath, "old"))
            assertIs<ContactAvatarChange.Set>(loader.resolve(firstFile.absolutePath, "old"))

            assertEquals(3, decodes, "first entry should be evicted when the byte budget is exceeded")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `decoded avatars outside the dimension contract are rejected and negatively cached`() {
        val root = Files.createTempDirectory("contact-avatar-dimensions").toFile()
        try {
            val source = root.resolve("avatar.img").apply { writeText("image") }
            var decodes = 0
            val loader = ContactAvatarLoader(maxDimension = 10) { _, hash ->
                decodes++
                ContactAvatarPhoto(byteArrayOf(1), hash, width = 11, height = 10)
            }

            assertSame(ContactAvatarChange.Clear, loader.resolve(source.absolutePath, "old-a"))
            assertSame(ContactAvatarChange.Clear, loader.resolve(source.absolutePath, "old-b"))
            assertEquals(1, decodes)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `operation batcher flushes before photo payload exceeds its byte budget`() {
        val applied = mutableListOf<List<String>>()
        val batcher = ContactOperationBatcher(
            maxOperations = 10,
            maxPayloadBytes = 5,
            applyBatch = { applied.add(it.toList()) },
        )

        batcher.add(listOf("first"), addedPayloadBytes = 3)
        batcher.add(listOf("second"), addedPayloadBytes = 3)
        batcher.flush()

        assertEquals(listOf(listOf("first"), listOf("second")), applied)
    }

    @Test
    fun `operation batcher propagates provider failures and retains the pending pass`() {
        val failure = IllegalStateException("provider died")
        var attempts = 0
        val batcher = ContactOperationBatcher<String>(10, 10) {
            attempts++
            throw failure
        }
        batcher.add(listOf("contact"), addedPayloadBytes = 1)

        assertSame(failure, assertFailsWith<IllegalStateException> { batcher.flush() })
        assertSame(failure, assertFailsWith<IllegalStateException> { batcher.flush() })
        assertEquals(2, attempts, "failed batches must not be reported or cleared as successful")
    }

    @Test
    fun `failed decode is also cached for the apply pass`() {
        val root = Files.createTempDirectory("contact-avatar-failure-cache").toFile()
        try {
            val source = root.resolve("invalid.img").apply { writeText("not an image") }
            var decodes = 0
            val loader = ContactAvatarLoader { _, _ ->
                decodes++
                null
            }

            assertSame(ContactAvatarChange.Clear, loader.resolve(source.absolutePath, "old-a"))
            assertSame(ContactAvatarChange.Clear, loader.resolve(source.absolutePath, "old-b"))
            assertEquals(1, decodes)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}

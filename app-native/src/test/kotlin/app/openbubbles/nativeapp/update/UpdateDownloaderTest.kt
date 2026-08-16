package app.openbubbles.nativeapp.update

import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class UpdateDownloaderTest {

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun manifest(bytes: ByteArray, sizeOverride: Long = bytes.size.toLong()) =
        UpdateManifest(
            versionCode = 42,
            versionName = "2.0.42",
            apkAsset = "openbubbles-2.0.42.apk",
            sha256 = sha256(bytes),
            bytes = sizeOverride,
        )

    private fun client() = GitHubUpdateSource.defaultClient()

    @Test
    fun `verified download lands at the versioned path with no partial file`() {
        val payload = ByteArray(300_000) { (it % 251).toByte() }
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
        val dir = File(Files.createTempDirectory("ob-update-test").toFile(), "updates")
        val feed = UpdateFeed(manifest(payload), server.url("/apk").toString())

        val file = UpdateDownloader(client(), { "t" }).download(feed, dir)

        assertEquals(File(dir, "openbubbles-42.apk"), file)
        assertTrue(file.length() == payload.size.toLong())
        assertTrue(dir.listFiles()!!.all { !it.name.endsWith(".part") })
        val request = server.takeRequest()
        assertEquals("Bearer t", request.getHeader("Authorization"))
        assertEquals("application/octet-stream", request.getHeader("Accept"))
        server.shutdown()
    }

    @Test
    fun `hash mismatch rejects the download and cleans up`() {
        val payload = "tampered-bytes".toByteArray()
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
        val dir = File(Files.createTempDirectory("ob-update-test").toFile(), "updates")
        val bad = manifest(payload).copy(sha256 = "deadbeef")

        assertFailsWith<UpdateDownloader.DownloadException.HashMismatch> {
            UpdateDownloader(client(), { "t" }).download(UpdateFeed(bad, server.url("/apk").toString()), dir)
        }
        assertEquals(0, dir.listFiles()?.size ?: 0)
        server.shutdown()
    }

    @Test
    fun `truncated transfer fails the byte count`() {
        val payload = ByteArray(1000)
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
        val dir = File(Files.createTempDirectory("ob-update-test").toFile(), "updates")
        val wrongSize = manifest(payload, sizeOverride = 2000)

        val e = assertFailsWith<UpdateDownloader.DownloadException.SizeMismatch> {
            UpdateDownloader(client(), { "t" })
                .download(UpdateFeed(wrongSize, server.url("/apk").toString()), dir)
        }
        assertEquals(1000L, e.got)
        assertEquals(2000L, e.want)
        assertTrue((dir.listFiles()?.size ?: 0) == 0)
        server.shutdown()
    }

    @Test
    fun `server error surfaces as Http`() {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setResponseCode(503))
        val dir = File(Files.createTempDirectory("ob-update-test").toFile(), "updates")
        val m = manifest(ByteArray(10))

        val e = assertFailsWith<UpdateDownloader.DownloadException.Http> {
            UpdateDownloader(client(), { "t" }).download(UpdateFeed(m, server.url("/apk").toString()), dir)
        }
        assertEquals(503, e.code)
        server.shutdown()
    }

    @Test
    fun `missing token is AuthRequired`() {
        val dir = File(Files.createTempDirectory("ob-update-test").toFile(), "updates")
        val m = manifest(ByteArray(10))
        assertFailsWith<UpdateDownloader.DownloadException.AuthRequired> {
            UpdateDownloader(client(), { null }).download(UpdateFeed(m, "https://example.invalid/apk"), dir)
        }
    }

    @Test
    fun `purgeStale keeps only the given version`() {
        val dir = File(Files.createTempDirectory("ob-update-test").toFile(), "updates").apply { mkdirs() }
        val keep = UpdateDownloader.apkFileFor(dir, 7).apply { writeText("v7") }
        UpdateDownloader.apkFileFor(dir, 6).apply { writeText("v6") }
        File(dir, "openbubbles-6.apk.part").apply { writeText("partial") }

        UpdateDownloader.purgeStale(dir, 7)

        assertEquals(listOf(keep.name), dir.listFiles()!!.map { it.name })
        assertFalse(File(dir, "openbubbles-6.apk").exists())
    }
}

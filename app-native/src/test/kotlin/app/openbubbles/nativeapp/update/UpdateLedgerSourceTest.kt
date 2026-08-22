package app.openbubbles.nativeapp.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class UpdateLedgerSourceTest {
    private val sha256 = "a".repeat(64)

    private fun appcast(
        build: Long = 200,
        version: String = "3.5.0",
        sha: String = sha256,
        downloadUrl: String,
    ) = """<?xml version="1.0" encoding="utf-8"?>
        <rss version="2.0"
          xmlns:sparkle="http://www.andymatuschak.org/xml-namespaces/sparkle"
          xmlns:ledger="https://update-ledger.mweinbach.chatgpt.site/xml-namespaces/ledger">
          <channel><title>OpenBubbles stable updates</title><item>
            <title>OpenBubbles $version</title>
            <description><![CDATA[Ready & verified]]></description>
            <enclosure url="$downloadUrl" sparkle:version="$build"
              sparkle:shortVersionString="$version" length="1234"
              ledger:assetName="openbubbles.apk" ledger:sha256="$sha"
              ledger:minVersionCode="150" type="application/octet-stream" />
          </item></channel>
        </rss>"""

    @Test
    fun `newer appcast enclosure maps to an Android update feed`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setBody(appcast(downloadUrl = url("/download/openbubbles.apk").toString())))
        }
        val source = UpdateLedgerSource(
            baseUrl = server.url("/").toString(),
            projectSlug = "openbubbles",
            channel = "stable",
        )

        val feed = source.fetch(100)!!

        assertEquals(200L, feed.manifest.versionCode)
        assertEquals("3.5.0", feed.manifest.versionName)
        assertEquals("openbubbles.apk", feed.manifest.apkAsset)
        assertEquals(sha256, feed.manifest.sha256)
        assertEquals(1234L, feed.manifest.bytes)
        assertEquals(150L, feed.manifest.minVersionCode)
        assertEquals("Ready & verified", feed.manifest.notes)
        assertEquals(server.url("/download/openbubbles.apk").toString(), feed.apkAssetUrl)
        assertEquals("/api/v1/appcast/openbubbles?channel=stable", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `current build returns no feed`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setBody(appcast(downloadUrl = url("/app.apk").toString())))
        }
        val source = UpdateLedgerSource(baseUrl = server.url("/").toString())

        assertNull(source.fetch(200))
        server.shutdown()
    }

    @Test
    fun `empty appcast returns no feed`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setBody("""<rss><channel /></rss>"""))
        }
        val source = UpdateLedgerSource(baseUrl = server.url("/").toString())

        assertNull(source.fetch(100))
        server.shutdown()
    }

    @Test
    fun `appcast without integrity metadata is malformed`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setBody(appcast(sha = "bad", downloadUrl = url("/app.apk").toString())))
        }
        val source = UpdateLedgerSource(baseUrl = server.url("/").toString())

        assertFailsWith<UpdateLedgerSource.SourceException.Malformed> {
            source.fetch(100)
        }
        server.shutdown()
    }

    @Test
    fun `appcast with a malformed build is rejected before update evaluation`() {
        val server = MockWebServer().apply {
            start()
            val malformed = appcast(downloadUrl = url("/app.apk").toString())
                .replace("sparkle:version=\"200\"", "sparkle:version=\"not-a-build\"")
            enqueue(MockResponse().setBody(malformed))
        }
        val source = UpdateLedgerSource(baseUrl = server.url("/").toString())

        assertFailsWith<UpdateLedgerSource.SourceException.Malformed> {
            source.fetch(100)
        }
        server.shutdown()
    }

    @Test
    fun `very high advertised build is not itself a trusted rollback floor`() {
        val server = MockWebServer().apply {
            start()
            enqueue(
                MockResponse().setBody(
                    appcast(build = Long.MAX_VALUE, downloadUrl = url("/app.apk").toString()),
                ),
            )
        }
        val source = UpdateLedgerSource(baseUrl = server.url("/").toString())

        val advertised = source.fetch(100)!!
        val floor = trustedRollbackFloor(
            RollbackFloorEvidence(
                installedVersionCode = 100L,
                legacyAdvertisedVersionCode = advertised.manifest.versionCode,
            ),
        )

        assertEquals(Long.MAX_VALUE, advertised.manifest.versionCode)
        assertEquals(100L, floor)
        server.shutdown()
    }

    @Test
    fun `server error maps to Http`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(503))
        }
        val source = UpdateLedgerSource(baseUrl = server.url("/").toString())

        val error = assertFailsWith<UpdateLedgerSource.SourceException.Http> {
            source.fetch(100)
        }
        assertEquals(503, error.code)
        server.shutdown()
    }
}

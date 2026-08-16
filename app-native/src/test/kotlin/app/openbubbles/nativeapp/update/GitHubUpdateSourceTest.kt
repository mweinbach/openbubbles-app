package app.openbubbles.nativeapp.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class GitHubUpdateSourceTest {

    private fun releaseJson(vararg assets: Pair<String, String>): String {
        val assetJson = assets.joinToString(",") { (name, url) ->
            """{"name":"$name","url":"$url","browser_download_url":"ignored"}"""
        }
        return """{"tag_name":"v2.0.1","assets":[$assetJson]}"""
    }

    @Test
    fun `fetch reads the feed asset with auth and returns the apk asset url`() {
        val server = MockWebServer().apply {
            start()
            enqueue(
                MockResponse().setBody(
                    releaseJson(
                        "update.json" to url("/repos/x/releases/assets/1").toString(),
                        "openbubbles-2.0.1.apk" to url("/repos/x/releases/assets/2").toString(),
                    ),
                ),
            )
            enqueue(MockResponse().setBody("""{"versionCode":7,"versionName":"2.0.1","apkAsset":"openbubbles-2.0.1.apk","sha256":"ab"}"""))
        }
        val source = GitHubUpdateSource(
            baseUrl = server.url("/").toString().trimEnd('/'),
            repoSlug = "x/y",
            token = { "tok123" },
        )
        val feed = source.fetch()

        assertEquals(7L, feed.manifest.versionCode)
        assertTrue(feed.apkAssetUrl.endsWith("/repos/x/releases/assets/2"))

        val release = server.takeRequest()
        assertEquals("/repos/x/y/releases/latest", release.path)
        assertEquals("Bearer tok123", release.getHeader("Authorization"))
        assertEquals("application/vnd.github+json", release.getHeader("Accept"))

        val asset = server.takeRequest()
        assertEquals("/repos/x/releases/assets/1", asset.path)
        assertEquals("Bearer tok123", asset.getHeader("Authorization"))
        assertEquals("application/octet-stream", asset.getHeader("Accept"))
        server.shutdown()
    }

    @Test
    fun `feed asset redirect is followed`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setBody(releaseJson(
                "update.json" to url("/repos/x/releases/assets/1").toString(),
                "a.apk" to url("/repos/x/releases/assets/2").toString(),
            )))
            enqueue(
                MockResponse().setResponseCode(302)
                    .setHeader("Location", "/signed/feed"),
            )
            enqueue(MockResponse().setBody("""{"versionCode":8,"versionName":"2.0.2","apkAsset":"a.apk","sha256":"cd"}"""))
        }
        val source = GitHubUpdateSource(
            baseUrl = server.url("/").toString().trimEnd('/'),
            repoSlug = "x/y",
            token = { "t" },
        )
        assertEquals(8L, source.fetch().manifest.versionCode)
        server.takeRequest()
        server.takeRequest()
        assertEquals("/signed/feed", server.takeRequest().path)
        server.shutdown()
    }

    @Test
    fun `no releases maps to NoReleases`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(404))
        }
        val source = GitHubUpdateSource(
            baseUrl = server.url("/").toString().trimEnd('/'),
            repoSlug = "x/y",
            token = { "t" },
        )
        assertFailsWith<GitHubUpdateSource.SourceException.NoReleases> { source.fetch() }
        server.shutdown()
    }

    @Test
    fun `auth rejected even tokenless maps to Http`() {
        val server = MockWebServer().apply {
            start()
            // First 401 triggers the tokenless retry; the second must surface.
            enqueue(MockResponse().setResponseCode(401))
            enqueue(MockResponse().setResponseCode(401))
        }
        val source = GitHubUpdateSource(
            baseUrl = server.url("/").toString().trimEnd('/'),
            repoSlug = "x/y",
            token = { "bad" },
        )
        val e = assertFailsWith<GitHubUpdateSource.SourceException.Http> { source.fetch() }
        assertEquals(401, e.code)
        server.shutdown()
    }

    @Test
    fun `release without feed asset is Malformed`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setBody(releaseJson("something-else.apk" to url("/u").toString())))
        }
        val source = GitHubUpdateSource(
            baseUrl = server.url("/").toString().trimEnd('/'),
            repoSlug = "x/y",
            token = { "t" },
        )
        assertFailsWith<GitHubUpdateSource.SourceException.Malformed> { source.fetch() }
        server.shutdown()
    }

    @Test
    fun `manifest naming an apk asset missing from the release is Malformed`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setBody(releaseJson("update.json" to url("/a/1").toString())))
            enqueue(MockResponse().setBody("""{"versionCode":7,"versionName":"2","apkAsset":"missing.apk","sha256":"ab"}"""))
        }
        val source = GitHubUpdateSource(
            baseUrl = server.url("/").toString().trimEnd('/'),
            repoSlug = "x/y",
            token = { "t" },
        )
        assertFailsWith<GitHubUpdateSource.SourceException.Malformed> { source.fetch() }
        server.shutdown()
    }

    @Test
    fun `missing token fetches unauthenticated`() {
        val server = MockWebServer().apply {
            start()
            enqueue(
                MockResponse().setBody(
                    releaseJson(
                        "update.json" to url("/a/1").toString(),
                        "a.apk" to url("/a/2").toString(),
                    ),
                ),
            )
            enqueue(MockResponse().setBody("""{"versionCode":7,"versionName":"2","apkAsset":"a.apk","sha256":"ab"}"""))
        }
        val source = GitHubUpdateSource(
            baseUrl = server.url("/").toString().trimEnd('/'),
            repoSlug = "x/y",
            token = { null },
        )
        assertEquals(7L, source.fetch().manifest.versionCode)
        val request = server.takeRequest()
        assertEquals(null, request.getHeader("Authorization"))
        server.shutdown()
    }

    @Test
    fun `rejected token retries once without auth on a public feed`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(401))
            enqueue(
                MockResponse().setBody(
                    releaseJson(
                        "update.json" to url("/a/1").toString(),
                        "a.apk" to url("/a/2").toString(),
                    ),
                ),
            )
            enqueue(MockResponse().setBody("""{"versionCode":9,"versionName":"2","apkAsset":"a.apk","sha256":"ab"}"""))
        }
        val source = GitHubUpdateSource(
            baseUrl = server.url("/").toString().trimEnd('/'),
            repoSlug = "x/y",
            token = { "stale-token" },
        )
        assertEquals(9L, source.fetch().manifest.versionCode)
        val first = server.takeRequest()
        assertEquals("Bearer stale-token", first.getHeader("Authorization"))
        val second = server.takeRequest()
        assertEquals(null, second.getHeader("Authorization"))
        server.shutdown()
    }

    @Test
    fun `tokenless 401 stays an Http error`() {
        val server = MockWebServer().apply {
            start()
            enqueue(MockResponse().setResponseCode(401))
        }
        val source = GitHubUpdateSource(
            baseUrl = server.url("/").toString().trimEnd('/'),
            repoSlug = "x/y",
            token = { null },
        )
        val e = assertFailsWith<GitHubUpdateSource.SourceException.Http> { source.fetch() }
        assertEquals(401, e.code)
        server.shutdown()
    }
}

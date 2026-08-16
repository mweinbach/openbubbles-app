package app.openbubbles.nativeapp.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

/** A fetched update feed: the decoded manifest plus its APK's asset API URL. */
data class UpdateFeed(
    val manifest: UpdateManifest,
    val apkAssetUrl: String,
)

/**
 * Reads the latest self-update release from the GitHub repo.
 *
 * The repo is public, so unauthenticated reads work; a stored token (when
 * present) is sent as `Authorization: Bearer` and dropped for one retry on
 * 401/403 so a stale token never blocks updates to a public feed. Asset
 * downloads use the asset API URL with `Accept: application/octet-stream`;
 * GitHub 302s to a signed object-storage URL, which OkHttp follows (and
 * strips our Authorization header on the cross-host hop, exactly as the
 * signed URL requires).
 *
 * Blocking OkHttp calls — run from `Dispatchers.IO`.
 */
class GitHubUpdateSource(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val repoSlug: String = DEFAULT_REPO,
    private val token: () -> String?,
    private val client: OkHttpClient = defaultClient(),
) {
    sealed class SourceException(message: String, cause: Throwable? = null) :
        Exception(message, cause) {
        /** Repo has no releases yet (HTTP 404 on /releases/latest). */
        class NoReleases : SourceException("no published releases")
        /** Non-recoverable HTTP status (rate limited, moved, …). */
        class Http(val code: Int) : SourceException("GitHub HTTP $code")
        /** Latest release exists but its feed asset is missing or invalid. */
        class Malformed(message: String, cause: Throwable? = null) :
            SourceException(message, cause)
    }

    fun fetch(): UpdateFeed {
        val releaseJson = getJson("$baseUrl/repos/$repoSlug/releases/latest")
        val release = runCatching { json.decodeFromString(GhRelease.serializer(), releaseJson) }
            .getOrElse { throw SourceException.Malformed("unreadable release payload", it) }
        val feedAsset = release.assets.firstOrNull { it.name == FEED_ASSET_NAME }
            ?: throw SourceException.Malformed("latest release has no $FEED_ASSET_NAME asset")
        val feedJson = getJson(feedAsset.url, octetStream = true)
        val manifest = runCatching { json.decodeFromString(UpdateManifest.serializer(), feedJson) }
            .getOrElse { throw SourceException.Malformed("unreadable $FEED_ASSET_NAME", it) }
        val apkAsset = release.assets.firstOrNull { it.name == manifest.apkAsset }
            ?: throw SourceException.Malformed(
                "release does not contain APK asset '${manifest.apkAsset}'",
            )
        return UpdateFeed(manifest, apkAsset.url)
    }

    /** Internal retry signal; never escapes [getJson]. */
    private class RetryWithoutAuth : Exception()

    private fun getJson(url: String, octetStream: Boolean = false): String {
        return try {
            getJsonOnce(url, octetStream, withToken = true)
        } catch (_: RetryWithoutAuth) {
            // A rejected token must not block reading a public feed: retry
            // once without authentication.
            getJsonOnce(url, octetStream, withToken = false)
        }
    }

    private fun getJsonOnce(url: String, octetStream: Boolean, withToken: Boolean): String {
        val builder = Request.Builder()
            .url(url)
            .header(
                "Accept",
                if (octetStream) "application/octet-stream" else "application/vnd.github+json",
            )
        val token = if (withToken) token() else null
        if (withToken && token != null) {
            builder.header("Authorization", "Bearer $token")
        }
        client.newCall(builder.build()).execute().use { response ->
            when {
                response.code == 404 && !octetStream -> throw SourceException.NoReleases()
                (response.code == 401 || response.code == 403) && withToken && token != null ->
                    throw RetryWithoutAuth()
                !response.isSuccessful -> throw SourceException.Http(response.code)
            }
            return response.body?.bytes()?.decodeToString()
                ?: throw SourceException.Malformed("empty body from $url")
        }
    }

    @Serializable
    internal data class GhAsset(val name: String, val url: String)

    @Serializable
    internal data class GhRelease(val assets: List<GhAsset> = emptyList())

    companion object {
        const val DEFAULT_BASE_URL = "https://api.github.com"
        const val DEFAULT_REPO = "mweinbach/openbubbles-app"
        const val FEED_ASSET_NAME = "update.json"

        private val json = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}

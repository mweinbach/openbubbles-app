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
 * Reads the latest self-update release from the private GitHub repo.
 *
 * Every call needs the fine-grained read-only PAT because both the release
 * metadata and asset bytes are private. Asset downloads use the asset API URL
 * with `Accept: application/octet-stream`; GitHub 302s to a signed
 * object-storage URL, which OkHttp follows (and strips our Authorization
 * header on the cross-host hop, exactly as the signed URL requires).
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
        /** No stored token — the UI must prompt for one. */
        class AuthRequired : SourceException("GitHub token required")
        /** Repo has no releases yet (HTTP 404 on /releases/latest). */
        class NoReleases : SourceException("no published releases")
        /** Non-recoverable HTTP status (auth rejected, rate limited, …). */
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

    private fun getJson(url: String, octetStream: Boolean = false): String {
        val token = token()
            ?: throw SourceException.AuthRequired()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header(
                "Accept",
                if (octetStream) "application/octet-stream" else "application/vnd.github+json",
            )
            .build()
        client.newCall(request).execute().use { response ->
            when {
                response.code == 404 && !octetStream -> throw SourceException.NoReleases()
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

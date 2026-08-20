package app.openbubbles.nativeapp.update

import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.w3c.dom.Element

/** Reads the public Sparkle-compatible OpenBubbles appcast hosted by Update Ledger. */
class UpdateLedgerSource(
    private val baseUrl: String = DEFAULT_BASE_URL,
    private val projectSlug: String = DEFAULT_PROJECT,
    private val channel: String = DEFAULT_CHANNEL,
    private val client: OkHttpClient = defaultClient(),
) {
    sealed class SourceException(message: String, cause: Throwable? = null) :
        Exception(message, cause) {
        class Http(val code: Int) : SourceException("Update Ledger HTTP $code")
        class Malformed(message: String, cause: Throwable? = null) : SourceException(message, cause)
        class Io(cause: IOException) : SourceException("Update Ledger IO failure", cause)
    }

    /** Returns null when the appcast has no build newer than [currentBuild]. */
    fun fetch(currentBuild: Long): UpdateFeed? {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/v1/appcast")
            .addPathSegment(projectSlug)
            .addQueryParameter("channel", channel)
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/rss+xml")
            .build()

        val body = try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw SourceException.Http(response.code)
                response.body?.bytes() ?: throw SourceException.Malformed("empty Update Ledger appcast")
            }
        } catch (e: SourceException) {
            throw e
        } catch (e: IOException) {
            throw SourceException.Io(e)
        }
        val document = runCatching { secureXml(body) }
            .getOrElse { throw SourceException.Malformed("unreadable Update Ledger appcast", it) }
        val item = document.getElementsByTagName("item").item(0) as? Element ?: return null
        val enclosure = item.getElementsByTagName("enclosure").item(0) as? Element
            ?: throw SourceException.Malformed("appcast item has no enclosure")
        val versionCode = enclosure.getAttributeNS(SPARKLE_NAMESPACE, "version").toLongOrNull()
            ?: throw SourceException.Malformed("appcast enclosure has no numeric build")
        if (versionCode <= currentBuild) return null
        val versionName = enclosure.getAttributeNS(SPARKLE_NAMESPACE, "shortVersionString")
        val downloadUrl = enclosure.getAttribute("url")
        val apkAsset = enclosure.getAttributeNS(LEDGER_NAMESPACE, "assetName")
        val sha256 = enclosure.getAttributeNS(LEDGER_NAMESPACE, "sha256").lowercase()
        val bytes = enclosure.getAttribute("length").toLongOrNull() ?: 0L
        val minVersionCode = enclosure.getAttributeNS(LEDGER_NAMESPACE, "minVersionCode")
            .toLongOrNull() ?: 0L
        val notes = (item.getElementsByTagName("description").item(0) as? Element)
            ?.textContent?.trim().orEmpty()
        if (versionName.isBlank() || apkAsset.isBlank() || !SHA256.matches(sha256) || bytes <= 0L) {
            throw SourceException.Malformed("appcast enclosure failed Android integrity validation")
        }
        runCatching { downloadUrl.toHttpUrl() }
            .getOrElse { throw SourceException.Malformed("available update has an invalid download URL", it) }
        val manifest = UpdateManifest(
            versionCode = versionCode,
            versionName = versionName,
            apkAsset = apkAsset,
            sha256 = sha256,
            bytes = bytes,
            notes = notes,
            minVersionCode = minVersionCode,
        )
        return UpdateFeed(manifest, downloadUrl)
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://update-ledger.mweinbach.chatgpt.site"
        const val DEFAULT_PROJECT = "openbubbles"
        const val DEFAULT_CHANNEL = "stable"
        private const val SPARKLE_NAMESPACE = "http://www.andymatuschak.org/xml-namespaces/sparkle"
        private const val LEDGER_NAMESPACE = "https://update-ledger.mweinbach.chatgpt.site/xml-namespaces/ledger"
        private val SHA256 = Regex("^[a-f0-9]{64}$")

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        private fun secureXml(bytes: ByteArray) = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { isXIncludeAware = false }
            runCatching { setExpandEntityReferences(false) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
}

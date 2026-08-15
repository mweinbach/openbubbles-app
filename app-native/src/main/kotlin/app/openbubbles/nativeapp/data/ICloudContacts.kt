package app.openbubbles.nativeapp.data

import android.content.Context
import android.util.Log
import app.openbubbles.core.contacts.RawContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import uniffi.rust_lib_bluebubbles.NativePushState
import java.io.ByteArrayInputStream
import java.io.File
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

private const val ICLOUD_CONTACTS_PREFS = "icloud_contacts"
private const val ICLOUD_CONTACTS_ROOT = "https://contacts.icloud.com/"
private const val MAX_REDIRECTS = 5
private const val MULTIGET_BATCH = 64

internal data class ParsedVCard(
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
    val addresses: List<String>,
    val photo: ByteArray?,
)

/** Minimal vCard 3/4 parser for the fields used by handle resolution. */
internal object ICloudVCardParser {
    fun parse(value: String): ParsedVCard {
        val lines = unfold(value)
        var displayName: String? = null
        var firstName: String? = null
        var lastName: String? = null
        var photo: ByteArray? = null
        val addresses = LinkedHashSet<String>()

        for (line in lines) {
            val separator = line.indexOf(':')
            if (separator <= 0) continue
            val descriptor = line.substring(0, separator)
            val property = descriptor.substringBefore(';').substringAfterLast('.').uppercase()
            val raw = line.substring(separator + 1)
            when (property) {
                "FN" -> displayName = decodeText(raw, descriptor)
                "N" -> {
                    val parts = splitEscaped(decodeText(raw, descriptor), ';')
                    lastName = parts.getOrNull(0)?.takeIf(String::isNotBlank)
                    firstName = parts.getOrNull(1)?.takeIf(String::isNotBlank)
                }
                "EMAIL", "TEL" -> decodeText(raw, descriptor)
                    .trim()
                    .let { address ->
                        when {
                            property == "EMAIL" && address.startsWith("mailto:", ignoreCase = true) ->
                                address.substring(7)
                            property == "TEL" && address.startsWith("tel:", ignoreCase = true) ->
                                address.substring(4)
                            else -> address
                        }
                    }
                    .takeIf(String::isNotEmpty)
                    ?.let(addresses::add)
                "PHOTO" -> if (
                    descriptor.contains("ENCODING=B", ignoreCase = true) ||
                    descriptor.contains("BASE64", ignoreCase = true) ||
                    descriptor.contains("VALUE=BINARY", ignoreCase = true)
                ) {
                    photo = runCatching {
                        Base64.getMimeDecoder().decode(raw.filterNot(Char::isWhitespace))
                    }.getOrNull()?.takeIf { it.size <= 5 * 1024 * 1024 }
                }
            }
        }

        if (displayName.isNullOrBlank()) {
            displayName = listOfNotNull(firstName, lastName).joinToString(" ").ifBlank { null }
        }
        return ParsedVCard(
            displayName = displayName,
            firstName = firstName,
            lastName = lastName,
            addresses = addresses.toList(),
            photo = photo,
        )
    }

    private fun unfold(value: String): List<String> {
        val normalized = value.replace("\r\n", "\n").replace('\r', '\n')
        val out = ArrayList<String>()
        normalized.split('\n').forEach { line ->
            if ((line.startsWith(' ') || line.startsWith('\t')) && out.isNotEmpty()) {
                out[out.lastIndex] += line.drop(1)
            } else {
                out += line
            }
        }
        return out
    }

    private fun decodeText(raw: String, descriptor: String): String {
        val decoded = if (descriptor.contains("QUOTED-PRINTABLE", ignoreCase = true)) {
            decodeQuotedPrintable(raw)
        } else {
            raw
        }
        return decoded
            .replace("\\n", "\n", ignoreCase = true)
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
            .trim()
    }

    private fun decodeQuotedPrintable(value: String): String {
        val bytes = ArrayList<Byte>(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '=' && index + 2 < value.length) {
                val decoded = value.substring(index + 1, index + 3).toIntOrNull(16)
                if (decoded != null) {
                    bytes += decoded.toByte()
                    index += 3
                    continue
                }
            }
            bytes += value[index].code.toByte()
            index++
        }
        return bytes.toByteArray().toString(Charsets.UTF_8)
    }

    private fun splitEscaped(value: String, delimiter: Char): List<String> {
        val out = ArrayList<String>()
        val current = StringBuilder()
        var escaped = false
        value.forEach { char ->
            when {
                escaped -> {
                    current.append(char)
                    escaped = false
                }
                char == '\\' -> escaped = true
                char == delimiter -> {
                    out += current.toString()
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        out += current.toString()
        return out
    }
}

private data class AddressBook(
    val url: URI,
    val displayName: String?,
    val ctag: String?,
)

private data class ChangedResource(
    val href: URI,
    val deleted: Boolean,
)

private data class CollectionChanges(
    val token: String?,
    val resources: List<ChangedResource>,
)

private data class BookSync(
    val book: AddressBook,
    val token: String?,
    val reset: Boolean,
    val cards: Map<URI, String>,
    val deleted: Set<URI>,
)

private class CardDavHttpException(val status: Int, message: String) : Exception(message)

/** Apple-authenticated CardDAV client ported from the original app. */
private class ICloudCardDavClient(
    private val authHeaders: Map<String, String>,
) {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    fun sync(stored: (URI) -> Pair<String?, String?>): List<BookSync> {
        return discoverAddressBooks().mapNotNull { discovered ->
            val current = fetchBookProperties(discovered.url)
            val (storedCtag, storedToken) = stored(discovered.url)
            if (current.ctag != null && storedCtag == current.ctag) return@mapNotNull null

            var reset = storedToken == null
            val changes = try {
                syncCollection(current.url, storedToken)
            } catch (error: CardDavHttpException) {
                if (storedToken == null || error.status !in setOf(403, 409, 410)) throw error
                reset = true
                syncCollection(current.url, null)
            }
            val changed = changes.resources.filterNot(ChangedResource::deleted).map(ChangedResource::href)
            BookSync(
                book = current.copy(displayName = discovered.displayName),
                token = changes.token,
                reset = reset,
                cards = downloadCards(current.url, changed),
                deleted = changes.resources.filter(ChangedResource::deleted).mapTo(LinkedHashSet()) { it.href },
            )
        }
    }

    private fun discoverAddressBooks(): List<AddressBook> {
        var principal = URI(ICLOUD_CONTACTS_ROOT)
        var home = addressBookHome(principal)
        if (home == null) {
            principal = currentPrincipal(principal)
                ?: error("iCloud CardDAV did not return a current-user-principal")
            home = addressBookHome(principal)
        }
        home ?: error("iCloud CardDAV did not return an addressbook-home-set")

        val response = xmlRequest(
            method = "PROPFIND",
            url = home,
            depth = "1",
            body = propfind(
                "<d:displayname/><d:resourcetype/><cs:getctag/><apple:getctag/><d:sync-token/>",
                extraNamespaces = " xmlns:cs=\"http://calendarserver.org/ns/\" xmlns:apple=\"http://apple.com/ns/ical/\"",
            ),
        )
        return response.document.responses().mapNotNull { element ->
            if (element.descendants("addressbook").isEmpty()) return@mapNotNull null
            val href = element.firstText("href") ?: return@mapNotNull null
            AddressBook(
                url = response.url.resolve(href),
                displayName = element.firstText("displayname"),
                ctag = element.firstText("getctag"),
            )
        }
    }

    private fun addressBookHome(url: URI): URI? {
        val response = xmlRequest(
            "PROPFIND",
            url,
            propfind(
                "<card:addressbook-home-set/>",
                extraNamespaces = " xmlns:card=\"urn:ietf:params:xml:ns:carddav\"",
            ),
            "0",
        )
        return response.document.elements("addressbook-home-set").firstOrNull()
            ?.firstText("href")?.let(response.url::resolve)
    }

    private fun currentPrincipal(url: URI): URI? {
        val response = xmlRequest(
            "PROPFIND",
            url,
            propfind("<d:current-user-principal/><d:principal-URL/>"),
            "0",
        )
        val href = response.document.elements("current-user-principal").firstOrNull()?.firstText("href")
            ?: response.document.elements("principal-URL").firstOrNull()?.firstText("href")
        return href?.let(response.url::resolve)
    }

    private fun fetchBookProperties(url: URI): AddressBook {
        val response = xmlRequest(
            "PROPFIND",
            url,
            propfind(
                "<cs:getctag/><apple:getctag/><d:sync-token/>",
                extraNamespaces = " xmlns:cs=\"http://calendarserver.org/ns/\" xmlns:apple=\"http://apple.com/ns/ical/\"",
            ),
            "0",
        )
        return AddressBook(response.url, null, response.document.firstText("getctag"))
    }

    private fun syncCollection(url: URI, token: String?): CollectionChanges {
        val tokenXml = token?.let { "<d:sync-token>${xmlEscape(it)}</d:sync-token>" }
            ?: "<d:sync-token/>"
        val response = xmlRequest(
            "REPORT",
            url,
            """<?xml version="1.0" encoding="UTF-8"?>
                <d:sync-collection xmlns:d="DAV:">
                  $tokenXml<d:sync-level>1</d:sync-level><d:prop><d:getetag/></d:prop>
                </d:sync-collection>""".trimIndent(),
            "1",
        )
        val resources = response.document.responses().mapNotNull { element ->
            val href = element.firstText("href") ?: return@mapNotNull null
            val statuses = element.descendants("status").map(Node::getTextContent)
            val deleted = statuses.any { it.contains(" 404 ") || it.contains(" 410 ") }
            ChangedResource(response.url.resolve(href), deleted)
        }
        return CollectionChanges(response.document.firstText("sync-token"), resources)
    }

    private fun downloadCards(bookUrl: URI, hrefs: List<URI>): Map<URI, String> {
        val cards = LinkedHashMap<URI, String>()
        hrefs.chunked(MULTIGET_BATCH).forEach { batch ->
            val hrefXml = batch.joinToString("") { "<d:href>${xmlEscape(it.rawPathWithQuery())}</d:href>" }
            val response = xmlRequest(
                "REPORT",
                bookUrl,
                """<?xml version="1.0" encoding="UTF-8"?>
                    <card:addressbook-multiget xmlns:d="DAV:" xmlns:card="urn:ietf:params:xml:ns:carddav">
                      <d:prop><d:getetag/><card:address-data/></d:prop>$hrefXml
                    </card:addressbook-multiget>""".trimIndent(),
                "1",
            )
            response.document.responses().forEach { element ->
                val href = element.firstText("href") ?: return@forEach
                val data = element.firstText("address-data") ?: return@forEach
                cards[response.url.resolve(href)] = data
            }
        }
        return cards
    }

    private data class XmlResponse(val url: URI, val document: Document)

    private fun xmlRequest(method: String, url: URI, body: String, depth: String): XmlResponse {
        var current = url
        repeat(MAX_REDIRECTS + 1) { redirect ->
            val builder = Request.Builder()
                .url(current.toString())
                .header("User-Agent", "macOS/15.5 (24F74) AddressBookCore/2695.500.71")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "*/*")
                .header("Depth", depth)
            authHeaders.forEach { (name, value) -> builder.header(name, value) }
            val requestBody = body.toRequestBody("application/xml; charset=utf-8".toMediaType())
            builder.method(method, requestBody)

            http.newCall(builder.build()).execute().use { response ->
                if (response.code in 300..399) {
                    if (redirect == MAX_REDIRECTS) error("too many iCloud CardDAV redirects")
                    val location = response.header("Location") ?: error("CardDAV redirect had no Location")
                    val next = current.resolve(location)
                    require(next.scheme == "https" && isAppleICloudHost(next.host)) {
                        "refusing to forward Apple authentication outside iCloud"
                    }
                    current = next
                    return@repeat
                }
                val text = response.body?.string().orEmpty()
                if (response.code !in 200..299) {
                    throw CardDavHttpException(response.code, "iCloud CardDAV $method failed (${response.code})")
                }
                return XmlResponse(current, secureXml(text))
            }
        }
        error("unreachable CardDAV redirect state")
    }

    private fun propfind(properties: String, extraNamespaces: String = ""): String =
        """<?xml version="1.0" encoding="UTF-8"?>
            <d:propfind xmlns:d="DAV:"$extraNamespaces><d:prop>$properties</d:prop></d:propfind>""".trimIndent()
}

/** Automatic iCloud contact import driven by the live, self-hosted Apple state. */
data class ICloudContactSyncStatus(
    val lastSuccessMs: Long,
    val imported: Int,
    val removed: Int,
    val error: String?,
)

object ICloudContactSync {
    private val mutex = Mutex()

    fun status(context: Context): ICloudContactSyncStatus {
        val prefs = context.getSharedPreferences(ICLOUD_CONTACTS_PREFS, Context.MODE_PRIVATE)
        return ICloudContactSyncStatus(
            lastSuccessMs = prefs.getLong("last_success_ms", 0L),
            imported = prefs.getInt("last_imported", 0),
            removed = prefs.getInt("last_removed", 0),
            error = prefs.getString("last_error", null),
        )
    }

    suspend fun sync(context: Context, state: NativePushState): ICloudContactSyncStatus = mutex.withLock {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(ICLOUD_CONTACTS_PREFS, Context.MODE_PRIVATE)
            runCatching {
                val headers = state.getContactsHeaders()
                val client = ICloudCardDavClient(headers)
                var imported = 0
                var removed = 0
                client.sync { url ->
                    prefs.getString(ctagKey(url), null) to prefs.getString(tokenKey(url), null)
                }.forEach { result ->
                    val knownBefore = prefs.getStringSet(knownKey(result.book.url), emptySet()).orEmpty()
                    val upsertIds = result.cards.keys.mapTo(LinkedHashSet()) { it.toString() }
                    val deletedIds = result.deleted.mapTo(LinkedHashSet()) { it.toString() }
                    val knownAfter = if (result.reset) {
                        upsertIds
                    } else {
                        (knownBefore - deletedIds + upsertIds).toSet()
                    }

                    val raw = result.cards.mapNotNull { (href, vcard) ->
                        val parsed = ICloudVCardParser.parse(vcard)
                        if (parsed.addresses.isEmpty()) return@mapNotNull null
                        RawContact(
                            id = contactId(href.toString()),
                            displayName = parsed.displayName,
                            firstName = parsed.firstName,
                            lastName = parsed.lastName,
                            avatarPath = savePhoto(context, href.toString(), parsed.photo),
                            addresses = parsed.addresses,
                        )
                    }
                    val noLongerUsable = upsertIds - raw.mapTo(HashSet()) { it.id.removePrefix("icloud:") }
                    val toRemove = (if (result.reset) knownBefore - knownAfter else deletedIds) + noLongerUsable

                    CoreGraph.syncContacts(raw)
                    CoreGraph.removeContacts(toRemove.map(::contactId))
                    imported += raw.size
                    removed += toRemove.size

                    prefs.edit()
                        .putString(ctagKey(result.book.url), result.book.ctag)
                        .putString(tokenKey(result.book.url), result.token)
                        .putStringSet(knownKey(result.book.url), knownAfter)
                        .apply()
                }
                prefs.edit()
                    .putLong("last_success_ms", System.currentTimeMillis())
                    .putInt("last_imported", imported)
                    .putInt("last_removed", removed)
                    .remove("last_error")
                    .apply()
                Log.i("ICloudContactSync", "iCloud Contacts synced: $imported updated, $removed removed")
            }.onFailure { error ->
                val message = error.message ?: error.javaClass.simpleName
                prefs.edit().putString("last_error", message).apply()
                Log.w("ICloudContactSync", "iCloud Contacts sync failed: $message")
            }
            status(context)
        }
    }

    private fun savePhoto(context: Context, href: String, bytes: ByteArray?): String? {
        bytes ?: return null
        val directory = File(context.filesDir, "icloud_contact_avatars").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256")
            .digest(href.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return runCatching {
            File(directory, "$name.img").apply { writeBytes(bytes) }.absolutePath
        }.getOrNull()
    }

    private fun contactId(href: String) = "icloud:$href"
    private fun prefSuffix(url: URI) = sha256(url.toString()).take(24)
    private fun ctagKey(url: URI) = "ctag:${prefSuffix(url)}"
    private fun tokenKey(url: URI) = "token:${prefSuffix(url)}"
    private fun knownKey(url: URI) = "known:${prefSuffix(url)}"
}

private fun secureXml(text: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isXIncludeAware = false
        setExpandEntityReferences(false)
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
        runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
    }
    return factory.newDocumentBuilder().parse(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
}

private fun Document.elements(localName: String): List<Element> =
    getElementsByTagNameNS("*", localName).let { nodes ->
        (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

private fun Document.responses(): List<Element> = elements("response")

private fun Element.descendants(localName: String): List<Element> =
    getElementsByTagNameNS("*", localName).let { nodes ->
        (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
    }

private fun Element.firstText(localName: String): String? =
    descendants(localName).firstOrNull()?.textContent?.trim()?.takeIf(String::isNotEmpty)

private fun Document.firstText(localName: String): String? =
    elements(localName).firstOrNull()?.textContent?.trim()?.takeIf(String::isNotEmpty)

private fun URI.rawPathWithQuery(): String = rawPath + rawQuery?.let { "?$it" }.orEmpty()

private fun isAppleICloudHost(host: String?): Boolean =
    host == "icloud.com" || host?.endsWith(".icloud.com") == true

private fun xmlEscape(value: String): String = value
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }

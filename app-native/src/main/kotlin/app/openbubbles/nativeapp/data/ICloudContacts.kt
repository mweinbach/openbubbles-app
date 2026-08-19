package app.openbubbles.nativeapp.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import app.openbubbles.core.contacts.AvatarUpdate
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.nativeapp.data.contacts.ContactDeviceSync
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
import java.io.FileOutputStream
import java.net.URI
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

private const val ICLOUD_CONTACTS_PREFS = "icloud_contacts"
private const val ICLOUD_CONTACTS_ROOT = "https://contacts.icloud.com/"
private const val MAX_REDIRECTS = 5
private const val MULTIGET_BATCH = 64
private const val AUTO_SYNC_FRESHNESS_MS = 15 * 60 * 1000L
private const val MAX_PHOTO_BYTES = 5 * 1024 * 1024
internal const val ICLOUD_PHOTO_CACHE_VERSION = 3
private const val PHOTO_CACHE_VERSION_KEY = "photo_cache_version"

internal fun hasImageMagic(bytes: ByteArray): Boolean {
    if (bytes.size < 12) return false
    val jpeg = bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()
    val png = bytes[0] == 0x89.toByte() && bytes[1] == 'P'.code.toByte() &&
        bytes[2] == 'N'.code.toByte() && bytes[3] == 'G'.code.toByte()
    val gif = bytes[0] == 'G'.code.toByte() && bytes[1] == 'I'.code.toByte() &&
        bytes[2] == 'F'.code.toByte()
    val webp = bytes[0] == 'R'.code.toByte() && bytes[8] == 'W'.code.toByte()
    val heif = bytes.copyOfRange(4, 8).toString(Charsets.US_ASCII) == "ftyp"
    return jpeg || png || gif || webp || heif
}

internal fun resolveContactPhoto(
    parsed: ParsedVCard,
    download: (String) -> ByteArray?,
): ByteArray? = parsed.photo ?: parsed.photoUri?.let(download)?.takeIf(::hasImageMagic)

/**
 * A missing PHOTO on a complete card is authoritative. A present PHOTO that
 * cannot be decoded, downloaded, or persisted is not: preserve the prior
 * avatar until a later pass can fetch it.
 */
internal fun cardDavAvatarUpdate(
    parsed: ParsedVCard,
    download: (String) -> ByteArray?,
    persist: (ByteArray) -> File?,
): AvatarUpdate {
    val inline = parsed.photo
    if (inline != null) {
        if (!hasImageMagic(inline)) return AvatarUpdate.Keep
        return persist(inline)?.absolutePath?.let(AvatarUpdate::Set) ?: AvatarUpdate.Keep
    }
    val uri = parsed.photoUri
        ?: return if (parsed.photoDeclared) AvatarUpdate.Keep else AvatarUpdate.Clear
    val downloaded = download(uri)?.takeIf(::hasImageMagic) ?: return AvatarUpdate.Keep
    return persist(downloaded)?.absolutePath?.let(AvatarUpdate::Set) ?: AvatarUpdate.Keep
}

/** Ignore a stored CardDAV cursor after the PHOTO parser changes so existing books re-download images. */
internal fun cardDavCursorForPhotoCache(
    storedCtag: String?,
    storedToken: String?,
    storedPhotoVersion: Int,
    photoCacheVersion: Int = ICLOUD_PHOTO_CACHE_VERSION,
): Pair<String?, String?> =
    if (storedPhotoVersion < photoCacheVersion) null to null else storedCtag to storedToken

internal fun writeContactPhoto(directory: File, stem: String, bytes: ByteArray?): File? {
    if (bytes == null || !hasImageMagic(bytes)) return null
    return runCatching {
        directory.mkdirs()
        val contentHash = MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { "%02x".format(it) }
        val target = File(directory, "$stem-$contentHash.img")
        if (target.isFile) return@runCatching target

        val temporary = File.createTempFile(".$stem-", ".tmp", directory)
        try {
            FileOutputStream(temporary).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
            if (!temporary.renameTo(target) && !target.isFile) {
                error("Could not atomically publish contact photo")
            }
            target
        } finally {
            temporary.delete()
        }
    }.getOrNull()
}

/** Deletes superseded versioned files and the pre-v3 fixed-path file. */
internal fun cleanupContactPhotos(directory: File, stem: String, keep: File?) {
    val keepPath = keep?.absolutePath
    directory.listFiles()?.forEach { candidate ->
        val owned = candidate.name == "$stem.img" ||
            (candidate.name.startsWith("$stem-") && candidate.name.endsWith(".img"))
        if (owned && candidate.absolutePath != keepPath) candidate.delete()
    }
}

internal data class ParsedVCard(
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
    val addresses: List<String>,
    val photo: ByteArray?,
    val photoUri: String? = null,
    val nickname: String? = null,
    val company: String? = null,
    val photoDeclared: Boolean = photo != null || photoUri != null,
)

/** Minimal vCard 3/4 parser for the fields used by handle resolution. */
internal object ICloudVCardParser {
    fun parse(value: String): ParsedVCard {
        val lines = unfold(value)
        var displayName: String? = null
        var firstName: String? = null
        var lastName: String? = null
        var photo: ByteArray? = null
        var photoUri: String? = null
        var photoDeclared = false
        var nickname: String? = null
        var company: String? = null
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
                // NICKNAME is a comma list; ORG is "company;unit;…" — the
                // device writer only carries one value of each.
                "NICKNAME" -> nickname = splitEscaped(decodeText(raw, descriptor), ',')
                    .firstOrNull()?.trim()?.takeIf(String::isNotBlank)
                "ORG" -> company = splitEscaped(decodeText(raw, descriptor), ';')
                    .firstOrNull()?.trim()?.takeIf(String::isNotBlank)
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
                "PHOTO" -> {
                    photoDeclared = true
                    val value = raw.trim().trim('"', '\'')
                    when {
                        value.startsWith("data:", ignoreCase = true) ->
                            photo = decodeDataUri(value)
                        isUriPhoto(descriptor, value) ->
                            photoUri = decodeText(value, descriptor).trim().trim('"', '\'')
                                .takeIf(String::isNotEmpty)
                        isInlinePhoto(descriptor) ->
                            photo = decodeBase64Image(value)
                        else -> {
                            photo = decodeBase64Image(value)?.takeIf(::hasImageMagic)
                            if (photo == null && looksLikeHttpUrl(value)) photoUri = value
                        }
                    }
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
            photoUri = photoUri,
            nickname = nickname,
            company = company,
            photoDeclared = photoDeclared,
        )
    }

    private fun isUriPhoto(descriptor: String, value: String): Boolean {
        val params = descriptor.uppercase()
        return params.contains("VALUE=URI") ||
            params.contains("VALUE=URL") ||
            looksLikeHttpUrl(value)
    }

    private fun isInlinePhoto(descriptor: String): Boolean {
        val params = descriptor.uppercase()
        return params.contains("ENCODING=B") ||
            params.contains("ENCODING=BASE64") ||
            params.contains("BASE64") ||
            params.contains("VALUE=BINARY")
    }

    private fun looksLikeHttpUrl(value: String): Boolean =
        value.startsWith("https://", ignoreCase = true) ||
            value.startsWith("http://", ignoreCase = true)

    private fun decodeDataUri(value: String): ByteArray? {
        val comma = value.indexOf(',')
        if (comma <= 4) return null
        val metadata = value.substring(5, comma)
        val data = value.substring(comma + 1)
        return if (metadata.contains("base64", ignoreCase = true)) {
            decodeBase64Image(data)
        } else {
            runCatching { data.toByteArray(Charsets.UTF_8) }.getOrNull()
                ?.takeIf { it.size <= MAX_PHOTO_BYTES }
        }
    }

    private fun decodeBase64Image(value: String): ByteArray? = runCatching {
        Base64.getMimeDecoder().decode(value.filterNot(Char::isWhitespace))
    }.getOrNull()?.takeIf { it.isNotEmpty() && it.size <= MAX_PHOTO_BYTES }

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
    val syncToken: String?,
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
            val fetched = fetchBookProperties(discovered.url)
            val current = fetched.copy(
                displayName = discovered.displayName,
                ctag = fetched.ctag ?: discovered.ctag,
                syncToken = fetched.syncToken ?: discovered.syncToken,
            )
            val (storedCtag, storedToken) = stored(discovered.url)
            if (current.ctag != null && storedCtag == current.ctag) return@mapNotNull null

            var reset = false
            val changes = if (storedToken == null) {
                // Apple's server rejected an empty-token sync-collection on
                // real devices. Enumerate the initial snapshot with Depth:1,
                // then continue incrementally from its advertised token.
                reset = true
                listCollection(current)
            } else try {
                syncCollection(current.url, storedToken)
            } catch (error: CardDavHttpException) {
                if (error.status !in setOf(400, 403, 409, 410)) throw error
                reset = true
                listCollection(current)
            }
            val changed = changes.resources.filterNot(ChangedResource::deleted).map(ChangedResource::href)
            BookSync(
                book = current,
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
                syncToken = element.firstText("sync-token"),
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
        return AddressBook(
            response.url,
            null,
            response.document.firstText("getctag"),
            response.document.firstText("sync-token"),
        )
    }

    private fun listCollection(book: AddressBook): CollectionChanges {
        val response = xmlRequest(
            "PROPFIND",
            book.url,
            propfind("<d:getetag/>"),
            "1",
        )
        val resources = response.document.responses().mapNotNull { element ->
            val href = element.firstText("href") ?: return@mapNotNull null
            val resolved = response.url.resolve(href)
            if (sameResource(resolved, book.url) || element.firstText("getetag") == null) {
                return@mapNotNull null
            }
            ChangedResource(resolved, deleted = false)
        }
        return CollectionChanges(book.syncToken, resources)
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

    fun downloadPhoto(url: URI): ByteArray? = runCatching { getBytes(url) }.getOrNull()

    private fun getBytes(url: URI): ByteArray? {
        var current = url
        repeat(MAX_REDIRECTS + 1) { redirect ->
            val builder = Request.Builder()
                .url(current.toString())
                .header("User-Agent", "macOS/15.5 (24F74) AddressBookCore/2695.500.71")
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Accept", "*/*")
                .get()
            authHeaders.forEach { (name, value) -> builder.header(name, value) }

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
                if (response.code !in 200..299) return null
                val bytes = response.body?.bytes() ?: return null
                return bytes.takeIf { it.isNotEmpty() && it.size <= MAX_PHOTO_BYTES }
            }
        }
        return null
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
            val requestBody = body.toRequestBody("text/xml; charset=utf-8".toMediaType())
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

    suspend fun sync(
        context: Context,
        state: NativePushState,
        force: Boolean = false,
    ): ICloudContactSyncStatus = mutex.withLock {
        withContext(Dispatchers.IO) {
            val prefs = context.getSharedPreferences(ICLOUD_CONTACTS_PREFS, Context.MODE_PRIVATE)
            val previous = status(context)
            val storedPhotoVersion = prefs.getInt(PHOTO_CACHE_VERSION_KEY, 0)
            if (!force && previous.lastSuccessMs > 0L &&
                storedPhotoVersion >= ICLOUD_PHOTO_CACHE_VERSION &&
                System.currentTimeMillis() - previous.lastSuccessMs < AUTO_SYNC_FRESHNESS_MS
            ) {
                // A fresh CardDAV snapshot can still predate handles created
                // by the concurrently running CloudKit history import.
                CoreGraph.relinkContacts()?.let { result ->
                    Log.i(
                        "ICloudContactSync",
                        "iCloud Contacts relinked: ${result.linkedContacts}/${result.contacts} contacts, " +
                            "${result.linkedHandles}/${result.handles} handles, ${result.changedContacts} changed",
                    )
                }
                return@withContext previous
            }
            runCatching {
                val headers = state.getContactsHeaders()
                val client = ICloudCardDavClient(headers)
                var imported = 0
                var removed = 0
                client.sync { url ->
                    cardDavCursorForPhotoCache(
                        storedCtag = prefs.getString(ctagKey(url), null),
                        storedToken = prefs.getString(tokenKey(url), null),
                        storedPhotoVersion = storedPhotoVersion,
                    )
                }.forEach { result ->
                    val knownBefore = prefs.getStringSet(knownKey(result.book.url), emptySet()).orEmpty()
                    val upsertIds = result.cards.keys.mapTo(LinkedHashSet()) { it.toString() }
                    val deletedIds = result.deleted.mapTo(LinkedHashSet()) { it.toString() }
                    val knownAfter = if (result.reset) {
                        upsertIds
                    } else {
                        (knownBefore - deletedIds + upsertIds).toSet()
                    }

                    val rawWithHref = result.cards.mapNotNull { (href, vcard) ->
                        val parsed = ICloudVCardParser.parse(vcard)
                        if (parsed.addresses.isEmpty()) return@mapNotNull null
                        val avatarUpdate = cardDavAvatarUpdate(
                            parsed = parsed,
                            download = { uri ->
                                val resolved = runCatching { href.resolve(uri) }.getOrNull()
                                if (resolved == null ||
                                    resolved.scheme != "https" ||
                                    !isAppleICloudHost(resolved.host)
                                ) {
                                    null
                                } else {
                                    client.downloadPhoto(resolved).also { bytes ->
                                        if (bytes == null) {
                                            Log.w(
                                                "ICloudContactSync",
                                                "contact photo download failed: $resolved",
                                            )
                                        }
                                    }
                                }
                            },
                            persist = { bytes ->
                                savePhoto(context, href.toString(), bytes)
                            },
                        )
                        href.toString() to RawContact(
                            id = contactId(href.toString()),
                            displayName = parsed.displayName,
                            firstName = parsed.firstName,
                            lastName = parsed.lastName,
                            avatarUpdate = avatarUpdate,
                            addresses = parsed.addresses,
                            nickname = parsed.nickname,
                            company = parsed.company,
                        )
                    }
                    val raw = rawWithHref.map { it.second }
                    val noLongerUsable = upsertIds - raw.mapTo(HashSet()) { it.id.removePrefix("icloud:") }
                    val toRemove = (if (result.reset) knownBefore - knownAfter else deletedIds) + noLongerUsable

                    check(CoreGraph.syncContacts(raw)) {
                        "Contact persistence unavailable during CardDAV sync"
                    }
                    rawWithHref.forEach { (href, contact) ->
                        when (val avatar = contact.avatarUpdate) {
                            AvatarUpdate.Keep -> {}
                            AvatarUpdate.Clear -> cleanupPhotos(context, href, keep = null)
                            is AvatarUpdate.Set -> cleanupPhotos(context, href, keep = File(avatar.path))
                        }
                    }
                    val removedNow = CoreGraph.removeContacts(toRemove.map(::contactId))
                    toRemove.forEach { href -> cleanupPhotos(context, href, keep = null) }
                    imported += raw.size
                    removed += removedNow

                    prefs.edit {
                        putString(ctagKey(result.book.url), result.book.ctag)
                        putString(tokenKey(result.book.url), result.token)
                        putStringSet(knownKey(result.book.url), knownAfter)
                    }
                }
                val relink = CoreGraph.relinkContacts()
                prefs.edit {
                    putLong("last_success_ms", System.currentTimeMillis())
                    putInt("last_imported", imported)
                    putInt("last_removed", removed)
                    putInt(PHOTO_CACHE_VERSION_KEY, ICLOUD_PHOTO_CACHE_VERSION)
                    remove("last_error")
                }
                Log.i(
                    "ICloudContactSync",
                    "iCloud Contacts synced: $imported updated, $removed removed; " +
                        if (relink == null) {
                            "linkage unavailable"
                        } else {
                            "${relink.linkedContacts}/${relink.contacts} contacts linked to " +
                                "${relink.linkedHandles}/${relink.handles} handles " +
                                "(${relink.changedContacts} changed)"
                        },
                )
            }.onSuccess {
                // A fresh CardDAV snapshot is the natural moment to mirror
                // into the phone's contact store; the 12h worker only covers
                // drift (permission granted later, missed passes).
                if (ContactDeviceSync.isEnabled(context)) {
                    ContactDeviceSync.schedule(context)
                    runCatching { ContactDeviceSync.syncNow(context) }
                        .onFailure { Log.w("ICloudContactSync", "device mirror failed: ${it.message}") }
                }
            }.onFailure { error ->
                val message = error.message ?: error.javaClass.simpleName
                prefs.edit { putString("last_error", message) }
                Log.w("ICloudContactSync", "iCloud Contacts sync failed: $message")
            }
            status(context)
        }
    }

    private fun savePhoto(context: Context, href: String, bytes: ByteArray): File? =
        writeContactPhoto(photoDirectory(context), photoStem(href), bytes)

    private fun cleanupPhotos(context: Context, href: String, keep: File?) =
        cleanupContactPhotos(photoDirectory(context), photoStem(href), keep)

    private fun photoDirectory(context: Context) =
        File(context.filesDir, "icloud_contact_avatars").apply { mkdirs() }

    private fun photoStem(href: String) = MessageDigest.getInstance("SHA-256")
        .digest(href.toByteArray())
        .joinToString("") { "%02x".format(it) }

    private fun contactId(href: String) = "icloud:$href"
    private fun prefSuffix(url: URI) = sha256(url.toString()).take(24)
    private fun ctagKey(url: URI) = "ctag:${prefSuffix(url)}"
    private fun tokenKey(url: URI) = "token:${prefSuffix(url)}"
    private fun knownKey(url: URI) = "known:${prefSuffix(url)}"
}

private fun secureXml(text: String): Document {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        // Android's bundled parser reports an "Unknown 0.0" specification
        // for optional JAXP capabilities instead of accepting the no-op.
        runCatching { isXIncludeAware = false }
        runCatching { setExpandEntityReferences(false) }
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

private fun sameResource(first: URI, second: URI): Boolean =
    first.normalize().toString().trimEnd('/') == second.normalize().toString().trimEnd('/')

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

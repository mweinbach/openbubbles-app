package app.openbubbles.nativeapp.data

import java.net.URI
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val RichLinkJson = Json { ignoreUnknownKeys = true }
private val HttpUrlPattern = Regex("https?://[^\\s<>\\\"]+", RegexOption.IGNORE_CASE)

@Serializable
private data class WireLinkMeta(
    val data: WireLinkData,
    val attachments: List<List<Int>> = emptyList(),
)

@Serializable
private data class WireLinkData(
    @SerialName("originalURL") val originalUrl: WireUrl? = null,
    @SerialName("URL") val url: WireUrl? = null,
    val title: String? = null,
    val summary: String? = null,
    val image: WireAttachment? = null,
    val icon: WireAttachment? = null,
)

@Serializable
private data class WireUrl(
    @SerialName("NS.base") val base: String = "",
    @SerialName("NS.relative") val relative: String = "",
) {
    fun value(): String = (base.takeUnless { it == "\$null" } ?: "") + relative
}

@Serializable
private data class WireAttachment(
    @SerialName("MIMEType") val mimeType: String? = null,
    val richLinkImageAttachmentSubstituteIndex: Long? = null,
)

/** Parses Apple LinkPresentation JSON and falls back to the first plain HTTP URL. */
internal fun parseRichLinkPreview(metadataJson: String?, messageText: String): RichLinkPreview? {
    val wire = metadataJson?.let { raw ->
        runCatching { RichLinkJson.decodeFromString<WireLinkMeta>(raw) }.getOrNull()
    }
    val url = sequenceOf(wire?.data?.url, wire?.data?.originalUrl)
        .mapNotNull { it?.value()?.normalizeHttpUrl() }
        .firstOrNull()
        ?: firstHttpUrl(messageText)
        ?: return null
    return RichLinkPreview(
        url = url,
        displayHost = displayHost(url),
        title = wire?.data?.title?.trim()?.takeIf(String::isNotEmpty),
        summary = wire?.data?.summary?.trim()?.takeIf(String::isNotEmpty),
        imageBytes = wire?.attachmentBytes(wire.data.image),
        imageMime = wire?.data?.image?.mimeType,
        iconBytes = wire?.attachmentBytes(wire.data.icon),
        iconMime = wire?.data?.icon?.mimeType,
    )
}

private fun WireLinkMeta.attachmentBytes(attachment: WireAttachment?): ByteArray? {
    val index = attachment?.richLinkImageAttachmentSubstituteIndex?.toInt() ?: return null
    val bytes = attachments.getOrNull(index) ?: return null
    if (bytes.isEmpty() || bytes.any { it !in 0..255 }) return null
    return ByteArray(bytes.size) { bytes[it].toByte() }
}

/**
 * Body text shown beside a rich-link card. Strips the preview URL so the
 * bubble does not repeat the same link the card already presents.
 */
internal fun displayTextForRichLink(messageText: String, previewUrl: String): String {
    val target = previewUrl.normalizeHttpUrl()?.trimEnd('/')
        ?: previewUrl.trim().trimEnd('/')
    if (target.isEmpty()) return messageText.trim()
    val matches = HttpUrlPattern.findAll(messageText).toList()
    if (matches.isEmpty()) return messageText.trim()
    val builder = StringBuilder(messageText)
    matches.asReversed().forEach { match ->
        val trimmed = match.value.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
        val normalized = trimmed.normalizeHttpUrl()?.trimEnd('/') ?: return@forEach
        if (normalized.equals(target, ignoreCase = true)) {
            // Delete only the URL portion so trailing punctuation survives.
            builder.delete(match.range.first, match.range.first + trimmed.length)
        }
    }
    return builder.toString()
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex(" *\\n+ *"), "\n")
        .trim()
        .trimEnd(',', ';', ':')
}

private fun firstHttpUrl(text: String): String? =
    HttpUrlPattern.find(text)?.value
        ?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}')
        ?.normalizeHttpUrl()

private fun String.normalizeHttpUrl(): String? = runCatching {
    URI(this).takeIf { uri ->
        uri.scheme.equals("http", ignoreCase = true) ||
            uri.scheme.equals("https", ignoreCase = true)
    }?.toASCIIString()
}.getOrNull()

private fun displayHost(url: String): String = runCatching {
    URI(url).host
        ?.removePrefix("www.")
        ?.takeIf(String::isNotBlank)
}.getOrNull() ?: url

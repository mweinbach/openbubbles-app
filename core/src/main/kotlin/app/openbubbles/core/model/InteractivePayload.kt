package app.openbubbles.core.model

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

sealed interface InteractivePayload {
    val bundleId: String?
    val appName: String
    val caption: String?
    val url: String?

    data class Poll(
        override val bundleId: String?,
        override val appName: String,
        override val caption: String?,
        override val url: String?,
        val question: String,
        val options: List<PollOption>,
    ) : InteractivePayload

    data class LiveLocation(
        override val bundleId: String?,
        override val appName: String,
        override val caption: String?,
        override val url: String?,
        val latitude: Double?,
        val longitude: Double?,
        val label: String?,
    ) : InteractivePayload

    data class Supported(
        override val bundleId: String?,
        override val appName: String,
        override val caption: String?,
        override val url: String?,
        val kind: SupportedKind,
        val detail: String?,
    ) : InteractivePayload

    data class Unsupported(
        override val bundleId: String?,
        override val appName: String,
        override val caption: String?,
        override val url: String?,
    ) : InteractivePayload
}

data class PollOption(
    val id: String,
    val text: String,
    val voteCount: Int,
)

enum class SupportedKind {
    APPLE_PAY,
    GAME_PIGEON,
    DIGITAL_TOUCH,
    PASSWORD_SHARE,
    APP,
}

object InteractivePayloadParser {
    private const val APP_PLUGIN = "com.apple.messages.MSMessageExtensionBalloonPlugin"

    fun parse(
        bundleId: String?,
        payloadJson: String?,
        summaryInfoJson: String? = null,
        fallbackText: String? = null,
    ): InteractivePayload? {
        if (bundleId == null && payloadJson == null) return null
        val payload = payloadJson.orEmpty()
        val resolvedBundle = bundleId
            ?: stringValue(payload, "bundleId")
            ?: stringValue(payload, "bundle_id")
        val appName = stringValue(payload, "appName")
            ?: stringValue(payload, "name")
            ?: stringValue(payload, "an")
            ?: appNameForBundle(resolvedBundle)
        val url = stringValue(payload, "url")
            ?: stringValue(payload, "URL")
            ?: nestedRelativeUrl(payload)
        val caption = firstNonBlank(
            stringValue(payload, "caption"),
            stringValue(payload, "ldText"),
            stringValue(payload, "ld_text"),
            stringValue(payload, "ldtext"),
            fallbackText,
        )
        val decodedUrl = decodeDataUrl(url)
        val searchable = listOfNotNull(resolvedBundle, appName, caption, payload, decodedUrl)
            .joinToString(" ")
            .lowercase()

        parsePoll(decodedUrl ?: payload, resolvedBundle, appName, caption, url)?.let { return it }

        if ("findmy" in searchable || "live location" in searchable) {
            val locationJson = decodedUrl ?: payload
            return InteractivePayload.LiveLocation(
                bundleId = resolvedBundle,
                appName = if (appName == "Unknown") "Find My" else appName,
                caption = caption,
                url = url,
                latitude = numberValue(locationJson, "latitude"),
                longitude = numberValue(locationJson, "longitude"),
                label = firstNonBlank(
                    stringValue(locationJson, "longAddress"),
                    stringValue(locationJson, "label"),
                    stringValue(locationJson, "name"),
                ),
            )
        }

        val kind = when {
            "peerpayment" in searchable || "apple pay" in searchable || "passbook" in searchable ->
                SupportedKind.APPLE_PAY
            "gamepigeon" in searchable || stringValue(summaryInfoJson.orEmpty(), "amd") != null ->
                SupportedKind.GAME_PIGEON
            "digitaltouch" in searchable || "handwriting" in searchable || "digital touch" in searchable ->
                SupportedKind.DIGITAL_TOUCH
            resolvedBundle == "com.openbubbles.passwords" || "password" in searchable ->
                SupportedKind.PASSWORD_SHARE
            payloadJson != null -> SupportedKind.APP
            else -> null
        }
        if (kind != null) {
            return InteractivePayload.Supported(
                bundleId = resolvedBundle,
                appName = supportedTitle(kind, appName),
                caption = caption,
                url = url,
                kind = kind,
                detail = firstNonBlank(
                    stringValue(payload, "secondarySubcaption"),
                    stringValue(payload, "secondary-subcaption"),
                    stringValue(payload, "subcaption"),
                    stringValue(payload, "imageTitle"),
                    stringValue(payload, "image-title"),
                ),
            )
        }
        return InteractivePayload.Unsupported(
            bundleId = resolvedBundle,
            appName = appName,
            caption = caption,
            url = url,
        )
    }

    private fun parsePoll(
        json: String,
        bundleId: String?,
        appName: String,
        caption: String?,
        url: String?,
    ): InteractivePayload.Poll? {
        val looksLikePoll = bundleId?.contains("Poll", ignoreCase = true) == true ||
            appName.equals("Polls", ignoreCase = true) ||
            json.contains("orderedPollOptions")
        if (!looksLikePoll) return null
        val optionArray = arrayValue(json, "orderedPollOptions") ?: return null
        val votesArray = arrayValue(json, "votes")
        val voteCounts = objectValues(votesArray.orEmpty())
            .mapNotNull { stringValue(it, "voteOptionIdentifier") }
            .groupingBy { it }
            .eachCount()
        val options = objectValues(optionArray).mapIndexedNotNull { index, optionJson ->
            val text = firstNonBlank(
                stringValue(optionJson, "text"),
                stringValue(optionJson, "attributedText"),
            ) ?: return@mapIndexedNotNull null
            val id = stringValue(optionJson, "optionIdentifier") ?: "option-$index"
            PollOption(id = id, text = text, voteCount = voteCounts[id] ?: 0)
        }
        if (options.isEmpty()) return null
        return InteractivePayload.Poll(
            bundleId = bundleId,
            appName = "Polls",
            caption = caption,
            url = url,
            question = stringValue(json, "title")?.takeIf { it.isNotBlank() } ?: "Poll",
            options = options,
        )
    }

    private fun appNameForBundle(bundleId: String?): String = when {
        bundleId == null -> "Unknown"
        bundleId.contains("findmy", ignoreCase = true) -> "Find My"
        bundleId.contains("gamepigeon", ignoreCase = true) -> "GamePigeon"
        bundleId.contains("PeerPayment", ignoreCase = true) -> "Apple Pay"
        bundleId.contains("DigitalTouch", ignoreCase = true) -> "Digital Touch"
        bundleId.contains("Poll", ignoreCase = true) -> "Polls"
        bundleId == "com.openbubbles.passwords" -> "Shared Passwords"
        bundleId.startsWith(APP_PLUGIN) -> bundleId.substringAfterLast(':').substringAfterLast('.')
        else -> bundleId.substringAfterLast('.')
    }

    private fun supportedTitle(kind: SupportedKind, fallback: String): String = when (kind) {
        SupportedKind.APPLE_PAY -> "Apple Pay"
        SupportedKind.GAME_PIGEON -> "GamePigeon"
        SupportedKind.DIGITAL_TOUCH -> "Digital Touch"
        SupportedKind.PASSWORD_SHARE -> "Shared Passwords"
        SupportedKind.APP -> fallback
    }

    private fun decodeDataUrl(url: String?): String? {
        if (url?.startsWith("data:") != true) return null
        val body = url.substringAfter(',', missingDelimiterValue = "").substringBefore('?')
        if (body.isEmpty()) return null
        val decoded = runCatching { Base64.getDecoder().decode(body) }.getOrNull()
            ?: runCatching { Base64.getUrlDecoder().decode(body) }.getOrNull()
        return decoded?.toString(StandardCharsets.UTF_8)
            ?: runCatching { URLDecoder.decode(body, StandardCharsets.UTF_8.name()) }.getOrNull()
    }

    private fun nestedRelativeUrl(json: String): String? {
        val urlObject = objectValue(json, "URL") ?: return null
        return stringValue(urlObject, "NS.relative")
    }

    // Every caller passes a literal key, so these caches stay bounded. The
    // parser runs for each balloon message on every transcript projection;
    // compiling the same patterns each call showed up as pure CPU waste.
    private val stringPatterns = ConcurrentHashMap<String, Regex>()
    private val numberPatterns = ConcurrentHashMap<String, Regex>()
    private val keyPatterns = ConcurrentHashMap<String, Regex>()

    private fun stringValue(json: String, key: String): String? {
        val pattern = stringPatterns.getOrPut(key) {
            Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*\\\"((?:[^\\\"\\\\]|\\\\.)*)\\\"")
        }
        val match = pattern.find(json) ?: return null
        return unescapeJson(match.groupValues[1])
    }

    private fun numberValue(json: String, key: String): Double? =
        numberPatterns.getOrPut(key) {
            Regex("\\\"${Regex.escape(key)}\\\"\\s*:\\s*(-?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][+-]?\\d+)?)")
        }
            .find(json)
            ?.groupValues
            ?.getOrNull(1)
            ?.toDoubleOrNull()

    private fun objectValue(json: String, key: String): String? = bracketValue(json, key, '{', '}')

    private fun arrayValue(json: String, key: String): String? = bracketValue(json, key, '[', ']')

    private fun bracketValue(json: String, key: String, open: Char, close: Char): String? {
        val keyPattern = keyPatterns.getOrPut(key) { Regex("\\\"${Regex.escape(key)}\\\"\\s*:") }
        val keyMatch = keyPattern.find(json) ?: return null
        val start = json.indexOf(open, keyMatch.range.last + 1)
        if (start < 0) return null
        return balancedSlice(json, start, open, close)
    }

    private fun objectValues(arrayJson: String): List<String> {
        val values = mutableListOf<String>()
        var index = 0
        while (index < arrayJson.length) {
            val start = arrayJson.indexOf('{', index)
            if (start < 0) break
            val value = balancedSlice(arrayJson, start, '{', '}') ?: break
            values += value
            index = start + value.length
        }
        return values
    }

    private fun balancedSlice(source: String, start: Int, open: Char, close: Char): String? {
        var depth = 0
        var quoted = false
        var escaped = false
        for (index in start until source.length) {
            val char = source[index]
            if (quoted) {
                if (escaped) escaped = false
                else if (char == '\\') escaped = true
                else if (char == '"') quoted = false
                continue
            }
            if (char == '"') quoted = true
            else if (char == open) depth += 1
            else if (char == close && --depth == 0) return source.substring(start, index + 1)
        }
        return null
    }

    private fun unescapeJson(value: String): String = buildString {
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char != '\\' || index + 1 >= value.length) {
                append(char)
                index += 1
                continue
            }
            when (val escaped = value[index + 1]) {
                'n' -> append('\n')
                'r' -> append('\r')
                't' -> append('\t')
                'b' -> append('\b')
                'f' -> append('\u000C')
                'u' -> {
                    val end = (index + 6).coerceAtMost(value.length)
                    val code = value.substring(index + 2, end).toIntOrNull(16)
                    if (code != null) {
                        append(code.toChar())
                        index += 4
                    } else {
                        append(escaped)
                    }
                }
                else -> append(escaped)
            }
            index += 2
        }
    }

    private fun firstNonBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }
}

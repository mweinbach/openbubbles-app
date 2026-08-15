package app.openbubbles.core.model

import app.openbubbles.db.Chat
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/** Legacy-compatible notification mute evaluation for upgraded chat rows. */
object ChatMute {
    fun shouldMute(
        chat: Chat,
        senderAddress: String? = null,
        messageText: String? = null,
        nowEpochMs: Long = System.currentTimeMillis(),
    ): Boolean = when (chat.muteType) {
        "mute" -> true
        "temporary_mute" -> expiryEpochMs(chat.muteArgs)?.let { it > nowEpochMs } == true
        "mute_individuals" -> {
            val muted = csv(chat.muteArgs).map(::normalizeAddress)
            senderAddress == null || normalizeAddress(senderAddress) in muted
        }
        "text_detection" -> {
            val keywords = csv(chat.muteArgs)
            messageText == null || keywords.none { keyword ->
                messageText.contains(keyword, ignoreCase = true)
            }
        }
        else -> false
    }

    internal fun expiryEpochMs(value: String?): Long? {
        val raw = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching { Instant.parse(raw).toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching {
                LocalDateTime.parse(raw).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            }.getOrNull()
    }

    private fun csv(value: String?): List<String> =
        value.orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

    private fun normalizeAddress(value: String): String =
        value.removePrefix("tel:").removePrefix("mailto:").lowercase()
}

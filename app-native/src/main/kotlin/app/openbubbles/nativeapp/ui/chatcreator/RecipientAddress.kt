package app.openbubbles.nativeapp.ui.chatcreator

import app.openbubbles.core.contacts.RawContact

/** One committed recipient. [display] is the normalized address; [key] dedupes. */
internal data class RecipientChip(val key: String, val display: String, val isEmail: Boolean)

/** A parsed, valid recipient address (email or phone with junk stripped). */
internal data class ParsedAddress(val display: String, val isEmail: Boolean)

/** Flattened, filtered + sorted contact row model for the picker list. */
internal data class ContactRowUi(
    val contactId: String,
    val name: String,
    val primaryRaw: String,
    val matchedRaw: String,
    val addresses: List<String>,
    val primaryKey: String,
    val subtitle: String,
    val avatarPath: String?,
)

private val EMAIL_REGEX = Regex("^[^\\s@,;]+@[^\\s@,;]+\\.[A-Za-z]{2,}$")
private val PHONE_REGEX = Regex("^\\+?\\d{7,15}$")
private val PHONE_JUNK = Regex("[\\s\\-().]")

/**
 * Parses raw user / provider input into a valid recipient address: emails
 * pass through trimmed, phones are stripped of spaces, dashes, dots and
 * parentheses and must contain 7-15 digits. Null when neither shape matches.
 */
internal fun parseAddress(raw: String): ParsedAddress? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    if (EMAIL_REGEX.matches(trimmed)) return ParsedAddress(trimmed, isEmail = true)
    val phone = trimmed.replace(PHONE_JUNK, "")
    if (PHONE_REGEX.matches(phone)) return ParsedAddress(phone, isEmail = false)
    return null
}

/** Case-insensitive dedupe key (emails only — phones are digit-normalized). */
internal fun keyOf(address: ParsedAddress): String =
    if (address.isEmail) address.display.lowercase() else address.display

/**
 * Addresses to try when the user taps a contact row: the query-matched
 * handle first, then the primary, then every other stored address.
 */
internal fun recipientAddressesToTry(row: ContactRowUi): List<String> =
    (listOf(row.matchedRaw, row.primaryRaw) + row.addresses)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()

internal fun buildRows(contacts: List<RawContact>, query: String): List<ContactRowUi> {
    val q = query.trim()
    return contacts.mapNotNull { contact ->
        val primary = contact.addresses.firstOrNull() ?: return@mapNotNull null
        val parsed = parseAddress(primary)
        val name = contact.displayName?.trim().takeUnless { it.isNullOrEmpty() }
        if (q.isNotEmpty()) {
            val nameHit = name?.contains(q, ignoreCase = true) == true
            val addressHit = contact.addresses.any { it.contains(q, ignoreCase = true) }
            if (!nameHit && !addressHit) return@mapNotNull null
        }
        val matched = if (q.isEmpty()) {
            primary
        } else {
            contact.addresses.firstOrNull { it.contains(q, ignoreCase = true) } ?: primary
        }
        ContactRowUi(
            contactId = contact.id,
            name = name ?: parsed?.display ?: primary,
            primaryRaw = primary,
            matchedRaw = matched,
            addresses = contact.addresses,
            primaryKey = parsed?.let(::keyOf) ?: primary.lowercase(),
            subtitle = matched,
            avatarPath = contact.avatarPath,
        )
    }.sortedWith(compareBy({ it.name.isBlank() }, { it.name.lowercase() }))
}

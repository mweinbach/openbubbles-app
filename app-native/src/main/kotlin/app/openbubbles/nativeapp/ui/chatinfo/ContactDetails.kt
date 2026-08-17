package app.openbubbles.nativeapp.ui.chatinfo

import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.RawContact
import app.openbubbles.nativeapp.ui.findmy.FmFriendUi
import app.openbubbles.nativeapp.ui.findmy.FmPoint

/** Photo, name, and every phone/email known for one conversation participant. */
data class ContactDetails(
    val displayName: String,
    val avatarPath: String?,
    val phones: List<String>,
    val emails: List<String>,
    val handleAddress: String,
) {
    val allAddresses: List<String>
        get() = (listOf(handleAddress) + phones + emails).distinct()
}

/** Find My availability for a contact, including explicit empty states. */
sealed class ContactLocationUi {
    data object Loading : ContactLocationUi()
    data object Unavailable : ContactLocationUi()
    data object NotSharing : ContactLocationUi()
    data class NoFix(val friendName: String) : ContactLocationUi()
    data class Located(val friendName: String, val point: FmPoint) : ContactLocationUi()
    data class Failed(val message: String) : ContactLocationUi()
}

internal fun displayContactAddress(address: String): String {
    val trimmed = address.trim()
    return when {
        trimmed.startsWith("mailto:", ignoreCase = true) -> trimmed.substring(7)
        trimmed.startsWith("tel:", ignoreCase = true) -> trimmed.substring(4)
        else -> trimmed
    }
}

internal fun isEmailAddress(address: String): Boolean =
    displayContactAddress(address).contains('@')

internal fun addressesMatch(left: String, right: String): Boolean {
    val leftKeys = ContactSync.addressMatchKeys(left)
    if (leftKeys.isEmpty()) return false
    return ContactSync.addressMatchKeys(right).any(leftKeys::contains)
}

internal fun resolveContactDetails(
    handleAddress: String,
    fallbackName: String?,
    contacts: List<RawContact>,
): ContactDetails {
    val handleKeys = ContactSync.addressMatchKeys(handleAddress)
    val match = contacts.firstOrNull { contact ->
        contact.addresses.any { candidate ->
            ContactSync.addressMatchKeys(candidate).any(handleKeys::contains)
        }
    }
    val rawAddresses = match?.addresses.orEmpty().ifEmpty { listOf(handleAddress) }
        .map(::displayContactAddress)
        .filter { it.isNotBlank() }
        .distinct()
    return ContactDetails(
        displayName = match?.displayName?.takeIf { it.isNotBlank() }
            ?: fallbackName?.takeIf { it.isNotBlank() }
            ?: displayContactAddress(handleAddress),
        avatarPath = match?.avatarPath,
        phones = rawAddresses.filterNot(::isEmailAddress),
        emails = rawAddresses.filter(::isEmailAddress),
        handleAddress = handleAddress,
    )
}

internal fun matchFriendLocation(
    candidateAddresses: List<String>,
    friends: List<FmFriendUi>,
): FmFriendUi? = friends.firstOrNull { friend ->
    val address = friend.address ?: return@firstOrNull false
    candidateAddresses.any { addressesMatch(it, address) }
}

internal fun contactLocationFromFriends(
    candidateAddresses: List<String>,
    friends: List<FmFriendUi>,
    available: Boolean,
    errorMessage: String? = null,
): ContactLocationUi {
    if (!available) return ContactLocationUi.Unavailable
    if (errorMessage != null) return ContactLocationUi.Failed(errorMessage)
    val friend = matchFriendLocation(candidateAddresses, friends)
        ?: return ContactLocationUi.NotSharing
    val point = friend.location
    return if (point != null) {
        ContactLocationUi.Located(friend.name, point)
    } else {
        ContactLocationUi.NoFix(friend.name)
    }
}

/** "just now", "7 min ago", "3 h ago", or a short date when stale. */
internal fun locationFreshness(
    timestampMs: Long?,
    nowMillis: Long = System.currentTimeMillis(),
): String? {
    if (timestampMs == null || timestampMs <= 0L) return null
    val ageMs = nowMillis - timestampMs
    val minutes = ageMs / 60_000
    val hours = minutes / 60
    return when {
        ageMs < 60_000 -> "just now"
        hours < 1 -> "$minutes min ago"
        hours < 24 -> "$hours h ago"
        else -> java.time.format.DateTimeFormatter.ofPattern("M/d/yy")
            .format(
                java.time.Instant.ofEpochMilli(timestampMs)
                    .atZone(java.time.ZoneId.systemDefault()),
            )
    }
}

internal fun locationAccuracy(meters: Double?): String? = meters?.takeIf { it > 0 }?.let {
    if (it < 1000) {
        "${kotlin.math.round(it).toInt()} m"
    } else {
        String.format(java.util.Locale.US, "%.1f km", it / 1000)
    }
}

/** Direct chats render the contact card; groups keep the participant list. */
internal fun shouldShowDirectContactCard(isGroup: Boolean?): Boolean = isGroup == false

/**
 * Address used to resolve the 1:1 contact card. The conversation list's
 * [avatarAddress] is the same identity the header already shows; handle
 * rows are a fallback for older ingests that never linked them.
 */
internal fun directContactAddress(
    avatarAddress: String?,
    participantAddresses: List<String>,
): String = avatarAddress?.takeIf { it.isNotBlank() }
    ?: participantAddresses.firstOrNull { it.isNotBlank() }.orEmpty()

/** Fold extra conversation handles (phone + email threads) into the card. */
internal fun mergeContactAddresses(
    details: ContactDetails,
    extraAddresses: List<String>,
): ContactDetails {
    val extras = extraAddresses.map(::displayContactAddress).filter { it.isNotBlank() }
    if (extras.isEmpty()) return details
    return details.copy(
        phones = (details.phones + extras.filterNot(::isEmailAddress)).distinct(),
        emails = (details.emails + extras.filter(::isEmailAddress)).distinct(),
    )
}

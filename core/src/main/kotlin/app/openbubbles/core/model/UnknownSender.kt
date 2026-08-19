package app.openbubbles.core.model

/**
 * Direct chats whose list title is still the raw handle have no resolved
 * contact. Groups are never unknown-sender.
 */
fun isUnknownDirectSender(
    isGroup: Boolean,
    title: String,
    avatarAddress: String?,
): Boolean {
    if (isGroup) return false
    val address = avatarAddress?.takeIf { it.isNotBlank() } ?: return false
    val handle = address.substringAfter(':')
    val trimmed = title.trim()
    return trimmed.equals(handle, ignoreCase = true) ||
        trimmed.equals(address, ignoreCase = true)
}

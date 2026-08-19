package app.openbubbles.core.contacts

/**
 * A genuine disagreement between an iCloud card and a device contact from
 * another account, surfaced for the user instead of silently writing a
 * competing card into the device store.
 */
data class ContactConflict(
    val icloudId: String,
    val icloudName: String?,
    val icloudNumbers: List<String>,
    val deviceName: String?,
    val deviceNumbers: List<String>,
)

enum class ConflictDecision { USE_ICLOUD, KEEP_PHONE }

/** Per-card outcome of [ContactMergePolicy.plan]. */
sealed interface MergeAction {
    val contact: RawContact

    /** No device presence yet (or the user chose iCloud): write our raw contact. */
    data class Insert(override val contact: RawContact) : MergeAction

    /** Our raw contact already exists: refresh it in place, iCloud wins every managed field. */
    data class Update(override val contact: RawContact) : MergeAction

    /** The user chose the phone version earlier: leave the device store alone. */
    data class Skip(override val contact: RawContact) : MergeAction

    /** Another account's contact genuinely disagrees: ask before writing. */
    data class AwaitDecision(
        override val contact: RawContact,
        val conflict: ContactConflict,
    ) : MergeAction
}

data class MergePlan(
    val actions: List<MergeAction>,
    /** SOURCE_IDs of our raw contacts whose iCloud card no longer exists. */
    val deletions: List<String>,
) {
    val conflicts: List<ContactConflict>
        get() = actions.filterIsInstance<MergeAction.AwaitDecision>().map { it.conflict }
}

/**
 * Classifies each iCloud card against a snapshot of the device contact
 * store. Pure and deterministic: the ContentProvider writer executes the
 * plan verbatim, so every rule here is host-testable.
 *
 * "Genuinely differs" means: both sides have a non-blank display name and
 * they differ case/whitespace-insensitively, or both sides have phone
 * numbers and the sets are not equivalent under
 * [ContactSync.phoneNumberVariants] (so "+1 (555) 123-4567" and
 * "5551234567" agree). A side with no name or no numbers never conflicts —
 * it has nothing competing to lose.
 */
object ContactMergePolicy {

    fun plan(
        icloud: List<RawContact>,
        device: List<RawContact>,
        oursSourceIds: Set<String>,
        decisions: Map<String, ConflictDecision>,
    ): MergePlan {
        val icloudIds = icloud.mapTo(HashSet()) { it.id }
        val actions = icloud.map { card -> classify(card, device, oursSourceIds, decisions) }
        return MergePlan(
            actions = actions,
            deletions = (oursSourceIds - icloudIds).sorted(),
        )
    }

    private fun classify(
        card: RawContact,
        device: List<RawContact>,
        oursSourceIds: Set<String>,
        decisions: Map<String, ConflictDecision>,
    ): MergeAction {
        if (card.id in oursSourceIds) return MergeAction.Update(card)
        when (decisions[card.id]) {
            ConflictDecision.KEEP_PHONE -> return MergeAction.Skip(card)
            ConflictDecision.USE_ICLOUD -> return MergeAction.Insert(card)
            null -> {}
        }
        val cardKeys = matchKeys(card.addresses)
        val matches = device.filter { candidate ->
            matchKeys(candidate.addresses).any(cardKeys::contains)
        }
        val disagreeing = matches.firstOrNull { genuinelyDiffers(card, it) }
            ?: return MergeAction.Insert(card)
        return MergeAction.AwaitDecision(
            contact = card,
            conflict = ContactConflict(
                icloudId = card.id,
                icloudName = renderedName(card),
                icloudNumbers = phoneNumbers(card),
                deviceName = disagreeing.displayName,
                deviceNumbers = phoneNumbers(disagreeing),
            ),
        )
    }

    private fun genuinelyDiffers(card: RawContact, deviceContact: RawContact): Boolean {
        val icloudName = renderedName(card)
        val deviceName = deviceContact.displayName?.trim().orEmpty()
        val nameDiffers = !icloudName.isNullOrBlank() && deviceName.isNotBlank() &&
            !icloudName.trim().equals(deviceName, ignoreCase = true)

        val icloudNumbers = phoneNumbers(card)
        val deviceNumbers = phoneNumbers(deviceContact)
        val numbersDiffer = icloudNumbers.isNotEmpty() && deviceNumbers.isNotEmpty() &&
            !numbersEquivalent(icloudNumbers, deviceNumbers)

        return nameDiffers || numbersDiffer
    }

    private fun numbersEquivalent(first: List<String>, second: List<String>): Boolean {
        fun covered(numbers: List<String>, by: List<String>): Boolean {
            val keys = by.flatMapTo(HashSet()) { ContactSync.phoneNumberVariants(it) }
            return numbers.all { number ->
                ContactSync.phoneNumberVariants(number).any(keys::contains)
            }
        }
        return covered(first, second) && covered(second, first)
    }

    /** The name our raw contact would surface: structured name, then FN. */
    private fun renderedName(card: RawContact): String? {
        val structured = "${card.firstName.orEmpty()} ${card.lastName.orEmpty()}".trim()
        return card.displayName?.takeIf(String::isNotBlank) ?: structured.takeIf(String::isNotBlank)
    }

    private fun phoneNumbers(contact: RawContact): List<String> =
        contact.addresses.filterNot { it.contains('@') }.filter { it.isNotBlank() }

    private fun matchKeys(addresses: List<String>): Set<String> =
        addresses.flatMapTo(HashSet()) { ContactSync.addressMatchKeys(it) }
}

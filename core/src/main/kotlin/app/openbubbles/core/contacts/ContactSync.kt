package app.openbubbles.core.contacts

import app.openbubbles.db.ContactV2
import app.openbubbles.db.ContactV2_
import app.openbubbles.db.Handle
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder

/**
 * A platform-provided contact, flattened from the device contact store
 * (Android ContactsProvider / desktop equivalent). [id] is the platform's
 * stable contact id, stored as [ContactV2.nativeContactId] — the sync's
 * unique match key.
 */
data class RawContact(
    val id: String,
    val displayName: String?,
    val firstName: String?,
    val lastName: String?,
    val avatarPath: String?,
    val addresses: List<String>,
)

/**
 * Display info for a handle, resolvable from the linked contact or falling
 * back to the handle's own address. Destructures as `(name, avatar)`:
 *
 * ```kotlin
 * val (name, avatar) = contactSync.displayInfoFor(handle)
 * ```
 */
data class HandleDisplayInfo(
    val name: String?,
    val avatar: String?,
)

/**
 * Contact integration — the Kotlin port of Dart's
 * `ContactV2Actions.syncContactsToHandles` + `Handle.displayName` /
 * `HandleState._resolveAvatarPath`:
 *
 * 1. [upsertContacts] ingests platform [RawContact]s into `ContactV2` rows,
 *    matched by the `nativeContactId` unique index. An existing
 *    `avatarPath` survives a sync that carries none (the platform only
 *    reports an avatar after it has exported the image file — clearing a
 *    good path on a null report would drop avatars on every partial sync).
 * 2. Each contact is re-linked to [Handle]s by matching normalized
 *    addresses (lower-cased emails; digit/plus-normalized phone numbers
 *    with country-code variants — `_getPhoneNumberVariants`). The ToMany
 *    relation is rebuilt wholesale, exactly like the Dart
 *    `contact.handles.clear(); addAll(matched)` cycle.
 * 3. [contactsForHandles] / [displayInfoFor] resolve handles → contact for
 *    the repos and UI (chat titles, sender names, avatars).
 */
class ContactSync(private val store: BoxStore) {

    private val contactBox = store.boxFor(ContactV2::class.java)
    private val handleBox = store.boxFor(Handle::class.java)

    /**
     * Upserts [rawContacts] in one transaction. New rows are created,
     * existing rows (same [RawContact.id]) are updated in place; removal of
     * a contact address automatically unlinks its no-longer-matching
     * handles (the ToMany is rebuilt from the current address list).
     * Returns the persisted rows in input order.
     */
    fun upsertContacts(rawContacts: List<RawContact>): List<ContactV2> = store.callInTx {
        // Handle lookup maps over the whole table, built once per sync
        // (mirrors the Dart email/phone handle maps).
        val emailHandles = mutableMapOf<String, MutableSet<Handle>>()
        val phoneHandles = mutableMapOf<String, MutableSet<Handle>>()
        handleBox.all.forEach { handle ->
            indexHandleAddress(handle.address, handle, emailHandles, phoneHandles)
            val formatted = handle.formattedAddress
            if (!formatted.isNullOrEmpty()) {
                indexHandleAddress(formatted, handle, emailHandles, phoneHandles)
            }
        }

        rawContacts.map { raw -> upsertOne(raw, emailHandles, phoneHandles) }
    }

    private fun upsertOne(
        raw: RawContact,
        emailHandles: Map<String, Set<Handle>>,
        phoneHandles: Map<String, Set<Handle>>,
    ): ContactV2 {
        val existing = contactBox.query()
            .equal(ContactV2_.nativeContactId, raw.id, QueryBuilder.StringOrder.CASE_SENSITIVE)
            .build().use { it.findFirst() }
        val contact = existing ?: ContactV2()

        contact.nativeContactId = raw.id
        if (raw.displayName != null) contact.displayName = raw.displayName
        contact.firstName = raw.firstName
        contact.lastName = raw.lastName
        contact.addresses = raw.addresses.map { normalizeAddress(it) }.filter { it.isNotEmpty() }
        // Keep a previously synced avatar when the platform reports none.
        if (raw.avatarPath != null) contact.avatarPath = raw.avatarPath
        contact.isNative = true

        // Re-link handles from the (normalized) address list.
        val matched = LinkedHashSet<Handle>()
        for (address in contact.addresses) {
            if (address.contains('@')) {
                emailHandles[address]?.let { matched += it }
            } else {
                phoneNumberVariants(address).forEach { variant ->
                    phoneHandles[variant]?.let { matched += it }
                }
            }
        }
        contact.handles.clear()
        contact.handles.addAll(matched)

        try {
            contactBox.put(contact)
        } catch (_: io.objectbox.exception.UniqueViolationException) {
            // Lost a race against a concurrent sync with the same id.
        }
        return contact
    }

    private fun indexHandleAddress(
        address: String,
        handle: Handle,
        emailHandles: MutableMap<String, MutableSet<Handle>>,
        phoneHandles: MutableMap<String, MutableSet<Handle>>,
    ) {
        if (address.contains('@')) {
            emailHandles.getOrPut(normalizeEmail(address)) { mutableSetOf() }.add(handle)
        } else {
            phoneNumberVariants(address).forEach { variant ->
                phoneHandles.getOrPut(variant) { mutableSetOf() }.add(handle)
            }
        }
    }

    /**
     * Resolves every handle that belongs to a contact:
     * `handleId → ContactV2` (first contact wins when several link the same
     * handle, matching Dart's `contactsV2.firstOrNull` reads).
     */
    fun contactsForHandles(): Map<Long, ContactV2> {
        val resolved = HashMap<Long, ContactV2>()
        contactBox.all.forEach { contact ->
            contact.handles.forEach { handle ->
                resolved.getOrPut(handle.id) { contact }
            }
        }
        return resolved
    }

    /**
     * Name + avatar path the UI should render for [handle] — the port of
     * `Handle.displayName` / `HandleState._resolveAvatarPath`: prefer the
     * linked (native-first) contact's computed name and avatar; fall back to
     * the formatted address, then the raw address. Business handles show as
     * "Business".
     */
    fun displayInfoFor(handle: Handle): HandleDisplayInfo {
        if (handle.address.startsWith("urn:biz")) {
            return HandleDisplayInfo(name = "Business", avatar = null)
        }
        val contacts = handle.contactsV2
        val contact = contacts.firstOrNull { it.isNative } ?: contacts.firstOrNull()
        val name = contact?.let { computedDisplayName(it) }?.takeIf { it.isNotEmpty() }
            ?: handle.formattedAddress?.takeIf { it.isNotEmpty() }
            ?: handle.address
        return HandleDisplayInfo(name = name, avatar = contact?.avatarPath)
    }

    companion object {
        /** Lower-cased, trimmed — `ContactV2.normalizeEmail`. */
        fun normalizeEmail(email: String): String = email.trim().lowercase()

        /** Digits and `+` only — `ContactV2.normalizePhoneNumber`. */
        fun normalizePhoneNumber(phone: String): String = phone.replace(Regex("[^\\d+]"), "")

        /** Emails lower-cased; phones digit/plus-normalized. */
        fun normalizeAddress(address: String): String =
            if (address.contains('@')) normalizeEmail(address) else normalizePhoneNumber(address)

        /**
         * Normalized phone variants for matching — the port of Dart's
         * `_getPhoneNumberVariants`: the bare normalized form plus
         * country-code-stripped (`+1`, `+44`, … 1–3 digits) and
         * country-code-prefixed forms, so a contact storing "1234567890"
         * matches a handle storing "+11234567890" and vice versa.
         */
        fun phoneNumberVariants(phone: String): Set<String> {
            val variants = mutableSetOf<String>()
            val normalized = normalizePhoneNumber(phone)
            if (normalized.isEmpty()) return variants

            variants += normalized
            if (normalized.startsWith("+")) {
                variants += normalized.substring(1)
                for (i in 1..3) {
                    if (i < normalized.length) {
                        val withoutCountryCode = normalized.substring(i + 1)
                        if (withoutCountryCode.isNotEmpty()) variants += withoutCountryCode
                    }
                }
            } else {
                variants += "+$normalized"
                for (i in 1..3) {
                    if (i < normalized.length) {
                        val withoutPrefix = normalized.substring(i)
                        variants += withoutPrefix
                        variants += "+$withoutPrefix"
                    }
                }
            }
            return variants
        }

        /**
         * Best display name of a contact — `ContactV2.computedDisplayName`:
         * nickname, then "first last", then the raw displayName.
         */
        fun computedDisplayName(contact: ContactV2): String {
            if (!contact.nickname.isNullOrEmpty()) return contact.nickname
            val structured = "${contact.firstName.orEmpty()} ${contact.lastName.orEmpty()}".trim()
            if (structured.isNotEmpty()) return structured
            return contact.displayName.orEmpty()
        }
    }
}

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

/** Result of rebuilding contact -> handle relations after new handles arrive. */
data class ContactRelinkResult(
    val contacts: Int,
    val handles: Int,
    val changedContacts: Int,
    val linkedContacts: Int,
    val linkedHandles: Int,
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
        val (emailHandles, phoneHandles) = buildHandleIndexes()

        rawContacts.map { raw -> upsertOne(raw, emailHandles, phoneHandles) }
    }

    /**
     * Rebuilds every stored contact relation against the current handle table.
     *
     * Contact providers and CloudKit history are independent streams. On a
     * fresh install CardDAV can finish first, leaving contacts correctly
     * stored but unable to name handles that history creates later. Running
     * this after a history pass (and even after an unchanged CardDAV pass)
     * closes that ordering gap without downloading contacts again.
     */
    fun relinkContacts(): ContactRelinkResult = store.callInTx {
        val handles = handleBox.all
        val (emailHandles, phoneHandles) = buildHandleIndexes(handles)
        val contacts = contactBox.all
        var changedContacts = 0
        var linkedContacts = 0
        val linkedHandleIds = HashSet<Long>()

        contacts.forEach { contact ->
            val matched = matchedHandles(contact.addresses, emailHandles, phoneHandles)
            val existingIds = contact.handles.mapTo(HashSet()) { it.id }
            val matchedIds = matched.mapTo(HashSet()) { it.id }
            if (existingIds != matchedIds) {
                contact.handles.clear()
                contact.handles.addAll(matched)
                contactBox.put(contact)
                changedContacts++
            }
            if (matched.isNotEmpty()) linkedContacts++
            matched.forEach { linkedHandleIds += it.id }
        }

        ContactRelinkResult(
            contacts = contacts.size,
            handles = handles.size,
            changedContacts = changedContacts,
            linkedContacts = linkedContacts,
            linkedHandles = linkedHandleIds.size,
        )
    }

    /**
     * Removes contacts by their platform-stable ids. This is intentionally
     * separate from [upsertContacts]: Android exposes a complete snapshot,
     * while CardDAV exposes explicit tombstones and collection resets.
     */
    fun removeContacts(nativeContactIds: Collection<String>): Int = store.callInTx {
        nativeContactIds.asSequence().distinct().mapNotNull { nativeId ->
            contactBox.query()
                .equal(ContactV2_.nativeContactId, nativeId, QueryBuilder.StringOrder.CASE_SENSITIVE)
                .build().use { it.findFirst() }
        }.count { contact ->
            // Flush the ToMany change before deleting the source row so no
            // stale handle relation can survive a CardDAV tombstone.
            contact.handles.clear()
            contactBox.put(contact)
            contactBox.remove(contact.id)
        }
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
        val matched = matchedHandles(contact.addresses, emailHandles, phoneHandles)
        contact.handles.clear()
        contact.handles.addAll(matched)

        try {
            contactBox.put(contact)
        } catch (_: io.objectbox.exception.UniqueViolationException) {
            // Lost a race against a concurrent sync with the same id.
        }
        return contact
    }

    private fun buildHandleIndexes(
        handles: List<Handle> = handleBox.all,
    ): Pair<Map<String, Set<Handle>>, Map<String, Set<Handle>>> {
        val emailHandles = mutableMapOf<String, MutableSet<Handle>>()
        val phoneHandles = mutableMapOf<String, MutableSet<Handle>>()
        handles.forEach { handle ->
            indexHandleAddress(handle.address, handle, emailHandles, phoneHandles)
            handle.formattedAddress?.takeIf { it.isNotEmpty() }?.let { formatted ->
                indexHandleAddress(formatted, handle, emailHandles, phoneHandles)
            }
        }
        return emailHandles to phoneHandles
    }

    private fun matchedHandles(
        addresses: List<String>,
        emailHandles: Map<String, Set<Handle>>,
        phoneHandles: Map<String, Set<Handle>>,
    ): LinkedHashSet<Handle> {
        val matched = LinkedHashSet<Handle>()
        addresses.forEach { rawAddress ->
            val address = normalizeAddress(rawAddress)
            if (address.contains('@')) {
                emailHandles[address]?.let { matched += it }
            } else {
                phoneNumberVariants(address).forEach { variant ->
                    phoneHandles[variant]?.let { matched += it }
                }
            }
        }
        return matched
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
     * Resolves every handle that belongs to a contact. Synced iCloud contacts
     * win, then native/platform contacts, then legacy rows.
     */
    fun contactsForHandles(): Map<Long, ContactV2> {
        return store.callInReadTx {
            val resolved = HashMap<Long, ContactV2>()
            contactsByPreference().forEach { contact ->
                contact.handles.forEach { handle ->
                    resolved.getOrPut(handle.id) { contact }
                }
            }
            resolved
        }
    }

    /**
     * One read-transaction projection for list rendering. This avoids opening
     * a lazy backlink transaction per chat row (which is both slower and can
     * keep ObjectBox transactions alive until finalization under heavy sync).
     */
    fun displayInfoByHandleId(): Map<Long, HandleDisplayInfo> = store.callInReadTx {
        val resolved = HashMap<Long, HandleDisplayInfo>()
        contactsByPreference().forEach { contact ->
            val name = computedDisplayName(contact).takeIf { it.isNotEmpty() }
            val info = HandleDisplayInfo(name = name, avatar = contact.avatarPath)
            contact.handles.forEach { handle ->
                resolved.putIfAbsent(handle.id, info)
            }
        }
        resolved
    }

    /** Stable contact ids for exact identity grouping across linked handles. */
    fun contactIdsByHandleId(): Map<Long, Long> = store.callInReadTx {
        val resolved = HashMap<Long, Long>()
        contactsByPreference().forEach { contact ->
            contact.handles.forEach { handle ->
                resolved.putIfAbsent(handle.id, contact.id)
            }
        }
        resolved
    }

    /**
     * Name + avatar path the UI should render for [handle] — the port of
     * `Handle.displayName` / `HandleState._resolveAvatarPath`: prefer the
     * linked iCloud contact, then the native contact, then the formatted or
     * raw address. Business handles show as "Business".
     */
    fun displayInfoFor(handle: Handle): HandleDisplayInfo {
        if (handle.address.startsWith("urn:biz")) {
            return HandleDisplayInfo(name = "Business", avatar = null)
        }
        val contact = store.callInReadTx {
            contactsByPreference().firstOrNull { candidate ->
                candidate.handles.any { it.id == handle.id }
            }
        }
        val name = contact?.let { computedDisplayName(it) }?.takeIf { it.isNotEmpty() }
            ?: handle.formattedAddress?.takeIf { it.isNotEmpty() }
            ?: handle.address
        return HandleDisplayInfo(name = name, avatar = contact?.avatarPath)
    }

    /**
     * Resolves a saved contact directly from an address, without requiring a
     * persisted Handle relation. Contact sync and message ingestion are
     * independent streams, so a notification can arrive after the contact is
     * stored but before its newly-created handle has been linked.
     */
    fun displayInfoForAddress(address: String): HandleDisplayInfo? = store.callInReadTx {
        val targetKeys = addressMatchKeys(address)
        if (targetKeys.isEmpty()) return@callInReadTx null
        contactsByPreference().firstOrNull { contact ->
            contact.addresses.any { candidate ->
                addressMatchKeys(candidate).any(targetKeys::contains)
            }
        }?.let { contact ->
            HandleDisplayInfo(
                name = computedDisplayName(contact).takeIf { it.isNotEmpty() },
                avatar = contact.avatarPath,
            )
        }
    }

    fun preferredContacts(includeNativeContacts: Boolean = true): List<RawContact> =
        store.callInReadTx {
            val claimedAddresses = HashSet<String>()
            contactsByPreference().mapNotNull { contact ->
                if (!includeNativeContacts && !isICloudContact(contact)) return@mapNotNull null
                val addresses = contact.addresses.distinct().filter { address ->
                    val keys = addressMatchKeys(address)
                    if (keys.any(claimedAddresses::contains)) {
                        false
                    } else {
                        claimedAddresses += keys
                        true
                    }
                }
                if (addresses.isEmpty()) return@mapNotNull null
                RawContact(
                    id = contact.nativeContactId ?: "contact:${contact.id}",
                    displayName = computedDisplayName(contact).takeIf(String::isNotEmpty),
                    firstName = contact.firstName,
                    lastName = contact.lastName,
                    avatarPath = contact.avatarPath,
                    addresses = addresses,
                )
            }
        }

    private fun contactsByPreference(): List<ContactV2> =
        contactBox.all.sortedWith(contactPreferenceComparator)

    private fun addressMatchKeys(address: String): Set<String> =
        if (address.contains('@')) {
            setOf(normalizeEmail(address))
        } else {
            phoneNumberVariants(address)
        }

    companion object {
        private const val ICLOUD_CONTACT_PREFIX = "icloud:"

        private val contactPreferenceComparator =
            compareBy<ContactV2> { contactPreference(it) }.thenBy { it.id }

        private fun contactPreference(contact: ContactV2): Int = when {
            isICloudContact(contact) -> 0
            contact.isNative -> 1
            else -> 2
        }

        private fun isICloudContact(contact: ContactV2): Boolean =
            contact.nativeContactId?.startsWith(ICLOUD_CONTACT_PREFIX) == true

        private fun withoutAddressScheme(address: String): String {
            val trimmed = address.trim()
            return when {
                trimmed.startsWith("mailto:", ignoreCase = true) -> trimmed.substring(7)
                trimmed.startsWith("tel:", ignoreCase = true) -> trimmed.substring(4)
                else -> trimmed
            }
        }

        /** Lower-cased, trimmed — `ContactV2.normalizeEmail`. */
        fun normalizeEmail(email: String): String = withoutAddressScheme(email).lowercase()

        /** Digits and `+` only — `ContactV2.normalizePhoneNumber`. */
        fun normalizePhoneNumber(phone: String): String =
            withoutAddressScheme(phone).replace(Regex("[^\\d+]"), "")

        /** Emails lower-cased; phones digit/plus-normalized. */
        fun normalizeAddress(address: String): String {
            val withoutScheme = withoutAddressScheme(address)
            return if (withoutScheme.contains('@')) {
                normalizeEmail(withoutScheme)
            } else {
                normalizePhoneNumber(withoutScheme)
            }
        }

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

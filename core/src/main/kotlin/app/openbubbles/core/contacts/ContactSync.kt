package app.openbubbles.core.contacts

import app.openbubbles.core.repo.StoreEntityChange
import app.openbubbles.core.repo.StoreInvalidationCoordinators
import app.openbubbles.db.ContactV2
import app.openbubbles.db.ContactV2_
import app.openbubbles.db.Handle
import io.objectbox.BoxStore
import io.objectbox.query.QueryBuilder
import java.util.LinkedHashMap

/** Explicit avatar ownership for contact updates; no persistence field is required. */
sealed interface AvatarUpdate {
    /** The source could not authoritatively read the photo, so preserve the stored value. */
    data object Keep : AvatarUpdate

    /** The source authoritatively reports that the contact has no photo. */
    data object Clear : AvatarUpdate

    /** The source read a photo whose render identity is [path]. */
    data class Set(val path: String) : AvatarUpdate {
        init {
            require(path.isNotBlank()) { "Avatar path must not be blank" }
        }
    }
}

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
    val avatarUpdate: AvatarUpdate,
    val addresses: List<String>,
    val nickname: String? = null,
    val company: String? = null,
) {
    /**
     * Compatibility constructor for read-only projections and callers that
     * have not claimed authoritative avatar ownership. A null path means
     * [AvatarUpdate.Keep], never removal.
     */
    constructor(
        id: String,
        displayName: String?,
        firstName: String?,
        lastName: String?,
        avatarPath: String?,
        addresses: List<String>,
        nickname: String? = null,
        company: String? = null,
    ) : this(
        id = id,
        displayName = displayName,
        firstName = firstName,
        lastName = lastName,
        avatarUpdate = avatarPath
            ?.takeIf(String::isNotBlank)
            ?.let(AvatarUpdate::Set)
            ?: AvatarUpdate.Keep,
        addresses = addresses,
        nickname = nickname,
        company = company,
    )

    /** The path consumers render; Keep and Clear both project as no new path. */
    val avatarPath: String?
        get() = (avatarUpdate as? AvatarUpdate.Set)?.path
}

/**
 * One complete, successful device-provider snapshot. [legacyNativeIds] maps
 * each stable namespaced id to the numeric ContactsProvider row id seen in
 * the same read, allowing a bounded migration from pre-LOOKUP_KEY rows.
 */
data class DeviceContactSnapshot(
    val contacts: List<RawContact>,
    val legacyNativeIds: Map<String, String>,
)

data class DeviceContactReconcileResult(
    val upserted: Int,
    val migratedLegacyIds: Int,
    val removed: Int,
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
    val poster: String? = null,
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
 *    matched by the `nativeContactId` unique index. [AvatarUpdate] makes
 *    preservation, replacement, and authoritative removal explicit.
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
    private val invalidations = StoreInvalidationCoordinators.forStore(store)

    // ------------------------------------------------------------------
    // Projection cache
    //
    // The chat list re-projects on every Chat/ContactV2 write; during a
    // history sync that is hundreds of emissions, and each one used to
    // rebuild these maps from full contacts + handles table scans. The maps
    // only depend on the contact and handle tables, so they are memoized and
    // rebuilt (in one pass) when those tables change: this class's own
    // mutators mark the cache dirty synchronously, the store's one shared
    // generation tracker catches outside writers, and row-count probes catch
    // inserts/deletes even before the (asynchronous) observer fires.
    // ------------------------------------------------------------------

    private val projectionLock = Any()

    @Volatile
    private var projectionDirty = true
    private var projectedGeneration = -1L
    private var projectedContactCount = -1L
    private var projectedHandleCount = -1L

    @Volatile
    private var cachedDisplayByHandleId: Map<Long, HandleDisplayInfo> = emptyMap()

    @Volatile
    private var cachedContactIdsByHandleId: Map<Long, Long> = emptyMap()

    @Volatile
    private var cachedDisplayByMatchKey: Map<String, HandleDisplayInfo> = emptyMap()

    /**
     * Drops the memoized handle/address projections. Automatic for writes
     * through this class and (asynchronously) for any committed
     * [ContactV2]/[Handle] write; call it directly after writing those boxes
     * when the very next read must observe an in-place field update.
     */
    fun invalidateProjections() {
        projectionDirty = true
    }

    private fun refreshProjections() {
        val generation = invalidations.generationFor(
            StoreEntityChange.CONTACT,
            StoreEntityChange.HANDLE,
        )
        val contactCount = contactBox.count()
        val handleCount = handleBox.count()
        if (!projectionDirty &&
            generation == projectedGeneration &&
            contactCount == projectedContactCount &&
            handleCount == projectedHandleCount
        ) {
            return
        }
        synchronized(projectionLock) {
            val lockedGeneration = invalidations.generationFor(
                StoreEntityChange.CONTACT,
                StoreEntityChange.HANDLE,
            )
            if (!projectionDirty &&
                lockedGeneration == projectedGeneration &&
                contactBox.count() == projectedContactCount &&
                handleBox.count() == projectedHandleCount
            ) {
                return
            }
            // Clear before reading: a write landing mid-rebuild re-dirties
            // and the next read rebuilds again instead of serving the torn set.
            projectionDirty = false
            store.callInReadTx {
                val (emailHandles, phoneHandles) = buildHandleIndexes()
                val byHandleId = HashMap<Long, HandleDisplayInfo>()
                val contactIds = HashMap<Long, Long>()
                val byMatchKey = HashMap<String, HandleDisplayInfo>()
                contactsByPreference().forEach { contact ->
                    val info = displayInfoOf(contact)
                    handlesFor(contact, emailHandles, phoneHandles).forEach { handle ->
                        byHandleId[handle.id] = mergeDisplayInfo(byHandleId[handle.id], info)
                        contactIds.putIfAbsent(handle.id, contact.id)
                    }
                    contact.addresses.forEach { address ->
                        addressMatchKeys(address).forEach { key ->
                            byMatchKey[key] = mergeDisplayInfo(byMatchKey[key], info)
                        }
                    }
                }
                projectedContactCount = contactBox.count()
                projectedHandleCount = handleBox.count()
                projectedGeneration = lockedGeneration
                cachedDisplayByHandleId = byHandleId
                cachedContactIdsByHandleId = contactIds
                cachedDisplayByMatchKey = byMatchKey
            }
        }
    }

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
    }.also { invalidateProjections() }

    /**
     * Applies a complete device-provider snapshot. Stale Android rows are
     * removed only through this API, so denied or failed provider reads can
     * never masquerade as an empty address book.
     *
     * Legacy numeric ids are migrated in place only when the current row-id
     * alias and at least one address agree. This preserves the ObjectBox row
     * id without trusting a ContactsProvider row id that may have been
     * recycled for a different person.
     */
    fun reconcileDeviceSnapshot(snapshot: DeviceContactSnapshot): DeviceContactReconcileResult =
        store.callInTx {
            val byNativeId = contactBox.all
                .mapNotNull { contact -> contact.nativeContactId?.let { it to contact } }
                .toMap(mutableMapOf())
            var migrated = 0

            snapshot.contacts.forEach { raw ->
                if (byNativeId[raw.id] != null) return@forEach
                val legacyId = snapshot.legacyNativeIds[raw.id]
                    ?.takeIf(::isLegacyDeviceContactId)
                    ?: return@forEach
                val legacy = byNativeId[legacyId] ?: return@forEach
                if (!sameContactAddresses(legacy, raw)) return@forEach
                legacy.nativeContactId = raw.id
                contactBox.put(legacy)
                byNativeId.remove(legacyId)
                byNativeId[raw.id] = legacy
                migrated++
            }

            val (emailHandles, phoneHandles) = buildHandleIndexes()
            snapshot.contacts.forEach { raw -> upsertOne(raw, emailHandles, phoneHandles) }

            val present = snapshot.contacts.mapTo(HashSet()) { it.id }
            val stale = contactBox.all.filter { contact ->
                val nativeId = contact.nativeContactId ?: return@filter false
                (isStableDeviceContactId(nativeId) || isLegacyDeviceContactId(nativeId)) &&
                    nativeId !in present
            }
            stale.forEach(::deleteContact)

            DeviceContactReconcileResult(
                upserted = snapshot.contacts.size,
                migratedLegacyIds = migrated,
                removed = stale.size,
            )
        }.also { invalidateProjections() }

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
    }.also { invalidateProjections() }

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
        }.count(::deleteContact)
    }.also { invalidateProjections() }

    private fun deleteContact(contact: ContactV2): Boolean {
        // Flush the ToMany change before deleting the source row so no stale
        // handle relation can survive a provider snapshot or CardDAV tombstone.
        contact.handles.clear()
        contactBox.put(contact)
        return contactBox.remove(contact.id)
    }

    private fun sameContactAddresses(existing: ContactV2, raw: RawContact): Boolean {
        val existingKeys = existing.addresses.flatMapTo(HashSet()) { addressMatchKeys(it) }
        return raw.addresses.any { address -> addressMatchKeys(address).any(existingKeys::contains) }
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
        when (val avatar = raw.avatarUpdate) {
            AvatarUpdate.Keep -> {}
            AvatarUpdate.Clear -> contact.avatarPath = null
            is AvatarUpdate.Set -> contact.avatarPath = avatar.path
        }
        // CardDAV re-sends the whole card, so a null there is a real removal.
        // Device reads never carry these and must not clear legacy rows.
        if (isICloudContactId(raw.id)) {
            contact.nickname = raw.nickname
            contact.company = raw.company
        } else {
            raw.nickname?.let { contact.nickname = it }
            raw.company?.let { contact.company = it }
        }
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
     *
     * Matching is by current address keys, not only the persisted ToMany
     * backlink: history can create handles after CardDAV finishes, and those
     * rows should still name and illustrate the chat list.
     */
    fun contactsForHandles(): Map<Long, ContactV2> {
        return store.callInReadTx {
            val resolved = HashMap<Long, ContactV2>()
            val (emailHandles, phoneHandles) = buildHandleIndexes()
            contactsByPreference().forEach { contact ->
                handlesFor(contact, emailHandles, phoneHandles).forEach { handle ->
                    resolved.putIfAbsent(handle.id, contact)
                }
            }
            resolved
        }
    }

    /**
     * One-pass projection for list rendering, memoized until the contact or
     * handle tables change. This avoids opening a lazy backlink transaction
     * per chat row and avoids re-scanning both tables on every chat-list
     * emission.
     *
     * iCloud names win. A later native contact can still fill a missing
     * avatar so Android photos are not dropped just because CardDAV had a
     * name and no PHOTO.
     */
    fun displayInfoByHandleId(): Map<Long, HandleDisplayInfo> {
        refreshProjections()
        return cachedDisplayByHandleId
    }

    /** Address-key projection for chat identifiers and unlinked handles. */
    fun displayInfoByMatchKey(): Map<String, HandleDisplayInfo> {
        refreshProjections()
        return cachedDisplayByMatchKey
    }

    /** Stable contact ids for exact identity grouping across linked handles. */
    fun contactIdsByHandleId(): Map<Long, Long> {
        refreshProjections()
        return cachedContactIdsByHandleId
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
        val fromHandle = displayInfoByHandleId()[handle.id]
        val fromAddress = listOfNotNull(handle.formattedAddress, handle.address)
            .firstNotNullOfOrNull(::displayInfoForAddress)
        val name = fromHandle?.name
            ?: fromAddress?.name
            ?: handle.formattedAddress?.takeIf { it.isNotEmpty() }
            ?: handle.address
        return HandleDisplayInfo(
            name = name,
            avatar = fromHandle?.avatar ?: fromAddress?.avatar,
            poster = fromHandle?.poster ?: fromAddress?.poster ?: handle.posterPath,
        )
    }

    /**
     * Resolves a saved contact directly from an address, without requiring a
     * persisted Handle relation. Contact sync and message ingestion are
     * independent streams, so a notification can arrive after the contact is
     * stored but before its newly-created handle has been linked.
     */
    fun contactIdForAddress(address: String): Long? = store.callInReadTx {
        val targetKeys = addressMatchKeys(address)
        if (targetKeys.isEmpty()) return@callInReadTx null
        contactsByPreference().firstOrNull { contact ->
            contact.addresses.any { candidate ->
                addressMatchKeys(candidate).any(targetKeys::contains)
            }
        }?.id
    }

    fun displayInfoForAddress(address: String): HandleDisplayInfo? = store.callInReadTx {
        val targetKeys = addressMatchKeys(address)
        if (targetKeys.isEmpty()) return@callInReadTx null
        var merged: HandleDisplayInfo? = null
        contactsByPreference().forEach { contact ->
            val matches = contact.addresses.any { candidate ->
                addressMatchKeys(candidate).any(targetKeys::contains)
            }
            if (matches) merged = mergeDisplayInfo(merged, displayInfoOf(contact))
        }
        merged
    }

    fun preferredContacts(includeNativeContacts: Boolean = true): List<RawContact> =
        store.callInReadTx {
            val claimedAddresses = HashSet<String>()
            val nativeAvatarByKey = HashMap<String, String>()
            if (includeNativeContacts) {
                contactsByPreference().forEach { contact ->
                    if (isICloudContact(contact)) return@forEach
                    val avatar = contact.avatarPath ?: return@forEach
                    contact.addresses.forEach { address ->
                        addressMatchKeys(address).forEach { key ->
                            nativeAvatarByKey.putIfAbsent(key, avatar)
                        }
                    }
                }
            }
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
                val avatar = contact.avatarPath ?: addresses.firstNotNullOfOrNull { address ->
                    addressMatchKeys(address).firstNotNullOfOrNull(nativeAvatarByKey::get)
                }
                RawContact(
                    id = contact.nativeContactId ?: "contact:${contact.id}",
                    displayName = computedDisplayName(contact).takeIf(String::isNotEmpty),
                    firstName = contact.firstName,
                    lastName = contact.lastName,
                    avatarUpdate = avatar?.let(AvatarUpdate::Set) ?: AvatarUpdate.Clear,
                    addresses = addresses,
                )
            }
        }

    /**
     * Every stored iCloud/CardDAV contact at full fidelity, for the device
     * contacts writer. Unlike [preferredContacts] nothing is deduplicated or
     * merged across sources — the writer mirrors each card one-to-one.
     */
    fun icloudContacts(): List<RawContact> = store.callInReadTx {
        contactBox.all
            .filter { isICloudContact(it) }
            .sortedBy { it.id }
            .map { contact ->
                RawContact(
                    id = contact.nativeContactId.orEmpty(),
                    displayName = contact.displayName,
                    firstName = contact.firstName,
                    lastName = contact.lastName,
                    avatarUpdate = contact.avatarPath?.let(AvatarUpdate::Set) ?: AvatarUpdate.Clear,
                    addresses = contact.addresses,
                    nickname = contact.nickname,
                    company = contact.company,
                )
            }
    }

    private fun handlesFor(
        contact: ContactV2,
        emailHandles: Map<String, Set<Handle>>,
        phoneHandles: Map<String, Set<Handle>>,
    ): Collection<Handle> {
        val matched = matchedHandles(contact.addresses, emailHandles, phoneHandles)
        contact.handles.forEach { matched += it }
        return matched
    }

    private fun displayInfoOf(contact: ContactV2): HandleDisplayInfo = HandleDisplayInfo(
        name = computedDisplayName(contact).takeIf { it.isNotEmpty() },
        avatar = contact.avatarPath,
        poster = contact.posterPath,
    )

    private fun contactsByPreference(): List<ContactV2> =
        contactBox.all.sortedWith(contactPreferenceComparator)

    companion object {
        private const val ICLOUD_CONTACT_PREFIX = "icloud:"
        const val DEVICE_CONTACT_PREFIX = "android:lookup:"
        private const val ADDRESS_CACHE_ENTRIES = 4_096

        private class SynchronizedLruCache<K, V>(maxEntries: Int) {
            private val values = object : LinkedHashMap<K, V>(maxEntries, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?): Boolean =
                    size > maxEntries
            }

            fun getOrPut(key: K, create: () -> V): V = synchronized(values) {
                values[key] ?: create().also { values[key] = it }
            }
        }

        private val normalizedPhones = SynchronizedLruCache<String, String>(ADDRESS_CACHE_ENTRIES)
        private val phoneVariants = SynchronizedLruCache<String, Set<String>>(ADDRESS_CACHE_ENTRIES)
        private val addressKeys = SynchronizedLruCache<String, Set<String>>(ADDRESS_CACHE_ENTRIES)

        private val contactPreferenceComparator =
            compareBy<ContactV2> { contactPreference(it) }.thenBy { it.id }

        private fun contactPreference(contact: ContactV2): Int = when {
            isICloudContact(contact) -> 0
            contact.isNative -> 1
            else -> 2
        }

        fun isICloudContactId(id: String?): Boolean =
            id?.startsWith(ICLOUD_CONTACT_PREFIX) == true

        fun isStableDeviceContactId(id: String?): Boolean =
            id?.startsWith(DEVICE_CONTACT_PREFIX) == true

        fun isLegacyDeviceContactId(id: String?): Boolean =
            !id.isNullOrEmpty() && id.all(Char::isDigit)

        private fun isICloudContact(contact: ContactV2): Boolean =
            isICloudContactId(contact.nativeContactId)

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
        fun normalizePhoneNumber(phone: String): String {
            val withoutScheme = withoutAddressScheme(phone)
            return normalizedPhones.getOrPut(withoutScheme) {
                buildString(withoutScheme.length) {
                    withoutScheme.forEach { char ->
                        if (char == '+' || char in '0'..'9') append(char)
                    }
                }
            }
        }

        /** Comparable keys for one address — email or phone variants. */
        fun addressMatchKeys(address: String): Set<String> {
            val withoutScheme = withoutAddressScheme(address)
            if (withoutScheme.isEmpty()) return emptySet()
            val email = withoutScheme.contains('@')
            val normalized = if (email) {
                normalizeEmail(withoutScheme)
            } else {
                normalizePhoneNumber(withoutScheme)
            }
            if (normalized.isEmpty()) return emptySet()
            return addressKeys.getOrPut("${if (email) 'e' else 'p'}:$normalized") {
                if (email) setOf(normalized) else phoneNumberVariants(normalized)
            }
        }

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
            val normalized = normalizePhoneNumber(phone)
            if (normalized.isEmpty()) return emptySet()
            return phoneVariants.getOrPut(normalized) {
                buildSet {
                    add(normalized)
                    if (normalized.startsWith("+")) {
                        add(normalized.substring(1))
                        for (i in 1..3) {
                            if (i < normalized.length) {
                                val withoutCountryCode = normalized.substring(i + 1)
                                if (withoutCountryCode.isNotEmpty()) add(withoutCountryCode)
                            }
                        }
                    } else {
                        add("+$normalized")
                        for (i in 1..3) {
                            if (i < normalized.length) {
                                val withoutPrefix = normalized.substring(i)
                                add(withoutPrefix)
                                add("+$withoutPrefix")
                            }
                        }
                    }
                }
            }
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

        /**
         * Combines a preferred contact (iCloud first) with a fallback. Names
         * and avatars are filled independently so a named CardDAV row without
         * a PHOTO still picks up a device contact image.
         */
        fun mergeDisplayInfo(
            preferred: HandleDisplayInfo?,
            fallback: HandleDisplayInfo,
        ): HandleDisplayInfo {
            if (preferred == null) return fallback
            return HandleDisplayInfo(
                name = preferred.name ?: fallback.name,
                avatar = preferred.avatar ?: fallback.avatar,
                poster = preferred.poster ?: fallback.poster,
            )
        }

        fun displayInfoForMatchKeys(
            address: String?,
            byMatchKey: Map<String, HandleDisplayInfo>,
        ): HandleDisplayInfo? {
            if (address.isNullOrBlank()) return null
            return addressMatchKeys(address).firstNotNullOfOrNull { byMatchKey[it] }
        }
    }
}

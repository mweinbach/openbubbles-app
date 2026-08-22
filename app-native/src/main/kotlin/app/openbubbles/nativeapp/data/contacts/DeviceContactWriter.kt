package app.openbubbles.nativeapp.data.contacts

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.core.content.ContextCompat
import androidx.core.graphics.scale
import app.openbubbles.core.contacts.MergeAction
import app.openbubbles.core.contacts.MergePlan
import app.openbubbles.core.contacts.RawContact
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest

/**
 * Mirrors iCloud cards into the Android contacts store, one raw contact per
 * card under the app's own account. All decisions come from a precomputed
 * [MergePlan]; this class only reads the current provider state and applies
 * the delta, so a rerun over unchanged data is a no-op.
 *
 * The photo hash of the source avatar file rides in [RawContacts.SYNC1] so
 * change detection needs no extra store; SYNC1 is sync-adapter-private by
 * contract. Other accounts' rows are never touched — deletes and updates
 * address only rows whose raw contact carries our account type.
 */
object DeviceContactWriter {

    const val ACCOUNT_TYPE = "com.openbubbles.messaging.icloud"
    const val ACCOUNT_NAME = "iCloud"
    private const val MAX_OPS_PER_BATCH = 400
    private const val MAX_PHOTO_BYTES_PER_BATCH = 512 * 1024

    data class ExistingRawContact(
        val rawContactId: Long,
        val sourceId: String,
        val displayName: String?,
        val givenName: String?,
        val familyName: String?,
        val nickname: String?,
        val company: String?,
        val phones: Set<String>,
        val emails: Set<String>,
        val photoHash: String?,
    )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Registers the account with AccountManager (ContactsProvider purges raw
     * contacts of accounts it cannot see there) and makes groupless contacts
     * visible. Safe to call before every write pass.
     */
    fun ensureAccount(context: Context) {
        runCatching {
            AccountManager.get(context)
                .addAccountExplicitly(Account(ACCOUNT_NAME, ACCOUNT_TYPE), null, null)
        }
        runCatching {
            val values = ContentValues().apply {
                put(ContactsContract.Settings.ACCOUNT_NAME, ACCOUNT_NAME)
                put(ContactsContract.Settings.ACCOUNT_TYPE, ACCOUNT_TYPE)
                put(ContactsContract.Settings.UNGROUPED_VISIBLE, 1)
            }
            context.contentResolver.insert(
                syncAdapterUri(ContactsContract.Settings.CONTENT_URI),
                values,
            )
        }
    }

    /** Our raw contacts and managed data rows, keyed by SOURCE_ID. */
    fun readOurs(context: Context): Map<String, ExistingRawContact> {
        if (!hasPermission(context)) return emptyMap()
        val resolver = context.contentResolver
        data class Row(
            val rawContactId: Long,
            val sourceId: String,
            val photoHash: String?,
            var displayName: String? = null,
            var givenName: String? = null,
            var familyName: String? = null,
            var nickname: String? = null,
            var company: String? = null,
            val phones: MutableSet<String> = LinkedHashSet(),
            val emails: MutableSet<String> = LinkedHashSet(),
        )
        val byRawId = LinkedHashMap<Long, Row>()
        resolver.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.SOURCE_ID, RawContacts.SYNC1),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=? AND ${RawContacts.DELETED}=0",
            arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val sourceId = cursor.getString(1) ?: continue
                byRawId[cursor.getLong(0)] = Row(cursor.getLong(0), sourceId, cursor.getString(2))
            }
        }
        if (byRawId.isEmpty()) return emptyMap()
        resolver.query(
            Data.CONTENT_URI,
            arrayOf(Data.RAW_CONTACT_ID, Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=?",
            arrayOf(ACCOUNT_TYPE, ACCOUNT_NAME),
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val row = byRawId[cursor.getLong(0)] ?: continue
                when (cursor.getString(1)) {
                    StructuredName.CONTENT_ITEM_TYPE -> {
                        row.displayName = cursor.getString(2)
                        row.givenName = cursor.getString(3)
                        row.familyName = cursor.getString(4)
                    }
                    Phone.CONTENT_ITEM_TYPE -> cursor.getString(2)?.let(row.phones::add)
                    Email.CONTENT_ITEM_TYPE -> cursor.getString(2)?.let(row.emails::add)
                    Nickname.CONTENT_ITEM_TYPE -> row.nickname = cursor.getString(2)
                    Organization.CONTENT_ITEM_TYPE -> row.company = cursor.getString(2)
                }
            }
        }
        return byRawId.values.associateBy(
            { it.sourceId },
            {
                ExistingRawContact(
                    rawContactId = it.rawContactId,
                    sourceId = it.sourceId,
                    displayName = it.displayName,
                    givenName = it.givenName,
                    familyName = it.familyName,
                    nickname = it.nickname,
                    company = it.company,
                    phones = it.phones,
                    emails = it.emails,
                    photoHash = it.photoHash,
                )
            },
        )
    }

    /** Applies inserts, in-place diffs, and deletions. Returns rows written. */
    fun apply(
        context: Context,
        plan: MergePlan,
        existing: Map<String, ExistingRawContact>,
    ): Int {
        if (!hasPermission(context)) return 0
        ensureAccount(context)
        val resolver = context.contentResolver
        var changed = 0
        val photos = ContactAvatarLoader()
        val batch = ContactOperationBatcher(
            maxOperations = MAX_OPS_PER_BATCH,
            maxPayloadBytes = MAX_PHOTO_BYTES_PER_BATCH,
        ) { operations ->
            // Provider failures must escape syncNow so WorkManager retries
            // instead of claiming contacts were written when the batch was
            // rejected or the provider died.
            resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(operations))
        }

        fun add(ops: ContactOperations) {
            if (ops.operations.isEmpty()) return
            batch.add(ops.operations, ops.photoBytes)
            changed++
        }

        // Data rows point at their RawContacts op through a batch-relative
        // back reference, so an insert is rebuilt against index 0 whenever
        // appending it would overflow and force a flush first.
        fun addInsert(contact: RawContact) {
            var ops = insertOps(contact, rawContactIndex = batch.size, photos = photos)
            if (batch.wouldFlush(ops.operations.size, ops.photoBytes)) {
                batch.flush()
                ops = insertOps(contact, rawContactIndex = 0, photos = photos)
            }
            batch.add(ops.operations, ops.photoBytes)
            changed++
        }

        plan.deletions.forEach { sourceId ->
            existing[sourceId]?.let { gone ->
                add(
                    ContactOperations(
                        listOf(
                            ContentProviderOperation.newDelete(
                                syncAdapterUri(RawContacts.CONTENT_URI),
                            )
                                .withSelection("${RawContacts._ID}=?", arrayOf(gone.rawContactId.toString()))
                                .build(),
                        ),
                    ),
                )
            }
        }
        plan.actions.forEach { action ->
            when (action) {
                is MergeAction.Insert -> addInsert(action.contact)
                is MergeAction.Update -> {
                    val current = existing[action.contact.id]
                    if (current == null) addInsert(action.contact)
                    else add(updateOps(action.contact, current, photos))
                }
                is MergeAction.Skip, is MergeAction.AwaitDecision -> {}
            }
        }
        batch.flush()
        return changed
    }

    private data class ContactOperations(
        val operations: List<ContentProviderOperation>,
        val photoBytes: Int = 0,
    )

    private fun insertOps(
        contact: RawContact,
        rawContactIndex: Int,
        photos: ContactAvatarLoader,
    ): ContactOperations {
        val ops = ArrayList<ContentProviderOperation>()
        val photo = (photos.resolve(contact.avatarPath, currentHash = null) as? ContactAvatarChange.Set)?.photo
        ops += ContentProviderOperation.newInsert(syncAdapterUri(RawContacts.CONTENT_URI))
            .withValue(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .withValue(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
            .withValue(RawContacts.SOURCE_ID, contact.id)
            .withValue(RawContacts.SYNC1, photo?.hash)
            .build()
        nameValues(contact)?.let { values ->
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactIndex)
                .withValues(values)
                .build()
        }
        contact.nickname?.let { nickname ->
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
                .withValue(Nickname.NAME, nickname)
                .build()
        }
        contact.company?.let { company ->
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                .withValue(Organization.COMPANY, company)
                .build()
        }
        phones(contact).forEach { number ->
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, number)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        }
        emails(contact).forEach { address ->
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, address)
                .withValue(Email.TYPE, Email.TYPE_OTHER)
                .build()
        }
        photo?.let {
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValueBackReference(Data.RAW_CONTACT_ID, rawContactIndex)
                .withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                .withValue(Photo.PHOTO, it.bytes)
                .build()
        }
        return ContactOperations(ops, photoBytes = photo?.bytes?.size ?: 0)
    }

    /** Only the rows that changed; an unchanged contact produces no ops. */
    private fun updateOps(
        contact: RawContact,
        current: ExistingRawContact,
        photos: ContactAvatarLoader,
    ): ContactOperations {
        val ops = ArrayList<ContentProviderOperation>()
        val rawId = current.rawContactId
        var photoBytes = 0

        val wantedName = nameValues(contact)
        val nameChanged = (wantedName?.getAsString(StructuredName.DISPLAY_NAME) != current.displayName) ||
            (wantedName?.getAsString(StructuredName.GIVEN_NAME) != current.givenName) ||
            (wantedName?.getAsString(StructuredName.FAMILY_NAME) != current.familyName)
        if (nameChanged) ops += replaceSingleRow(rawId, StructuredName.CONTENT_ITEM_TYPE, wantedName)

        if (contact.nickname != current.nickname) {
            ops += replaceSingleRow(
                rawId,
                Nickname.CONTENT_ITEM_TYPE,
                contact.nickname?.let {
                    ContentValues().apply {
                        put(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE)
                        put(Nickname.NAME, it)
                    }
                },
            )
        }
        if (contact.company != current.company) {
            ops += replaceSingleRow(
                rawId,
                Organization.CONTENT_ITEM_TYPE,
                contact.company?.let {
                    ContentValues().apply {
                        put(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                        put(Organization.COMPANY, it)
                    }
                },
            )
        }

        val wantedPhones = phones(contact).toSet()
        (current.phones - wantedPhones).forEach { ops += deleteValueRow(rawId, Phone.CONTENT_ITEM_TYPE, it) }
        (wantedPhones - current.phones).forEach { number ->
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValue(Data.RAW_CONTACT_ID, rawId)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                .withValue(Phone.NUMBER, number)
                .withValue(Phone.TYPE, Phone.TYPE_MOBILE)
                .build()
        }
        val wantedEmails = emails(contact).toSet()
        (current.emails - wantedEmails).forEach { ops += deleteValueRow(rawId, Email.CONTENT_ITEM_TYPE, it) }
        (wantedEmails - current.emails).forEach { address ->
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValue(Data.RAW_CONTACT_ID, rawId)
                .withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE)
                .withValue(Email.ADDRESS, address)
                .withValue(Email.TYPE, Email.TYPE_OTHER)
                .build()
        }

        when (val photo = photos.resolve(contact.avatarPath, current.photoHash)) {
            ContactAvatarChange.Unchanged -> Unit
            ContactAvatarChange.Clear -> {
                ops += replaceSingleRow(rawId, Photo.CONTENT_ITEM_TYPE, values = null)
                ops += photoHashUpdate(rawId, hash = null)
            }
            is ContactAvatarChange.Set -> {
                photoBytes = photo.photo.bytes.size
                ops += replaceSingleRow(
                    rawId,
                    Photo.CONTENT_ITEM_TYPE,
                    ContentValues().apply {
                        put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                        put(Photo.PHOTO, photo.photo.bytes)
                    },
                )
                ops += photoHashUpdate(rawId, photo.photo.hash)
            }
        }
        return ContactOperations(ops, photoBytes)
    }

    private fun photoHashUpdate(rawContactId: Long, hash: String?): ContentProviderOperation =
        ContentProviderOperation.newUpdate(syncAdapterUri(RawContacts.CONTENT_URI))
            .withSelection("${RawContacts._ID}=?", arrayOf(rawContactId.toString()))
            .withValue(RawContacts.SYNC1, hash)
            .build()

    private fun replaceSingleRow(
        rawContactId: Long,
        mimeType: String,
        values: ContentValues?,
    ): List<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        ops += ContentProviderOperation.newDelete(syncAdapterUri(Data.CONTENT_URI))
            .withSelection(
                "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?",
                arrayOf(rawContactId.toString(), mimeType),
            )
            .build()
        values?.let {
            ops += ContentProviderOperation.newInsert(syncAdapterUri(Data.CONTENT_URI))
                .withValue(Data.RAW_CONTACT_ID, rawContactId)
                .withValues(it)
                .build()
        }
        return ops
    }

    private fun deleteValueRow(
        rawContactId: Long,
        mimeType: String,
        value: String,
    ): ContentProviderOperation =
        ContentProviderOperation.newDelete(syncAdapterUri(Data.CONTENT_URI))
            .withSelection(
                "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=? AND ${Data.DATA1}=?",
                arrayOf(rawContactId.toString(), mimeType, value),
            )
            .build()

    private fun nameValues(contact: RawContact): ContentValues? {
        val display = contact.displayName
            ?: "${contact.firstName.orEmpty()} ${contact.lastName.orEmpty()}".trim().takeIf(String::isNotEmpty)
        if (display == null && contact.firstName == null && contact.lastName == null) return null
        return ContentValues().apply {
            put(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
            put(StructuredName.DISPLAY_NAME, display)
            put(StructuredName.GIVEN_NAME, contact.firstName)
            put(StructuredName.FAMILY_NAME, contact.lastName)
        }
    }

    private fun phones(contact: RawContact): List<String> =
        contact.addresses.filterNot { it.contains('@') }.filter { it.isNotBlank() }

    private fun emails(contact: RawContact): List<String> =
        contact.addresses.filter { it.contains('@') }

    private fun syncAdapterUri(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
        .build()
}

/**
 * Small testable boundary around ContactsProvider batching. Photo payload is
 * budgeted separately from operation count because a handful of blobs can
 * exceed Binder's transaction buffer long before 400 operations do.
 */
internal class ContactOperationBatcher<T>(
    private val maxOperations: Int,
    private val maxPayloadBytes: Int,
    private val applyBatch: (List<T>) -> Unit,
) {
    private val pending = ArrayList<T>()
    private var payloadBytes = 0

    init {
        require(maxOperations > 0)
        require(maxPayloadBytes > 0)
    }

    val size: Int get() = pending.size

    fun wouldFlush(operationCount: Int, addedPayloadBytes: Int): Boolean =
        pending.isNotEmpty() &&
            (pending.size > maxOperations - operationCount ||
                payloadBytes > maxPayloadBytes - addedPayloadBytes)

    fun add(operations: List<T>, addedPayloadBytes: Int = 0) {
        if (operations.isEmpty()) return
        require(addedPayloadBytes in 0..maxPayloadBytes) {
            "contact photo payload exceeds batch limit"
        }
        require(operations.size <= maxOperations) { "contact update has too many operations" }
        if (wouldFlush(operations.size, addedPayloadBytes)) flush()
        pending += operations
        payloadBytes += addedPayloadBytes
    }

    fun flush() {
        if (pending.isEmpty()) return
        // Deliberately clear only after success. Callers see the provider
        // exception and can retry the whole convergent merge pass.
        applyBatch(ArrayList(pending))
        pending.clear()
        payloadBytes = 0
    }
}

internal data class ContactAvatarPhoto(
    val bytes: ByteArray,
    val hash: String,
    val width: Int,
    val height: Int,
)

internal sealed interface ContactAvatarChange {
    data object Unchanged : ContactAvatarChange
    data object Clear : ContactAvatarChange
    data class Set(val photo: ContactAvatarPhoto) : ContactAvatarChange
}

/**
 * Per-apply-pass avatar resolver. It hashes source files as streams and does
 * not decode or JPEG-compress when SYNC1 already holds the same source hash.
 * Multiple contacts that reference one iCloud avatar also share both work.
 */
internal class ContactAvatarLoader(
    private val maxDecodedBytes: Int = DEFAULT_AVATAR_CACHE_BYTES,
    private val maxDimension: Int = MAX_CONTACT_PHOTO_DIM,
    private val decode: (File, String) -> ContactAvatarPhoto? = ::decodeContactAvatar,
) {
    private data class Source(val file: File, val hash: String)

    private val sources = HashMap<String, Source?>()
    private val decoded = LinkedHashMap<String, ContactAvatarPhoto>(16, 0.75f, true)
    private val decodeFailures = HashSet<String>()
    private var decodedBytes = 0

    init {
        require(maxDecodedBytes >= 0)
        require(maxDimension > 0)
    }

    fun resolve(avatarPath: String?, currentHash: String?): ContactAvatarChange {
        val file = avatarPath?.let(::File)?.takeIf(File::isFile)
            ?: return if (currentHash == null) ContactAvatarChange.Unchanged else ContactAvatarChange.Clear
        val key = runCatching { file.canonicalPath }.getOrElse { file.absolutePath }
        if (key !in sources) sources[key] = streamHash(file)?.let { Source(file, it) }
        val source = sources[key]
            ?: return if (currentHash == null) ContactAvatarChange.Unchanged else ContactAvatarChange.Clear
        if (source.hash == currentHash) return ContactAvatarChange.Unchanged
        if (key in decodeFailures) {
            return if (currentHash == null) ContactAvatarChange.Unchanged else ContactAvatarChange.Clear
        }
        val photo = decoded[key] ?: decode(source.file, source.hash)
            ?.takeIf { candidate ->
                candidate.width in 1..maxDimension &&
                    candidate.height in 1..maxDimension &&
                    candidate.bytes.size <= MAX_CONTACT_PHOTO_BYTES
            }
            ?.also { candidate -> cache(key, candidate) }
            ?: run {
                decodeFailures += key
                return if (currentHash == null) ContactAvatarChange.Unchanged else ContactAvatarChange.Clear
            }
        return ContactAvatarChange.Set(photo)
    }

    private fun cache(key: String, photo: ContactAvatarPhoto) {
        if (photo.bytes.size > maxDecodedBytes) return
        while (decodedBytes > maxDecodedBytes - photo.bytes.size && decoded.isNotEmpty()) {
            val iterator = decoded.entries.iterator()
            val eldest = iterator.next()
            decodedBytes -= eldest.value.bytes.size
            iterator.remove()
        }
        decoded[key] = photo
        decodedBytes += photo.bytes.size
    }
}

private fun streamHash(file: File): String? = runCatching {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
    }
    digest.digest().joinToString("") { "%02x".format(it) }
}.getOrNull()

/** Bounds a changed avatar for the provider; unchanged files never reach here. */
private fun decodeContactAvatar(file: File, hash: String): ContactAvatarPhoto? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > MAX_CONTACT_PHOTO_DIM ||
        bounds.outHeight / sample > MAX_CONTACT_PHOTO_DIM) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    val scale = minOf(
        1f,
        MAX_CONTACT_PHOTO_DIM.toFloat() / maxOf(decoded.width, decoded.height),
    )
    val bounded = if (scale < 1f) {
        decoded.scale(
            maxOf(1, (decoded.width * scale).toInt()),
            maxOf(1, (decoded.height * scale).toInt()),
            filter = true,
        ).also { decoded.recycle() }
    } else {
        decoded
    }
    try {
        val encoded = encodeBoundedContactAvatar(bounded) ?: return null
        ContactAvatarPhoto(encoded.bytes, hash, encoded.width, encoded.height)
    } finally {
        bounded.recycle()
    }
}.getOrNull()

private data class EncodedAvatar(val bytes: ByteArray, val width: Int, val height: Int)

private fun encodeBoundedContactAvatar(bitmap: Bitmap): EncodedAvatar? {
    var current = bitmap
    var ownsCurrent = false
    try {
        while (true) {
            for (quality in intArrayOf(90, 80, 70, 60, 50)) {
                val output = ByteArrayOutputStream()
                if (!current.compress(Bitmap.CompressFormat.JPEG, quality, output)) return null
                val bytes = output.toByteArray()
                if (bytes.size <= MAX_CONTACT_PHOTO_BYTES) {
                    return EncodedAvatar(bytes, current.width, current.height)
                }
            }
            if (maxOf(current.width, current.height) <= MIN_CONTACT_PHOTO_DIM) return null
            val scale = 0.75f
            val smaller = current.scale(
                maxOf(MIN_CONTACT_PHOTO_DIM, (current.width * scale).toInt()),
                maxOf(MIN_CONTACT_PHOTO_DIM, (current.height * scale).toInt()),
                filter = true,
            )
            if (ownsCurrent) current.recycle()
            current = smaller
            ownsCurrent = true
        }
    } finally {
        if (ownsCurrent) current.recycle()
    }
}

private const val MAX_CONTACT_PHOTO_DIM = 720
private const val MIN_CONTACT_PHOTO_DIM = 96
private const val MAX_CONTACT_PHOTO_BYTES = 256 * 1024
private const val DEFAULT_AVATAR_CACHE_BYTES = 4 * 1024 * 1024

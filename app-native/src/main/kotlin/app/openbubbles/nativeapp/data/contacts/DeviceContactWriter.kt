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
import android.util.Log
import androidx.core.content.ContextCompat
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
    private const val TAG = "DeviceContactWriter"
    private const val MAX_OPS_PER_BATCH = 400
    private const val MAX_PHOTO_DIM = 720

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
        val batch = ArrayList<ContentProviderOperation>()

        fun flush() {
            if (batch.isEmpty()) return
            runCatching { resolver.applyBatch(ContactsContract.AUTHORITY, ArrayList(batch)) }
                .onFailure { Log.w(TAG, "contact batch failed: ${it.message}") }
            batch.clear()
        }

        // A raw contact's ops must land in one batch: inserts reference the
        // RawContacts op by back-reference index.
        fun add(ops: List<ContentProviderOperation>) {
            if (ops.isEmpty()) return
            if (batch.size + ops.size > MAX_OPS_PER_BATCH) flush()
            batch += ops
            changed++
        }

        plan.deletions.forEach { sourceId ->
            existing[sourceId]?.let { gone ->
                add(
                    listOf(
                        ContentProviderOperation.newDelete(
                            syncAdapterUri(RawContacts.CONTENT_URI),
                        )
                            .withSelection("${RawContacts._ID}=?", arrayOf(gone.rawContactId.toString()))
                            .build(),
                    ),
                )
            }
        }
        plan.actions.forEach { action ->
            when (action) {
                is MergeAction.Insert -> add(insertOps(action.contact))
                is MergeAction.Update -> {
                    val current = existing[action.contact.id]
                    if (current == null) add(insertOps(action.contact))
                    else add(updateOps(action.contact, current))
                }
                is MergeAction.Skip, is MergeAction.AwaitDecision -> {}
            }
        }
        flush()
        return changed
    }

    private fun insertOps(contact: RawContact): List<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        val photo = loadPhoto(contact.avatarPath)
        ops += ContentProviderOperation.newInsert(syncAdapterUri(RawContacts.CONTENT_URI))
            .withValue(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
            .withValue(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
            .withValue(RawContacts.SOURCE_ID, contact.id)
            .withValue(RawContacts.SYNC1, photo?.hash)
            .build()
        val rawContactIndex = 0
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
        return ops
    }

    /** Only the rows that changed; an unchanged contact produces no ops. */
    private fun updateOps(
        contact: RawContact,
        current: ExistingRawContact,
    ): List<ContentProviderOperation> {
        val ops = ArrayList<ContentProviderOperation>()
        val rawId = current.rawContactId

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

        val photo = loadPhoto(contact.avatarPath)
        if (photo?.hash != current.photoHash) {
            ops += replaceSingleRow(
                rawId,
                Photo.CONTENT_ITEM_TYPE,
                photo?.let {
                    ContentValues().apply {
                        put(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE)
                        put(Photo.PHOTO, it.bytes)
                    }
                },
            )
            ops += ContentProviderOperation.newUpdate(syncAdapterUri(RawContacts.CONTENT_URI))
                .withSelection("${RawContacts._ID}=?", arrayOf(rawId.toString()))
                .withValue(RawContacts.SYNC1, photo?.hash)
                .build()
        }
        return ops
    }

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

    private class LoadedPhoto(val bytes: ByteArray, val hash: String)

    /**
     * Decodes and bounds the avatar so a photo data row stays well under the
     * binder transaction limit; the provider derives its own thumbnail.
     */
    private fun loadPhoto(avatarPath: String?): LoadedPhoto? {
        val file = avatarPath?.let(::File)?.takeIf(File::isFile) ?: return null
        return runCatching {
            val source = file.readBytes()
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(source)
                .joinToString("") { "%02x".format(it) }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= MAX_PHOTO_DIM ||
                bounds.outHeight / (sample * 2) >= MAX_PHOTO_DIM
            ) {
                sample *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(source, 0, source.size, options) ?: return null
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            bitmap.recycle()
            LoadedPhoto(output.toByteArray(), hash)
        }.getOrNull()
    }

    private fun syncAdapterUri(uri: Uri): Uri = uri.buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(RawContacts.ACCOUNT_NAME, ACCOUNT_NAME)
        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, ACCOUNT_TYPE)
        .build()
}

package app.openbubbles.nativeapp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import app.openbubbles.core.contacts.AvatarUpdate
import app.openbubbles.core.contacts.ContactSync
import app.openbubbles.core.contacts.DeviceContactSnapshot
import app.openbubbles.core.contacts.RawContact
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface DeviceContactsReadResult {
    data class Success(val snapshot: DeviceContactSnapshot) : DeviceContactsReadResult
    data object PermissionDenied : DeviceContactsReadResult
    data class Failure(val cause: Throwable) : DeviceContactsReadResult
}

/**
 * Runs reconciliation only for a complete successful provider snapshot.
 * In particular, successful empty snapshots are authoritative while denied
 * and failed reads are not.
 */
internal inline fun DeviceContactsReadResult.applySuccessfulSnapshot(
    reconcile: (DeviceContactSnapshot) -> Unit,
): Boolean = when (this) {
    is DeviceContactsReadResult.Success -> {
        reconcile(snapshot)
        true
    }
    DeviceContactsReadResult.PermissionDenied,
    is DeviceContactsReadResult.Failure,
    -> false
}

internal fun stableAndroidContactId(lookupKey: String): String =
    ContactSync.DEVICE_CONTACT_PREFIX + lookupKey

internal data class ContactProviderRevision(
    val rowId: String,
    val lookupKey: String,
    val updatedAtMillis: Long,
)

internal fun providerSnapshotIsStable(
    before: Collection<ContactProviderRevision>,
    after: Collection<ContactProviderRevision>,
): Boolean = before.toSet() == after.toSet()

/**
 * Reads the device contacts provider into [RawContact] rows for
 * [app.openbubbles.core.contacts.ContactSync]. Avatars are referenced by
 * lookup URI; ContactSync stores whatever path string we hand it, so we
 * pass the photo URI when present.
 */
object DeviceContacts {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun read(context: Context): DeviceContactsReadResult = withContext(Dispatchers.IO) {
        if (!hasPermission(context)) {
            DeviceContactsReadResult.PermissionDenied
        } else {
            runCatching { DeviceContactsReadResult.Success(readLocked(context)) }
                .getOrElse(DeviceContactsReadResult::Failure)
        }
    }

    private fun readLocked(context: Context): DeviceContactSnapshot {
        repeat(2) {
            val rows = readContactRows(context)
            val addressesById = readAddresses(context)
            val revisionsAfterAddresses = readContactRows(context).map(ContactProviderRow::revision)
            if (providerSnapshotIsStable(rows.map(ContactProviderRow::revision), revisionsAfterAddresses)) {
                return buildSnapshot(rows, addressesById)
            }
        }
        error("ContactsProvider changed while reading contacts")
    }

    private fun buildSnapshot(
        rows: List<ContactProviderRow>,
        addressesById: Map<String, List<String>>,
    ): DeviceContactSnapshot {
        val out = mutableListOf<RawContact>()
        val legacyIds = LinkedHashMap<String, String>()
        rows.forEach { row ->
            val addresses = addressesById[row.rowId].orEmpty()
            if (addresses.isEmpty()) return@forEach // unreachable over iMessage/SMS anyway
            val id = stableAndroidContactId(row.lookupKey)
            val (first, last) = splitName(row.name)
            out += RawContact(
                id = id,
                displayName = row.name,
                firstName = first,
                lastName = last,
                avatarUpdate = row.photo?.let(AvatarUpdate::Set) ?: AvatarUpdate.Clear,
                addresses = addresses,
            )
            legacyIds[id] = row.rowId
        }
        return DeviceContactSnapshot(
            contacts = out,
            legacyNativeIds = legacyIds,
        )
    }

    private fun readContactRows(context: Context): List<ContactProviderRow> {
        val cursor = context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.PHOTO_URI,
                ContactsContract.Contacts.PHOTO_THUMBNAIL_URI,
                ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
            ),
            null, null, null,
        ) ?: error("ContactsProvider returned no contacts cursor")
        return cursor.use {
            buildList {
                while (cursor.moveToNext()) {
                    val rowId = cursor.getString(0)
                        ?: error("ContactsProvider returned a contact without _ID")
                    val lookupKey = cursor.getString(1)?.takeIf(String::isNotBlank)
                        ?: error("ContactsProvider returned contact $rowId without LOOKUP_KEY")
                    val name = cursor.getString(2)
                    val photo = cursor.getString(3)?.takeIf { it.isNotBlank() }
                        ?: cursor.getString(4)?.takeIf { it.isNotBlank() }
                    add(
                        ContactProviderRow(
                            rowId = rowId,
                            lookupKey = lookupKey,
                            name = name,
                            photo = photo,
                            updatedAtMillis = cursor.getLong(5),
                        ),
                    )
                }
            }
        }
    }

    private fun readAddresses(context: Context): Map<String, List<String>> {
        val byId = mutableMapOf<String, MutableList<String>>()
        val dataCursor = context.contentResolver.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(
                ContactsContract.Data.CONTACT_ID,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
                ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ),
            "${ContactsContract.Data.MIMETYPE} IN (?, ?)",
            arrayOf(
                ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE,
                ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
            ),
            null,
        ) ?: error("ContactsProvider returned no address cursor")
        dataCursor.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getString(0) ?: continue
                val address = preferredContactAddress(
                    mimeType = cursor.getString(1),
                    rawAddress = cursor.getString(2),
                    normalizedPhone = cursor.getString(3),
                ) ?: continue
                byId.getOrPut(id) { mutableListOf() }.add(address)
            }
        }
        return byId.mapValues { (_, addresses) -> addresses.distinct() }
    }

    private fun splitName(displayName: String?): Pair<String?, String?> {
        displayName ?: return null to null
        return when (displayName.count { it == ' ' }) {
            0 -> displayName to null
            else -> {
                val idx = displayName.indexOf(' ')
                displayName.substring(0, idx) to displayName.substring(idx + 1)
            }
        }
    }

    private data class ContactProviderRow(
        val rowId: String,
        val lookupKey: String,
        val name: String?,
        val photo: String?,
        val updatedAtMillis: Long,
    ) {
        fun revision(): ContactProviderRevision =
            ContactProviderRevision(rowId, lookupKey, updatedAtMillis)
    }
}

internal fun preferredContactAddress(
    mimeType: String?,
    rawAddress: String?,
    normalizedPhone: String?,
): String? = when (mimeType) {
    ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE ->
        normalizedPhone?.takeIf(String::isNotBlank) ?: rawAddress?.takeIf(String::isNotBlank)
    ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE -> rawAddress?.takeIf(String::isNotBlank)
    else -> null
}
